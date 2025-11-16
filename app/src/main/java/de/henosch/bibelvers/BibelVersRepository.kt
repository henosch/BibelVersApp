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
    private const val SESSION_PREFS = "bibelverse_session"
    private const val SESSION_CURRENT_PREFIX = "current_"
    private const val SESSION_NEXT_PREFIX = "next_"
    private val parserDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
    private var cachedEntries: List<BibelVersData>? = null
    private val activeSessionOffsets = mutableMapOf<String, Int>()

    fun getEntry(context: Context, date: Date, preferLocal: Boolean = false): BibelVersEntry? {
        val entries = loadEntries(context)
        if (entries.isEmpty()) return null

        val calendar = Calendar.getInstance().apply { time = date }
        val year = calendar.get(Calendar.YEAR)
        val dayIndex = calendar.get(Calendar.DAY_OF_YEAR) - 1
        val order = VerseOrderManager.orderForYear(context, year, entries.size)
        val formattedDate = parserDateFormat.format(date)
        val offset = computeSessionOffset(context, formattedDate, entries.size, calendar.time)
        val normalizedDayIndex = positiveModulo(dayIndex, entries.size)
        val orderIndex = (normalizedDayIndex + offset) % entries.size
        val entryIndex = order[orderIndex]
        val verse = entries[entryIndex]
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

    fun beginTodaySession(context: Context) {
        val entries = loadEntries(context)
        if (entries.isEmpty()) return
        val dateKey = parserDateFormat.format(Date())
        val offset = SessionOffsetStore.consumeNext(context, dateKey, entries.size)
        activeSessionOffsets.clear()
        activeSessionOffsets[dateKey] = offset
    }

    private fun computeSessionOffset(
        context: Context,
        dateKey: String,
        size: Int,
        date: Date
    ): Int {
        if (size <= 0) return 0
        activeSessionOffsets[dateKey]?.let { return it }
        val todayCal = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply { time = date }
        val isToday = todayCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
            todayCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
        return if (isToday) {
            SessionOffsetStore.current(context, dateKey, size)
        } else {
            0
        }
    }

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

    private object SessionOffsetStore {
        fun consumeNext(context: Context, dateKey: String, size: Int): Int {
            if (size <= 0) return 0
            val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            val nextKey = SESSION_NEXT_PREFIX + dateKey
            val currentKey = SESSION_CURRENT_PREFIX + dateKey
            val nextRaw = prefs.getInt(nextKey, 0)
            val next = positiveModulo(nextRaw, size)
            val newNext = (next + 1) % size
            prefs.edit {
                putInt(currentKey, next)
                putInt(nextKey, newNext)
            }
            return next
        }

        fun current(context: Context, dateKey: String, size: Int): Int {
            if (size <= 0) return 0
            val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            val currentKey = SESSION_CURRENT_PREFIX + dateKey
            val nextKey = SESSION_NEXT_PREFIX + dateKey
            val raw = if (prefs.contains(currentKey)) {
                prefs.getInt(currentKey, 0)
            } else {
                prefs.getInt(nextKey, 0)
            }
            return positiveModulo(raw, size)
        }
    }

    private fun positiveModulo(value: Int, size: Int): Int {
        if (size <= 0) return 0
        val mod = value % size
        return if (mod >= 0) mod else mod + size
    }
}

data class BibelVersEntry(
    val date: String,
    val bibelversText: String,
    val bibelversVers: String,
    val zusatzText: String,
    val zusatzVers: String
)
