package pl.tomasz.tvaudiorec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.min

interface AudioSink {
    fun write(buffer: ByteArray, length: Int)
    fun finish()
    fun close()
}

object EncoderSupport {
    fun mp3Available(): Boolean = try {
        val codec=MediaCodec.createEncoderByType("audio/mpeg")
        codec.release()
        true
    } catch (_: Exception) { false }
}

class WavSink(fd: ParcelFileDescriptor, private val rate: Int, private val channels: Int) : AudioSink {
    private val stream=FileOutputStream(fd.fileDescriptor)
    private var bytes=0L
    init { stream.write(ByteArray(44)) }
    override fun write(buffer: ByteArray, length: Int) {
        if (length>0) { stream.write(buffer,0,length); bytes+=length }
    }
    override fun finish() {
        val h=ByteArrayOutputStream()
        h.write("RIFF".toByteArray()); le(h, (36+bytes).toInt(),4)
        h.write("WAVEfmt ".toByteArray()); le(h,16,4); le(h,1,2)
        le(h,channels,2); le(h,rate,4); le(h,rate*channels*2,4)
        le(h,channels*2,2); le(h,16,2)
        h.write("data".toByteArray()); le(h,bytes.toInt(),4)
        stream.channel.position(0)
        stream.write(h.toByteArray())
        stream.flush()
    }
    override fun close() { try { stream.flush() } catch (_:Exception) {} }
    private fun le(o:ByteArrayOutputStream,v:Int,n:Int) {
        for(i in 0 until n) o.write((v ushr (i*8)) and 255)
    }
}

class FlacSink(fd: ParcelFileDescriptor, private val rate:Int, private val channels:Int):AudioSink {
    private val stream=FileOutputStream(fd.fileDescriptor)
    private val pending=ByteArrayOutputStream()
    private val md5=MessageDigest.getInstance("MD5")
    private var totalSamples=0L
    private var frameIndex=0L
    private val sampleBytes=channels*2
    private val frameSamples=4096
    init { header(0L,ByteArray(16)) }
    override fun write(buffer:ByteArray,length:Int) {
        val aligned=length/sampleBytes*sampleBytes
        if(aligned<=0) return
        md5.update(buffer,0,aligned)
        pending.write(buffer,0,aligned)
        val bytes=pending.toByteArray()
        var start=0
        val frameLen=frameSamples*sampleBytes
        while(bytes.size-start >= frameLen) {
            frame(bytes,start,frameSamples)
            start+=frameLen
        }
        pending.reset()
        if(start<bytes.size) pending.write(bytes,start,bytes.size-start)
    }
    override fun finish() {
        val remain=pending.toByteArray()
        val samples=remain.size/sampleBytes
        if(samples>0) frame(remain,0,samples)
        stream.channel.position(0)
        header(totalSamples,md5.digest())
        stream.flush()
    }
    override fun close() { try{stream.flush()} catch (_:Exception){} }
    private fun header(samples:Long,digest:ByteArray) {
        val h=ByteArrayOutputStream()
        h.write("fLaC".toByteArray())
        h.write(0x80); h.write(0); h.write(0); h.write(34)
        be(h,16,2); be(h,4096,2)
        be(h,0,3); be(h,0,3)
        val packed=(rate.toLong() shl 44) or ((channels-1).toLong() shl 41) or
            (15L shl 36) or (samples and 0xfffffffffL)
        for(i in 7 downTo 0)h.write(((packed ushr (i*8)) and 255L).toInt())
        h.write(digest)
        stream.write(h.toByteArray())
    }
    private fun frame(data:ByteArray,offset:Int,samples:Int) {
        val out=ByteArrayOutputStream()
        out.write(255);out.write(248)
        out.write(0x70)
        out.write(if(channels==1) 0x08 else 0x18)
        utf8Number(out,frameIndex++)
        be(out,samples-1,2)
        out.write(crc8(out.toByteArray()))
        for(c in 0 until channels) {
            out.write(0x02) // verbatim subframe, 16-bit, no wasted bits
            for (i in 0 until samples) {
                val pos=offset+(i*channels+c)*2
                out.write(data[pos+1].toInt() and 255)
                out.write(data[pos].toInt() and 255)
            }
        }
        val body=out.toByteArray()
        val crc=crc16(body)
        stream.write(body)
        stream.write((crc ushr 8) and 255);stream.write(crc and 255)
        totalSamples+=samples
    }
    private fun utf8Number(o:ByteArrayOutputStream,n:Long) {
        val count=when {
            n < (1L shl 7)->1
            n < (1L shl 11)->2
            n < (1L shl 16)->3
            n < (1L shl 21)->4
            n < (1L shl 26)->5
            else->6
        }
        if(count==1) {o.write(n.toInt()); return}
        o.write(((0xff shl (8-count)) and 255) or
            ((n ushr ((count-1)*6)).toInt() and ((1 shl (7-count))-1)))
        for(i in (count-2) downTo 0)
            o.write(0x80 or ((n ushr (i*6)).toInt() and 0x3f))
    }
    private fun be(o:ByteArrayOutputStream,n:Int,count:Int) {
        for(i in count-1 downTo 0)o.write((n ushr (i*8)) and 255)
    }
    private fun crc8(data:ByteArray):Int {
        var crc=0
        for(b in data) {
            crc=crc xor (b.toInt() and 255)
            repeat(8){crc=if((crc and 0x80)!=0) ((crc shl 1) xor 7) and 255 else (crc shl 1) and 255}
        }
        return crc
    }
    private fun crc16(data:ByteArray):Int {
        var crc=0
        for(b in data) {
            crc=crc xor ((b.toInt() and 255) shl 8)
            repeat(8){crc=if((crc and 0x8000)!=0) ((crc shl 1) xor 0x8005) and 65535 else (crc shl 1) and 65535}
        }
        return crc
    }
}

