package com.weathernot.app.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weathernot.app.AppEnvironment
import com.weathernot.app.SessionViewModel
import com.weathernot.app.event.CriterionEditorSheet
import com.weathernot.app.event.previewSentence
import com.weathernot.app.theme.LocalPalette
import com.weathernot.app.ui.DateTimeField
import com.weathernot.app.ui.ErrorBanner
import com.weathernot.app.ui.FieldError
import com.weathernot.app.ui.GradientTopBar
import com.weathernot.app.ui.LocalUnits
import com.weathernot.app.ui.MutedText
import com.weathernot.app.ui.PlaceSearch
import com.weathernot.app.ui.SectionCard
import com.weathernot.kit.format.blankDraft
import com.weathernot.kit.format.defaultEventName
import com.weathernot.kit.format.draftError
import com.weathernot.kit.format.orderedEventTypes
import com.weathernot.kit.format.timeZoneName
import com.weathernot.kit.models.GridVariable

@Composable
fun NewEventScreen(session: SessionViewModel, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val unitSystem = LocalUnits.current
    val preferred = session.preferences?.eventTypes.orEmpty()
    val vm: NewEventViewModel = viewModel { NewEventViewModel(AppEnvironment.api, preferred, unitSystem) }
    val onLastStep = vm.step == STEPS.lastIndex

    // Back steps back through the form before leaving it.
    BackHandler(enabled = vm.step > 0) { vm.back() }

    Scaffold(
        topBar = { GradientTopBar("New event", onBack = onBack) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (vm.step > 0) OutlinedButton(onClick = vm::back) { Text("Back") }
                    Row(Modifier.weight(1f)) {}
                    if (onLastStep) {
                        Button(onClick = { vm.submit(onCreated) }, enabled = !vm.submitting) {
                            Text(if (vm.submitting) "Creating…" else "Create event")
                        }
                    } else {
                        Button(onClick = vm::next) { Text("Next") }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Step ${vm.step + 1} of ${STEPS.size} · ${STEPS[vm.step]}", style = MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(progress = { (vm.step + 1f) / STEPS.size }, modifier = Modifier.fillMaxWidth())
            }
            when (vm.step) {
                0 -> WhatAndWhen(vm, session)
                1 -> Where(vm)
                else -> WhatToWatch(vm)
            }
            vm.error?.let { ErrorBanner(it, onDismiss = { vm.error = null }) }
            if (onLastStep && vm.drafts.none { it.checked }) {
                MutedText("Nothing ticked yet — the event won't be monitored until you add criteria.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatAndWhen(vm: NewEventViewModel, session: SessionViewModel) {
    val catalog = session.eventTypes
    val preferred = session.preferences?.eventTypes.orEmpty()
    SectionCard {
        OutlinedTextField(
            value = vm.name,
            onValueChange = { vm.name = it },
            label = { Text("Event name") },
            placeholder = { Text(defaultEventName(vm.eventType, vm.placeName)) },
            supportingText = { Text("What you call it, e.g. \"Sam & Alex's wedding\". Leave blank to use the name shown.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        var open by remember { mutableStateOf(false) }
        val ordered = orderedEventTypes(catalog.map { it.value }, preferred)
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
            OutlinedTextField(
                value = catalog.firstOrNull { it.value == vm.eventType }?.label ?: vm.eventType.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Type of event") },
                supportingText = catalog.firstOrNull { it.value == vm.eventType }?.hint?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                ordered.forEachIndexed { index, type ->
                    if (index == preferred.size && preferred.isNotEmpty()) HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(catalog.firstOrNull { it.value == type }?.label ?: type.label) },
                        onClick = {
                            open = false
                            vm.setType(type)
                        },
                    )
                }
            }
        }
        Text("Starts", style = MaterialTheme.typography.labelLarge)
        DateTimeField(vm.start, vm::changeStart)
        Text("Ends", style = MaterialTheme.typography.labelLarge)
        DateTimeField(vm.end, { vm.end = it })
        FieldError(vm.shownError(0))
        MutedText(
            if (vm.place != null) {
                "Times are ${timeZoneName(vm.zone)}, where the event is."
            } else {
                "Enter the times as they'll be where the event is — its time zone comes from the location."
            },
        )
        OutlinedTextField(
            value = vm.description,
            onValueChange = { vm.description = it },
            label = { Text("Notes (optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        MutedText("A series (weekly games, a multi-day festival) or an imported schedule is set up on the web app.")
    }
}

@Composable
private fun Where(vm: NewEventViewModel) {
    SectionCard {
        PlaceSearch(picked = vm.place, onPick = vm::pickPlace)
        FieldError(vm.shownError(1))
        OutlinedTextField(
            value = vm.placeName,
            onValueChange = { vm.placeName = it },
            label = { Text("Location name") },
            placeholder = { Text("e.g. Liberty Park, Salt Lake City") },
            supportingText = { Text("Filled in from the place you pick — change it to anything.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WhatToWatch(vm: NewEventViewModel) {
    val unitSystem = LocalUnits.current
    var editingKey by remember { mutableStateOf<String?>(null) }
    var addingOwn by remember { mutableStateOf(false) }
    val palette = LocalPalette.current

    SectionCard {
        MutedText(
            "You'll be alerted when the forecast for your event crosses any of these. We've ticked what usually matters for a " +
                "${vm.eventType.label.lowercase()} — untick anything that doesn't, adjust a threshold, or add your own.",
        )
        when (vm.suggestionStatus) {
            SuggestionStatus.LOADING -> MutedText("Finding suggestions…")
            SuggestionStatus.ERROR -> MutedText("Couldn't load suggestions — add your own below.")
            SuggestionStatus.WAITING -> if (vm.place == null) MutedText("Pick a place first for suggestions.")
            SuggestionStatus.READY -> Unit
        }
        vm.drafts.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = palette.border)
            val invalid = draftError(item.draft)
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = item.checked, onCheckedChange = { checked -> vm.update(item.key) { it.copy(checked = checked) } })
                Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        previewSentence(unitSystem, item.draft) ?: item.draft.variable.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    item.reason?.let { MutedText("Why: $it") }
                    if (item.checked && invalid != null && 2 in vm.tried) FieldError(invalid)
                    TextButton(onClick = { editingKey = item.key }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Adjust")
                    }
                }
            }
        }
        OutlinedButton(onClick = { addingOwn = true }) {
            Icon(Icons.Filled.Add, null)
            Text(" Add your own")
        }
        if (2 in vm.tried && vm.draftsError() != null) FieldError("Fix the highlighted criterion above, or untick it.")
    }

    editingKey?.let { key ->
        val item = vm.drafts.firstOrNull { it.key == key }
        if (item != null) {
            CriterionEditorSheet(
                title = "Adjust criterion",
                initial = item.draft,
                lockVariable = item.suggested,
                saveLabel = "Done",
                onDismiss = { editingKey = null },
                onSave = { draft ->
                    vm.update(key) { it.copy(draft = draft, checked = true) }
                    null
                },
            )
        }
    }
    if (addingOwn) {
        CriterionEditorSheet(
            title = "Add a criterion",
            initial = blankDraft(unitSystem, GridVariable.WIND_GUST),
            saveLabel = "Add",
            onDismiss = { addingOwn = false },
            onSave = { draft ->
                vm.addOwn(draft)
                null
            },
        )
    }
}
