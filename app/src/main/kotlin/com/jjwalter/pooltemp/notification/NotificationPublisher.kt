package com.jjwalter.pooltemp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jjwalter.pooltemp.MainActivity

/**
 * Single ongoing notification showing the current pool temperature + heater
 * state. Refreshed by [RefreshWorker] on a periodic schedule.
 *
 * Importance is LOW so the notification is silent / non-alerting -- it's a
 * status row, not an alert. setOnlyAlertOnce protects against the initial
 * post making any sound on devices that override channel importance.
 */
object NotificationPublisher {
    const val CHANNEL_ID = "pool_temp_ongoing"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Pool temperature",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent current pool temperature in the notification shade."
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        nm.createNotificationChannel(ch)
    }

    fun post(context: Context, title: String, text: String) {
        ensureChannel(context)
        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            // TODO Phase 6: ship a proper monochrome status icon
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tap)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, n)
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }
}
