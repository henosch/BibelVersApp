package de.henosch.bibelvers

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.core.content.edit
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlin.text.Charsets

object BibelVersRepository {

    private const val TAG = "BibelVersRepository"
    private const val ORDER_PREFS = "bibelverse_order"
    private const val ORDER_KEY_PREFIX = "order_"
    private val parserDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
    private var cachedEntries: List<BibelVersData>? = null

    fun getEntry(context: Context, date: Date, preferLocal: Boolean = false): BibelVersEntry? {
        val entries = loadEntries(context)
        if (entries.isEmpty()) return null

        val calendar = Calendar.getInstance().apply { time = date }
        val year = calendar.get(Calendar.YEAR)
        val dayIndex = calendar.get(Calendar.DAY_OF_YEAR) - 1
        val order = VerseOrderManager.orderForYear(context, year, entries.size)
        val entryIndex = order[dayIndex % entries.size]
        val verse = entries[entryIndex]
        val formattedDate = parserDateFormat.format(date)
        return BibelVersEntry(
            formattedDate,
            sanitize(verse.textAltesTestament),
            verse.textAltesTestamentQuelle.trim(),
            sanitize(verse.textNeuesTestament),
            verse.textNeuesTestamentQuelle.trim()
        )
    }

    fun formatDate(date: Date): String = parserDateFormat.format(date)

    fun isFallbackActive(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = false

    private fun loadEntries(context: Context): List<BibelVersData> {
        cachedEntries?.let { return it }
        val parsed = parseBibelVerseXml(context)
        cachedEntries = parsed
        return parsed
    }

    private fun parseBibelVerseXml(context: Context): List<BibelVersData> {
        return try {
            context.assets.open("BibelVerse.xml").use { stream ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(stream.reader(Charsets.UTF_8))
                }
                val entries = mutableListOf<BibelVersData>()
                var builder: VerseBuilder? = null
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "BibelVers" -> builder = VerseBuilder()
                                "TextAltesTestament" -> builder?.textAltesTestament = parser.nextText()
                                "TextAltesTestamentQuelle" -> builder?.textAltesTestamentQuelle = parser.nextText()
                                "TextNeuesTestament" -> builder?.textNeuesTestament = parser.nextText()
                                "TextNeuesTestamentQuelle" -> builder?.textNeuesTestamentQuelle = parser.nextText()
                            }
                        }

                        XmlPullParser.END_TAG -> {
                            if (parser.name == "BibelVers" && builder != null) {
                                entries += builder.toData()
                                builder = null
                            }
                        }
                    }
                    eventType = parser.next()
                }
                entries
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse BibelVerse.xml", e)
            emptyList()
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

    private data class BibelVersData(
        val textAltesTestament: String,
        val textAltesTestamentQuelle: String,
        val textNeuesTestament: String,
        val textNeuesTestamentQuelle: String
    )

    private data class VerseBuilder(
        var textAltesTestament: String = "",
        var textAltesTestamentQuelle: String = "",
        var textNeuesTestament: String = "",
        var textNeuesTestamentQuelle: String = ""
    ) {
        fun toData(): BibelVersData = BibelVersData(
            textAltesTestament,
            textAltesTestamentQuelle,
            textNeuesTestament,
            textNeuesTestamentQuelle
        )
    }

    private object VerseOrderManager {
        fun orderForYear(context: Context, year: Int, size: Int): IntArray {
            val prefs = context.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getString(ORDER_KEY_PREFIX + year, null)
            if (stored != null) {
                val parsed = stored.split(",").mapNotNull { it.toIntOrNull() }
                if (parsed.size == size) {
                    return parsed.toIntArray()
                }
            }
            val order = (0 until size).shuffled(Random(year)).toIntArray()
            prefs.edit {
                putString(ORDER_KEY_PREFIX + year, order.joinToString(","))
            }
            return order
        }
    }
}

data class BibelVersEntry(
    val date: String,
    val bibelversText: String,
    val bibelversVers: String,
    val zusatzText: String,
    val zusatzVers: String
)
