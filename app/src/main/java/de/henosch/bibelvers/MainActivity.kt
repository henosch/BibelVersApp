package de.henosch.bibelvers

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import de.henosch.bibelvers.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetector

    private val currentDate: Calendar = Calendar.getInstance()
    private val displayDateFormat = SimpleDateFormat("EEEE, dd. MMMM yyyy", Locale.GERMANY)
    private val requestedYears = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = ContextCompat.getColor(this, R.color.wall_status_bar_color)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.wall_navigation_bar_color)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = isNightMode
        }
        val originalTop = binding.root.paddingTop
        val originalBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                maxOf(originalTop, systemBars.top),
                view.paddingRight,
                originalBottom + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        currentDate.time = Date()
        setupGestureDetection()
        setupKotelStreamButton()

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.dateTextView.setOnClickListener {
            returnToToday()
        }

        displayVerseForDate(currentDate.time)
        prefetchYear(currentDate.get(Calendar.YEAR))
        prefetchYear(currentDate.get(Calendar.YEAR) + 1)
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
    }

    private fun setupGestureDetection() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) &&
                    kotlin.math.abs(diffX) > SWIPE_THRESHOLD &&
                    kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                ) {
                    if (diffX > 0) {
                        showPreviousDay()
                    } else {
                        showNextDay()
                    }
                    return true
                }
                return false
            }
        })

        binding.root.setGestureDetector(gestureDetector)
    }

    private fun showPreviousDay() {
        currentDate.add(Calendar.DAY_OF_YEAR, -1)
        prefetchYear(currentDate.get(Calendar.YEAR))
        displayVerseForDate(currentDate.time)
    }

    private fun showNextDay() {
        currentDate.add(Calendar.DAY_OF_YEAR, 1)
        prefetchYear(currentDate.get(Calendar.YEAR))
        displayVerseForDate(currentDate.time)
    }

    private fun returnToToday() {
        currentDate.time = Date()
        prefetchYear(currentDate.get(Calendar.YEAR))
        displayVerseForDate(currentDate.time)
    }

    private fun updateGreeting() {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

        val isShabbat = when (dayOfWeek) {
            Calendar.FRIDAY -> hourOfDay >= 19
            Calendar.SATURDAY -> hourOfDay < 19
            else -> false
        }

        if (isShabbat) {
            binding.greetingTextView.visibility = View.VISIBLE
            binding.greetingTextView.text = getString(R.string.shabbat_shalom)
        } else {
            binding.greetingTextView.visibility = View.GONE
        }
    }

    private fun displayVerseForDate(date: Date, preferLocal: Boolean = false) {
        val formattedDate = displayDateFormat.format(date)
        binding.dateTextView.text = formattedDate.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.GERMANY) else it.toString()
        }
        val entry = LosungRepository.getEntry(this, date, preferLocal)
        if (entry != null) {
            binding.losungTextView.text = entry.losungText
            binding.losungVersTextView.text = entry.losungVers
            binding.lehrtextTextView.text = entry.lehrtext
            binding.lehrtextVersTextView.text = entry.lehrtextVers
        } else {
            binding.losungTextView.text = getString(R.string.losung_not_found)
            binding.losungVersTextView.text = ""
            binding.lehrtextTextView.text = ""
            binding.lehrtextVersTextView.text = ""
        }
    }

    private fun prefetchYear(year: Int) {
        if (year <= 0 || requestedYears.contains(year)) return
        requestedYears.add(year)
        lifecycleScope.launch(Dispatchers.IO) {
            val downloaded = LosungRepository.ensureYear(applicationContext, year)
            if (downloaded && currentDate.get(Calendar.YEAR) == year) {
                withContext(Dispatchers.Main) {
                    displayVerseForDate(currentDate.time, preferLocal = true)
                }
            }
        }
    }

    companion object {
        private const val SWIPE_THRESHOLD = 120
        private const val SWIPE_VELOCITY_THRESHOLD = 1200
    }

    private fun setupKotelStreamButton() {
        binding.kotelStreamButton.setOnClickListener {
            lifecycleScope.launch {
                binding.kotelStreamProgress.isVisible = true
                try {
                    val streams = withContext(Dispatchers.IO) { KotelStreamProvider.fetchAvailableStreams() }
                    binding.kotelStreamProgress.isVisible = false

                    if (streams.isEmpty()) {
                        Toast.makeText(this@MainActivity, R.string.stream_picker_error, Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val labels = streams.map { it.label }.toTypedArray()
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.stream_picker_title)
                        .setItems(labels) { _, index ->
                            StreamActivity.start(this@MainActivity, streams[index])
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } catch (e: Exception) {
                    binding.kotelStreamProgress.isVisible = false
                    Toast.makeText(this@MainActivity, R.string.stream_picker_network_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
