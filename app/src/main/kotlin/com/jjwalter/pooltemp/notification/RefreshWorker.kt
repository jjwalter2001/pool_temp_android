package com.jjwalter.pooltemp.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjwalter.pooltemp.PoolTempApp
import com.jjwalter.pooltemp.data.ApiClient
import com.jjwalter.pooltemp.data.Reading
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

/**
 * Periodic background fetch. Pulls /api/readings + /api/switch, picks the
 * primary pool sensor (first device whose name contains "pool", else the
 * first device), and updates the ongoing notification.
 *
 * On failure: returns retry() so WorkManager's exponential backoff kicks in
 * for the next attempt instead of waiting the full 30 min.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PoolTempApp
        val cfg = app.settings.current()
        if (!cfg.isComplete) return Result.success()  // not configured yet

        val api = ApiClient.forConfig(cfg)
        return try {
            val (readings, switch) = coroutineScope {
                val r = async { api.readings() }
                val s = async { runCatching { api.switch() }.getOrNull() }
                r.await() to s.await()
            }
            val primary = pickPrimary(readings)
            val title = primary?.temperatureF
                ?.let { String.format(Locale.US, "Pool: %.1f °F", it) }
                ?: "Pool: —"
            val heater = when (switch?.state) {
                1 -> "Heater: ON"
                0 -> "Heater: OFF"
                else -> null
            }
            val ago = primary?.lastUpdateAgo?.let { agoString(it * 1000L) }
            val text = listOfNotNull(heater, ago?.let { "Updated $it" })
                .joinToString(" · ")
                .ifBlank { "Tap to open the dashboard" }
            NotificationPublisher.post(applicationContext, title, text)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

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
