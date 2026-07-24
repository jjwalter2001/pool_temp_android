package com.jjwalter.pooltemp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jjwalter.pooltemp.MainActivity
import com.jjwalter.pooltemp.data.LightningState
import com.jjwalter.pooltemp.data.Reading
import com.jjwalter.pooltemp.data.SwitchState
import java.util.Locale

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

    /**
     * Render + post the ongoing status notification from domain data. Shared by
     * [RefreshWorker] (periodic background refresh) and the dashboard (immediate
     * update whenever it has fresh state, e.g. right after an arm/disarm) so the
     * two never disagree. Callers are responsible for honoring the user's
     * notifications-enabled preference before calling.
     */
    fun postStatus(
        context: Context,
        readings: List<Reading>,
        switch: SwitchState?,
        lightning: LightningState?,
    ) {
        val primary = pickPrimary(readings)
        val title = primary?.temperatureF
            ?.let { String.format(Locale.US, "Pool: %.1f °F", it) }
            ?: "Pool: —"
        val heater = when (switch?.state) {
            1 -> "Heater: ON"
            0 -> "Heater: OFF"
            else -> null
        }
        val alert = when (lightning?.armed) {
            true -> "Alert: ON"
            false -> "Alert: OFF"
            else -> null
        }
        val ago = primary?.lastUpdateAgo?.let { agoString(it * 1000L) }
        val text = listOfNotNull(heater, alert, ago?.let { "Updated $it" })
            .joinToString(" · ")
            .ifBlank { "Tap to open the dashboard" }
        post(context, title, text)
    }

    /** Primary pool sensor: first device whose name contains "pool", else the
     *  first device (matches the dashboard's own selection). */
    private fun pickPrimary(readings: List<Reading>): Reading? {
        if (readings.isEmpty()) return null
        return readings.firstOrNull { (it.name ?: "").contains("pool", ignoreCase = true) }
            ?: readings.first()
    }

    private fun agoString(deltaMs: Long): String {
        val s = (deltaMs / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "${s}s ago"
            s < 3600 -> "${s / 60}m ago"
            s < 86400 -> "${s / 3600}h ago"
            else -> "${s / 86400}d ago"
        }
    }
}
