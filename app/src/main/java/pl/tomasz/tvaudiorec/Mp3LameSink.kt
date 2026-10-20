package pl.tomasz.tvaudiorec

import android.os.ParcelFileDescriptor
import java.io.FileOutputStream

/** Self-contained MP3 encoding using the bundled LGPL LAME native library.
 * No system MediaCodec MP3 encoder is required.
 */
internal object Mp3Native {
    init { System.loadLibrary("tvmp3") }
    external fun create(sampleRate: Int, channels: Int, bitrateKbps: Int): Long
    external fun encode(handle: Long, pcm: ByteArray, size: Int): ByteArray
    external fun flush(handle: Long): ByteArray
    external fun destroy(handle: Long)
    fun available(): Boolean = try {
        System.loadLibrary("tvmp3")
        true
    } catch (_: UnsatisfiedLinkError) { false }
}

class Mp3LameSink(
    fd: ParcelFileDescriptor,
    sampleRate: Int,
    channels: Int,
    bitrate: Int
) : AudioSink {
    private val stream = FileOutputStream(fd.fileDescriptor)
    private var encoder = Mp3Native.create(sampleRate, channels, bitrate / 1000)
    init { check(encoder != 0L) { "Nie można uruchomić enkodera LAME MP3" } }
    override fun write(buffer: ByteArray, length: Int) {
        check(encoder != 0L) { "Enkoder MP3 został zamknięty" }
        if (length > 0) {
            val compressed = Mp3Native.encode(encoder, buffer, length)
            if (compressed.isNotEmpty()) stream.write(compressed)
        }
    }
    override fun finish() {
        check(encoder != 0L) { "Enkoder MP3 został zamknięty" }
        val remaining = Mp3Native.flush(encoder)
        if (remaining.isNotEmpty()) stream.write(remaining)
        stream.flush()
    }
    override fun close() {
        if (encoder != 0L) {
            Mp3Native.destroy(encoder)
            encoder = 0L
        }
        try { stream.flush() } catch (_: Exception) {}
    }
}
