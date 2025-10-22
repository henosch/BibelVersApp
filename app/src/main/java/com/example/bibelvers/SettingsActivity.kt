package com.example.bibelvers

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bibelvers.databinding.ActivitySettingsBinding
import com.example.bibelvers.NotificationScheduler
import com.example.bibelvers.DailyVerseReceiver
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var pushChangeListener: CompoundButton.OnCheckedChangeListener
    private val prefs by lazy { getSharedPreferences(PREFS_FILE, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        val pushEnabled = prefs.getBoolean(KEY_PUSH_NOTIFICATIONS, false)
        binding.pushSwitch.isChecked = pushEnabled
        updateTimeDisplay(prefs.getString(KEY_PUSH_TIME, DEFAULT_TIME) ?: DEFAULT_TIME)

        pushChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (isChecked && !ensureNotificationPermission()) {
                setPushSwitchChecked(false)
                return@OnCheckedChangeListener
            }

            prefs.edit().putBoolean(KEY_PUSH_NOTIFICATIONS, isChecked).apply()
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
                    prefs.edit().putString(KEY_PUSH_TIME, value).apply()
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

        binding.sourceTextView.setOnClickListener {
            if (!prefs.getBoolean(KEY_PUSH_NOTIFICATIONS, false)) {
                Toast.makeText(this, R.string.settings_push_test_disabled, Toast.LENGTH_SHORT).show()
            } else {
                DailyVerseReceiver.showNow(applicationContext)
                Toast.makeText(this, R.string.settings_push_test_sent, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setPushSwitchChecked(true)
                prefs.edit().putBoolean(KEY_PUSH_NOTIFICATIONS, true).apply()
                scheduleNotifications()
                Toast.makeText(this, R.string.settings_push_enabled, Toast.LENGTH_SHORT).show()
            } else {
                setPushSwitchChecked(false)
                prefs.edit().putBoolean(KEY_PUSH_NOTIFICATIONS, false).apply()
                NotificationScheduler.cancelDaily(applicationContext)
                Toast.makeText(this, R.string.settings_push_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val PREFS_FILE = "bibelvers_prefs"
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
