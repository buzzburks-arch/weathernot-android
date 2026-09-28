package com.weathernot.kit.format

import com.weathernot.kit.models.EventType
import com.weathernot.kit.models.GoNoGo
import com.weathernot.kit.models.WatchPhase

// Wording shared across screens, from the web app's api/types.ts and the
// components that use it -- same states, same words, wherever they show up.

private val GO_NO_GO_LABELS = mapOf(
    GoNoGo.GO to "Go",
    GoNoGo.WATCH to "Watch",
    GoNoGo.NO_GO to "No-go",
    GoNoGo.UNKNOWN to "Waiting for forecast",
)

// Too far out for a forecast, a verdict is a typical risk for the dates, not a call.
private val TYPICAL_RISK_LABELS = mapOf(
    GoNoGo.GO to "Low risk",
    GoNoGo.WATCH to "Moderate risk",
    GoNoGo.NO_GO to "High risk",
    GoNoGo.UNKNOWN to "Waiting for forecast",
)

fun verdictLabel(verdict: GoNoGo, mode: String?): String =
    (if (mode == "climatology") TYPICAL_RISK_LABELS else GO_NO_GO_LABELS).getValue(verdict)

val STABILITY_WORDS: Map<String, String> = mapOf(
    "consistent" to "forecast has been consistent",
    "changing" to "forecast is still changing a lot",
)

/** The name an event gets when left blank, like the backend's `default_event_name`. */
fun defaultEventName(eventType: EventType, place: String): String =
    if (place.isBlank()) eventType.label else "${eventType.label} at ${place.trim()}"

/** The place to show beside an event's name, or "" when blank or the same text as the name. */
fun placeToShow(name: String, place: String): String =
    place.trim().takeIf { it.isNotEmpty() && it != name.trim() } ?: ""

/** Shortcuts for "What kinds of events do you plan?". */
data class RolePreset(val label: String, val types: List<EventType>)

val ROLE_PRESETS: List<RolePreset> = listOf(
    RolePreset("Wedding or event planner", listOf(EventType.WEDDING, EventType.PARTY, EventType.REUNION, EventType.FESTIVAL, EventType.CONCERT)),
    RolePreset("Coach or league", listOf(EventType.YOUTH_SPORTS, EventType.TRACK_MEET, EventType.RACE, EventType.GOLF)),
    RolePreset("Construction or outdoor work", listOf(EventType.CONSTRUCTION)),
    RolePreset("Just me", listOf(EventType.PICNIC, EventType.PARTY, EventType.TAILGATE, EventType.HIKE, EventType.CAMP)),
)

/** Their first type, or wedding (the long-standing default). */
fun defaultEventType(preferred: List<EventType>): EventType = preferred.firstOrNull() ?: EventType.WEDDING

/** Picker order: their types first, then the rest in catalog order. */
fun orderedEventTypes(catalog: List<EventType>, preferred: List<EventType>): List<EventType> =
    preferred.filter { it in catalog } + catalog.filter { it !in preferred }

private val CHANNEL_WORDS = mapOf("email" to "email", "sms" to "text message", "push" to "push notification")

fun joinWords(words: List<String>): String = when (words.size) {
    0 -> ""
    1 -> words[0]
    else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
}

/** How an alert went out: "sent by email; text message held for quiet hours or your digest". */
fun sentVia(channels: List<String>, held: List<String>): String {
    val parts = buildList {
        if (channels.isNotEmpty()) add("sent by ${joinWords(channels.map { CHANNEL_WORDS[it] ?: it })}")
        if (held.isNotEmpty()) add("${joinWords(held.map { CHANNEL_WORDS[it] ?: it })} held for quiet hours or your digest")
    }
    return parts.joinToString("; ").ifEmpty { "not sent — no email or phone on file" }
}

/** Where the event is in its watch, explained -- the verdict panel's "?" text. */
fun phaseExplanation(phase: WatchPhase, forecastBeginsAt: String?): String = when (phase) {
    WatchPhase.OUTLOOK ->
        "Too far out for a forecast, so this is how often the weather crossed your limits around these dates in past years." +
            (forecastBeginsAt?.let { " Forecast begins around ${formatLongDay(it)}." } ?: "")
    WatchPhase.FORECAST ->
        "Based on the latest forecast models. We check again on our own as new forecasts come in."
    WatchPhase.LIVE ->
        "The event is only hours away or under way, so we check every 15 minutes."
    WatchPhase.ENDED ->
        "The event is over, so we've stopped checking."
}
