package com.jjwalter.pooltemp.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns the unique-name periodic work request. Safe to call [schedule]
 * repeatedly (UPDATE policy keeps a single registration and reapplies the
 * constraints / interval if they change).
 */
object Scheduler {
    private const val WORK_NAME = "pool_temp_refresh"
    private const val INTERVAL_MIN = 30L

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(INTERVAL_MIN, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        NotificationPublisher.clear(context)
    }
}
