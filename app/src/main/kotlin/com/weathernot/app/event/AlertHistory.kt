package com.weathernot.app.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.weathernot.app.theme.LocalPalette
import com.weathernot.app.ui.FieldError
import com.weathernot.app.ui.MutedText
import com.weathernot.app.ui.Pill
import com.weathernot.kit.format.criterionSentence
import com.weathernot.kit.format.formatStamp
import com.weathernot.kit.format.formatValue
import com.weathernot.kit.format.sentVia
import com.weathernot.kit.format.wording
import com.weathernot.kit.models.Alert
import com.weathernot.kit.models.Criterion
import com.weathernot.kit.models.GoNoGo
import com.weathernot.kit.models.UnitSystem
import kotlinx.coroutines.launch

/**
 * The event's alerts, newest first: what set each off, what was sent and
 * how, and the owner's note on what they did about it -- a decision trail.
 */
@Composable
fun AlertHistory(
    alerts: List<Alert>,
    criteria: List<Criterion>,
    unitSystem: UnitSystem,
    onSaveNote: suspend (String, String) -> String?,
    onShowCriterion: (String) -> Unit,
) {
    if (alerts.isEmpty()) {
        MutedText("No alerts sent yet.")
        return
    }
    Column {
        alerts.sortedByDescending { it.sentAt }.forEachIndexed { index, alert ->
            if (index > 0) HorizontalDivider(color = LocalPalette.current.border)
            AlertRow(alert, criteria.firstOrNull { it.criterionId == alert.criterionId }, unitSystem, onSaveNote, onShowCriterion)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertRow(
    alert: Alert,
    criterion: Criterion?,
    unitSystem: UnitSystem,
    onSaveNote: suspend (String, String) -> String?,
    onShowCriterion: (String) -> Unit,
) {
    val palette = LocalPalette.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(alert.note) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val fallback = if (criterion != null && alert.value != null) {
        "${criterionSentence(unitSystem, criterion.wording())} It was ${formatValue(unitSystem, criterion.variable, alert.value!!)}."
    } else {
        "The details of this alert weren't kept."
    }
    val basis = listOfNotNull(
        when (alert.mode) {
            "climatology" -> "Historical outlook"
            "forecast" -> "Forecast"
            else -> null
        },
        alert.confidence?.let { "$it confidence" },
        sentVia(alert.channels, alert.held),
    ).joinToString(" · ").replaceFirstChar { it.uppercase() }

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                alert.kind == "improved" -> Pill("Improved", palette.go)
                alert.kind == "decision" -> Pill("Decision time", MaterialTheme.colorScheme.primary)
                alert.level == GoNoGo.NO_GO -> Pill("No-go", palette.hold)
                alert.level == GoNoGo.WATCH -> Pill("Watch", palette.caution)
            }
            Text(alert.title ?: "Alert", fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.CenterVertically))
        }
        MutedText(formatStamp(alert.sentAt))
        Text(alert.body ?: fallback, style = MaterialTheme.typography.bodyMedium)
        MutedText(basis)
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 2000) draft = it },
                placeholder = { Text("What did you do about it? e.g. Called the tent company, moved to the indoor backup.") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            FieldError(error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !saving, onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        error = onSaveNote(alert.alertId, draft)
                        saving = false
                        if (error == null) editing = false
                    }
                }) { Text(if (saving) "Saving…" else "Save note") }
                OutlinedButton(onClick = { editing = false }) { Text("Cancel") }
            }
        } else {
            if (alert.note.isNotBlank()) {
                Text(
                    "Your note: ${alert.note}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                )
            }
            Row {
                if (criterion != null) TextButton(onClick = { onShowCriterion(criterion.criterionId) }) { Text("See the criterion") }
                TextButton(onClick = {
                    draft = alert.note
                    error = null
                    editing = true
                }) { Text(if (alert.note.isNotBlank()) "Edit note" else "Add a note") }
            }
        }
    }
}
