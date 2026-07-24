package com.jjwalter.pooltemp.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jjwalter.pooltemp.PoolTempApp
import com.jjwalter.pooltemp.data.ApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Periodic background fetch. Pulls /api/readings + /api/switch + /api/lightning
 * and updates the ongoing notification via [NotificationPublisher.postStatus].
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
            val (readings, switch, lightning) = coroutineScope {
                val r = async { api.readings() }
                val s = async { runCatching { api.switch() }.getOrNull() }
                val l = async { runCatching { api.lightning() }.getOrNull() }
                Triple(r.await(), s.await(), l.await())
            }
            NotificationPublisher.postStatus(applicationContext, readings, switch, lightning)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
