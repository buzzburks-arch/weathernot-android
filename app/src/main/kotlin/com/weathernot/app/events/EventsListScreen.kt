package com.weathernot.app.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weathernot.app.AppEnvironment
import com.weathernot.app.SessionViewModel
import com.weathernot.app.theme.LocalPalette
import com.weathernot.app.ui.CheckingBadge
import com.weathernot.app.ui.ConfirmDialog
import com.weathernot.app.ui.ErrorBanner
import com.weathernot.app.ui.GradientTopBar
import com.weathernot.app.ui.GroupHeading
import com.weathernot.app.ui.LoadingBox
import com.weathernot.app.ui.MutedText
import com.weathernot.app.ui.Pill
import com.weathernot.app.ui.SectionCard
import com.weathernot.app.ui.ShowMessages
import com.weathernot.app.ui.VerdictBadge
import com.weathernot.kit.format.EventRow
import com.weathernot.kit.format.buildEventList
import com.weathernot.kit.format.formatCountdown
import com.weathernot.kit.format.formatEventDate
import com.weathernot.kit.format.isPast
import com.weathernot.kit.format.joinWords
import com.weathernot.kit.format.placeToShow
import com.weathernot.kit.format.zoneOf
import com.weathernot.kit.models.Event
import com.weathernot.kit.models.EventType
import com.weathernot.kit.models.SeriesSummary
import com.weathernot.kit.models.WatchPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Enough events to go on, and few enough kinds to be worth narrowing to.
private const val HINT_MIN_EVENTS = 3
private const val HINT_MAX_TYPES = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListScreen(
    session: SessionViewModel,
    onOpenEvent: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onNewEvent: () -> Unit,
    onProfile: () -> Unit,
) {
    val vm: EventsListViewModel = viewModel { EventsListViewModel(AppEnvironment.api) }
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(vm.messages, snackbar)

    var showPast by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf<EventType?>(null) }
    var confirmDelete by remember { mutableStateOf<Event?>(null) }
    var confirmDeletePast by remember { mutableStateOf(false) }
    var deletingPast by remember { mutableStateOf(false) }

    // Coming back to the list (after creating or editing) shows the latest.
    LaunchedEffect(Unit) { vm.refresh() }
    // A check in flight: keep polling until it lands, so badges update on their own.
    LaunchedEffect(vm.anyEvaluating) {
        while (vm.anyEvaluating) {
            delay(1500)
            vm.refresh()
        }
    }

    Scaffold(
        topBar = {
            GradientTopBar("WeatherNot") {
                IconButton(onClick = onProfile) {
                    Icon(Icons.Outlined.AccountCircle, contentDescription = "Profile", tint = Color.White)
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewEvent) {
                Icon(Icons.Filled.Add, null)
                Text("  New event")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val events = vm.events
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = { vm.refresh(showSpinner = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                events == null && vm.error == null -> LoadingBox()
                events == null -> Column(Modifier.padding(16.dp)) {
                    ErrorBanner(vm.error!!)
                    TextButton(onClick = { vm.refresh() }) { Text("Try again") }
                }
                events.isEmpty() && vm.series.isEmpty() -> EmptyState(onNewEvent)
                else -> {
                    val view = buildEventList(events, vm.series, showPast, query, typeFilter, vm.now)
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { TypesHint(session, vm) }
                        vm.error?.let { item { ErrorBanner(it, onDismiss = { vm.error = null }) } }
                        item {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf(false to "Upcoming (${view.upcomingCount})", true to "Past (${view.pastCount})")
                                    .forEachIndexed { index, (past, label) ->
                                        SegmentedButton(
                                            selected = showPast == past,
                                            onClick = { showPast = past },
                                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                                        ) { Text(label) }
                                    }
                            }
                        }
                        item {
                            Filters(
                                query = query,
                                onQuery = { query = it },
                                types = view.typesPresent,
                                typeFilter = typeFilter,
                                onTypeFilter = { typeFilter = it },
                            )
                        }
                        if (showPast && view.pastEventCount > 0) {
                            item {
                                TextButton(onClick = { confirmDeletePast = true }) {
                                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    Text("  Delete all past events", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (view.attention.isEmpty() && view.rest.isEmpty()) {
                            item {
                                MutedText(
                                    when {
                                        query.isNotBlank() || typeFilter != null -> "No events match."
                                        showPast -> "No past events."
                                        else -> "Nothing coming up — create an event, or see your past ones."
                                    },
                                    Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                        if (view.attention.isNotEmpty()) {
                            item { GroupHeading("Needs attention", color = LocalPalette.current.caution) }
                            items(view.attention, key = { it.key }) { row ->
                                RowCard(row, vm, onOpenEvent, onOpenSeries, onDelete = { confirmDelete = it })
                            }
                            if (view.rest.isNotEmpty()) item { GroupHeading("Upcoming") }
                        }
                        items(view.rest, key = { it.key }) { row ->
                            RowCard(row, vm, onOpenEvent, onOpenSeries, onDelete = { confirmDelete = it })
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { event ->
        ConfirmDialog(
            title = "Delete this event?",
            message = "This permanently deletes \"${event.name}\" and all of its criteria. This can't be undone.",
            confirmLabel = "Delete event",
            danger = true,
            onConfirm = {
                vm.delete(event)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
    if (confirmDeletePast) {
        ConfirmDialog(
            title = "Delete all past events?",
            message = "This permanently deletes every event that has ended, with its criteria and history. " +
                "A series' dates stay with the series — delete a series from its own page. This can't be undone.",
            confirmLabel = "Delete past events",
            danger = true,
            busy = deletingPast,
            onConfirm = {
                deletingPast = true
                vm.deletePast {
                    deletingPast = false
                    confirmDeletePast = false
                }
            },
            onDismiss = { confirmDeletePast = false },
        )
    }
}

@Composable
private fun RowCard(
    row: EventRow,
    vm: EventsListViewModel,
    onOpenEvent: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onDelete: (Event) -> Unit,
) {
    when (row) {
        is EventRow.Single -> EventRowCard(row.event, vm, onOpenEvent, onDelete)
        is EventRow.OfSeries -> SeriesRowCard(row.series, vm, onOpenSeries)
    }
}

@Composable
private fun RowSurface(onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, LocalPalette.current.border),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventRowCard(event: Event, vm: EventsListViewModel, onOpenEvent: (String) -> Unit, onDelete: (Event) -> Unit) {
    val zone = zoneOf(event.timezone)
    val ended = isPast(event, vm.now)
    val palette = LocalPalette.current
    val place = placeToShow(event.name, event.displayName)
    val whenText = if (ended) formatEventDate(event.startTime, zone) else formatCountdown(event.startTime, event.endTime, zone, vm.now)
    RowSurface(onClick = { onOpenEvent(event.eventId) }) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(event.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(event.eventType.label, place, whenText).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        ended -> Pill("Ended", palette.mutedForeground)
                        event.evaluating -> CheckingBadge()
                        else -> {
                            VerdictBadge(event.goNoGo, event.verdictMode, reason = event.verdictReason)
                            if (event.phase != WatchPhase.ENDED) Pill(event.phase.label, MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (event.isPaused && !ended) Pill("Paused", palette.hold)
                    if (event.isCancelled) Pill("Cancelled", palette.mutedForeground)
                }
            }
            EventMenu(event, ended, enabled = vm.busyEventId != event.eventId, vm, onOpenEvent, onDelete)
        }
    }
}

private data class MenuItem(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

@Composable
private fun EventMenu(
    event: Event,
    ended: Boolean,
    enabled: Boolean,
    vm: EventsListViewModel,
    onOpenEvent: (String) -> Unit,
    onDelete: (Event) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val items = buildList {
        if (!ended) {
            if (event.isCancelled) {
                add(MenuItem("It's back on", Icons.Outlined.PlayCircle) { vm.toggleCancelled(event) })
            } else {
                add(
                    if (event.isPaused) {
                        MenuItem("Resume monitoring", Icons.Outlined.PlayCircle) { vm.togglePaused(event) }
                    } else {
                        MenuItem("Pause monitoring", Icons.Outlined.PauseCircle) { vm.togglePaused(event) }
                    },
                )
                add(MenuItem("Mark as cancelled", Icons.Outlined.Block) { vm.toggleCancelled(event) })
            }
        }
        add(MenuItem("Duplicate", Icons.Outlined.ContentCopy) { vm.duplicate(event, onOpenEvent) })
        add(MenuItem("Delete…", Icons.Outlined.Delete, danger = true) { onDelete(event) })
    }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More actions for ${event.name}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                val color = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                DropdownMenuItem(
                    text = { Text(item.label, color = color) },
                    leadingIcon = { Icon(item.icon, null, tint = color) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun SeriesRowCard(series: SeriesSummary, vm: EventsListViewModel, onOpenSeries: (String) -> Unit) {
    val palette = LocalPalette.current
    val place = placeToShow(series.name, series.displayName)
    val unit = if (series.kind == "multi_day") "day" else "date"
    val next = series.nextStart?.let {
        "next " + formatCountdown(it, series.nextEnd ?: it, zoneOf(series.timezone), vm.now).lowercase()
    } ?: "no upcoming dates"
    RowSurface(onClick = { onOpenSeries(series.seriesId) }) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(series.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Repeat, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    " ${series.eventType.label} series · ${series.dateCount} $unit${if (series.dateCount == 1) "" else "s"}" +
                        (if (place.isNotEmpty()) " · $place" else "") + " · $next",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                series.evaluating -> CheckingBadge()
                series.upcomingNoGo > 0 -> Pill("${series.upcomingNoGo} at risk", palette.hold)
                series.upcomingWatch > 0 -> Pill("${series.upcomingWatch} to watch", palette.caution)
                else -> Pill("${series.upcomingCount} upcoming", palette.mutedForeground)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(
    query: String,
    onQuery: (String) -> Unit,
    types: List<EventType>,
    typeFilter: EventType?,
    onTypeFilter: (EventType?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text("Search by name or place") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (types.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = typeFilter == null, onClick = { onTypeFilter(null) }, label = { Text("All types") })
                types.forEach { type ->
                    FilterChip(
                        selected = typeFilter == type,
                        onClick = { onTypeFilter(if (typeFilter == type) null else type) },
                        label = { Text(type.label) },
                    )
                }
            }
        }
    }
}

/** Skipped the "what do you plan" question but has made a few events of a few kinds: offer to show those first. */
@Composable
private fun TypesHint(session: SessionViewModel, vm: EventsListViewModel) {
    val prefs = session.preferences ?: return
    val used = vm.typesByUse()
    val offer = prefs.eventTypesAsked && prefs.eventTypes.isEmpty() && !prefs.eventTypesHintDismissed &&
        vm.plannedCount >= HINT_MIN_EVENTS && used.isNotEmpty() && used.size <= HINT_MAX_TYPES
    if (!offer) return
    val scope = rememberCoroutineScope()
    SectionCard {
        Text("Most of your events are ${joinWords(used.map { it.label })}. Show those types first when you create an event?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { runCatching { session.update { setEventTypes(used) } } } }) { Text("Show them first") }
            TextButton(onClick = { scope.launch { runCatching { session.update { dismissEventTypesHint() } } } }) { Text("No thanks") }
        }
    }
}

@Composable
private fun EmptyState(onNewEvent: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No events yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        MutedText(
            "Add a wedding, a game, a race or a picnic, pick what weather matters for it, " +
                "and WeatherNot keeps watching the forecast so you don't have to.",
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onNewEvent) { Text("Create your first event") }
        MutedText("Series and imported schedules are set up on the web app.")
    }
}

