package pl.tomasz.tvaudiorec

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android instrumentation check: save one actual WAV file and discover it. */
@RunWith(AndroidJUnit4::class)
class RecordingLibraryTest {
    @Test
    fun savedFileIsVisibleWithoutWaitingForMediaScanner() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val result=RecordingLibrary.testFile(context)
        assertTrue(result,result.contains("Zapisano:"))
        val files=RecordingLibrary.scan(context,false).items
        val entry=files.firstOrNull {it.name.startsWith("TEST_TV_")}
        assertTrue("No test recording found in library",entry!=null)
        val record=entry!!
        assertTrue("WAV file size must exceed header",record.size>44)
        val header=ByteArray(4)
        val input=context.contentResolver.openInputStream(record.uri)
        assertTrue("Audio file cannot be reopened",input!=null)
        input!!.use {assertEquals(4,it.read(header))}
        assertEquals("RIFF",String(header,Charsets.US_ASCII))
    }
}
