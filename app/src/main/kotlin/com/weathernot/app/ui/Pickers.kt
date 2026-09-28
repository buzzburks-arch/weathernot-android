package com.weathernot.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val CLOCK = DateTimeFormatter.ofPattern("h:mm a")

/**
 * A date and a time as two buttons, each opening its picker. The value
 * is a clock reading with no zone of its own -- the caller says whose
 * clock (the event's place).
 */
@Composable
fun DateTimeField(value: LocalDateTime, onChange: (LocalDateTime) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateButton(value.toLocalDate(), { onChange(LocalDateTime.of(it, value.toLocalTime())) }, Modifier.weight(1.3f))
        TimeButton(value.toLocalTime(), { onChange(LocalDateTime.of(value.toLocalDate(), it)) }, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(value: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
        Text("  " + DAY.format(value), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (open) {
        // The picker speaks UTC midnights; the date itself is all that's kept.
        val state = rememberDatePickerState(initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeButton(value: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        // A labelled one ("From 10:00 PM") needs the room more than the icon.
        if (label == null) Icon(Icons.Outlined.Schedule, contentDescription = null)
        Text((label?.let { "$it " } ?: "  ") + CLOCK.format(value), maxLines = 1)
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
