package de.henosch.bibelvers

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object KotelStreamProvider {
    private const val TAG = "BibelVersStream"
    private val names = listOf("Wilson's Arch", "Prayer Plaza", "Western Wall")
    private val cdns = listOf("cdn", "cdn1", "cdn2")

    fun fetchAvailableStreams(): List<KotelStream> {
        val results = mutableListOf<KotelStream>()
        for (index in names.indices) {
            val stream = findStream(index + 1)
            if (stream != null) {
                results.add(stream)
            }
        }
        return results
    }

    private fun findStream(number: Int): KotelStream? {
        // prefer 4K
        cdns.forEach { cdn ->
            val base = "https://${cdn}.cast-tv.com/23595/Live_Kotel${number}_ABR"
            val url4k = "$base/23595/Live_Kotel${number}_4K_1080p/playlist.m3u8"
            if (isValidStream(url4k)) {
                Log.d(TAG, "Found 4K stream for $number at $cdn")
                return KotelStream("${names[number - 1]} • 4K", url4k)
            }
        }
        // fallback STD
        cdns.forEach { cdn ->
            val base = "https://${cdn}.cast-tv.com/23595/Live_Kotel${number}_ABR"
            val urlStd = "$base/playlist.m3u8"
            if (isValidStream(urlStd)) {
                Log.d(TAG, "Found STD stream for $number at $cdn")
                return KotelStream("${names[number - 1]} • STD", urlStd)
            }
        }
        return null
    }

    private fun isValidStream(urlString: String): Boolean {
        return try {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            connection.connect()
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val firstLine = reader.readLine()
            reader.close()
            connection.disconnect()
            firstLine?.startsWith("#EXTM3U") == true
        } catch (e: Exception) {
            Log.d(TAG, "Stream check failed for $urlString", e)
            false
        }
    }
}

data class KotelStream(
    val label: String,
    val url: String
)
