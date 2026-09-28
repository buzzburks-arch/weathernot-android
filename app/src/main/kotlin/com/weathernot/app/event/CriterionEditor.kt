package com.weathernot.app.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.weathernot.app.ui.FieldError
import com.weathernot.app.ui.LocalUnits
import com.weathernot.app.ui.MutedText
import com.weathernot.app.ui.SwitchRow
import com.weathernot.kit.format.CriterionDraft
import com.weathernot.kit.format.CriterionWording
import com.weathernot.kit.format.DURATIONS
import com.weathernot.kit.format.blankDraft
import com.weathernot.kit.format.canonicalOf
import com.weathernot.kit.format.criterionClause
import com.weathernot.kit.format.criterionSentence
import com.weathernot.kit.format.defaultWatch
import com.weathernot.kit.format.draftError
import com.weathernot.kit.format.formatWindow
import com.weathernot.kit.format.hintFor
import com.weathernot.kit.format.phrasingOf
import com.weathernot.kit.format.sliderText
import com.weathernot.kit.format.sliderValue
import com.weathernot.kit.format.thresholdInputError
import com.weathernot.kit.format.toCanonicalValue
import com.weathernot.kit.format.toDisplayString
import com.weathernot.kit.format.unitLabel
import com.weathernot.kit.format.watchInputError
import com.weathernot.kit.format.watchToCanonical
import com.weathernot.kit.models.GridVariable
import com.weathernot.kit.models.Segment
import com.weathernot.kit.models.UnitSystem
import kotlinx.coroutines.launch
import java.time.ZoneId

/** The draft's wording, when it's valid enough to word. */
fun previewSentence(unitSystem: UnitSystem, d: CriterionDraft): String? {
    if (draftError(d) != null) return null
    return criterionSentence(
        unitSystem,
        CriterionWording(
            variable = d.variable,
            operator = d.operator,
            threshold = toCanonicalValue(unitSystem, d.variable, d.threshold.trim().toDouble()),
            watchThreshold = watchToCanonical(unitSystem, d.variable, d.watchThreshold),
            minDurationHours = d.durationHours,
            also = d.also?.let {
                com.weathernot.kit.models.Condition(it.variable, it.operator, toCanonicalValue(unitSystem, it.variable, it.threshold.trim().toDouble()))
            },
            informational = d.informational,
        ),
    )
}

