package de.henosch.bibelvers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(BaseActivity.PREFS_FILE, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SettingsActivity.KEY_PUSH_NOTIFICATIONS, false)) {
            Log.d(TAG, "Boot/update broadcast $action received but push disabled")
            return
        }
        val time = prefs.getString(SettingsActivity.KEY_PUSH_TIME, SettingsActivity.DEFAULT_TIME)
            ?: SettingsActivity.DEFAULT_TIME
        Log.d(TAG, "Boot/update broadcast $action – rescheduling daily verse for $time")
        NotificationScheduler.scheduleDaily(appContext, time)
    }

    companion object {
        private const val TAG = "BibelVersPush"
    }
}
