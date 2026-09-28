package com.weathernot.app.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.weathernot.app.ui.DateTimeField
import com.weathernot.app.ui.FieldError
import com.weathernot.app.ui.MutedText
import com.weathernot.kit.format.formatMoment
import com.weathernot.kit.format.instantToWallTime
import com.weathernot.kit.format.timeZoneName
import com.weathernot.kit.models.Event
import com.weathernot.kit.models.UserPreferences
import com.weathernot.kit.format.zoneOf
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * How this event's alerts go out: the decision deadline, email and texts
 * for the owner (their profile's settings unless changed here), and who
 * else to alert -- by email or text, no account needed.
 */
@Composable
fun NotificationsSection(event: Event, vm: EventDetailViewModel, profile: UserPreferences?) {
    val settings = vm.notifications ?: return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DecisionDeadlineField(event, vm)

        val defaults = mapOf(
            NotifyChannel.EMAIL to profile?.emailEnabled,
            NotifyChannel.SMS to profile?.let { it.smsEnabled && it.phoneNumber != null },
            NotifyChannel.IMPROVEMENTS to profile?.improvements,
        )
        ChannelChoice("Email me", settings.email, defaults[NotifyChannel.EMAIL]) { vm.setChannel(NotifyChannel.EMAIL, it) }
        ChannelChoice("Text me", settings.sms, defaults[NotifyChannel.SMS]) { vm.setChannel(NotifyChannel.SMS, it) }
        if (settings.sms == true && profile?.phoneNumber == null) MutedText("Add your phone number on your profile first.")
        ChannelChoice("Tell me when it improves", settings.improvements, defaults[NotifyChannel.IMPROVEMENTS]) {
            vm.setChannel(NotifyChannel.IMPROVEMENTS, it)
        }

        Text("Also alert", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (settings.recipients.isEmpty()) {
            MutedText("Nobody else yet — add a partner, the coach or the venue.")
        }
        settings.recipients.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name + if (r.follower) " · followed from your shared page" else "", fontWeight = FontWeight.Medium)
                    MutedText(listOfNotNull(r.email, r.phone).joinToString(" · "))
                }
                IconButton(onClick = { vm.removeRecipient(r.recipientId) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Stop alerting ${r.name}")
                }
            }
        }
        AddRecipient(vm)
        MutedText("They get the same alerts you do, at the email or number given — no account needed. Your quiet hours apply.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelChoice(label: String, value: Boolean?, profileDefault: Boolean?, onChange: (Boolean?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val defaultWord = profileDefault?.let { if (it) " (on)" else " (off)" } ?: ""
    val options = listOf(null to "As in my profile$defaultWord", true to "On for this event", false to "Off for this event")
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = options.first { it.first == value }.second,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (choice, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    open = false
                    if (choice != value) onChange(choice)
                })
            }
        }
    }
}

/** "I need to decide by" -- when the tent company needs notice. At that time we send where things stand. */
@Composable
private fun DecisionDeadlineField(event: Event, vm: EventDetailViewModel) {
    val deadline = vm.decision ?: return
    val zone = zoneOf(event.timezone)
    val saved = deadline.decideBy?.let { instantToWallTime(it, zone) }
    // Default suggestion: two days before the event, same clock time.
    var wall by remember(saved) {
        mutableStateOf(saved ?: instantToWallTime(event.startTime, zone).minusDays(2).truncatedTo(ChronoUnit.HOURS))
    }
    var editing by remember(saved) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save(value: LocalDateTime?) {
        saving = true
        error = null
        scope.launch {
            error = vm.setDecision(value)
            saving = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("I need to decide by", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (saved != null && !editing) {
            Text(formatMoment(deadline.decideBy!!, zone), style = MaterialTheme.typography.bodyLarge)
            Row {
                TextButton(onClick = { editing = true }) { Text("Change") }
                TextButton(onClick = { save(null) }, enabled = !saving) { Text("Clear") }
            }
        } else if (saved == null && !editing) {
            OutlinedButton(onClick = { editing = true }) { Text("Set a decision deadline") }
        } else {
            DateTimeField(wall, { wall = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { save(wall) }, enabled = !saving && wall != saved) { Text(if (saving) "Saving…" else "Save") }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        }
        MutedText(
            when {
                deadline.sentAt != null -> "Summary sent ${formatMoment(deadline.sentAt!!, zone)}."
                saved != null -> "We'll send where things stand at that time, whatever has changed."
                else -> "E.g. when the tent company needs notice. At that time we send where things stand, " +
                    "whatever has changed. ${timeZoneName(zone)}, where the event is."
            },
        )
        FieldError(error)
    }
}

@Composable
private fun AddRecipient(vm: EventDetailViewModel) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text("Coach Rivera") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") }, placeholder = { Text("coach@example.com") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            phone, { phone = it }, label = { Text("Phone (for texts)") }, placeholder = { Text("+15551234567") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
        )
        FieldError(error)
        OutlinedButton(
            enabled = !adding && (email.isNotBlank() || phone.isNotBlank()),
            onClick = {
                adding = true
                error = null
                scope.launch {
                    error = vm.addRecipient(name, email, phone)
                    if (error == null) {
                        name = ""
                        email = ""
                        phone = ""
                    }
                    adding = false
                }
            },
        ) { Text(if (adding) "Adding…" else "Add") }
    }
}
