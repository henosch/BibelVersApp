package de.henosch.bibelvers

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
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
    private var defaultDateChipEndMargin: Int = 0

    private val currentDate: Calendar = Calendar.getInstance()
    private val displayDateFormat = SimpleDateFormat("EEEE, dd. MMMM yyyy", Locale.GERMANY)
    private val prefs by lazy { getSharedPreferences(BaseActivity.PREFS_FILE, MODE_PRIVATE) }
    private var isShabbatActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        defaultDateChipEndMargin = (binding.dateTextView.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd ?: 0

        supportActionBar?.hide()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = false
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

        adjustDateChipSpacing()

        displayVerseForDate(currentDate.time)
        updateGreeting()
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adjustDateChipSpacing()
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

    private fun adjustDateChipSpacing() {
        val widthDp = resources.configuration.screenWidthDp
        val params = binding.dateTextView.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val isSmallScreen = widthDp > 0 && widthDp <= 411

        // Adjust margin
        val targetMargin = if (isSmallScreen) {
            resources.getDimensionPixelSize(R.dimen.main_small_date_end_margin)
        } else {
            defaultDateChipEndMargin
        }
        if (params.marginEnd != targetMargin) {
            params.marginEnd = targetMargin
            binding.dateTextView.layoutParams = params
        }

        // Adjust text size and padding for small screens
        if (isSmallScreen) {
            binding.dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            binding.dateTextView.setPadding(
                resources.getDimensionPixelSize(R.dimen.main_small_date_horizontal_padding),
                binding.dateTextView.paddingTop,
                resources.getDimensionPixelSize(R.dimen.main_small_date_horizontal_padding),
                binding.dateTextView.paddingBottom
            )
        } else {
            binding.dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            val paddingDp16 = (16 * resources.displayMetrics.density).toInt()
            binding.dateTextView.setPadding(
                paddingDp16,
                binding.dateTextView.paddingTop,
                paddingDp16,
                binding.dateTextView.paddingBottom
            )
        }
    }

    private fun showPreviousDay() {
        currentDate.add(Calendar.DAY_OF_YEAR, -1)
        displayVerseForDate(currentDate.time)
    }

    private fun showNextDay() {
        currentDate.add(Calendar.DAY_OF_YEAR, 1)
        displayVerseForDate(currentDate.time)
    }

    private fun returnToToday() {
        currentDate.time = Date()
        displayVerseForDate(currentDate.time)
    }

    private fun updateGreeting() {
        val calendar = Calendar.getInstance()
        val shabbat = isShabbat(calendar)
        isShabbatActive = shabbat
        if (shabbat) {
            binding.greetingTextView.visibility = View.VISIBLE
            binding.greetingTextView.text = getString(R.string.shabbat_shalom)
        } else {
            binding.greetingTextView.visibility = View.GONE
        }
        updateKotelAvailability()
    }

    private fun displayVerseForDate(date: Date, preferLocal: Boolean = false) {
        val formattedDate = displayDateFormat.format(date)
        binding.dateTextView.text = formattedDate.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.GERMANY) else it.toString()
        }
        val entry = BibelVersRepository.getEntry(this, date, preferLocal)
        if (entry != null) {
            binding.bibelversTextView.text = entry.bibelversText
            binding.bibelversVersTextView.text = entry.bibelversVers
            binding.zusatzTextView.text = entry.zusatzText
            binding.zusatzVersTextView.text = entry.zusatzVers
        } else {
            binding.bibelversTextView.text = getString(R.string.bibelvers_not_found)
            binding.bibelversVersTextView.text = ""
            binding.zusatzTextView.text = ""
            binding.zusatzVersTextView.text = ""
        }
    }

    private fun updateKotelAvailability() {
        val blocked = isKotelBlocked()
        binding.kotelStreamButton.alpha = if (blocked) 0.4f else 1f
        if (!blocked) {
            binding.kotelStreamButton.contentDescription = getString(R.string.stream_cross_description)
        }
    }

    private fun isKotelBlocked(): Boolean = isShabbatActive && !isShabbatOverrideEnabled()

    private fun isShabbatOverrideEnabled(): Boolean =
        prefs.getBoolean(BaseActivity.KEY_SHABBAT_OVERRIDE, false)

    private fun isShabbat(calendar: Calendar): Boolean {
        val month = calendar.get(Calendar.MONTH)
        val winterMonths = month >= Calendar.OCTOBER || month <= Calendar.MARCH
        val startHour = if (winterMonths) 17 else 19
        val endHour = if (winterMonths) 17 else 19
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.FRIDAY -> hour > startHour || (hour == startHour && minute >= 0)
            Calendar.SATURDAY -> hour < endHour || (hour == endHour && minute == 0)
            else -> false
        }
    }

    companion object {
        private const val SWIPE_THRESHOLD = 120
        private const val SWIPE_VELOCITY_THRESHOLD = 1200
    }

    private fun setupKotelStreamButton() {
        binding.kotelStreamButton.setOnClickListener {
            if (isKotelBlocked()) {
                Toast.makeText(this, R.string.stream_blocked_shabbat, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
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
