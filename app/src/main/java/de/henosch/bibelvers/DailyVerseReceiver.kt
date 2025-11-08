package de.henosch.bibelvers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Date

class DailyVerseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        handleNotification(context.applicationContext, reschedule = true)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun loadVerseSummary(context: Context): String {
        val entry = BibelVersRepository.getEntry(context, Date(), preferLocal = true)
        return when {
            entry == null -> context.getString(R.string.notification_message)
            entry.bibelversVers.isNotBlank() -> "${entry.bibelversText} (${entry.bibelversVers})"
            else -> entry.bibelversText
        }
    }

    companion object {
        private const val CHANNEL_ID = "daily_verse_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "BibelVersPush"

        fun showNow(context: Context) {
            DailyVerseReceiver().handleNotification(context.applicationContext, reschedule = false)
        }
    }

    private fun handleNotification(context: Context, reschedule: Boolean) {
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_FILE, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SettingsActivity.KEY_PUSH_NOTIFICATIONS, false)) {
            NotificationScheduler.cancelDaily(context)
            Log.d(TAG, "Push disabled – skipping notification")
            return
        }

        if (reschedule) {
            val timeString = prefs.getString(SettingsActivity.KEY_PUSH_TIME, SettingsActivity.DEFAULT_TIME)
                ?: SettingsActivity.DEFAULT_TIME
            NotificationScheduler.scheduleDaily(context, timeString)
            Log.d(TAG, "DailyVerseReceiver triggered, rescheduled for $timeString")
        }

        createChannel(context)

        val summary = loadVerseSummary(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cross)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Notification suppressed – permission missing")
            return
        }

        Log.d(TAG, "Showing notification for daily verse")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
