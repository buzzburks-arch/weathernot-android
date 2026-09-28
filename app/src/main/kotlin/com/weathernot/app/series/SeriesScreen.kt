package com.weathernot.app.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weathernot.app.AppEnvironment
import com.weathernot.app.event.CriterionEditorSheet
import com.weathernot.app.theme.LocalPalette
import com.weathernot.app.ui.CheckingBadge
import com.weathernot.app.ui.ConfirmDialog
import com.weathernot.app.ui.ErrorBanner
import com.weathernot.app.ui.GradientTopBar
import com.weathernot.app.ui.LoadingBox
import com.weathernot.app.ui.LocalUnits
import com.weathernot.app.ui.MutedText
import com.weathernot.app.ui.Pill
import com.weathernot.app.ui.SectionCard
import com.weathernot.app.ui.VerdictBadge
import com.weathernot.app.ui.userMessage
import com.weathernot.kit.format.CriterionDraft
import com.weathernot.kit.format.blankDraft
import com.weathernot.kit.format.criterionSentence
import com.weathernot.kit.format.describeWeekdays
import com.weathernot.kit.format.draftFrom
import com.weathernot.kit.format.draftToInput
import com.weathernot.kit.format.formatDay
import com.weathernot.kit.format.formatEventDate
import com.weathernot.kit.format.formatWindow
import com.weathernot.kit.format.instantToWallTime
import com.weathernot.kit.format.isPast
import com.weathernot.kit.format.placeToShow
import com.weathernot.kit.format.timeZoneName
import com.weathernot.kit.format.wording
import com.weathernot.kit.format.zoneOf
import com.weathernot.kit.models.Event
import com.weathernot.kit.models.GridVariable
import com.weathernot.kit.models.Series
import com.weathernot.kit.models.SeriesCriterion
import com.weathernot.kit.models.UnitSystem
import com.weathernot.kit.networking.ApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SeriesViewModel(private val seriesId: String, private val api: ApiClient) : ViewModel() {
    var series by mutableStateOf<Series?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    var now by mutableStateOf(Instant.now())
        private set
    var busy by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                series = api.getSeries(seriesId)
                now = Instant.now()
            } catch (e: Exception) {
                error = e.userMessage("Could not load this series.")
            }
        }
    }

    private fun act(failure: String, block: suspend () -> Unit) {
        error = null
        viewModelScope.launch {
            try {
                block()
                refresh()
            } catch (e: Exception) {
                error = e.userMessage(failure)
            }
        }
    }

    fun toggleCancelled(event: Event) = act("Could not update that date.") { api.setEventCancelled(event.eventId, !event.isCancelled) }

    fun toggleCriterion(criterion: SeriesCriterion) = act("Could not update the criterion.") {
        api.setSeriesCriterionActive(seriesId, criterion.criterionId, !criterion.active)
    }

    fun removeCriterion(criterion: SeriesCriterion) = act("Could not remove the criterion.") {
        api.deleteSeriesCriterion(seriesId, criterion.criterionId)
    }

    suspend fun addCriterion(unitSystem: UnitSystem, draft: CriterionDraft): String? = try {
        api.addSeriesCriterion(seriesId, draftToInput(unitSystem, draft))
        refresh()
        null
    } catch (e: Exception) {
        e.userMessage("Could not add the criterion.")
    }

    suspend fun editCriterion(unitSystem: UnitSystem, criterion: SeriesCriterion, draft: CriterionDraft): String? = try {
        api.updateSeriesCriterion(seriesId, criterion.criterionId, draftToInput(unitSystem, draft.copy(variable = criterion.variable)))
        refresh()
        null
    } catch (e: Exception) {
        e.userMessage("Could not update the criterion.")
    }

    fun delete(onDeleted: () -> Unit) {
        busy = true
        viewModelScope.launch {
            try {
                api.deleteSeries(seriesId)
                onDeleted()
            } catch (e: Exception) {
                error = e.userMessage("Could not delete this series.")
            } finally {
                busy = false
            }
        }
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a")

/** "Saturdays, 9:00 AM – 11:00 AM · Jun 6 – Jul 25, 2026", "3 days · Jun 5 – Jun 7, 2026". */
private fun describeSchedule(series: Series): String {
    val schedule = series.schedule
    if (schedule?.kind == "weekly" && schedule.firstDate != null && schedule.untilDate != null) {
        val start = schedule.startTime?.let { CLOCK.format(LocalTime.parse(it)) } ?: ""
        val end = schedule.endTime?.let { CLOCK.format(LocalTime.parse(it)) } ?: ""
        return "${describeWeekdays(schedule.weekdays)}, $start – $end · ${formatDay(schedule.firstDate!!)} – ${formatDay(schedule.untilDate!!, true)}"
    }
    val count = "${series.dateCount} ${if (series.kind == "multi_day") "day" else "date"}${if (series.dateCount == 1) "" else "s"}"
    val first = series.events.firstOrNull() ?: return count
    val last = series.events.last()
    val firstDay = instantToWallTime(first.startTime, zoneOf(first.timezone)).toLocalDate().toString()
    val lastDay = instantToWallTime(last.startTime, zoneOf(last.timezone)).toLocalDate().toString()
    return "$count · ${formatDay(firstDay)} – ${formatDay(lastDay, true)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesScreen(seriesId: String, onBack: () -> Unit, onOpenEvent: (String) -> Unit, onDeleted: () -> Unit) {
    val vm: SeriesViewModel = viewModel(key = "series:$seriesId") { SeriesViewModel(seriesId, AppEnvironment.api) }
    val unitSystem = LocalUnits.current
    val palette = LocalPalette.current
    var confirmDelete by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SeriesCriterion?>(null) }
    val series = vm.series

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(series?.evaluating) {
        while (vm.series?.evaluating == true) {
            delay(1500)
            vm.refresh()
        }
    }

    Scaffold(topBar = {
        GradientTopBar(series?.name ?: "Series", onBack = onBack) {
            if (series != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete series", tint = Color.White) }
            }
        }
    }) { padding ->
        if (series == null) {
            if (vm.error != null) Column(Modifier.padding(padding).padding(16.dp)) { ErrorBanner(vm.error!!) } else LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val zone = zoneOf(series.timezone)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            vm.error?.let { ErrorBanner(it, onDismiss = { vm.error = null }) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(series.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf("${series.eventType.label} series", placeToShow(series.name, series.displayName)).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(describeSchedule(series), style = MaterialTheme.typography.bodyMedium)
                MutedText("Times are ${timeZoneName(zone)}, where it happens.")
            }

            SectionCard(title = "Dates") {
                series.events.forEachIndexed { index, event ->
                    if (index > 0) HorizontalDivider(color = palette.border)
                    val ended = isPast(event, vm.now)
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenEvent(event.eventId) }.alpha(if (ended) 0.6f else 1f).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(formatEventDate(event.startTime, zoneOf(event.timezone)), fontWeight = FontWeight.Medium)
                            MutedText(formatWindow(event.startTime, event.endTime, zoneOf(event.timezone)))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                when {
                                    ended -> Pill("Ended", palette.mutedForeground)
                                    event.isCancelled -> Pill("Cancelled", palette.mutedForeground)
                                    event.evaluating -> CheckingBadge()
                                    else -> VerdictBadge(event.goNoGo, event.verdictMode, reason = event.verdictReason)
                                }
                            }
                        }
                        if (!ended) {
                            TextButton(onClick = { vm.toggleCancelled(event) }) {
                                Text(if (event.isCancelled) "It's back on" else "Cancel date")
                            }
                        }
                    }
                }
            }

            SectionCard(title = "What every date watches") {
                MutedText("Changes here reach every upcoming date, except dates where that criterion was changed on its own.")
                if (series.criteria.isEmpty()) MutedText("No criteria yet.")
                series.criteria.forEachIndexed { index, criterion ->
                    if (index > 0) HorizontalDivider(color = palette.border)
                    Column(Modifier.alpha(if (criterion.active) 1f else 0.55f).padding(vertical = 6.dp)) {
                        Text(criterionSentence(unitSystem, criterion.wording()), fontWeight = FontWeight.Medium)
                        Row {
                            if (criterion.informational) Pill("no alerts", palette.mutedForeground, Modifier.align(Alignment.CenterVertically))
                            TextButton(onClick = { editing = criterion }) { Text("Edit") }
                            TextButton(onClick = { vm.toggleCriterion(criterion) }) { Text(if (criterion.active) "Pause" else "Resume") }
                            TextButton(onClick = { vm.removeCriterion(criterion) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                OutlinedButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, null)
                    Text(" Add a criterion")
                }
            }
        }
    }

    if (adding) {
        CriterionEditorSheet(
            title = "Add to every date",
            initial = blankDraft(unitSystem, GridVariable.WIND_GUST),
            saveLabel = "Add",
            onDismiss = { adding = false },
            onSave = { vm.addCriterion(unitSystem, it) },
        )
    }
    editing?.let { criterion ->
        CriterionEditorSheet(
            title = "Edit for every date",
            initial = draftFrom(unitSystem, criterion.wording()),
            lockVariable = true,
            onDismiss = { editing = null },
            onSave = { vm.editCriterion(unitSystem, criterion, it) },
        )
    }
    if (confirmDelete && series != null) {
        ConfirmDialog(
            title = "Delete this series?",
            message = "This permanently deletes \"${series.name}\", all ${series.dateCount} of its dates and their criteria and history. This can't be undone.",
            confirmLabel = "Delete series",
            danger = true,
            busy = vm.busy,
            onConfirm = { vm.delete(onDeleted) },
            onDismiss = { confirmDelete = false },
        )
    }
}
