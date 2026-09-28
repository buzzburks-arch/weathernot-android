package com.weathernot.app.event

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weathernot.app.theme.LocalPalette
import com.weathernot.app.ui.CheckingBadge
import com.weathernot.app.ui.GroupHeading
import com.weathernot.app.ui.MutedText
import com.weathernot.app.ui.Pill
import com.weathernot.app.ui.VerdictBadge
import com.weathernot.kit.format.STABILITY_WORDS
import com.weathernot.kit.format.criterionSentence
import com.weathernot.kit.format.describeCriterion
import com.weathernot.kit.format.formatMargin
import com.weathernot.kit.format.formatStamp
import com.weathernot.kit.format.formatValue
import com.weathernot.kit.format.formatWindow
import com.weathernot.kit.format.verdictLabel
import com.weathernot.kit.format.wording
import com.weathernot.kit.format.zoneOf
import com.weathernot.kit.models.Criterion
import com.weathernot.kit.models.CriterionTrend
import com.weathernot.kit.models.EvaluationResult
import com.weathernot.kit.models.Event
import com.weathernot.kit.models.GoNoGo
import com.weathernot.kit.models.Segment
import com.weathernot.kit.models.SuggestedCriterion
import com.weathernot.kit.models.UnitSystem
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.math.roundToInt

