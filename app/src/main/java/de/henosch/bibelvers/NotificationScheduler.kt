package de.henosch.bibelvers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object NotificationScheduler {
    private const val REQUEST_CODE = 2001

    private const val TAG = "BibelVersPush"

    fun scheduleDaily(context: Context, timeString: String) {
        val (hour, minute) = parseTime(timeString)
        scheduleDaily(context, hour, minute)
    }

    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(appContext)
        alarmManager.cancel(pendingIntent)

        val triggerTime = computeTriggerTime(hour, minute)
        Log.d(TAG, "Scheduling alarm for $hour:$minute at $triggerTime")

        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            Log.w(TAG, "Exact alarm not permitted; scheduling inexact alarm")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelDaily(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        Log.d(TAG, "Canceling alarm")
        alarmManager.cancel(createPendingIntent(appContext))
    }

    private fun createPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyVerseReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun computeTriggerTime(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun parseTime(time: String): Pair<Int, Int> {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: SettingsActivity.DEFAULT_TIME_HOUR
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: SettingsActivity.DEFAULT_TIME_MINUTE
        return hour to minute
    }
}
