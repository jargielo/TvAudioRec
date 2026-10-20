#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <opus.h>

typedef struct {
    OpusEncoder *encoder;
    uint32_t serial;
    uint32_t sequence;
    uint64_t samples;
    int channels;
} TVOpus;

static void fail(JNIEnv *env,const char *msg){
    jclass ex=(*env)->FindClass(env,"java/lang/IllegalStateException");
    if(ex)(*env)->ThrowNew(env,ex,msg);
}
static void u32(unsigned char *buf,uint32_t value){
    for(int i=0;i<4;i++)buf[i]=(unsigned char)(value>>(8*i));
}
static void u64(unsigned char *buf,uint64_t value){
    for(int i=0;i<8;i++)buf[i]=(unsigned char)(value>>(8*i));
}
static uint32_t crc_ogg(unsigned char *bytes,int length){
    uint32_t crc=0;
    for(int i=0;i<length;i++){
        crc^=((uint32_t)bytes[i])<<24;
        for(int j=0;j<8;j++)crc=(crc&0x80000000u)?(crc<<1)^0x04c11db7u:(crc<<1);
    }
    return crc;
}
/* Single packet per Ogg page, padded lacing entry for exact multiples of 255. */
static int make_page(TVOpus *w,const unsigned char *packet,int length,
                     int headerType,uint64_t granule,unsigned char *out,int capacity){
    if(length<0 || length>65000)return -1;
    int segments=length/255+1;
    int size=27+segments+length;
    if(size>capacity)return -2;
    memset(out,0,(size_t)size);
    memcpy(out,"OggS",4);
    out[4]=0;
    out[5]=(unsigned char)headerType;
    u64(out+6,granule);
    u32(out+14,w->serial);
    u32(out+18,w->sequence++);
    out[26]=(unsigned char)segments;
    for(int i=0;i<segments;i++)out[27+i]=(unsigned char)(
        (i==segments-1) ? length%255 : 255
    );
    if(length)memcpy(out+27+segments,packet,(size_t)length);
    uint32_t crc=crc_ogg(out,size);
    u32(out+22,crc);
    return size;
}
static jbyteArray to_java(JNIEnv *env,const unsigned char *buf,int count){
    jbyteArray arr=(*env)->NewByteArray(env,count);
    if(arr && count) (*env)->SetByteArrayRegion(env,arr,0,count,(const jbyte*)buf);
    return arr;
}
JNIEXPORT jlong JNICALL
Java_pl_tomasz_tvaudiorec_OpusNative_create(JNIEnv *env,jobject obj,jint channels,jint bitrate){
    if((channels!=1 && channels!=2) || bitrate<12000 || bitrate>256000){
        fail(env,"Nieprawidłowe parametry Opus");return 0;
    }
    TVOpus *w=(TVOpus*)calloc(1,sizeof(TVOpus));
    if(!w){fail(env,"Brak pamięci dla Opus");return 0;}
    int status=0;
    w->encoder=opus_encoder_create(48000,channels,OPUS_APPLICATION_AUDIO,&status);
    if(!w->encoder || status!=OPUS_OK){
        free(w);fail(env,"Nie można uruchomić biblioteki Opus");return 0;
    }
    opus_encoder_ctl(w->encoder,OPUS_SET_BITRATE(bitrate));
    opus_encoder_ctl(w->encoder,OPUS_SET_VBR(1));
    opus_encoder_ctl(w->encoder,OPUS_SET_COMPLEXITY(8));
    w->channels=channels;
    w->serial=0x41555232u;
    return (jlong)(intptr_t)w;
}
JNIEXPORT jbyteArray JNICALL
Java_pl_tomasz_tvaudiorec_OpusNative_header(JNIEnv *env,jobject obj,jlong ptr){
    TVOpus *w=(TVOpus*)(intptr_t)ptr;
    if(!w){fail(env,"Brak kodeka Opus");return NULL;}
    unsigned char head[19]={0};
    memcpy(head,"OpusHead",8);
    head[8]=1;
    head[9]=(unsigned char)w->channels;
    head[10]=0x38;head[11]=0x01; /* pre-skip 312 */
    u32(head+12,48000);
    head[18]=0;
    const char *vendor="AudioRelay Recorder";
    uint32_t vlen=(uint32_t)strlen(vendor);
    unsigned char tags[128]={0};
    memcpy(tags,"OpusTags",8);
    u32(tags+8,vlen);
    memcpy(tags+12,vendor,vlen);
    u32(tags+12+vlen,0);
    unsigned char output[512];
    int a=make_page(w,head,19,2,0,output,sizeof(output));
    int b=make_page(w,tags,16+(int)vlen,0,0,output+a,sizeof(output)-a);
    if(a<0 || b<0){fail(env,"Błąd nagłówka Ogg Opus");return NULL;}
    return to_java(env,output,a+b);
}
JNIEXPORT jbyteArray JNICALL
Java_pl_tomasz_tvaudiorec_OpusNative_encode(JNIEnv *env,jobject obj,jlong ptr,jbyteArray pcm,jboolean eos){
    TVOpus *w=(TVOpus*)(intptr_t)ptr;
    if(!w || !pcm){fail(env,"Nieprawidłowy bufor Opus");return NULL;}
    int count=960*w->channels*2;
    if((*env)->GetArrayLength(env,pcm)!=count){
        fail(env,"Opus wymaga 20 ms dźwięku PCM 48 kHz");return NULL;
    }
    jbyte *data=(*env)->GetByteArrayElements(env,pcm,NULL);
    if(!data)return NULL;
    opus_int16 frame[1920];
    for(int i=0;i<960*w->channels;i++)
        frame[i]=(opus_int16)(
            (uint8_t)data[2*i] | ((uint16_t)(uint8_t)data[2*i+1]<<8)
        );
    (*env)->ReleaseByteArrayElements(env,pcm,data,JNI_ABORT);
    unsigned char encoded[4096];
    int len=opus_encode(w->encoder,frame,960,encoded,sizeof(encoded));
    if(len<0){fail(env,"Błąd kompresji Opus");return NULL;}
    w->samples+=960;
    unsigned char out[8192];
    int wrote=make_page(w,encoded,len,eos?4:0,w->samples+312,out,sizeof(out));
    if(wrote<0){fail(env,"Błąd kontenera Ogg");return NULL;}
    return to_java(env,out,wrote);
}
JNIEXPORT void JNICALL
Java_pl_tomasz_tvaudiorec_OpusNative_release(JNIEnv *env,jobject obj,jlong ptr){
    TVOpus *w=(TVOpus*)(intptr_t)ptr;
    if(!w)return;
    if(w->encoder)opus_encoder_destroy(w->encoder);
    free(w);
}
