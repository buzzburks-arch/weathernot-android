package com.weathernot.app.event

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weathernot.app.theme.LocalPalette
import com.weathernot.app.ui.verdictIcon
import com.weathernot.kit.format.NWS_LEVEL_NAMES
import com.weathernot.kit.format.STABILITY_WORDS
import com.weathernot.kit.format.ago
import com.weathernot.kit.format.formatHour
import com.weathernot.kit.format.formatVariableLabel
import com.weathernot.kit.format.nextCheck
import com.weathernot.kit.format.parseInstant
import com.weathernot.kit.format.phaseExplanation
import com.weathernot.kit.format.toDisplayString
import com.weathernot.kit.models.EventSummary
import com.weathernot.kit.models.GoNoGo
import com.weathernot.kit.models.GridVariable
import com.weathernot.kit.models.Timeline
import com.weathernot.kit.models.UnitSystem
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The event's overall answer: verdict and what drives it, the sentence
 * behind it, then what it's based on and when it was checked.
 */
@Composable
fun VerdictPanel(summary: EventSummary, checking: Boolean, onCheckNow: () -> Unit) {
    val palette = LocalPalette.current
    val color = palette.verdict(summary.verdict)
    var explaining by remember { mutableStateOf(false) }
    // Ticks every minute so "checked 12 min ago" stays true on an open page.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }
    val timing = listOfNotNull(
        summary.confidence?.let { "$it confidence" },
        summary.stability?.let { STABILITY_WORDS[it] },
        summary.checkedAt?.let { "checked ${ago(it, now)}" } ?: "not checked yet",
        summary.nextCheckAt?.let { "next check ${nextCheck(it, now)}" },
    ).joinToString(" · ").replaceFirstChar { it.uppercase() }

    val tint = if (summary.verdict == GoNoGo.UNKNOWN) MaterialTheme.colorScheme.surfaceVariant else color.copy(alpha = 0.10f)
    Row(
        Modifier
            .fillMaxWidth()
            .background(tint, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, if (summary.verdict == GoNoGo.UNKNOWN) palette.border else color.copy(alpha = 0.4f)), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        if (checking) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = color)
        } else {
            Icon(verdictIcon(summary.verdict), contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (checking) "Checking the latest weather…" else summary.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            if (summary.detail.isNotBlank()) Text(summary.detail, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { explaining = !explaining },
                    label = { Text(summary.phase.label, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.height(30.dp),
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCheckNow, enabled = !checking, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Check the weather now", modifier = Modifier.size(20.dp))
                }
            }
            Text(timing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (explaining) {
                Text(
                    phaseExplanation(summary.phase, summary.forecastBeginsAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val LEVEL_WORD = mapOf(GoNoGo.NO_GO to "past your No-go level", GoNoGo.WATCH to "past your Watch level")

/**
 * The forecast hour by hour across the event, a couple of hours either
 * side (faded), with hours past a criterion's level shaded -- "is it the
 * whole afternoon or just 3-4 pm?"
 */
@Composable
fun HourlyTimeline(timeline: Timeline, zone: ZoneId, unitSystem: UnitSystem) {
    val palette = LocalPalette.current
    val times = timeline.rows.flatMap { row -> row.cells.map { it.time } }.distinct().sortedBy { parseInstant(it) }
    val start = parseInstant(timeline.start)
    val end = parseInstant(timeline.end)
    fun outside(iso: String) = parseInstant(iso).let { it.isBefore(start) || it.isAfter(end) }
    val cellWidth = 52.dp

    Row {
        // The variable names stay put; the hours scroll.
        Column(Modifier.padding(end = 8.dp)) {
            Text(" ", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
            timeline.rows.forEach { row ->
                Text(
                    formatVariableLabel(unitSystem, row.variable),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.height(30.dp).padding(top = 6.dp),
                    maxLines = 1,
                )
            }
        }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.padding(bottom = 6.dp)) {
                times.forEach { t ->
                    Text(
                        formatHour(t, zone),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (outside(t)) 0.5f else 1f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(cellWidth),
                    )
                }
            }
            timeline.rows.forEach { row ->
                val byTime = row.cells.associateBy { it.time }
                Row(Modifier.height(30.dp)) {
                    times.forEach { t ->
                        val cell = byTime[t]
                        val level = cell?.level?.takeIf { it == GoNoGo.WATCH || it == GoNoGo.NO_GO }
                        val color = level?.let { palette.verdict(it) }
                        val text = when {
                            cell == null -> "–"
                            row.variable == GridVariable.NWS_ALERT -> NWS_LEVEL_NAMES[cell.value.roundToInt()] ?: "–"
                            else -> toDisplayString(unitSystem, row.variable, cell.value)
                        }
                        Text(
                            text,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (level != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = (color ?: MaterialTheme.colorScheme.onSurface).copy(alpha = if (outside(t)) 0.5f else 1f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .semantics { if (level != null) contentDescription = "$text, ${LEVEL_WORD[level]}" }
                                .width(cellWidth)
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                .background(color?.copy(alpha = 0.18f) ?: Color.Transparent, RoundedCornerShape(4.dp))
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

