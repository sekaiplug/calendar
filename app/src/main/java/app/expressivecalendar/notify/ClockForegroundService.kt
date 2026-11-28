package app.expressivecalendar.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.expressivecalendar.MainActivity
import app.expressivecalendar.R

class ClockForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "clock_live_alerts"
        const val CHANNEL_NAME = "Live Alerts (Focus & Timer)"
        const val NOTIFICATION_ID = 202611

        const val ACTION_START = "app.expressivecalendar.action.START_TIMER"
        const val ACTION_TICK = "app.expressivecalendar.action.TICK_TIMER"
        const val ACTION_PAUSE = "app.expressivecalendar.action.PAUSE_TIMER"
        const val ACTION_STOP = "app.expressivecalendar.action.STOP_TIMER"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_END_TIME = "extra_end_time"
        const val EXTRA_REMAINING_SECONDS = "extra_remaining_seconds"
        const val EXTRA_IS_PAUSED = "extra_is_paused"

        fun start(context: Context, title: String, remainingSeconds: Long) {
            val intent = Intent(context, ClockForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
                putExtra(EXTRA_END_TIME, System.currentTimeMillis() + (remainingSeconds * 1000L))
                putExtra(EXTRA_IS_PAUSED, false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateTick(context: Context, remainingSeconds: Long, endTime: Long) {
            val intent = Intent(context, ClockForegroundService::class.java).apply {
                action = ACTION_TICK
                putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
                putExtra(EXTRA_END_TIME, endTime)
            }
            context.startService(intent)
        }

        fun pause(context: Context, title: String, remainingSeconds: Long) {
            val intent = Intent(context, ClockForegroundService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
                putExtra(EXTRA_IS_PAUSED, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClockForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Focus Timer"
                val remainingSeconds = intent.getLongExtra(EXTRA_REMAINING_SECONDS, 0L)
                val notification = buildPausedNotification(title, remainingSeconds)
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_TICK -> {
                val remainingSeconds = intent.getLongExtra(EXTRA_REMAINING_SECONDS, 0L)
                val endTime = intent.getLongExtra(EXTRA_END_TIME, System.currentTimeMillis())
                val notification = buildRunningNotification("Focus Timer", endTime, remainingSeconds)
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIFICATION_ID, notification)
            }
            ACTION_START, null -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Focus Timer"
                val remainingSeconds = intent?.getLongExtra(EXTRA_REMAINING_SECONDS, 0L) ?: 0L
                val endTime = intent?.getLongExtra(EXTRA_END_TIME, System.currentTimeMillis()) ?: System.currentTimeMillis()
                val notification = buildRunningNotification(title, endTime, remainingSeconds)
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_STICKY
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Displays live countdown timer capsule on Live Alerts and notification bar"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildRunningNotification(title: String, endTime: Long, remainingSeconds: Long = 0L): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, ClockReceiver::class.java).apply {
            action = ClockReceiver.ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ClockReceiver::class.java).apply {
            action = ClockReceiver.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val shortTimeText = String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)

        val extras = Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            putString("android.shortCriticalText", shortTimeText)
            putBoolean("android.chronometer", true)
            putBoolean("android.showChronometer", true)
            putBoolean("android.showWhen", true)
            putBoolean("android.substName", false)
            // Tell OxygenOS/ColorOS Fluid Cloud to treat this as an active timer
            putString("oplus_app_category", "timer")
            putString("oplus_smart_type", "timer")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_event)
            .setContentTitle(shortTimeText)
            .setContentText("Focus session in progress")
            .setSubText(title)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endTime)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Pause", pausePendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addExtras(extras)
            .build()
    }

    private fun buildPausedNotification(title: String, remainingSeconds: Long): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent(this, ClockReceiver::class.java).apply {
            action = ClockReceiver.ACTION_RESUME
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            this,
            3,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ClockReceiver::class.java).apply {
            action = ClockReceiver.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            4,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val timeFormatted = String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)

        val extras = Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_event)
            .setContentTitle(title)
            .setContentText("Paused at $timeFormatted")
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(false)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Resume", resumePendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addExtras(extras)
            .build()
    }
}
