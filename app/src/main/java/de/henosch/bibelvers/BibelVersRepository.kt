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
    private const val USED_VERSES_PREFS = "bibelverse_used"
    private const val USED_VERSES_KEY_PREFIX = "used_"
    private val parserDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
    private var cachedEntries: List<BibelVersData>? = null
    private val activeSessionOffsets = mutableMapOf<String, Int>()

    fun getEntry(context: Context, date: Date, preferLocal: Boolean = false): BibelVersEntry? {
        val entries = loadEntries(context)
        if (entries.isEmpty()) return null

        val calendar = Calendar.getInstance().apply { time = date }
        val year = calendar.get(Calendar.YEAR)
        val formattedDate = parserDateFormat.format(date)
        val randomActive = SessionOffsetStore.isRandomModeEnabled(context)
        
        return if (randomActive) {
            getRandomEntry(context, entries, year, formattedDate)
        } else {
            getSequentialEntry(context, entries, year, date, formattedDate)
        }
    }

    private fun getSequentialEntry(context: Context, entries: List<BibelVersData>, year: Int, date: Date, formattedDate: String): BibelVersEntry? {
        val dayIndex = Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_YEAR) - 1
        val order = VerseOrderManager.orderForYear(context, year, entries.size)
        
        // Einfache Berechnung des Index basierend auf dem Tag des Jahres
        val targetDayIndex = positiveModulo(dayIndex, entries.size)
        val entryIndex = order[targetDayIndex]
        
        // Keine Duplikats-Prüfung nötig - die Liste wurde bereits optimiert erstellt
        // Keine Balance-Optimierung nötig - die Liste wurde bereits mit ausgewogenen Kombinationen erstellt
        
        val verse = entries[entryIndex]
        return BibelVersEntry(
            formattedDate,
            sanitize(verse.textAltesTestament),
            verse.textAltesTestamentQuelle.trim(),
            sanitize(verse.textNeuesTestament),
            verse.textNeuesTestamentQuelle.trim()
        )
    }

    private fun getRandomEntry(context: Context, entries: List<BibelVersData>, year: Int, formattedDate: String): BibelVersEntry? {
        val usedVerses = UsedVersesTracker.getUsedVersesForYear(context, year)
        val availableVerses = entries.indices.filter { it !in usedVerses }
        
        val entryIndex = if (availableVerses.isNotEmpty()) {
            // Suche nach einer ausgewogenen Kombination für Random Mode
            findBalancedVerse(entries, availableVerses)
        } else {
            // Alle Verse wurden bereits verwendet, zurücksetzen für das neue Jahr
            if (Calendar.getInstance().get(Calendar.YEAR) > year) {
                UsedVersesTracker.resetUsedVersesForYear(context, year)
                findBalancedVerse(entries, entries.indices.toList())
            } else {
                // Innerhalb desselben Jahres: Verbleibende Verse verwenden
                val remainingVerses = entries.indices.filter { it !in usedVerses.take(usedVerses.size - 1) }
                findBalancedVerse(entries, remainingVerses)
            }
        }
        
        UsedVersesTracker.addUsedVerse(context, year, entryIndex)
        
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
        
        if (SessionOffsetStore.isRandomModeEnabled(context)) {
            // Die neue Logik verhindert automatisch Duplikate
            // Keine zusätzliche Aktion erforderlich
            Log.d(TAG, "Random mode active - duplicate prevention enabled automatically")
        } else {
            // Sequentieller Modus - bestehende Logik beibehalten
            val offset = SessionOffsetStore.consumeNext(context, dateKey, entries.size)
            activeSessionOffsets.clear()
            activeSessionOffsets[dateKey] = offset
        }
    }

    private fun computeSessionOffset(
        context: Context,
        dateKey: String,
        size: Int,
        date: Date
    ): Int {
        // Diese Funktion wird nur noch für ältere Kompatibilität verwendet
        // Die neue Logik verhindert Duplikate direkt
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

    private object UsedVersesTracker {
        fun getUsedVersesForYear(context: Context, year: Int): Set<Int> {
            val prefs = context.getSharedPreferences(USED_VERSES_PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getString(USED_VERSES_KEY_PREFIX + year, null)
            return if (stored != null) {
                stored.split(",").mapNotNull { it.toIntOrNull() }.toSet()
            } else {
                emptySet()
            }
        }

        fun addUsedVerse(context: Context, year: Int, verseIndex: Int) {
            val usedVerses = getUsedVersesForYear(context, year).toMutableSet()
            usedVerses.add(verseIndex)
            val prefs = context.getSharedPreferences(USED_VERSES_PREFS, Context.MODE_PRIVATE)
            prefs.edit {
                putString(USED_VERSES_KEY_PREFIX + year, usedVerses.joinToString(","))
            }
        }

        fun resetUsedVersesForYear(context: Context, year: Int) {
            val prefs = context.getSharedPreferences(USED_VERSES_PREFS, Context.MODE_PRIVATE)
            prefs.edit {
                remove(USED_VERSES_KEY_PREFIX + year)
            }
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
        val textNeuesTestamentQuelle: String,
        val totalLength: Int = textAltesTestament.length + textNeuesTestament.length,
        val isLong: Boolean = totalLength > 331, // 90th percentile
        val isVeryLong: Boolean = totalLength > 431  // Kritisch lang
    )

    private data class VerseBuilder(
        var textAltesTestament: String = "",
        var textAltesTestamentQuelle: String = "",
        var textNeuesTestament: String = "",
        var textNeuesTestamentQuelle: String = ""
    ) {
        fun toData(): BibelVersData {
            val totalLength = textAltesTestament.length + textNeuesTestament.length
            return BibelVersData(
                textAltesTestament,
                textAltesTestamentQuelle,
                textNeuesTestament,
                textNeuesTestamentQuelle,
                totalLength = totalLength,
                isLong = totalLength > 331,
                isVeryLong = totalLength > 431
            )
        }
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
            
            // Hole die Einträge für die Längen-Analyse
            val entries = cachedEntries ?: parseBibelVerseXml(context)
            
            val order = if (entries.isNotEmpty()) {
                // Erstelle optimierte Reihenfolge basierend auf Verse-Längen
                createOptimizedOrder(entries, year, size)
            } else {
                // Fallback: einfache zufällige Reihenfolge
                val enhancedSeed = year * 10000L + System.currentTimeMillis() / (1000L * 60 * 60 * 24)
                (0 until size).shuffled(Random(enhancedSeed)).toIntArray()
            }
            
            prefs.edit {
                putString(ORDER_KEY_PREFIX + year, order.joinToString(","))
            }
            return order
        }
        
        private fun createOptimizedOrder(entries: List<BibelVersData>, year: Int, size: Int): IntArray {
            Log.d(TAG, "Erstelle optimierte Jahres-Reihenfolge mit Balance und ohne Duplikate...")
            
            val random = Random(year * 10000L + System.currentTimeMillis() / (1000L * 60 * 60 * 24))
            
            // Sicherstellen, dass alle Indizes eindeutig sind und Balance berücksichtigt wird
            val allIndices = entries.indices.toList()
            
            // Erstelle Kategorien basierend auf Balance-Qualität
            val excellentBalance = mutableListOf<Int>()   // 8-10 Punkte
            val goodBalance = mutableListOf<Int>()        // 6 Punkte
            val acceptableBalance = mutableListOf<Int>()  // 4 Punkte
            val poorBalance = mutableListOf<Int>()        // 2 Punkte
            
            for (index in allIndices) {
                val score = getCombinationScore(entries[index])
                when (score) {
                    in 8..10 -> excellentBalance.add(index)
                    6 -> goodBalance.add(index)
                    4 -> acceptableBalance.add(index)
                    else -> poorBalance.add(index)
                }
            }
            
            Log.d(TAG, "Balance-Verteilung: Exzellent=${excellentBalance.size}, Gut=${goodBalance.size}, Akzeptabel=${acceptableBalance.size}, Schlecht=${poorBalance.size}")
            
            // Erstelle eine gemischte Reihenfolge mit guter Verteilung
            val result = mutableListOf<Int>()
            val usedIndices = mutableSetOf<Int>()
            
            // Strategie: Mische die Kategorien für eine gute Verteilung über das Jahr
            var categoryIndex = 0
            val categories = listOf(excellentBalance, goodBalance, acceptableBalance, poorBalance)
            
            while (result.size < size) {
                val currentCategory = categories[categoryIndex % categories.size]
                val availableInCategory = currentCategory.filter { it !in usedIndices }
                
                if (availableInCategory.isNotEmpty()) {
                    // Wähle zufällig aus der aktuellen Kategorie
                    val selectedIndex = availableInCategory.random(random)
                    result.add(selectedIndex)
                    usedIndices.add(selectedIndex)
                }
                
                // Wechsle zur nächsten Kategorie für bessere Verteilung
                categoryIndex++
                
                // Wenn keine verfügbaren Indizes mehr in der aktuellen Kategorie, überspringe sie
                if (availableInCategory.isEmpty() && result.size < size) {
                    continue
                }
            }
            
            Log.d(TAG, "Optimierte Reihenfolge erstellt: ${result.size} Verse mit Balance-Optimierung")
            return result.toIntArray()
        }
    }

    private object SessionOffsetStore {

        fun isRandomModeEnabled(context: Context): Boolean =
            context.getSharedPreferences(BaseActivity.PREFS_FILE, Context.MODE_PRIVATE)
                .getBoolean(BaseActivity.KEY_RANDOM_VERSE_MODE, true)

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

    /**
     * Findet einen ausgewogenen Vers aus der Liste der verfügbaren Verse
     * Neue Strategie: Biete Vielfalt, vermeide Extreme
     */
    private fun findBalancedVerse(entries: List<BibelVersData>, availableVerses: List<Int>): Int {
        if (availableVerses.isEmpty()) return 0
        if (availableVerses.size == 1) return availableVerses[0]
        
        // Erstelle eine gewichtete Auswahl basierend auf Qualität und Vielfalt
        val weightedSelection = mutableListOf<Int>()
        
        for (index in availableVerses) {
            val verse = entries[index]
            val score = getCombinationScore(verse)
            
            // Füge den Index mehrfach hinzu basierend auf der Qualität
            // Höhere Qualität = mehr Einträge = höhere Wahrscheinlichkeit
            val repetitions = when (score) {
                10 -> 5  // Perfekte Balance: 5x
                8 -> 4   // Gute Balance: 4x  
                6 -> 3   // Akzeptabel: 3x
                4 -> 2   // Okay: 2x
                else -> 1 // Weniger optimal: 1x
            }
            
            repeat(repetitions) {
                weightedSelection.add(index)
            }
        }
        
        // Zufällige Auswahl aus der gewichteten Liste
        return weightedSelection.random()
    }

    /**
     * Findet eine ausgewogene Alternative in der Reihenfolge
     * Neue Strategie: Erlaube mehr Vielfalt, vermeide nur Extreme
     */
    private fun findBalancedAlternativeInOrder(
        entries: List<BibelVersData>, 
        order: IntArray, 
        usedVerses: Set<Int>, 
        startIndex: Int
    ): Int {
        // Suche in der Reihenfolge nach einer guten Alternative
        val alternatives = mutableListOf<Pair<Int, Int>>() // (index, score)
        
        for (i in 1 until entries.size) {
            val alternativeIndex = order[positiveModulo(startIndex + i, entries.size)]
            if (alternativeIndex !in usedVerses) {
                val verse = entries[alternativeIndex]
                val score = getCombinationScore(verse)
                alternatives.add(Pair(alternativeIndex, score))
                
                // Sammle nur die ersten 10 Alternativen für Performance
                if (alternatives.size >= 10) break
            }
        }
        
        if (alternatives.isEmpty()) return -1
        
        // Sortiere nach Qualität (höchste Punktzahl zuerst)
        alternatives.sortByDescending { it.second }
        
        // Wähle aus den besten Alternativen mit einer gewissen Zufälligkeit
        // Top 3 Alternativen bekommen höhere Wahrscheinlichkeit
        val topCount = minOf(3, alternatives.size)
        val selectedIndex = when (topCount) {
            1 -> alternatives[0].first
            2 -> if (kotlin.random.Random.nextFloat() < 0.7f) alternatives[0].first else alternatives[1].first
            else -> {
                // 70% Wahrscheinlichkeit für Top 3, sonst zufällig
                if (kotlin.random.Random.nextFloat() < 0.7f) {
                    alternatives.subList(0, topCount).random().first
                } else {
                    alternatives.random().first
                }
            }
        }
        
        return selectedIndex
    }

    /**
     * Prüft, ob ein Vers-Paar ausgewogen ist (ideal)
     * Neue Strategie: Vermeide extreme Kombinationen, aber erlaube Vielfalt
     */
    private fun isBalancedCombination(verse: BibelVersData): Boolean {
        val atLen = verse.textAltesTestament.length
        val ntLen = verse.textNeuesTestament.length
        
        return when {
            // Nicht ausgewogen: Beide sehr lang (> 350 Zeichen)
            atLen > 350 && ntLen > 350 -> false
            
            // Nicht ausgewogen: Ein sehr lang, ein sehr kurz (extremer Unterschied)
            atLen > 350 && ntLen < 80 -> false
            atLen < 80 && ntLen > 350 -> false
            
            // Ausgewogen: Alle anderen Kombinationen sind okay
            else -> true
        }
    }

    /**
     * Prüft, ob ein Vers-Paar akzeptabel ist (nicht optimal, aber okay)
     * Gibt eine Punktzahl für die Qualität der Kombination
     */
    private fun getCombinationScore(verse: BibelVersData): Int {
        val atLen = verse.textAltesTestament.length
        val ntLen = verse.textNeuesTestament.length
        val totalLen = atLen + ntLen
        val lenDiff = kotlin.math.abs(atLen - ntLen)
        
        return when {
            // Perfekte Balance: ähnliche Längen, nicht zu lang gesamt
            lenDiff < 100 && totalLen < 400 -> 10
            
            // Gute Balance: mittlere Längen
            atLen in 120..280 && ntLen in 120..280 -> 8
            
            // Akzeptabel: Ein lang, ein mittel
            (atLen > 280 && ntLen in 100..280) || (ntLen > 280 && atLen in 100..280) -> 6
            
            // Akzeptabel: Beide kurz oder mittel
            totalLen < 350 -> 4
            
            // Weniger optimal: Extremere Kombinationen
            else -> 2
        }
    }

    /**
     * Prüft, ob ein Vers-Paar akzeptabel ist (alt - für Kompatibilität)
     */
    private fun isAcceptableCombination(verse: BibelVersData): Boolean {
        return getCombinationScore(verse) >= 4
    }
}

data class BibelVersEntry(
    val date: String,
    val bibelversText: String,
    val bibelversVers: String,
    val zusatzText: String,
    val zusatzVers: String
)
