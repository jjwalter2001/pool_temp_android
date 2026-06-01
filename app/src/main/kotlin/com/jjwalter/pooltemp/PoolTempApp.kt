package com.jjwalter.pooltemp

import android.app.Application
import com.jjwalter.pooltemp.data.Settings
import com.jjwalter.pooltemp.notification.NotificationPublisher

/**
 * Application singleton. Holds the [Settings] handle. The notification
 * channel is registered eagerly here so a Worker that survives across
 * process restarts never hits an "unknown channel" path.
 *
 * WorkManager scheduling itself lives in MainActivity (keyed off
 * Settings.hasConfig) so it doesn't run until the user has finished
 * onboarding.
 */
class PoolTempApp : Application() {
    val settings: Settings by lazy { Settings(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationPublisher.ensureChannel(this)
    }
}
