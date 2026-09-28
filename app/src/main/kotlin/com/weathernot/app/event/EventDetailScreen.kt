package com.weathernot.app.event

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weathernot.app.AppEnvironment
import com.weathernot.app.SessionViewModel
import com.weathernot.app.theme.LocalPalette
import com.weathernot.app.ui.ConfirmDialog
import com.weathernot.app.ui.ErrorBanner
import com.weathernot.app.ui.EventMap
import com.weathernot.app.ui.GradientTopBar
import com.weathernot.app.ui.LoadingBox
import com.weathernot.app.ui.LocalUnits
import com.weathernot.app.ui.MutedText
import com.weathernot.app.ui.SectionCard
import com.weathernot.app.ui.ShowMessages
import com.weathernot.kit.format.blankDraft
import com.weathernot.kit.format.draftFrom
import com.weathernot.kit.format.formatLongDay
import com.weathernot.kit.format.formatWindow
import com.weathernot.kit.format.placeToShow
import com.weathernot.kit.format.viewerWindow
import com.weathernot.kit.format.wording
import com.weathernot.kit.format.zoneOf
import com.weathernot.kit.models.Criterion
import com.weathernot.kit.models.GridVariable
import com.weathernot.kit.models.WatchPhase
import kotlinx.coroutines.delay

