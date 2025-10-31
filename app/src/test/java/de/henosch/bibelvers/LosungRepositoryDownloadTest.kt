package de.henosch.bibelvers

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LosungRepositoryDownloadTest {

    private lateinit var appContext: Application
    private lateinit var server: MockWebServer
    private lateinit var losungFile: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        losungFile = File(appContext.filesDir, "losungen_2025.xml")
        if (losungFile.exists()) {
            losungFile.delete()
        }

        val zipBytes = this::class.java.getResourceAsStream("/Losung_2025_XML.zip")!!.readBytes()
        server = MockWebServer().apply {
            enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/zip")
                    .setBody(Buffer().write(zipBytes))
            )
            start()
        }
        val baseUrl = server.url("/").toString()
        LosungRepository.zipTemplateOverride = baseUrl + "Losung_%d_XML.zip"
    }

    @After
    fun tearDown() {
        losungFile.delete()
        LosungRepository.zipTemplateOverride = null
        server.shutdown()
    }

    @Test
    fun ensureYear_downloadsAndParses2025() = runBlocking {
        val downloaded = LosungRepository.ensureYear(appContext, 2025)
        assertTrue("Der Download sollte erfolgreich sein", downloaded)
        assertTrue("Die entpackte XML muss auf dem Gerät liegen", losungFile.exists())

        val entryDate = dateFormat.parse("2025-01-01")
        requireNotNull(entryDate)
        val entry = LosungRepository.getEntry(appContext, entryDate, preferLocal = true)
        assertNotNull("Die Losung für den 01.01.2025 muss gefunden werden", entry)
        assertEquals("2025-01-01", entry!!.date)
    }
}
