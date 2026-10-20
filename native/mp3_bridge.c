#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <lame.h>

static void throw_error(JNIEnv *env, const char *message) {
    jclass cls = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (cls) (*env)->ThrowNew(env, cls, message);
}

JNIEXPORT jlong JNICALL
Java_pl_tomasz_tvaudiorec_Mp3Native_create(JNIEnv *env, jobject instance,
                                             jint sample_rate, jint channels, jint kbps) {
    if (sample_rate < 8000 || sample_rate > 48000 ||
        (channels != 1 && channels != 2) ||
        kbps < 8 || kbps > 320) {
        throw_error(env, "Nieprawidłowe ustawienia MP3");
        return 0;
    }
    lame_t gfp = lame_init();
    if (!gfp) { throw_error(env, "Nie można zainicjalizować LAME"); return 0; }
    int max_kbps = sample_rate <= 16000 ? 64 : (sample_rate < 32000 ? 160 : 320);
    if (kbps > max_kbps) kbps = max_kbps;
    lame_set_in_samplerate(gfp, sample_rate);
    lame_set_num_channels(gfp, channels);
    lame_set_brate(gfp, kbps);
    lame_set_mode(gfp, channels == 1 ? MONO : JOINT_STEREO);
    lame_set_quality(gfp, 5);
    lame_set_bWriteVbrTag(gfp, 0);
    if (lame_init_params(gfp) < 0) {
        lame_close(gfp);
        throw_error(env, "Nie można skonfigurować MP3 dla tego kHz/bitrate");
        return 0;
    }
    return (jlong)(intptr_t)gfp;
}

JNIEXPORT jbyteArray JNICALL
Java_pl_tomasz_tvaudiorec_Mp3Native_encode(JNIEnv *env, jobject instance,
                                             jlong pointer, jbyteArray pcm, jint size) {
    lame_t gfp = (lame_t)(intptr_t)pointer;
    if (!gfp || !pcm || size < 0 || size > (*env)->GetArrayLength(env, pcm)) {
        throw_error(env, "Nieprawidłowy bufor audio");
        return NULL;
    }
    int channels = lame_get_num_channels(gfp);
    int sample_count = size / (channels * 2);
    if (sample_count <= 0) return (*env)->NewByteArray(env, 0);
    jbyte *input = (*env)->GetByteArrayElements(env, pcm, NULL);
    if (!input) return NULL;
    short *samples = (short*)malloc((size_t)sample_count * (size_t)channels * sizeof(short));
    if (!samples) {
        (*env)->ReleaseByteArrayElements(env, pcm, input, JNI_ABORT);
        throw_error(env, "Brak pamięci dla MP3");
        return NULL;
    }
    for (int i = 0; i < sample_count * channels; ++i) {
        const unsigned char lo = (unsigned char)input[i*2];
        const unsigned char hi = (unsigned char)input[i*2+1];
        samples[i] = (short)((hi << 8) | lo);
    }
    (*env)->ReleaseByteArrayElements(env, pcm, input, JNI_ABORT);
    int capacity = (int)(1.25 * sample_count + 7200);
    unsigned char *out = (unsigned char*)malloc((size_t)capacity);
    if (!out) {
        free(samples);
        throw_error(env, "Brak pamięci na bufor MP3");
        return NULL;
    }
    int length;
    if (channels == 2)
        length = lame_encode_buffer_interleaved(gfp, samples, sample_count, out, capacity);
    else
        length = lame_encode_buffer(gfp, samples, samples, sample_count, out, capacity);
    free(samples);
    if (length < 0) {
        free(out);
        throw_error(env, "Błąd kodowania strumienia LAME MP3");
        return NULL;
    }
    jbyteArray data = (*env)->NewByteArray(env, length);
    if (data && length > 0)
        (*env)->SetByteArrayRegion(env, data, 0, length, (const jbyte*)out);
    free(out);
    return data;
}

JNIEXPORT jbyteArray JNICALL
Java_pl_tomasz_tvaudiorec_Mp3Native_flush(JNIEnv *env, jobject instance, jlong pointer) {
    lame_t gfp = (lame_t)(intptr_t)pointer;
    if (!gfp) {throw_error(env, "Brak enkodera"); return NULL;}
    unsigned char output[8192];
    int length = lame_encode_flush(gfp, output, (int)sizeof(output));
    if (length < 0) {
        throw_error(env, "Błąd zamykania pliku MP3");
        return NULL;
    }
    jbyteArray data = (*env)->NewByteArray(env, length);
    if (data && length > 0)
        (*env)->SetByteArrayRegion(env, data, 0, length, (const jbyte*)output);
    return data;
}

JNIEXPORT void JNICALL
Java_pl_tomasz_tvaudiorec_Mp3Native_destroy(JNIEnv *env, jobject instance, jlong pointer) {
    lame_t gfp = (lame_t)(intptr_t)pointer;
    if (gfp) lame_close(gfp);
}