@Composable
fun EventDetailScreen(
    session: SessionViewModel,
    eventId: String,
    linkedCriterionId: String?,
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val vm: EventDetailViewModel = viewModel(key = "event:$eventId") { EventDetailViewModel(eventId, AppEnvironment.api) }
    val unitSystem = LocalUnits.current
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(vm.messages, snackbar)

    var sharing by rememberSaveable { mutableStateOf(false) }
    var editingDetails by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var addingCriterion by rememberSaveable { mutableStateOf(false) }
    var editingCriterion by remember { mutableStateOf<Criterion?>(null) }
    var reviewingSuggestions by rememberSaveable { mutableStateOf(false) }
    var shownCriterion by rememberSaveable { mutableStateOf(linkedCriterionId) }

    val event = vm.event
    // A check in flight after an edit: keep polling until it lands.
    LaunchedEffect(event?.evaluating) {
        while (vm.event?.evaluating == true) {
            delay(1500)
            vm.refresh()
        }
    }

    Scaffold(
        topBar = {
            GradientTopBar(event?.name ?: "Event", onBack = onBack) {
                if (event != null) {
                    IconButton(onClick = { sharing = true }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Color.White)
                    }
                    EventMenu(
                        paused = event.isPaused,
                        cancelled = event.isCancelled,
                        enabled = !vm.busy,
                        onEdit = { editingDetails = true },
                        onTogglePaused = { vm.togglePaused() },
                        onToggleCancelled = vm::toggleCancelled,
                        onDuplicate = { vm.duplicate(onOpenEvent) },
                        onDelete = { confirmDelete = true },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            event == null && vm.loadFailed -> Column(Modifier.padding(padding).padding(16.dp)) {
                ErrorBanner(vm.error ?: "Could not load this event.")
                TextButton(onClick = vm::refresh) { Text("Try again") }
            }
            event == null -> LoadingBox(Modifier.padding(padding))
            else -> {
                val zone = zoneOf(event.timezone)
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    vm.seriesInfo?.takeIf { it.id == event.seriesId }?.let { info ->
                        Card(
                            onClick = { onOpenSeries(info.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, LocalPalette.current.border),
                        ) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Repeat, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "  One date of ${info.name}" + if (info.position > 0) " (${info.position} of ${info.count})" else "",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text("All dates →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    vm.error?.let { ErrorBanner(it, onDismiss = { vm.error = null }) }

                    // Header.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(event.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        val place = placeToShow(event.name, event.displayName)
                        Text(
                            listOf(event.eventType.label, place).filter { it.isNotEmpty() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(formatWindow(event.startTime, event.endTime, zone), style = MaterialTheme.typography.bodyMedium)
                        viewerWindow(event.startTime, event.endTime, zone)?.let { MutedText("$it your time") }
                        if (event.address.isNotBlank()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Outlined.Place, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(" ${event.address}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (!event.isActive) {
                        Text(
                            if (event.isCancelled) {
                                "Marked as cancelled — no checks or alerts. It's kept here for your records."
                            } else {
                                "Monitoring is paused — no checks or alerts until you resume it."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                        )
                    }

                    vm.summary?.let { summary ->
                        VerdictPanel(summary, checking = event.evaluating || vm.checking, onCheckNow = vm::checkNow)
                        if (summary.phase != WatchPhase.ENDED) {
                            SectionCard(title = "Hour by hour") {
                                val timeline = vm.timeline
                                if (timeline != null && timeline.rows.isNotEmpty()) {
                                    HourlyTimeline(timeline, zone, unitSystem)
                                    MutedText("Times are the event's. Faded hours are just before and after it; shaded ones are past a Watch or No-go level.")
                                } else {
                                    MutedText(
                                        if (summary.phase == WatchPhase.OUTLOOK && summary.forecastBeginsAt != null) {
                                            "The hourly forecast starts around ${formatLongDay(summary.forecastBeginsAt!!)}."
                                        } else {
                                            "No hourly forecast covers the event yet — it fills in as new forecasts land."
                                        },
                                    )
                                }
                            }
                        }
                    }

                    EventMap(event.latitude, event.longitude, event.displayName.ifBlank { event.name })

                    if (vm.segments.isNotEmpty()) {
                        SectionCard(title = "Parts of the event") {
                            vm.segments.forEach { s ->
                                Row {
                                    Text(s.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    MutedText(formatWindow(s.startTime, s.endTime, zone))
                                }
                            }
                            MutedText("Parts are set up on the web app. Criteria can watch one part instead of the whole event.")
                        }
                    }

                    SectionCard(title = "Criteria") {
                        MutedText(
                            "An alert fires when the forecast (or, far out, the historical outlook) crosses one of these during the event. " +
                                "We keep checking on our own; \"Waiting for forecast\" means nothing covers this event's window yet.",
                        )
                        if (event.seriesId != null) {
                            MutedText("These come from the series. Changing one here changes it for this date only — edit the series to change every upcoming date.")
                        }
                        CriteriaList(
                            event = event,
                            criteria = vm.criteria,
                            segments = vm.segments,
                            vm = vm,
                            unitSystem = unitSystem,
                            linkedCriterionId = shownCriterion,
                            onEdit = { editingCriterion = it },
                        )
                        if (reviewingSuggestions) {
                            SuggestionReview(vm, unitSystem, onDone = { reviewingSuggestions = false })
                        } else {
                            OutlinedButton(onClick = { reviewingSuggestions = true }) { Text("Suggest criteria for this event type") }
                        }
                        OutlinedButton(onClick = { addingCriterion = true }) {
                            Icon(Icons.Filled.Add, null)
                            Text(" Add a criterion")
                        }
                    }

                    SectionCard(title = "Alert history") {
                        AlertHistory(
                            alerts = vm.alerts,
                            criteria = vm.criteria,
                            unitSystem = unitSystem,
                            onSaveNote = vm::saveNote,
                            onShowCriterion = { shownCriterion = it },
                        )
                    }

                    SectionCard(title = "Notifications") {
                        NotificationsSection(event, vm, session.preferences)
                    }
                }
            }
        }
    }

    if (event != null) {
        if (sharing) ShareSheet(event.name, vm, onDismiss = { sharing = false })
        if (editingDetails) EditDetailsSheet(event, vm, onDismiss = { editingDetails = false })
        if (addingCriterion) {
            CriterionEditorSheet(
                title = "Add a criterion",
                initial = blankDraft(unitSystem, GridVariable.WIND_GUST),
                segments = vm.segments,
                zone = zoneOf(event.timezone),
                saveLabel = "Add",
                onDismiss = { addingCriterion = false },
                onSave = { vm.addCriterion(unitSystem, it) },
            )
        }
        editingCriterion?.let { criterion ->
            CriterionEditorSheet(
                title = "Edit criterion",
                initial = draftFrom(unitSystem, criterion.wording(), criterion.segmentId),
                lockVariable = true,
                segments = vm.segments,
                zone = zoneOf(event.timezone),
                onDismiss = { editingCriterion = null },
                onSave = { vm.editCriterion(unitSystem, criterion, it) },
            )
        }
        if (confirmDelete) {
            ConfirmDialog(
                title = "Delete this event?",
                message = "This permanently deletes \"${event.name}\" and all of its criteria. This can't be undone.",
                confirmLabel = "Delete event",
                danger = true,
                busy = vm.busy,
                onConfirm = { vm.delete(onDeleted) },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

private data class Action(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

@Composable
private fun EventMenu(
    paused: Boolean,
    cancelled: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit,
    onTogglePaused: () -> Unit,
    onToggleCancelled: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val actions = buildList {
        add(Action("Edit details", Icons.Outlined.Edit, onClick = onEdit))
        if (cancelled) {
            add(Action("It's back on", Icons.Outlined.PlayCircle, onClick = onToggleCancelled))
        } else {
            add(
                if (paused) Action("Resume monitoring", Icons.Outlined.PlayCircle, onClick = onTogglePaused)
                else Action("Pause monitoring", Icons.Outlined.PauseCircle, onClick = onTogglePaused),
            )
            add(Action("Mark as cancelled", Icons.Outlined.Block, onClick = onToggleCancelled))
        }
        add(Action("Duplicate", Icons.Outlined.ContentCopy, onClick = onDuplicate))
        add(Action("Delete event…", Icons.Outlined.Delete, danger = true, onClick = onDelete))
    }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More actions for this event", tint = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                val color = if (action.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                DropdownMenuItem(
                    text = { Text(action.label, color = color) },
                    leadingIcon = { Icon(action.icon, null, tint = color) },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
