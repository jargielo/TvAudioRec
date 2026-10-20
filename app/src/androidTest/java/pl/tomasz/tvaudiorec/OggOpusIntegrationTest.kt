package pl.tomasz.tvaudiorec

import android.media.MediaExtractor
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OggOpusIntegrationTest {
    @Test fun opusFileIsWrittenAndReopened() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="TV_Audio_Opus_Android12_Test_"+System.currentTimeMillis()+".opus"
        val output=RecordingLibrary.create(context,name,"audio/ogg")
        var success=false
        try {
            val encoder=OggOpusSink(output.fd,2,64000)
            try {
                val pcm=ByteArray(48000*2*2)
                for(i in 0 until 48000) {
                    val value=(3800*Math.sin(2*Math.PI*440.0*i/48000.0)).toInt()
                    for(channel in 0..1) {
                        val at=(i*2+channel)*2
                        pcm[at]=(value and 255).toByte()
                        pcm[at+1]=(value shr 8).toByte()
                    }
                }
                encoder.write(pcm,pcm.size)
                encoder.finish()
                success=true
            } finally {
                encoder.close()
            }
        } finally {
            output.fd.close()
        }
        val saved=RecordingLibrary.finalize(context,output,success)
        assertTrue(saved,saved.startsWith("Zapisano:"))
        val indexed=RecordingLibrary.scan(context,false).items
        assertTrue("Opus must appear in My Recordings",
            indexed.any{it.uri==output.uri && it.name==name})
        val bytes=context.contentResolver.openInputStream(output.uri)!!.use{it.readBytes()}
        assertTrue("Ogg Opus file too small",bytes.size>180)
        assertEquals("OggS",String(bytes.copyOfRange(0,4),Charsets.US_ASCII))
        assertEquals("OpusHead",String(bytes.copyOfRange(28,36),Charsets.US_ASCII))
        val extractor=MediaExtractor()
        try {
            extractor.setDataSource(context,output.uri,null)
            assertTrue("No audio track recognized by Android",extractor.trackCount>0)
            val fmt=extractor.getTrackFormat(0)
            val mime=fmt.getString("mime") ?: ""
            assertTrue("Expected opus track, received "+mime,
                mime.contains("opus",ignoreCase=true))
        } finally {extractor.release()}
    }
}
