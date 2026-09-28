package com.weathernot.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.weathernot.app.AppEnvironment
import com.weathernot.app.SessionViewModel
import com.weathernot.app.auth.EventTypeChooser
import com.weathernot.app.ui.ErrorBanner
import com.weathernot.app.ui.FieldError
import com.weathernot.app.ui.GradientTopBar
import com.weathernot.app.ui.LoadingBox
import com.weathernot.app.ui.MutedText
import com.weathernot.app.ui.SectionCard
import com.weathernot.app.ui.SwitchRow
import com.weathernot.app.ui.TimeButton
import com.weathernot.app.ui.userMessage
import com.weathernot.kit.format.timeZoneName
import com.weathernot.kit.models.UnitSystem
import com.weathernot.kit.models.UserPreferences
import com.weathernot.kit.networking.ApiClient
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun ProfileScreen(session: SessionViewModel, onBack: () -> Unit) {
    val prefs = session.preferences
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /** Each change saves as it's made, like the web app's profile. */
    fun save(failure: String, change: suspend ApiClient.() -> UserPreferences) {
        error = null
        scope.launch {
            try {
                session.update(change)
            } catch (e: Exception) {
                error = e.userMessage(failure)
            }
        }
    }

    Scaffold(topBar = { GradientTopBar("Profile", onBack = onBack) }) { padding ->
        if (prefs == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(prefs.email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            error?.let { ErrorBanner(it, onDismiss = { error = null }) }

            SectionCard(title = "Units") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(UnitSystem.IMPERIAL to "Imperial (°F, mph, in)", UnitSystem.METRIC to "Metric (°C, m/s, mm)").forEachIndexed { i, (system, label) ->
                        SegmentedButton(
                            selected = prefs.unitSystem == system,
                            onClick = { save("Could not save your preference. Try again.") { setUnitSystem(system) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                MutedText("How thresholds and forecasts are shown and entered throughout the app.")
            }

            EventTypesCard(session)

            SectionCard(title = "Notifications") {
                DeliverySettings(prefs, ::save)
                HorizontalDivider()
                TextMessages(prefs, session, ::save)
            }

            SectionCard {
                MutedText("Connected to ${AppEnvironment.apiBaseUrl}")
                OutlinedButton(onClick = session::signOut) { Text("Sign out") }
            }
        }
    }
}

/** The kinds of event they plan -- shown first when creating one. */
@Composable
private fun EventTypesCard(session: SessionViewModel) {
    val saved = session.preferences?.eventTypes.orEmpty()
    var types by remember(saved) { mutableStateOf(saved) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    SectionCard(title = "Event types you plan") {
        MutedText("These come first when you create an event, and the first is picked for you. Leave them all unticked to see every type.")
        EventTypeChooser(catalog = session.eventTypes, selected = types, onChange = { types = it })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !saving && types != saved, onClick = {
                saving = true
                message = null
                scope.launch {
                    message = try {
                        session.update { setEventTypes(types) }
                        "Saved."
                    } catch (_: Exception) {
                        "Couldn't save your event types. Try again."
                    }
                    saving = false
                }
            }) { Text(if (saving) "Saving…" else "Save event types") }
            message?.let { MutedText(it) }
        }
    }
}

/** Email on or off, a note when things improve, digest or right away, and quiet hours. */
@Composable
private fun DeliverySettings(prefs: UserPreferences, save: (String, suspend ApiClient.() -> UserPreferences) -> Unit) {
    // Quiet hours and the digest run on the viewer's clock.
    val zone = ZoneId.systemDefault()
    var quietStart by remember { mutableStateOf(prefs.quietHoursStart ?: "22:00") }
    var quietEnd by remember { mutableStateOf(prefs.quietHoursEnd ?: "07:00") }
    val failure = "Couldn't save that. Try again."

    SwitchRow("Email me alerts at ${prefs.email}", null, prefs.emailEnabled) { on -> save(failure) { setEmailEnabled(on) } }
    SwitchRow(
        "Tell me when things improve",
        "A note when something you were alerted on clears or drops a level — sent once it has held for a few hours.",
        prefs.improvements,
    ) { on -> save(failure) { setImprovements(on) } }

    Text("Email delivery", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    listOf(false to "As soon as something changes", true to "One digest each morning at 7 AM").forEach { (digest, label) ->
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = prefs.digest == digest,
                    enabled = prefs.emailEnabled,
                    role = Role.RadioButton,
                    onClick = { save(failure) { setDigest(digest, zone.id) } },
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = prefs.digest == digest, onClick = null, enabled = prefs.emailEnabled)
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }

    val quietOn = prefs.quietHoursStart != null
    SwitchRow("Quiet hours", "${timeZoneName(zone)}. Emails and texts during these hours wait until they end — nothing is lost.", quietOn) { on ->
        save(failure) { if (on) setQuietHours(quietStart, quietEnd, zone.id) else setQuietHours(null, null, zone.id) }
    }
    if (quietOn) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeButton(LocalTime.parse(quietStart), {
                quietStart = "%02d:%02d".format(it.hour, it.minute)
                save(failure) { setQuietHours(quietStart, quietEnd, zone.id) }
            }, Modifier.weight(1f), label = "From")
            TimeButton(LocalTime.parse(quietEnd), {
                quietEnd = "%02d:%02d".format(it.hour, it.minute)
                save(failure) { setQuietHours(quietStart, quietEnd, zone.id) }
            }, Modifier.weight(1f), label = "Until")
        }
    }
    MutedText("Push notifications aren't wired up on the server yet. Each event can also change these for itself on its page.")
}

@Composable
private fun TextMessages(prefs: UserPreferences, session: SessionViewModel, save: (String, suspend ApiClient.() -> UserPreferences) -> Unit) {
    var phone by remember(prefs.phoneNumber) { mutableStateOf(prefs.phoneNumber.orEmpty()) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text("Text messages", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Phone number") },
        placeholder = { Text("+15551234567") },
        supportingText = { Text("International format. Used to send SMS alerts when a criterion triggers.") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )
    FieldError(phoneError)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !saving && phone.isNotBlank() && phone.trim() != prefs.phoneNumber, onClick = {
            saving = true
            phoneError = null
            scope.launch {
                try {
                    session.update { setPhoneNumber(phone.trim()) }
                } catch (e: Exception) {
                    phoneError = e.userMessage("Could not save your phone number. Enter it in international format, e.g. +15551234567.")
                }
                saving = false
            }
        }) { Text(if (saving) "Saving…" else "Save phone number") }
        if (prefs.phoneNumber != null) {
            TextButton(onClick = { save("Could not remove your phone number. Try again.") { setPhoneNumber(null) } }) { Text("Remove") }
        }
    }
    SwitchRow(
        "Send SMS alerts to this number",
        if (prefs.phoneNumber == null) "Add a phone number above to turn on SMS alerts." else null,
        prefs.smsEnabled,
        enabled = prefs.phoneNumber != null,
    ) { on -> save("Could not update SMS alerts. Try again.") { setSmsEnabled(on) } }
}
