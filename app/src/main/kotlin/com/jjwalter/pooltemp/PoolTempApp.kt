package com.jjwalter.pooltemp

import android.app.Application
import com.jjwalter.pooltemp.data.Settings

/**
 * Application singleton. Holds the [Settings] handle and (later, in Phase 5)
 * the WorkManager scheduling for the persistent pool-temp notification.
 */
class PoolTempApp : Application() {
    val settings: Settings by lazy { Settings(this) }

    override fun onCreate() {
        super.onCreate()
        // Phase 5 will schedule the periodic refresh worker here once the
        // user has completed onboarding (settings.hasConfig.first() == true).
    }
}
