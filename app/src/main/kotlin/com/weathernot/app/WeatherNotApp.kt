package com.weathernot.app

import android.app.Application
import org.osmdroid.config.Configuration

class WeatherNotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppEnvironment.init(this)
        // OpenStreetMap's tile servers turn away requests with no user agent.
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
    }
}
