package pl.tomasz.tvaudiorec

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class OpusCodecTest {
    @Test
    fun writesRealOggOpusAndReopensOnAndroid12() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="TEST_OPUS_"+System.currentTimeMillis()+".opus"
        val out=RecordingLibrary.create(context,name,"audio/ogg")
        var completed=false
        val sink=CompressedSink(out.fd,48000,2,96000,false,true)
        try {
            // 1 second of valid stereo PCM, the same path as the TV recorder.
            val block=ByteArray(48000*4)
            for(i in 0 until 48000) {
                val sample=(2000*Math.sin(2.0*Math.PI*440.0*i/48000.0)).toInt()
                for(ch in 0..1) {
                    val pos=i*4+ch*2
                    block[pos]=(sample and 255).toByte()
                    block[pos+1]=((sample shr 8) and 255).toByte()
                }
            }
            sink.write(block,block.size)
            sink.finish()
            completed=true
        }finally {
            sink.close()
            out.fd.close()
        }
        assertTrue(RecordingLibrary.finalize(context,out,completed).contains("Zapisano"))
        val file=RecordingLibrary.scan(context,false).items.firstOrNull{it.name==name}
        assertTrue("Opus file missing from recording library",file!=null)
        assertTrue("Opus must be significantly smaller than WAV",file!!.size<48000L*4L/2L)
        context.contentResolver.openInputStream(file.uri)!!.use {source->
            val header=ByteArray(4)
            assertEquals(4,source.read(header))
            assertEquals("OggS",String(header,StandardCharsets.US_ASCII))
        }
        val extractor=MediaExtractor()
        try {
            extractor.setDataSource(context,file.uri,null)
            assertTrue("No Opus track extracted",extractor.trackCount>0)
            assertEquals(MediaFormat.MIMETYPE_AUDIO_OPUS,
                extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME))
        }finally{extractor.release()}
    }
}
