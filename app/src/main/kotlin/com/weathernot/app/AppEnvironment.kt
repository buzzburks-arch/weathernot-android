package com.weathernot.app

import android.content.Context
import com.weathernot.kit.networking.ApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * The app's one [ApiClient]. The base URLs come from BuildConfig
 * (app/build.gradle.kts): 10.0.2.2 is the emulator's name for the host's
 * localhost. A physical device needs the machine's LAN IP there instead.
 */
object AppEnvironment {
    const val apiBaseUrl: String = BuildConfig.API_BASE_URL

    /** Where share links point: the web app's status page. */
    const val webBaseUrl: String = BuildConfig.WEB_BASE_URL

    /** For work that should finish even if the screen that started it goes away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var api: ApiClient
        private set

    fun init(context: Context) {
        if (::api.isInitialized) return
        api = ApiClient(apiBaseUrl, SecureTokenStore(context.applicationContext), HttpClient(OkHttp))
    }

    fun statusUrl(token: String): String = "$webBaseUrl/status/$token"
}
