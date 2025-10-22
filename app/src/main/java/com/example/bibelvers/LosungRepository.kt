package com.example.bibelvers

import android.content.Context
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

object LosungRepository {

    private const val TAG = "LosungRepository"
    private const val ZIP_TEMPLATE =
        "https://www.losungen.de/fileadmin/media-losungen/download/Losung_%d_XML.zip"
    private val parserDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun ensureYear(context: Context, year: Int): Boolean {
        val outputFile = File(context.filesDir, localFileName(year))
        if (outputFile.exists()) {
            return false
        }
        return try {
            val url = URL(String.format(Locale.US, ZIP_TEMPLATE, year))
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
            }
            try {
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    Log.w(TAG, "Failed to download losungen for $year: HTTP ${connection.responseCode}")
                    false
                } else {
                    ZipInputStream(BufferedInputStream(connection.inputStream)).use { zip ->
                        var entry = zip.nextEntry
                        var copied = false
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.lowercase(Locale.ROOT).endsWith(".xml")) {
                                outputFile.outputStream().use { out ->
                                    zip.copyTo(out)
                                }
                                copied = true
                                break
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                        if (!copied) {
                            outputFile.delete()
                            Log.w(TAG, "No XML entry found in archive for $year")
                        }
                        copied
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading losungen for $year", e)
            outputFile.delete()
            false
        }
    }

    fun getEntry(context: Context, date: Date, preferLocal: Boolean = false): LosungEntry? {
        val target = parserDateFormat.format(date)
        val year = target.substring(0, 4).toInt()

        val localFile = File(context.filesDir, localFileName(year))
        if (localFile.exists()) {
            FileInputStream(localFile).use { input ->
                val entry = parseFromStream(input, target)
                if (entry != null) {
                    return entry
                } else {
                    Log.w(TAG, "Failed to parse cached losungen for $year, removing file")
                }
            }
            localFile.delete()
        }

        if (preferLocal) {
            return null
        }

        context.resources.openRawResource(R.xml.losungen).use { input ->
            return parseFromStream(input, target)
        }
    }

    fun formatDate(date: Date): String = parserDateFormat.format(date)

    private fun parseFromStream(stream: InputStream, targetDate: String): LosungEntry? {
        val buffered = if (stream.markSupported()) stream else BufferedInputStream(stream)
        buffered.use { inputStream ->
            return try {
                val bytes = readAllBytes(inputStream)
                val encoding = detectEncoding(bytes) ?: Charsets.UTF_8
                val xmlText = String(bytes, encoding)
                val sanitized = sanitizeXmlContent(xmlText)
                if (!sanitized.contains("<Losungen", ignoreCase = true)) {
                    Log.w(TAG, "Sanitized XML missing expected tag")
                    return null
                }
                val parser: XmlPullParser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(StringReader(sanitized))
                }
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "Losungen") {
                        var dateValue = ""
                        var losungText = ""
                        var losungVers = ""
                        var lehrtext = ""
                        var lehrtextVers = ""
                        while (!(eventType == XmlPullParser.END_TAG && parser.name == "Losungen")) {
                            if (eventType == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "Datum" -> dateValue = parser.nextText().substring(0, 10)
                                    "Losungstext" -> losungText = parser.nextText()
                                    "Losungsvers" -> losungVers = parser.nextText()
                                    "Lehrtext" -> lehrtext = parser.nextText()
                                    "Lehrtextvers" -> lehrtextVers = parser.nextText()
                                }
                            }
                            eventType = parser.next()
                        }
                        if (dateValue == targetDate) {
                            return LosungEntry(
                                dateValue,
                                sanitize(losungText),
                                sanitize(losungVers),
                                sanitize(lehrtext),
                                sanitize(lehrtextVers)
                            )
                        }
                    }
                    eventType = parser.next()
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Parsing error", e)
                null
            }
        }
    }

    private fun sanitize(raw: String): String {
        if (raw.isBlank()) return raw.trim()
        var text = raw.trim()
        text = text.replace(
            Regex(
                """^/?\s*([A-Za-zÄÖÜäöüß]+\s*){0,2}(text|vers)\s*:/*\s*""",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        text = text.replace(Regex("""/\s*[^/]+:/"""), "")
        text = text.replace(Regex("""\s{2,}"""), " ")
        return text.trim()
    }

    private fun sanitizeXmlContent(source: String): String {
        val sb = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val ch = source[i]
            val code = ch.code
            val isValid = ch == '\u0009' || ch == '\u000A' || ch == '\u000D' ||
                (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
            if (isValid) {
                sb.append(ch)
            }
            i++
        }
        val pattern = Regex("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9A-Fa-f]+;)")
        return pattern.replace(sb.toString()) { "&amp;" }
    }

    private fun readAllBytes(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(DEFAULT_BUFFER_SIZE)
        var count: Int
        while (input.read(data).also { count = it } != -1) {
            buffer.write(data, 0, count)
        }
        return buffer.toByteArray()
    }

    private fun detectEncoding(bytes: ByteArray): Charset? {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        val preview = String(bytes, 0, minOf(bytes.size, 400), Charsets.ISO_8859_1)
        val matcher = ENCODING_PATTERN.matcher(preview)
        return if (matcher.find()) {
            runCatching { Charset.forName(matcher.group(1)) }.getOrNull()
        } else {
            null
        }
    }

    private val ENCODING_PATTERN = Pattern.compile("encoding=['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE)

    private fun localFileName(year: Int) = "losungen_$year.xml"
}

data class LosungEntry(
    val date: String,
    val losungText: String,
    val losungVers: String,
    val lehrtext: String,
    val lehrtextVers: String
)
