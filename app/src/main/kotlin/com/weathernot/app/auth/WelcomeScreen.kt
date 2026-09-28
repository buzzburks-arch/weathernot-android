package com.weathernot.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weathernot.app.SessionViewModel
import com.weathernot.app.ui.ErrorBanner
import com.weathernot.app.ui.GradientTopBar
import com.weathernot.app.ui.MutedText
import com.weathernot.kit.format.ROLE_PRESETS
import com.weathernot.kit.models.EventType
import com.weathernot.kit.models.EventTypeInfo
import kotlinx.coroutines.launch

/** Asked once, on first sign-in: which kinds of event they plan, so those come first. Skippable. */
@Composable
fun WelcomeScreen(session: SessionViewModel) {
    var types by remember { mutableStateOf(session.preferences?.eventTypes.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun finish(skip: Boolean) {
        saving = true
        error = null
        scope.launch {
            try {
                session.update { if (skip) skipEventTypesQuestion() else setEventTypes(types) }
            } catch (_: Exception) {
                error = "Couldn't save that — try again."
                saving = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        GradientTopBar("WeatherNot")
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("What kinds of events do you plan?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            MutedText("We'll show these first when you create an event. Pick any that fit — you can change them in your profile.")
            EventTypeChooser(catalog = session.eventTypes, selected = types, onChange = { types = it })
            error?.let { ErrorBanner(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { finish(skip = false) }, enabled = !saving && types.isNotEmpty()) {
                    Text(
                        when {
                            saving -> "Saving…"
                            types.isEmpty() -> "Continue"
                            else -> "Continue with ${types.size} type${if (types.size == 1) "" else "s"}"
                        },
                    )
                }
                TextButton(onClick = { finish(skip = true) }, enabled = !saving) { Text("Skip for now") }
            }
        }
    }
}

/** Role shortcuts, then every event type as a toggle chip. Shared with the profile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventTypeChooser(catalog: List<EventTypeInfo>, selected: List<EventType>, onChange: (List<EventType>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Start from what you do", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ROLE_PRESETS.forEach { preset ->
                val active = preset.types.size == selected.size && selected.containsAll(preset.types)
                FilterChip(
                    selected = active,
                    onClick = { onChange(preset.types) },
                    label = { Text(preset.label) },
                )
            }
        }
        Text("Event types", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            catalog.forEach { info ->
                val on = info.value in selected
                FilterChip(
                    selected = on,
                    onClick = { onChange(if (on) selected - info.value else selected + info.value) },
                    label = { Text(info.label) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
