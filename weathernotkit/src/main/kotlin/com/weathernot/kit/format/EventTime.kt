package com.weathernot.kit.format

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

// Port of the web app's frontend/src/lib/time.ts. The API stores times as
// UTC instants and gives each event its location's IANA zone. People plan
// in the place's local time -- a 4 pm wedding in Denver is 4 pm Mountain
// time whoever views it -- so times are entered and shown in that zone.

/** An API timestamp ("2026-09-29T14:00:00Z", or with an offset, or naive UTC) as an instant. */
fun parseInstant(iso: String): Instant = try {
    OffsetDateTime.parse(iso).toInstant()
} catch (_: Exception) {
    LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC)
}

/** An instant as the API takes it. */
fun Instant.toApiString(): String = DateTimeFormatter.ISO_INSTANT.format(truncatedTo(ChronoUnit.SECONDS))

fun zoneOf(id: String?): ZoneId = try {
    if (id.isNullOrBlank()) ZoneOffset.UTC else ZoneId.of(id)
} catch (_: Exception) {
    ZoneOffset.UTC
}

/** A clock reading at the event's place -> the instant it names. */
fun wallTimeToInstant(wall: LocalDateTime, zone: ZoneId): Instant = ZonedDateTime.of(wall, zone).toInstant()

/** An instant -> the clock reading at the event's place. */
fun instantToWallTime(iso: String, zone: ZoneId): LocalDateTime = parseInstant(iso).atZone(zone).toLocalDateTime()

/** "Mountain Time" (or the zone id when there's no friendlier name). */
fun timeZoneName(zone: ZoneId, locale: Locale = Locale.getDefault()): String =
    zone.getDisplayName(TextStyle.FULL, locale).ifBlank { zone.id }

private fun pattern(p: String, locale: Locale) = DateTimeFormatter.ofPattern(p, locale)

/**
 * "Sat, Jun 14, 2026, 4:00 – 6:00 PM MDT", or across days
 * "Sat, Jun 14, 4:00 PM – Sun, Jun 15, 10:00 AM MDT" -- in the event's zone.
 */
fun formatWindow(startIso: String, endIso: String, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val start = parseInstant(startIso).atZone(zone)
    val end = parseInstant(endIso).atZone(zone)
    val zoneAbbrev = pattern("zzz", locale).format(end)
    return if (start.toLocalDate() == end.toLocalDate()) {
        val day = pattern("EEE, MMM d, yyyy", locale).format(start)
        val sameHalf = pattern("a", locale).format(start) == pattern("a", locale).format(end)
        val from = pattern(if (sameHalf) "h:mm" else "h:mm a", locale).format(start)
        "$day, $from – ${pattern("h:mm a", locale).format(end)} $zoneAbbrev"
    } else {
        val f = pattern("EEE, MMM d, h:mm a", locale)
        "${f.format(start)} – ${f.format(end)} $zoneAbbrev"
    }
}

/**
 * The same window on the viewer's clock, when that reads differently --
 * "4:00 – 6:00 PM MDT" is "6:00 – 8:00 PM EDT" in New York -- or null.
 */