class CompressedSink(fd:ParcelFileDescriptor, private val rate:Int,
                     private val channels:Int,private val bitrate:Int, private val mp3:Boolean,
                     private val opus:Boolean=false):AudioSink {
    private val codec=MediaCodec.createEncoderByType(
        if(opus) MediaFormat.MIMETYPE_AUDIO_OPUS
        else if(mp3) "audio/mpeg" else MediaFormat.MIMETYPE_AUDIO_AAC)
    private val stream=if(mp3) FileOutputStream(fd.fileDescriptor) else null
    private val muxer=if(mp3)null else MediaMuxer(fd.fileDescriptor,if(opus)MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
        else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var track=-1
    private var started=false
    private var frames=0L
    private val info=MediaCodec.BufferInfo()
    init {
        val f=MediaFormat.createAudioFormat(if(opus) MediaFormat.MIMETYPE_AUDIO_OPUS
            else if(mp3) "audio/mpeg" else MediaFormat.MIMETYPE_AUDIO_AAC,rate,channels)
        f.setInteger(MediaFormat.KEY_BIT_RATE,bitrate)
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384)
        if(!mp3 && !opus)f.setInteger(MediaFormat.KEY_AAC_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        codec.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }
    override fun write(buffer:ByteArray,length:Int) {
        var off=0
        val aligned=length/(channels*2)*(channels*2)
        while(off<aligned) {
            val input=codec.dequeueInputBuffer(100000)
            if(input<0) { drain(false);continue }
            val b=codec.getInputBuffer(input) ?: error("Brak wejścia kodeka")
            b.clear()
            val size=min(aligned-off,b.remaining())/(channels*2)*(channels*2)
            if(size<=0)error("Zbyt mały bufor kodeka")
            b.put(buffer,off,size)
            codec.queueInputBuffer(input,0,size,frames*1000000L/rate,0)
            frames+=size/(channels*2)
            off+=size
            drain(false)
        }
    }
    private fun drain(eos:Boolean) {
        var empty=0
        while(true) {
            val i=codec.dequeueOutputBuffer(info,if(eos)10000L else 0L)
            when(i) {
                MediaCodec.INFO_TRY_AGAIN_LATER->{
                    if(!eos)return
                    if(++empty>2000)error("Kodek nie zakończył pliku")
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED->{
                    if(!mp3) {
                        track=muxer!!.addTrack(codec.outputFormat)
                        muxer.start();started=true
                    }
                }
                else -> if(i>=0) {
                    val b=codec.getOutputBuffer(i) ?: error("Brak danych audio")
                    if(info.size>0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0) {
                        b.position(info.offset);b.limit(info.offset+info.size)
                        if(mp3) {
                            val bytes=ByteArray(info.size);b.get(bytes);stream!!.write(bytes)
                        } else {
                            if(!started)error("Brak ścieżki audio")
                            muxer!!.writeSampleData(track,b,info)
                        }
                    }
                    val finished=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
                    codec.releaseOutputBuffer(i,false)
                    if(finished)return
                }
            }
        }
    }
    override fun finish() {
        var i=codec.dequeueInputBuffer(100000)
        while(i<0){ drain(false);i=codec.dequeueInputBuffer(100000)}
        codec.queueInputBuffer(i,0,0,frames*1000000L/rate,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(true)
        if(!mp3 && !started)error("Brak danych audio")
        stream?.flush()
    }
    override fun close() {
        try{codec.stop()}catch (_:Exception){}
        try{codec.release()}catch (_:Exception){}
        try{if(started)muxer?.stop()}catch (_:Exception){}
        try{muxer?.release()}catch (_:Exception){}
        try{stream?.flush()}catch (_:Exception){}
    }
}