package com.weathernot.kit.format

import com.weathernot.kit.models.Event
import com.weathernot.kit.models.EventType
import com.weathernot.kit.models.GoNoGo
import com.weathernot.kit.models.SeriesSummary
import java.time.Instant

// The events list's rules, from the web app's EventsListPage.tsx: a
// series shows as one row standing in for its dates; upcoming soonest
// first, past most recent first; forecast Watch / No-go rows first.

sealed class EventRow {
    abstract val at: Instant

    data class Single(val event: Event, override val at: Instant) : EventRow()
    data class OfSeries(val series: SeriesSummary, override val at: Instant) : EventRow()

    val key: String
        get() = when (this) {
            is Single -> "event:${event.eventId}"
            is OfSeries -> "series:${series.seriesId}"
        }
}

/** Worth a look first: a forecast Watch or No-go, or a series with one coming up. */
fun needsAttention(row: EventRow): Boolean = when (row) {
    is EventRow.OfSeries -> row.series.upcomingNoGo + row.series.upcomingWatch > 0
    is EventRow.Single -> row.event.isActive && row.event.verdictMode == "forecast" &&
        (row.event.goNoGo == GoNoGo.WATCH || row.event.goNoGo == GoNoGo.NO_GO)
}

fun isPast(event: Event, now: Instant): Boolean = parseInstant(event.endTime).isBefore(now)

data class EventListView(
    val attention: List<EventRow>,
    val rest: List<EventRow>,
    val upcomingCount: Int,
    val pastCount: Int,
    /** Event types present, to offer as a filter when there's more than one. */
    val typesPresent: List<EventType>,
    /** Past standalone events -- what "Delete all past events" removes. */
    val pastEventCount: Int,
)

fun buildEventList(
    events: List<Event>,
    series: List<SeriesSummary>,
    showPast: Boolean,
    query: String,
    typeFilter: EventType?,
    now: Instant,
): EventListView {
    val standalone = events.filter { it.seriesId == null }
    val (pastEvents, upcomingEvents) = standalone.partition { isPast(it, now) }
    val (pastSeries, upcomingSeries) = series.partition { it.upcomingCount == 0 }
    val needle = query.trim().lowercase()
    fun matches(name: String, place: String, type: EventType) =
        (typeFilter == null || type == typeFilter) &&
            (needle.isEmpty() || name.lowercase().contains(needle) || place.lowercase().contains(needle))

    val rows = ((if (showPast) pastSeries else upcomingSeries)
        .filter { matches(it.name, it.displayName, it.eventType) }
        .map { EventRow.OfSeries(it, (it.nextStart ?: it.nextEnd)?.let(::parseInstant) ?: Instant.EPOCH) } +
        (if (showPast) pastEvents else upcomingEvents)
            .filter { matches(it.name, it.displayName, it.eventType) }
            .map { EventRow.Single(it, parseInstant(it.startTime)) })
        .let { list -> if (showPast) list.sortedByDescending { it.at } else list.sortedBy { it.at } }

    val attention = if (showPast) emptyList() else rows.filter(::needsAttention)
    return EventListView(
        attention = attention,
        rest = rows - attention.toSet(),
        upcomingCount = upcomingEvents.size + upcomingSeries.size,
        pastCount = pastEvents.size + pastSeries.size,
        typesPresent = (standalone.map { it.eventType } + series.map { it.eventType }).distinct(),
        pastEventCount = pastEvents.size,
    )
}
