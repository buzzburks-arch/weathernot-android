package com.weathernot.app.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.weathernot.app.AppEnvironment
import com.weathernot.app.SessionViewModel
import com.weathernot.app.theme.LocalPalette
import com.weathernot.app.ui.ErrorBanner
import com.weathernot.app.ui.SectionCard
import com.weathernot.kit.networking.ApiError
import kotlinx.coroutines.launch

/** Passwordless sign-in: an email, and that's it -- there's no separate sign-up. */
@Composable
fun LoginScreen(session: SessionViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (email.isBlank() || busy) return
        busy = true
        error = null
        scope.launch {
            try {
                session.signIn(email)
            } catch (e: Exception) {
                error = when (e) {
                    is ApiError.Server -> e.detail ?: "Couldn't sign in with that email."
                    else -> "Couldn't sign in. Is the API running at ${AppEnvironment.apiBaseUrl}?"
                }
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(LocalPalette.current.headerBrush)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 40.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.WbCloudy, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                Text("WeatherNot", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Weather alerts for the events you plan — know early whether it's a go.",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Column(Modifier.padding(20.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            SectionCard(title = "Sign in") {
                Text(
                    "Enter your email to continue. WeatherNot uses passwordless sign-in — no password to remember.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    placeholder = { Text("you@example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = ::submit, enabled = email.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Signing in…" else "Continue")
                }
                error?.let { ErrorBanner(it) }
            }
        }
    }
}