fun viewerWindow(
    startIso: String,
    endIso: String,
    zone: ZoneId,
    viewer: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String? {
    val start = parseInstant(startIso)
    if (zone.rules.getOffset(start) == viewer.rules.getOffset(start)) return null
    val eventDay = start.atZone(zone).toLocalDate()
    val viewerStart = start.atZone(viewer)
    val viewerEnd = parseInstant(endIso).atZone(viewer)
    val zoneAbbrev = pattern("zzz", locale).format(viewerEnd)
    return if (viewerStart.toLocalDate() == eventDay && viewerEnd.toLocalDate() == eventDay) {
        "${pattern("h:mm a", locale).format(viewerStart)} – ${pattern("h:mm a", locale).format(viewerEnd)} $zoneAbbrev"
    } else {
        val f = pattern("EEE, MMM d, h:mm a", locale)
        "${f.format(viewerStart)} – ${f.format(viewerEnd)} $zoneAbbrev"
    }
}

/** "Sat, Jun 14" -- the event's date where it happens. */
fun formatEventDate(iso: String, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
    pattern("EEE, MMM d", locale).format(parseInstant(iso).atZone(zone))

/** "4 PM", "4:30 PM" in [zone]. */
fun formatClock(iso: String, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
    pattern("h:mm a", locale).format(parseInstant(iso).atZone(zone)).replace(":00", "")

/** "4 PM" -- an hour column's heading. */
fun formatHour(iso: String, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
    pattern("h a", locale).format(parseInstant(iso).atZone(zone))

/** "Sat, Jun 14, 4:00 PM MDT" -- one moment, labelled with its zone. */
fun formatMoment(iso: String, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
    pattern("EEE, MMM d, h:mm a zzz", locale).format(parseInstant(iso).atZone(zone))

/**
 * An upcoming event as a countdown where that's quicker to read:
 * "Happening now", "Today, 4 PM", "Tomorrow, 4 PM", "In 3 days", else
 * "Sat, Jun 14" -- in the event's zone.
 */
fun formatCountdown(startIso: String, endIso: String, zone: ZoneId, now: Instant = Instant.now(), locale: Locale = Locale.getDefault()): String {
    val start = parseInstant(startIso)
    val end = parseInstant(endIso)
    if (!start.isAfter(now) && !now.isAfter(end)) return "Happening now"
    val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), start.atZone(zone).toLocalDate())
    val time = formatClock(startIso, zone, locale)
    return when {
        days == 0L -> "Today, $time"
        days == 1L -> "Tomorrow, $time"
        days in 2..6 -> "In $days days"
        else -> formatEventDate(startIso, zone, locale)
    }
}

/** "just now", "12 min ago", "3 h ago", "2 days ago". */
fun ago(iso: String, now: Instant = Instant.now()): String {
    val minutes = max(0L, (Duration.between(parseInstant(iso), now).seconds / 60.0).roundToLong())
    if (minutes < 1) return "just now"
    if (minutes < 60) return "$minutes min ago"
    val hours = (minutes / 60.0).roundToLong()
    if (hours < 48) return "$hours h ago"
    return "${(hours / 24.0).roundToLong()} days ago"
}

/** "~6 PM", or "~Tue 6 PM" when it isn't today; "any minute" when due -- on the viewer's clock. */
fun nextCheck(iso: String, now: Instant = Instant.now(), viewer: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val at = parseInstant(iso)
    if (Duration.between(now, at).seconds < 60) return "any minute"
    val local = at.atZone(viewer)
    val time = pattern(if (local.minute == 0) "h a" else "h:mm a", locale).format(local)
    val today = local.toLocalDate() == now.atZone(viewer).toLocalDate()
    return "~" + (if (today) "" else pattern("EEE ", locale).format(local)) + time
}

/** "September 15" -- a date on the viewer's clock. */
fun formatLongDay(iso: String, viewer: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    pattern("MMMM d", locale).format(parseInstant(iso).atZone(viewer))

/** "Sep 28, 2026, 5:07 AM" -- when something happened, on the viewer's clock. */
fun formatStamp(iso: String, viewer: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    pattern("MMM d, yyyy, h:mm a", locale).format(parseInstant(iso).atZone(viewer))

/** The event's month (1-12) where it happens -- what suggestions are keyed on. */
fun monthAt(iso: String, zone: ZoneId): Int = parseInstant(iso).atZone(zone).monthValue

/** "Mondays and Wednesdays" from 0 = Monday ... 6 = Sunday. */
fun describeWeekdays(weekdays: List<Int>, locale: Locale = Locale.getDefault()): String {
    val names = weekdays.sorted().map { java.time.DayOfWeek.of(it + 1).getDisplayName(TextStyle.FULL, locale) + "s" }
    return when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
}

/** "Jun 6" (or "Jun 6, 2026") from "2026-06-06". */
fun formatDay(date: String, withYear: Boolean = false, locale: Locale = Locale.getDefault()): String =
    pattern(if (withYear) "MMM d, yyyy" else "MMM d", locale).format(LocalDate.parse(date))
