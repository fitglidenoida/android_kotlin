package com.trailblazewellness.fitglide

import android.app.Application
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager

class FitGlideApplication : Application() {
    lateinit var healthConnectManager: HealthConnectManager
        private set

    override fun onCreate() {
        super.onCreate()
        healthConnectManager = HealthConnectManager(this)
    }
}