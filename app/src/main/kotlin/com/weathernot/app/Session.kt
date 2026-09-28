package com.weathernot.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weathernot.kit.models.EventType
import com.weathernot.kit.models.EventTypeInfo
import com.weathernot.kit.models.UserPreferences
import com.weathernot.kit.networking.ApiClient
import kotlinx.coroutines.launch

/**
 * App-wide sign-in state and the viewer's preferences (units, event
 * types) -- the web app's AuthContext + PreferencesContext in one.
 */
class SessionViewModel(private val api: ApiClient) : ViewModel() {
    var signedIn by mutableStateOf(api.isSignedIn)
        private set
    var preferences by mutableStateOf<UserPreferences?>(null)
        private set
    var loadError by mutableStateOf(false)
        private set

    /** The event-type picker's entries, in the server's order. */
    var eventTypes by mutableStateOf(EventType.entries.map { EventTypeInfo(it, it.label) })
        private set

    init {
        if (signedIn) load()
        viewModelScope.launch {
            api.sessionExpired.collect {
                signedIn = false
                preferences = null
            }
        }
    }

    fun load() {
        loadError = false
        viewModelScope.launch {
            runCatching { api.me() }
                .onSuccess { preferences = it }
                .onFailure { if (api.isSignedIn) loadError = true }
        }
        viewModelScope.launch {
            runCatching { api.listEventTypes() }.onSuccess { if (it.isNotEmpty()) eventTypes = it }
        }
    }

    /** Signs in and loads the profile; throws on failure for the caller to word. */
    suspend fun signIn(email: String) {
        api.signIn(email.trim())
        preferences = api.me()
        signedIn = true
        load()
    }

    fun signOut() {
        api.signOut()
        signedIn = false
        preferences = null
    }

    /** Runs a preferences change and keeps what the server sends back. */
    suspend fun update(change: suspend ApiClient.() -> UserPreferences): UserPreferences =
        api.change().also { preferences = it }
}