/**
 * Adds or edits one criterion. [onSave] returns an error to show, or null
 * when saved (the sheet then closes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CriterionEditorSheet(
    title: String,
    initial: CriterionDraft,
    onDismiss: () -> Unit,
    onSave: suspend (CriterionDraft) -> String?,
    lockVariable: Boolean = false,
    segments: List<Segment> = emptyList(),
    zone: ZoneId? = null,
    saveLabel: String = "Save",
) {
    val unitSystem = LocalUnits.current
    var draft by remember { mutableStateOf(initial) }
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            CriterionFields(
                draft = draft,
                onChange = { draft = it },
                unitSystem = unitSystem,
                lockVariable = lockVariable,
                segments = segments,
                zone = zone,
                showErrors = tried,
            )
            previewSentence(unitSystem, draft)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            FieldError(serverError)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !saving,
                    onClick = {
                        tried = true
                        if (draftError(draft) != null) return@Button
                        saving = true
                        serverError = null
                        scope.launch {
                            val failure = onSave(draft)
                            saving = false
                            if (failure == null) onDismiss() else serverError = failure
                        }
                    },
                ) { Text(if (saving) "Saving…" else saveLabel) }
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

/**
 * "Warn me if [wind gusts] [reach] [20] mph or more" as form fields -- the
 * web app's CriterionBuilder, less its second-condition builder (an
 * existing second condition is kept and can be removed).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CriterionFields(
    draft: CriterionDraft,
    onChange: (CriterionDraft) -> Unit,
    unitSystem: UnitSystem,
    lockVariable: Boolean,
    segments: List<Segment>,
    zone: ZoneId?,
    showErrors: Boolean,
) {
    val phrasing = phrasingOf(draft.variable)
    val unit = unitLabel(unitSystem, draft.variable)

    // What to watch.
    if (lockVariable) {
        Text(draft.variable.label, style = MaterialTheme.typography.titleSmall)
    } else {
        var open by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
            OutlinedTextField(
                value = draft.variable.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("What to watch") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                GridVariable.pickable.forEach { variable ->
                    DropdownMenuItem(
                        text = { Text(variable.label) },
                        onClick = {
                            open = false
                            if (variable != draft.variable) {
                                onChange(blankDraft(unitSystem, variable).copy(segmentId = draft.segmentId, informational = draft.informational))
                            }
                        },
                    )
                }
            }
        }
    }

    // Which part of the event.
    if (segments.isNotEmpty()) {
        var open by remember { mutableStateOf(false) }
        val current = segments.firstOrNull { it.segmentId == draft.segmentId }
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
            OutlinedTextField(
                value = current?.name ?: "The whole event",
                onValueChange = {},
                readOnly = true,
                label = { Text("During") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("The whole event") }, onClick = {
                    open = false
                    onChange(draft.copy(segmentId = null))
                })
                segments.forEach { segment ->
                    DropdownMenuItem(
                        text = {
                            Text(segment.name + (zone?.let { " · " + formatWindow(segment.startTime, segment.endTime, it) } ?: ""))
                        },
                        onClick = {
                            open = false
                            onChange(draft.copy(segmentId = segment.segmentId))
                        },
                    )
                }
            }
        }
    }

    // Which way (temperature only).
    if (phrasing.directions.isNotEmpty()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            phrasing.directions.forEachIndexed { index, direction ->
                SegmentedButton(
                    selected = draft.operator == direction.operator,
                    onClick = {
                        if (draft.operator != direction.operator) {
                            // A Watch level on the old side of the threshold no longer makes sense.
                            onChange(draft.copy(operator = direction.operator, watchThreshold = ""))
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, phrasing.directions.size),
                ) { Text(direction.label) }
            }
        }
    }

    // The No-go threshold.
    val thresholdError = if (showErrors || draft.threshold.isNotBlank()) thresholdInputError(draft.variable, draft.threshold) else null
    if (phrasing.levels) {
        Text("Warn me at", style = MaterialTheme.typography.labelLarge)
        LevelChoice(draft.variable, unitSystem, draft.threshold) { onChange(draft.copy(threshold = it, watchThreshold = "")) }
    } else {
        val range = phrasing.range.getValue(unitSystem)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = draft.threshold,
                onValueChange = { onChange(draft.copy(threshold = it)) },
                label = { Text(if (draft.hasWatch) "No-go level" else "Threshold") },
                suffix = { if (unit.isNotEmpty()) Text(unit) },
                isError = thresholdError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(150.dp),
            )
            val words = phrasing.words.getValue(draft.operator)
            MutedText("${phrasing.subject} ${words.first} … ${words.second}".trim())
        }
        Slider(
            value = sliderValue(unitSystem, draft.variable, draft.threshold).toFloat(),
            onValueChange = { onChange(draft.copy(threshold = sliderText(unitSystem, draft.variable, it.toDouble()))) },
            valueRange = range.min.toFloat()..range.max.toFloat(),
        )
        canonicalOf(unitSystem, draft.variable, draft.threshold)?.let { hintFor(draft.variable, draft.operator, it) }?.let {
            MutedText(it)
        }
    }
    FieldError(thresholdError)

    // An earlier heads-up.
    SwitchRow("Heads-up first (Watch level)", "A Watch when it gets close, before the No-go.", draft.hasWatch) { on ->
        onChange(draft.copy(watchThreshold = if (on) defaultWatch(unitSystem, draft.variable, draft.operator, draft.threshold) else ""))
    }
    if (draft.hasWatch) {
        if (phrasing.levels) {
            LevelChoice(draft.variable, unitSystem, draft.watchThreshold, below = draft.threshold.toDoubleOrNull()) {
                onChange(draft.copy(watchThreshold = it))
            }
        } else {
            OutlinedTextField(
                value = draft.watchThreshold,
                onValueChange = { onChange(draft.copy(watchThreshold = it)) },
                label = { Text("Watch level") },
                suffix = { if (unit.isNotEmpty()) Text(unit) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(150.dp),
            )
        }
        FieldError(watchInputError(draft.variable, draft.operator, draft.threshold, draft.watchThreshold))
    }

    // How long it has to hold.
    if (!phrasing.levels) {
        Text("For how long", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = draft.durationHours == null, onClick = { onChange(draft.copy(durationHours = null)) }, label = { Text("At any point") })
            DURATIONS.forEach { hours ->
                FilterChip(
                    selected = draft.durationHours == hours,
                    onClick = { onChange(draft.copy(durationHours = hours)) },
                    label = { Text("$hours+ hours") },
                )
            }
        }
    }

    draft.also?.let { also ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            val threshold = also.threshold.trim().toDoubleOrNull()
            Text(
                "Only if " + (threshold?.let { criterionClause(unitSystem, also.variable, also.operator, toCanonicalValue(unitSystem, also.variable, it)) } ?: "a second condition holds"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onChange(draft.copy(also = null)) }) { Text("Remove") }
        }
    }

    // Informational only.
    SwitchRow("Just show it — no alerts", "Shown and checked, but never changes the verdict or sends an alert.", draft.informational) {
        onChange(draft.copy(informational = it))
    }
}

/** A named level (NWS advisory / watch / warning), picked from its key points. */
@Composable
private fun LevelChoice(variable: GridVariable, unitSystem: UnitSystem, selected: String, below: Double? = null, onPick: (String) -> Unit) {
    Column {
        phrasingOf(variable).marks.filter { below == null || it.value < below }.forEach { mark ->
            val value = toDisplayString(unitSystem, variable, mark.value)
            val isSelected = selected.trim().toDoubleOrNull() == value.toDouble()
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = isSelected, onClick = { onPick(value) }, role = Role.RadioButton)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Text(mark.hint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