/** The criteria by the part of the event they watch: the whole event's first, then each part's. */
@Composable
fun CriteriaList(
    event: Event,
    criteria: List<Criterion>,
    segments: List<Segment>,
    vm: EventDetailViewModel,
    unitSystem: UnitSystem,
    linkedCriterionId: String?,
    onEdit: (Criterion) -> Unit,
) {
    val zone = zoneOf(event.timezone)
    val segmentIds = segments.map { it.segmentId }.toSet()
    val groups = listOf("The whole event" to criteria.filter { it.segmentId == null || it.segmentId !in segmentIds }) +
        segments.map { s -> "${s.name} · ${formatWindow(s.startTime, s.endTime, zone)}" to criteria.filter { it.segmentId == s.segmentId } }

    if (criteria.isEmpty()) {
        MutedText("No criteria yet — nothing is being watched. Add one below, or ask for suggestions.")
        return
    }
    Column {
        groups.forEachIndexed { index, (title, group) ->
            if (segments.isNotEmpty()) {
                GroupHeading(title + if (group.isEmpty()) " — nothing watched" else "")
            }
            group.filter { it.criterionId !in vm.removing }.forEach { criterion ->
                CriterionRow(
                    criterion = criterion,
                    event = event,
                    latest = vm.latestResult(criterion.criterionId),
                    history = vm.history(criterion.criterionId),
                    trend = vm.trends[criterion.criterionId],
                    unitSystem = unitSystem,
                    zone = zone,
                    linked = criterion.criterionId == linkedCriterionId,
                    onEdit = { onEdit(criterion) },
                    onToggle = { vm.toggleCriterion(unitSystem, criterion) },
                    onRemove = { vm.removeCriterion(unitSystem, criterion) },
                )
                HorizontalDivider(color = LocalPalette.current.border)
            }
            if (index == 0 && group.isEmpty() && segments.isEmpty()) MutedText("No criteria yet.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun CriterionRow(
    criterion: Criterion,
    event: Event,
    latest: EvaluationResult?,
    history: List<EvaluationResult>,
    trend: CriterionTrend?,
    unitSystem: UnitSystem,
    zone: ZoneId,
    linked: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = LocalPalette.current
    // An alert's link to one criterion opens its details and brings it into view.
    var expanded by remember { mutableStateOf(linked) }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(linked) {
        if (linked) {
            expanded = true
            bringIntoView.bringIntoView()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .alpha(if (criterion.active) 1f else 0.55f)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(criterionSentence(unitSystem, criterion.wording()), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (event.evaluating) {
                CheckingBadge()
            } else {
                VerdictBadge(latest?.goNoGo ?: GoNoGo.UNKNOWN, latest?.mode, muted = criterion.informational)
            }
            if (criterion.source == "ai_suggested") Pill("suggested", MaterialTheme.colorScheme.primary)
            if (criterion.informational) Pill("no alerts", palette.mutedForeground)
            if (event.seriesId != null && !criterion.fromSeries) Pill("this date only", palette.mutedForeground)
            if (!criterion.active) Pill("paused", palette.mutedForeground)
            trend?.let { TrendPill(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide details" else "Details") }
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onToggle) { Text(if (criterion.active) "Pause" else "Resume") }
            TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (history.isEmpty()) {
                    MutedText("Not checked yet — the first check fills this in.")
                }
                history.reversed().forEach { r ->
                    Text(historyLine(r, criterion, unitSystem), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** One check, in the web app's words: when — mode — value (margin) — confidence — verdict. */
private fun historyLine(r: EvaluationResult, criterion: Criterion, unitSystem: UnitSystem): String {
    val mode = if (r.mode == "climatology") "Historical outlook" else "Forecast"
    val value = r.value
    val detail = when {
        value == null -> "no data available"
        r.frequency != null ->
            "${(r.frequency!! * 100).roundToInt()}% of past days around the date crossed it " +
                "(typically ${formatValue(unitSystem, criterion.variable, value)}) — ${verdictLabel(r.goNoGo, r.mode).lowercase()}"
        else ->
            "${formatValue(unitSystem, criterion.variable, value)} " +
                "(${formatMargin(unitSystem, criterion.variable, criterion.operator, criterion.threshold, value)}) — " +
                "${r.confidence} confidence${r.stability?.let { " (${STABILITY_WORDS[it]})" } ?: ""} — " +
                verdictLabel(r.goNoGo, r.mode).lowercase()
    }
    return "${formatStamp(r.evaluatedAt)} — $mode — $detail"
}

@Composable
private fun TrendPill(trend: CriterionTrend) {
    val palette = LocalPalette.current
    when (trend.direction) {
        "worsening" -> Pill("worsening", palette.caution, icon = Icons.AutoMirrored.Filled.TrendingUp)
        "improving" -> Pill("improving", palette.go, icon = Icons.AutoMirrored.Filled.TrendingDown)
        else -> Pill("steady", palette.mutedForeground, icon = Icons.AutoMirrored.Filled.TrendingFlat)
    }
}

/**
 * "Suggest criteria": what we'd suggest for the event's type, place and
 * month, minus what it already watches, each with why. Keeping some adds
 * them; the rest are recorded as turned down, which tunes future suggestions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionReview(vm: EventDetailViewModel, unitSystem: UnitSystem, onDone: () -> Unit) {
    var suggestions by remember { mutableStateOf<List<SuggestedCriterion>?>(null) }
    var ticked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val loaded = vm.loadSuggestions()
        if (loaded == null) onDone() else {
            suggestions = loaded
            ticked = loaded.indices.toSet()
        }
    }

    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val list = suggestions
        when {
            list == null -> MutedText("Finding suggestions…")
            list.isEmpty() -> {
                Text("You're already watching everything we'd suggest for this event.")
                OutlinedButton(onClick = onDone) { Text("Close") }
            }
            else -> {
                Text("We'd suggest watching:", fontWeight = FontWeight.Medium)
                list.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = i in ticked, onCheckedChange = { ticked = if (it) ticked + i else ticked - i })
                        Column(Modifier.padding(top = 12.dp)) {
                            Text(describeCriterion(unitSystem, s.wording()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            MutedText("Why: ${s.reason}")
                        }
                    }
                }
                val keep = list.filterIndexed { i, _ -> i in ticked }
                val turnDown = list.filterIndexed { i, _ -> i !in ticked }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !saving && keep.isNotEmpty(), onClick = {
                        saving = true
                        scope.launch { if (vm.finishSuggestions(keep, turnDown)) onDone() else saving = false }
                    }) { Text(if (saving) "Saving…" else "Add selected (${keep.size})") }
                    OutlinedButton(enabled = !saving, onClick = {
                        saving = true
                        scope.launch { if (vm.finishSuggestions(emptyList(), list)) onDone() else saving = false }
                    }) { Text("None of these are relevant") }
                }
            }
        }
    }
}
