package pl.tomasz.tvaudiorec

import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

/** Ogg/Opus encoder: 48 kHz, 20 ms frames, software native libopus. */
internal object OpusNative {
    init { System.loadLibrary("tvopus") }
    external fun create(channels:Int,bitrate:Int):Long
    external fun header(handle:Long):ByteArray
    external fun encode(handle:Long,frame:ByteArray,eos:Boolean):ByteArray
    external fun release(handle:Long)
    fun available()=try {System.loadLibrary("tvopus");true} catch(_:Throwable){false}
}

class OggOpusSink(
    fd:ParcelFileDescriptor,channels:Int,bitrate:Int
):AudioSink {
    private val frameBytes=960*channels*2
    private val stream=FileOutputStream(fd.fileDescriptor)
    private var handle=OpusNative.create(channels,bitrate)
    private val waiting=ByteArrayOutputStream()
    private var closed=false
    init {
        check(handle!=0L) {"Nie można uruchomić kodeka Opus"}
        stream.write(OpusNative.header(handle))
    }
    override fun write(buffer:ByteArray,length:Int) {
        check(!closed){"Kodek Opus już zamknięty"}
        if(length<=0)return
        waiting.write(buffer,0,length)
        val all=waiting.toByteArray()
        var off=0
        while(all.size-off>=frameBytes) {
            val frame=all.copyOfRange(off,off+frameBytes)
            stream.write(OpusNative.encode(handle,frame,false))
            off+=frameBytes
        }
        waiting.reset()
        if(off<all.size)waiting.write(all,off,all.size-off)
    }
    override fun finish() {
        check(!closed){"Kodek Opus już zamknięty"}
        val rem=waiting.toByteArray()
        val finalFrame=ByteArray(frameBytes)
        if(rem.isNotEmpty())
            System.arraycopy(rem,0,finalFrame,0,minOf(rem.size,frameBytes))
        stream.write(OpusNative.encode(handle,finalFrame,true))
        stream.flush()
    }
    override fun close() {
        if(!closed){
            closed=true
            OpusNative.release(handle)
            handle=0L
            try{stream.flush()}catch(_:Exception){}
        }
    }
}
