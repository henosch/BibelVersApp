package de.henosch.bibelvers

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import de.henosch.bibelvers.databinding.ActivitySettingsBinding
import de.henosch.bibelvers.NotificationScheduler
import de.henosch.bibelvers.DailyVerseReceiver
import java.util.Locale

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var pushChangeListener: CompoundButton.OnCheckedChangeListener
    private val prefs by lazy { getSharedPreferences(BaseActivity.PREFS_FILE, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = ContextCompat.getColor(this, R.color.wall_status_bar_color)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.wall_navigation_bar_color)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = isNightMode
        }

        val toolbar = binding.settingsToolbar
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.settings_title)
        }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val initialToolbarPaddingTop = toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                initialToolbarPaddingTop + statusBars.top,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        val scrollView = binding.settingsScrollView
        val initialScrollPaddingBottom = scrollView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                initialScrollPaddingBottom + navBars.bottom
            )
            insets
        }

        val pushEnabled = prefs.getBoolean(KEY_PUSH_NOTIFICATIONS, false)
        binding.pushSwitch.isChecked = pushEnabled
        updateTimeDisplay(prefs.getString(KEY_PUSH_TIME, DEFAULT_TIME) ?: DEFAULT_TIME)

        binding.backLabel.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        pushChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (isChecked && !ensureNotificationPermission()) {
                setPushSwitchChecked(false)
                return@OnCheckedChangeListener
            }

            prefs.edit { putBoolean(KEY_PUSH_NOTIFICATIONS, isChecked) }
            if (isChecked) {
                scheduleNotifications()
            } else {
                NotificationScheduler.cancelDaily(applicationContext)
            }
            val message = if (isChecked) R.string.settings_push_enabled else R.string.settings_push_disabled
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        binding.pushSwitch.setOnCheckedChangeListener(pushChangeListener)

        if (pushEnabled) {
            scheduleNotifications()
        }

        binding.timeButton.setOnClickListener {
            val currentTime = prefs.getString(KEY_PUSH_TIME, DEFAULT_TIME) ?: DEFAULT_TIME
            val parts = currentTime.split(":")
            val currentHour = parts.getOrNull(0)?.toIntOrNull() ?: DEFAULT_TIME_HOUR
            val currentMinute = parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_TIME_MINUTE

            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val value = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                    prefs.edit { putString(KEY_PUSH_TIME, value) }
                    updateTimeDisplay(value)
                    if (binding.pushSwitch.isChecked) {
                        scheduleNotifications()
                    }
                },
                currentHour,
                currentMinute,
                true
            ).apply { setTitle(R.string.settings_time_dialog_title) }.show()
        }

        // Theme-Auswahl Setup
        setupThemeSelection()

        binding.sourceTextView.setOnClickListener {
            if (!prefs.getBoolean(KEY_PUSH_NOTIFICATIONS, false)) {
                Toast.makeText(this, R.string.settings_push_test_disabled, Toast.LENGTH_SHORT).show()
            } else {
                DailyVerseReceiver.showNow(applicationContext)
                Toast.makeText(this, R.string.settings_push_test_sent, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupThemeSelection() {
        val currentTheme = prefs.getString(BaseActivity.KEY_THEME_MODE, BaseActivity.THEME_MODE_SYSTEM) ?: BaseActivity.THEME_MODE_SYSTEM

        // Setze aktuelles Theme
        when (currentTheme) {
            BaseActivity.THEME_MODE_LIGHT -> binding.themeLight.isChecked = true
            BaseActivity.THEME_MODE_DARK -> binding.themeDark.isChecked = true
            else -> binding.themeSystem.isChecked = true
        }

        // Theme-Änderung Handler
        binding.themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.themeLight -> BaseActivity.THEME_MODE_LIGHT
                R.id.themeDark -> BaseActivity.THEME_MODE_DARK
                R.id.themeSystem -> BaseActivity.THEME_MODE_SYSTEM
                else -> BaseActivity.THEME_MODE_SYSTEM
            }

            if (newTheme != currentTheme) {
                prefs.edit { putString(BaseActivity.KEY_THEME_MODE, newTheme) }
                // Activity neu erstellen um Theme anzuwenden
                recreate()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setPushSwitchChecked(true)
                prefs.edit { putBoolean(KEY_PUSH_NOTIFICATIONS, true) }
                scheduleNotifications()
                Toast.makeText(this, R.string.settings_push_enabled, Toast.LENGTH_SHORT).show()
            } else {
                setPushSwitchChecked(false)
                prefs.edit { putBoolean(KEY_PUSH_NOTIFICATIONS, false) }
                NotificationScheduler.cancelDaily(applicationContext)
                Toast.makeText(this, R.string.settings_push_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val KEY_PUSH_NOTIFICATIONS = "pref_push_notifications"
        const val KEY_PUSH_TIME = "pref_push_time"
        const val DEFAULT_TIME = "08:00"
        const val DEFAULT_TIME_HOUR = 8
        const val DEFAULT_TIME_MINUTE = 0
        private const val REQUEST_NOTIFICATIONS = 1001
    }

    private fun updateTimeDisplay(time: String) {
        val (hour, minute) = parseTime(time)
        binding.timeValue.text = getString(R.string.settings_time_format, hour, minute)
    }

    private fun ensureNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
                return false
            }
        }
        return true
    }

    private fun scheduleNotifications() {
        val time = prefs.getString(KEY_PUSH_TIME, DEFAULT_TIME) ?: DEFAULT_TIME
        NotificationScheduler.scheduleDaily(applicationContext, time)
    }

    private fun setPushSwitchChecked(checked: Boolean) {
        binding.pushSwitch.setOnCheckedChangeListener(null)
        binding.pushSwitch.isChecked = checked
        binding.pushSwitch.setOnCheckedChangeListener(pushChangeListener)
    }

    private fun parseTime(time: String): Pair<Int, Int> {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: DEFAULT_TIME_HOUR
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_TIME_MINUTE
        return hour to minute
    }
}
