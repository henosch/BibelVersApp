package de.henosch.bibelvers

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.widget.CompoundButton
import android.widget.Toast
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.annotation.DimenRes
import androidx.core.text.HtmlCompat
import de.henosch.bibelvers.databinding.ActivitySettingsBinding
import de.henosch.bibelvers.NotificationScheduler
import de.henosch.bibelvers.DailyVerseReceiver
import java.util.Locale

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var pushChangeListener: CompoundButton.OnCheckedChangeListener
    private val prefs by lazy { getSharedPreferences(BaseActivity.PREFS_FILE, MODE_PRIVATE) }
    private var activeThemeMode: String = BaseActivity.THEME_MODE_SYSTEM
    private val defaultTopMargins = mutableMapOf<Int, Int>()
    private var defaultScrollPaddingStart = 0
    private var defaultScrollPaddingEnd = 0
    private var dedicationTapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = false
        }

        val toolbar = binding.settingsToolbar
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.settings_title)
        }
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.action_bar_text_color))
        toolbar.setNavigationOnClickListener { finish() }

        val initialToolbarPaddingTop = toolbar.paddingTop
        val toolbarBaseHeight = toolbar.layoutParams.height.takeIf { it > 0 } ?: getActionBarSize()
        val scrollView = binding.settingsScrollView
        var lastAppliedInset = 0
        val applyToolbarInset: (Int) -> Unit = { insetTop ->
            val safeInset = insetTop.coerceAtLeast(0)
            toolbar.setPadding(
                toolbar.paddingLeft,
                initialToolbarPaddingTop + safeInset,
                toolbar.paddingRight,
                toolbar.paddingBottom
            )
            toolbar.updateLayoutParams<ViewGroup.LayoutParams> {
                height = toolbarBaseHeight + safeInset
            }
            scrollView.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                topMargin = toolbarBaseHeight + safeInset
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val insetTop = resolveStatusBarInset(view, insets)
            lastAppliedInset = insetTop
            applyToolbarInset(insetTop)
            insets
        }
        toolbar.doOnLayout {
            if (lastAppliedInset == 0) {
                val fallbackInset = resolveStatusBarInset(toolbar, null)
                if (fallbackInset != 0) {
                    lastAppliedInset = fallbackInset
                    applyToolbarInset(fallbackInset)
                }
            }
        }
        ViewCompat.requestApplyInsets(toolbar)

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

        activeThemeMode = prefs.getString(BaseActivity.KEY_THEME_MODE, BaseActivity.THEME_MODE_SYSTEM)
            ?: BaseActivity.THEME_MODE_SYSTEM

        val pushEnabled = prefs.getBoolean(KEY_PUSH_NOTIFICATIONS, false)
        binding.pushSwitch.isChecked = pushEnabled
        updateTimeDisplay(prefs.getString(KEY_PUSH_TIME, DEFAULT_TIME) ?: DEFAULT_TIME)
        binding.randomVerseSwitch.isChecked =
            prefs.getBoolean(BaseActivity.KEY_RANDOM_VERSE_MODE, true)

        captureDefaultSpacing()
        applyCompactLayoutIfNeeded()
        updateAccentColors()

        val hintText = getString(R.string.settings_push_hint)
        val clickTarget = getString(R.string.settings_push_hint_click_target)
        val hintSpannable = SpannableString(hintText)
        val clickStart = hintText.indexOf(clickTarget)
        if (clickStart >= 0) {
            val clickSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    if (!prefs.getBoolean(KEY_PUSH_NOTIFICATIONS, false)) {
                        Toast.makeText(this@SettingsActivity, R.string.settings_push_test_disabled, Toast.LENGTH_SHORT).show()
                    } else {
                        DailyVerseReceiver.showNow(applicationContext)
                        Toast.makeText(this@SettingsActivity, R.string.settings_push_test_sent, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                    ds.color = ContextCompat.getColor(this@SettingsActivity, R.color.settings_source_text_color)
                }
            }
            hintSpannable.setSpan(clickSpan, clickStart, clickStart + clickTarget.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.pushHintTextView.apply {
            text = hintSpannable
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = ContextCompat.getColor(context, android.R.color.transparent)
        }

        binding.dataSourceTextView.apply {
            text = HtmlCompat.fromHtml(
                getString(R.string.settings_data_source),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            movementMethod = LinkMovementMethod.getInstance()
        }
        binding.projectInfoTextView.apply {
            text = HtmlCompat.fromHtml(
                getString(R.string.settings_more_info),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, DatenschutzActivity::class.java))
            }
        }
        binding.streamSourceTextView.apply {
            text = HtmlCompat.fromHtml(
                getString(R.string.settings_stream_source),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            movementMethod = LinkMovementMethod.getInstance()
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

        binding.randomVerseSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(BaseActivity.KEY_RANDOM_VERSE_MODE, isChecked) }
        }

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

    }

    private fun resolveStatusBarInset(view: View, insets: WindowInsetsCompat?): Int {
        val fromCallback = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        if (fromCallback > 0) {
            return fromCallback
        }
        val rootInsets = ViewCompat.getRootWindowInsets(view)
        return rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
    }

    private fun getActionBarSize(): Int {
        val typedValue = TypedValue()
        return if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
        } else {
            (56 * resources.displayMetrics.density).toInt()
        }
    }

    private fun setupThemeSelection() {
        when (activeThemeMode) {
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

            if (newTheme != activeThemeMode) {
                prefs.edit { putString(BaseActivity.KEY_THEME_MODE, newTheme) }
                activeThemeMode = newTheme
                val mode = when (newTheme) {
                    BaseActivity.THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    BaseActivity.THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                delegate.applyDayNight()
            }
        }
    }

    private fun captureDefaultSpacing() {
        if (defaultTopMargins.isNotEmpty()) return
        val scrollView = binding.settingsScrollView
        defaultScrollPaddingStart = scrollView.paddingLeft
        defaultScrollPaddingEnd = scrollView.paddingRight

        recordDefaultTopMargin(binding.pushSwitch)
        recordDefaultTopMargin(binding.timeLabel)
        recordDefaultTopMargin(binding.randomVerseTitle)
        recordDefaultTopMargin(binding.randomVerseSwitch)
        recordDefaultTopMargin(binding.randomVerseHint)
        recordDefaultTopMargin(binding.themeLabel)
        recordDefaultTopMargin(binding.dataSourceTextView)
        recordDefaultTopMargin(binding.projectInfoTextView)
        recordDefaultTopMargin(binding.streamSourceTextView)
        recordDefaultTopMargin(binding.dedicationTextView)
    }

    private fun recordDefaultTopMargin(view: View) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        defaultTopMargins[view.id] = params.topMargin
    }

    private fun applyCompactLayoutIfNeeded() {
        if (defaultTopMargins.isEmpty()) {
            captureDefaultSpacing()
        }
        val config = resources.configuration
        val heightDp = config.screenHeightDp
        val widthDp = config.screenWidthDp
        val smallestDp = config.smallestScreenWidthDp
        val useCompact = (widthDp > 0 && widthDp <= 450) ||
            (heightDp > 0 && heightDp <= 860) ||
            (smallestDp > 0 && smallestDp <= 430)
        if (useCompact) {
            val horizontalPadding = resources.getDimensionPixelSize(R.dimen.settings_compact_content_padding)
            val scrollView = binding.settingsScrollView
            scrollView.setPadding(
                horizontalPadding,
                scrollView.paddingTop,
                horizontalPadding,
                scrollView.paddingBottom
            )

            setTopMargin(binding.pushSwitch, R.dimen.settings_compact_push_top_margin)
            setTopMargin(binding.timeLabel, R.dimen.settings_compact_section_spacing)
            setTopMargin(binding.randomVerseTitle, R.dimen.settings_compact_section_spacing)
            setTopMargin(binding.randomVerseSwitch, R.dimen.settings_compact_random_spacing)
            setTopMargin(binding.randomVerseHint, R.dimen.settings_compact_random_spacing)
            setTopMargin(binding.themeLabel, R.dimen.settings_compact_theme_spacing)
            setTopMargin(binding.dataSourceTextView, R.dimen.settings_compact_section_spacing)
            setTopMargin(binding.projectInfoTextView, R.dimen.settings_compact_link_spacing)
            setTopMargin(binding.streamSourceTextView, R.dimen.settings_compact_link_spacing)
            setTopMargin(binding.dedicationTextView, R.dimen.settings_compact_dedication_spacing)
            applyRadioSpacing(compact = true)
        } else {
            restoreDefaultSpacing()
        }
        val dedicationSizePx = resources.getDimension(
            if (useCompact) R.dimen.dedication_text_size_small else R.dimen.dedication_text_size_large
        )
        binding.dedicationTextView.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dedicationSizePx)
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { handleDedicationTap() }
        }
        applyTextSizes(useCompact)
    }

    private fun restoreDefaultSpacing() {
        val scrollView = binding.settingsScrollView
        scrollView.setPadding(
            defaultScrollPaddingStart,
            scrollView.paddingTop,
            defaultScrollPaddingEnd,
            scrollView.paddingBottom
        )

        restoreTopMargin(binding.pushSwitch)
        restoreTopMargin(binding.timeLabel)
        restoreTopMargin(binding.randomVerseTitle)
        restoreTopMargin(binding.randomVerseSwitch)
        restoreTopMargin(binding.randomVerseHint)
        restoreTopMargin(binding.themeLabel)
        restoreTopMargin(binding.dataSourceTextView)
        restoreTopMargin(binding.projectInfoTextView)
        restoreTopMargin(binding.streamSourceTextView)
        restoreTopMargin(binding.dedicationTextView)
        applyRadioSpacing(compact = false)
        applyTextSizes(useCompact = false)
    }

    private fun applyTextSizes(useCompact: Boolean) {
        val titleSizePx = resources.getDimension(
            if (useCompact) R.dimen.settings_title_text_size_compact else R.dimen.settings_title_text_size_regular
        )
        val bodySizePx = resources.getDimension(
            if (useCompact) R.dimen.settings_body_text_size_compact else R.dimen.settings_body_text_size_regular
        )
        setTextSize(binding.pushSwitch, titleSizePx)
        setTextSize(binding.timeLabel, titleSizePx)
        setTextSize(binding.randomVerseTitle, titleSizePx)
        setTextSize(binding.randomVerseSwitch, titleSizePx)
        setTextSize(binding.themeLabel, titleSizePx)
        setTextSize(binding.pushHintTextView, bodySizePx)
        setTextSize(binding.randomVerseHint, bodySizePx)
        setTextSize(binding.dataSourceTextView, bodySizePx)
        setTextSize(binding.projectInfoTextView, bodySizePx)
        setTextSize(binding.streamSourceTextView, bodySizePx)
        setTextSize(binding.timeValue, bodySizePx)
    }

    private fun setTextSize(view: TextView, sizePx: Float) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
    }

    private fun applyRadioSpacing(compact: Boolean) {
        val spacing = resources.getDimensionPixelSize(
            if (compact) R.dimen.settings_compact_radio_spacing else R.dimen.settings_regular_radio_spacing
        )
        val padding = resources.getDimensionPixelSize(
            if (compact) R.dimen.settings_compact_radio_padding else R.dimen.settings_regular_radio_padding
        )
        for (i in 0 until binding.themeRadioGroup.childCount) {
            val child = binding.themeRadioGroup.getChildAt(i)
            val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
            val topMargin = if (i == 0) 0 else spacing
            if (params.topMargin != topMargin) {
                params.topMargin = topMargin
                child.layoutParams = params
            }
            if (child.paddingTop != padding || child.paddingBottom != padding) {
                child.setPadding(child.paddingLeft, padding, child.paddingRight, padding)
            }
            if (child.minimumHeight != 0) {
                child.minimumHeight = 0
            }
        }
    }

    private fun setTopMargin(view: View, @DimenRes dimenRes: Int) {
        val margin = resources.getDimensionPixelSize(dimenRes)
        applyTopMargin(view, margin)
    }

    private fun restoreTopMargin(view: View) {
        val margin = defaultTopMargins[view.id] ?: return
        applyTopMargin(view, margin)
    }

    private fun applyTopMargin(view: View, margin: Int) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.topMargin != margin) {
            params.topMargin = margin
            view.layoutParams = params
        }
    }

    private fun updateAccentColors() {
        val fallbackActive = BibelVersRepository.isFallbackActive(this)
        applyAccentColors(fallbackActive)
    }

    private fun applyAccentColors(fallbackActive: Boolean) {
        val trackTint = AppCompatResources.getColorStateList(
            this,
            if (fallbackActive) R.color.switch_track_red else R.color.switch_track_blue
        )
        val thumbTint = AppCompatResources.getColorStateList(
            this,
            if (fallbackActive) R.color.switch_thumb_red else R.color.switch_thumb_blue
        )
        val radioTint = AppCompatResources.getColorStateList(
            this,
            if (fallbackActive) R.color.radio_tint_red else R.color.radio_tint_blue
        )
        binding.pushSwitch.trackTintList = trackTint
        binding.pushSwitch.thumbTintList = thumbTint
        listOf(binding.themeSystem, binding.themeLight, binding.themeDark).forEach { radio ->
            radio.buttonTintList = radioTint
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

    override fun onResume() {
        super.onResume()
        updateAccentColors()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyCompactLayoutIfNeeded()
        updateAccentColors()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val KEY_PUSH_NOTIFICATIONS = "pref_push_notifications"
        const val KEY_PUSH_TIME = "pref_push_time"
        const val DEFAULT_TIME = "08:00"
        const val DEFAULT_TIME_HOUR = 8
        const val DEFAULT_TIME_MINUTE = 0
        private const val REQUEST_NOTIFICATIONS = 1001
        private const val SECRET_TAP_THRESHOLD = 7
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

    private fun handleDedicationTap() {
        dedicationTapCount++
        if (dedicationTapCount >= SECRET_TAP_THRESHOLD) {
            dedicationTapCount = 0
            val nextValue = !prefs.getBoolean(BaseActivity.KEY_SHABBAT_OVERRIDE, false)
            prefs.edit { putBoolean(BaseActivity.KEY_SHABBAT_OVERRIDE, nextValue) }
            val message = if (nextValue) {
                R.string.settings_shabbat_override_enabled
            } else {
                R.string.settings_shabbat_override_disabled
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

}
