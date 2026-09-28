package com.weathernot.app.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weathernot.app.ui.DateTimeField
import com.weathernot.app.ui.FieldError
import com.weathernot.app.ui.MutedText
import com.weathernot.app.ui.PickedPlace
import com.weathernot.app.ui.PlaceSearch
import com.weathernot.kit.format.defaultEventName
import com.weathernot.kit.format.instantToWallTime
import com.weathernot.kit.format.timeZoneName
import com.weathernot.kit.format.zoneOf
import com.weathernot.kit.models.Event
import kotlinx.coroutines.launch

/** Name, place and window. Changing the window re-checks every criterion against it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDetailsSheet(event: Event, vm: EventDetailViewModel, onDismiss: () -> Unit) {
    val zone = zoneOf(event.timezone)
    var name by remember { mutableStateOf(event.name) }
    var place by remember { mutableStateOf(PickedPlace(event.latitude, event.longitude, event.displayName, event.address)) }
    var placeName by remember { mutableStateOf(event.displayName) }
    var start by remember { mutableStateOf(instantToWallTime(event.startTime, zone)) }
    var end by remember { mutableStateOf(instantToWallTime(event.endTime, zone)) }
    var timesError by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit event", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Event name") },
                placeholder = { Text(defaultEventName(event.eventType, placeName)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PlaceSearch(picked = place, onPick = {
                place = it
                if (it.name.isNotBlank()) placeName = it.name
            })
            OutlinedTextField(
                value = placeName,
                onValueChange = { placeName = it },
                label = { Text("Location name") },
                placeholder = { Text("e.g. Denver, CO") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Starts", style = MaterialTheme.typography.labelLarge)
            DateTimeField(start, {
                // Moving the start keeps the event's length.
                end = end.plus(java.time.Duration.between(start, it))
                start = it
            })
            Text("Ends", style = MaterialTheme.typography.labelLarge)
            DateTimeField(end, { end = it })
            FieldError(timesError)
            MutedText(
                "Times are ${timeZoneName(zone)}, where the event is. Changing the date or time re-checks every criterion " +
                    "against the new window right away.",
            )
            FieldError(serverError)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !saving, onClick = {
                    timesError = if (!end.isAfter(start)) "The end has to be after the start." else null
                    if (timesError != null) return@Button
                    saving = true
                    serverError = null
                    scope.launch {
                        serverError = vm.saveDetails(name, placeName, place.address, place.latitude, place.longitude, start, end)
                        saving = false
                        if (serverError == null) onDismiss()
                    }
                }) { Text(if (saving) "Saving…" else "Save") }
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}
