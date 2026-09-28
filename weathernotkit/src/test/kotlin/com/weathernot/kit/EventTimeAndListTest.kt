package com.weathernot.kit

import com.weathernot.kit.format.EventRow
import com.weathernot.kit.format.ago
import com.weathernot.kit.format.buildEventList
import com.weathernot.kit.format.describeWeekdays
import com.weathernot.kit.format.formatCountdown
import com.weathernot.kit.format.formatWindow
import com.weathernot.kit.format.instantToWallTime
import com.weathernot.kit.format.parseInstant
import com.weathernot.kit.format.toApiString
import com.weathernot.kit.format.viewerWindow
import com.weathernot.kit.format.wallTimeToInstant
import com.weathernot.kit.models.Event
import com.weathernot.kit.models.EventType
import com.weathernot.kit.models.GoNoGo
import com.weathernot.kit.models.SeriesSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class EventTimeAndListTest {
    private val denver = ZoneId.of("America/Denver")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun parsesApiTimestamps() {
        assertEquals(Instant.parse("2026-09-29T14:00:00Z"), parseInstant("2026-09-29T14:00:00Z"))
        assertEquals(Instant.parse("2026-09-29T14:00:00Z"), parseInstant("2026-09-29T08:00:00-06:00"))
        assertEquals(Instant.parse("2026-09-28T11:07:46.266717Z"), parseInstant("2026-09-28T11:07:46.266717Z"))
        assertEquals(Instant.parse("2026-09-29T14:00:00Z"), parseInstant("2026-09-29T14:00:00"))
    }

    @Test
    fun wallTimesAreTheEventsClock() {
        val instant = wallTimeToInstant(LocalDateTime.of(2026, 6, 14, 16, 0), denver)
        assertEquals("2026-06-14T22:00:00Z", instant.toApiString())
        assertEquals(LocalDateTime.of(2026, 6, 14, 16, 0), instantToWallTime("2026-06-14T22:00:00Z", denver))
    }

    @Test
    fun formatsWindowsInTheEventsZone() {
        assertEquals(
            "Sun, Jun 14, 2026, 4:00 – 6:00 PM MDT",
            formatWindow("2026-06-14T22:00:00Z", "2026-06-15T00:00:00Z", denver, Locale.US),
        )
        assertEquals(
            "6:00 PM – 8:00 PM EDT",
            viewerWindow("2026-06-14T22:00:00Z", "2026-06-15T00:00:00Z", denver, newYork, Locale.US),
        )
        assertNull(viewerWindow("2026-06-14T22:00:00Z", "2026-06-15T00:00:00Z", denver, denver, Locale.US))
    }

    @Test
    fun countsDownToUpcomingEvents() {
        val now = Instant.parse("2026-06-14T15:00:00Z") // 9 AM in Denver
        assertEquals("Today, 4 PM", formatCountdown("2026-06-14T22:00:00Z", "2026-06-15T00:00:00Z", denver, now, Locale.US))
        assertEquals("Tomorrow, 4:30 PM", formatCountdown("2026-06-15T22:30:00Z", "2026-06-16T00:00:00Z", denver, now, Locale.US))
        assertEquals("In 3 days", formatCountdown("2026-06-17T22:00:00Z", "2026-06-18T00:00:00Z", denver, now, Locale.US))
        assertEquals("Happening now", formatCountdown("2026-06-14T14:00:00Z", "2026-06-14T16:00:00Z", denver, now, Locale.US))
        assertEquals("12 min ago", ago("2026-06-14T14:48:00Z", now))
        assertEquals("Mondays and Wednesdays", describeWeekdays(listOf(2, 0), Locale.US))
    }

    private fun event(id: String, start: String, end: String, verdict: GoNoGo = GoNoGo.GO, mode: String? = "forecast", name: String = id) =
        Event(
            eventId = id, eventType = EventType.PICNIC, name = name, latitude = 0.0, longitude = 0.0,
            startTime = start, endTime = end, goNoGo = verdict, verdictMode = mode,
        )

    @Test
    fun splitsUpcomingPastAndNeedsAttention() {
        val now = Instant.parse("2026-06-14T12:00:00Z")
        val events = listOf(
            event("later", "2026-06-20T12:00:00Z", "2026-06-20T14:00:00Z"),
            event("soon", "2026-06-15T12:00:00Z", "2026-06-15T14:00:00Z", GoNoGo.NO_GO),
            event("outlook", "2026-07-15T12:00:00Z", "2026-07-15T14:00:00Z", GoNoGo.NO_GO, mode = "climatology"),
            event("done", "2026-06-01T12:00:00Z", "2026-06-01T14:00:00Z"),
            event("dated", "2026-06-16T12:00:00Z", "2026-06-16T14:00:00Z").copy(seriesId = "s"),
        )
        val series = listOf(
            SeriesSummary(seriesId = "s", kind = "weekly", name = "Saturday league", eventType = EventType.YOUTH_SPORTS,
                upcomingCount = 3, nextStart = "2026-06-16T12:00:00Z", upcomingWatch = 1),
        )
        val view = buildEventList(events, series, showPast = false, query = "", typeFilter = null, now = now)
        assertEquals(listOf("event:soon", "series:s"), view.attention.map { it.key })
        assertEquals(listOf("event:later", "event:outlook"), view.rest.map { it.key })
        assertEquals(4, view.upcomingCount)
        assertEquals(1, view.pastCount)

        val past = buildEventList(events, series, showPast = true, query = "", typeFilter = null, now = now)
        assertEquals(listOf("event:done"), past.rest.map { it.key })

        val searched = buildEventList(events, series, showPast = false, query = "LEAGUE", typeFilter = null, now = now)
        assertEquals(listOf("series:s"), (searched.attention + searched.rest).map { it.key })
        assert(searched.attention.single() is EventRow.OfSeries)
    }
}
