package com.weathernot.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.weathernot.app.auth.LoginScreen
import com.weathernot.app.auth.WelcomeScreen
import com.weathernot.app.theme.WeatherNotTheme
import com.weathernot.app.ui.LoadingBox
import com.weathernot.app.ui.LocalUnits

class MainActivity : ComponentActivity() {
    private val session: SessionViewModel by viewModels {
        viewModelFactory { initializer { SessionViewModel(AppEnvironment.api) } }
    }

    /** A weathernot:// link that arrived while the app was already open. */
    private var newIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WeatherNotTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Root(session, newIntent, onIntentHandled = { newIntent = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        newIntent = intent
    }
}

@Composable
private fun Root(session: SessionViewModel, newIntent: Intent?, onIntentHandled: () -> Unit) {
    val preferences = session.preferences
    when {
        !session.signedIn -> LoginScreen(session)
        session.loadError -> LoadFailed(onRetry = session::load, onSignOut = session::signOut)
        preferences == null -> LoadingBox()
        // Asked once, on first sign-in, then on to where they were going.
        !preferences.eventTypesAsked -> WelcomeScreen(session)
        else -> CompositionLocalProvider(LocalUnits provides preferences.unitSystem) {
            AppNavHost(session, newIntent, onIntentHandled)
        }
    }
}

@Composable
private fun LoadFailed(onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Couldn't reach WeatherNot.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Check your connection, or that the API is running at ${AppEnvironment.apiBaseUrl}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry) { Text("Try again") }
        TextButton(onClick = onSignOut) { Text("Sign out") }
    }
}
