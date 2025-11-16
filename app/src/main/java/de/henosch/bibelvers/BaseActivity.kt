package de.henosch.bibelvers

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Theme vor super.onCreate() setzen
        applyTheme()
        super.onCreate(savedInstanceState)
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences(PREFS_FILE, MODE_PRIVATE)
        val themeMode = prefs.getString(KEY_THEME_MODE, THEME_MODE_SYSTEM) ?: THEME_MODE_SYSTEM

        val mode = when (themeMode) {
            THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            THEME_MODE_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        AppCompatDelegate.setDefaultNightMode(mode)
    }

    companion object {
        const val PREFS_FILE = "bibelvers_prefs"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SHABBAT_OVERRIDE = "shabbat_override"
        const val KEY_RANDOM_VERSE_MODE = "random_verse_mode"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"
        const val THEME_MODE_SYSTEM = "system"
    }
}
