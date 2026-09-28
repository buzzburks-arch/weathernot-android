package com.weathernot.kit.models

import kotlinx.serialization.Serializable

// Mirrors the web app's frontend/src/api/types.ts (itself a mirror of the
// backend's src/weathernot/api/schemas.py). Property names are camelCase
// here and snake_case on the wire -- ApiClient's Json maps them. Times
// stay ISO strings; see format/EventTime.kt for reading them.
//
// Every threshold and value is canonical (metric: °C, m/s, mm, 0-1 for
// chances) whatever the viewer's unit system -- convert with format/Units.kt
// at display time only.

@Serializable
data class UserPreferences(
    val email: String,
    val unitSystem: UnitSystem = UnitSystem.IMPERIAL,
    val phoneNumber: String? = null,
    val smsEnabled: Boolean = false,
    val eventTypes: List<EventType> = emptyList(),
    val eventTypesAsked: Boolean = false,
    val eventTypesHintDismissed: Boolean = false,
    val emailEnabled: Boolean = true,
    val quietHoursStart: String? = null,
    val quietHoursEnd: String? = null,
    val timezone: String? = null,
    val digest: Boolean = false,
    val improvements: Boolean = false,
)

@Serializable
data class CriterionDefault(
    val variable: GridVariable,
    val operator: Operator,
    val threshold: Double,
    val watchThreshold: Double? = null,
)

/** One entry of the event-type picker. */
@Serializable
data class EventTypeInfo(
    val value: EventType,
    val label: String,
    val hint: String = "",
    val defaults: List<CriterionDefault> = emptyList(),
)

@Serializable
data class Event(
    val eventId: String,
    val ownerId: String = "",
    val eventType: EventType,
    /** The event's title -- never blank. */
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** The place's name ("Denver, CO"); may be blank. */
    val displayName: String = "",
    val address: String = "",
    /** IANA zone of the location: show the event's times in it, not the viewer's. */
    val timezone: String = "UTC",
    val seriesId: String? = null,
    val startTime: String,
    val endTime: String,
    val createdAt: String? = null,
    /** "active", "paused" or "cancelled". */
    val status: String = "active",
    val description: String = "",
    val goNoGo: GoNoGo = GoNoGo.UNKNOWN,
    /** "forecast", "climatology" (a typical risk, not a call), or null with nothing checked. */
    val verdictMode: String? = null,
    /** What drives a Watch / No-go, in a few words ("gusts 28 mph"). */
    val verdictReason: String? = null,
    val phase: WatchPhase = WatchPhase.FORECAST,
    /** A background check is in flight; [goNoGo] may be the previous one. Poll meanwhile. */
    val evaluating: Boolean = false,
) {
    val isActive: Boolean get() = status == "active"
    val isPaused: Boolean get() = status == "paused"
    val isCancelled: Boolean get() = status == "cancelled"
}

/** A criterion's second condition ("and rain amount above 2.5 mm"). */
@Serializable
data class Condition(
    val variable: GridVariable,
    val operator: Operator,
    val threshold: Double,
)

/** A named part of an event with its own window ("Ceremony", 4-5 pm). */
@Serializable
data class Segment(
    val segmentId: String,
    val eventId: String = "",
    val name: String,
    val startTime: String,
    val endTime: String,
)

@Serializable
data class Criterion(
    val criterionId: String,
    val eventId: String = "",
    val variable: GridVariable,
    val operator: Operator,
    /** The No-go level. */
    val threshold: Double,
    /** "manual" or "ai_suggested". */
    val source: String = "manual",
    val active: Boolean = true,
    /** The earlier Watch (heads-up) level. */
    val watchThreshold: Double? = null,
    /** Held this many hours in a row; null: at any point. */
    val minDurationHours: Int? = null,
    val also: Condition? = null,
    /** Shown and checked, but never drives the verdict or alerts. */
    val informational: Boolean = false,
    /** The part of the event it watches; null: the whole event. */
    val segmentId: String? = null,
    /** Still following its series' criterion. */
    val fromSeries: Boolean = false,
)

@Serializable
data class EvaluationResult(
    val resultId: String,
    val eventId: String = "",
    val criterionId: String,
    val evaluatedAt: String,
    val mode: String = "forecast",
    val value: Double? = null,
    val triggered: Boolean = false,
    val confidence: String? = null,
    val level: GoNoGo? = null,
    /** Climatology: share of past days around the date that crossed the threshold. */
    val frequency: Double? = null,
    /** "consistent" or "changing"; null with too little history. */
    val stability: String? = null,
) {
    /** The criterion's own go/no-go from this result -- "unknown" with no value. */
    val goNoGo: GoNoGo
        get() = if (value == null) GoNoGo.UNKNOWN else level ?: if (triggered) GoNoGo.NO_GO else GoNoGo.GO
}

/** The event's overall verdict and headline -- the same text the share page and alerts use. */
@Serializable
data class EventSummary(
    val verdict: GoNoGo = GoNoGo.UNKNOWN,
    val mode: String? = null,
    val headline: String = "",
    val detail: String = "",
    val basis: String? = null,
    val checkedAt: String? = null,
    val drivingCriterionIds: List<String> = emptyList(),
    val confidence: String? = null,
    val stability: String? = null,
    val phase: WatchPhase = WatchPhase.FORECAST,
    val nextCheckAt: String? = null,
    val forecastBeginsAt: String? = null,
)

@Serializable
data class TimelineCell(
    val time: String,
    val value: Double,
    val level: GoNoGo? = null,
)

@Serializable
data class TimelineRow(
    val variable: GridVariable,
    val cells: List<TimelineCell> = emptyList(),
)

/** The forecast hour by hour across the event, padded a couple of hours either side. */
@Serializable
data class Timeline(
    val start: String,
    val end: String,
    val rows: List<TimelineRow> = emptyList(),
)

@Serializable
data class TrendPoint(val time: String, val value: Double)

/** Which way a criterion's forecast has been moving across its checks. */
@Serializable
data class CriterionTrend(
    val criterionId: String,
    /** "worsening", "improving" or "steady". */
    val direction: String,
    val since: String? = null,
    val change: Double = 0.0,
    val points: List<TrendPoint> = emptyList(),
)

@Serializable
data class Alert(
    val alertId: String,
    val eventId: String = "",
    val criterionId: String = "",
    val resultId: String? = null,
    val sentAt: String,
    val reason: String = "",
    val title: String? = null,
    val body: String? = null,
    val channels: List<String> = emptyList(),
    val held: List<String> = emptyList(),
    val level: GoNoGo? = null,
    val mode: String? = null,
    val value: Double? = null,
    val confidence: String? = null,
    val note: String = "",
    /** "alert", "improved" or "decision". */
    val kind: String = "alert",
)

@Serializable
data class EventRecipient(
    val recipientId: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    /** Signed up from the shared status page. */
    val follower: Boolean = false,
)

/** How one event's alerts go out: null channels follow the owner's profile. */
@Serializable
data class EventNotifications(
    val email: Boolean? = null,
    val sms: Boolean? = null,
    val improvements: Boolean? = null,
    val recipients: List<EventRecipient> = emptyList(),
)

/** "I need to decide by": when, and when that summary went out. */
@Serializable
data class DecisionDeadline(
    val decideBy: String? = null,
    val sentAt: String? = null,
)

@Serializable
data class StatusLink(
    val token: String,
    val createdAt: String? = null,
)

@Serializable
data class StatusMessage(val message: String = "")

@Serializable
data class SuggestedCriterion(
    val variable: GridVariable,
    val operator: Operator,
    val threshold: Double,
    val label: String = "",
    val watchThreshold: Double? = null,
    /** Why it's suggested, in a sentence. */
    val reason: String = "",
)

/** A place-search result: [name] is the place, [context] where it is. */
@Serializable
data class PlaceMatch(
    val latitude: Double,
    val longitude: Double,
    val name: String = "",
    val context: String = "",
    val displayName: String = "",
    val address: String = "",
)

@Serializable
data class WeeklySchedule(
    /** "weekly", "multi_day" or "schedule"; the fields below are the weekly one's. */
    val kind: String,
    /** 0 = Monday ... 6 = Sunday. */
    val weekdays: List<Int> = emptyList(),
    val firstDate: String? = null,
    val untilDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
)

@Serializable
data class SeriesCriterion(
    val criterionId: String,
    val variable: GridVariable,
    val operator: Operator,
    val threshold: Double,
    val source: String = "manual",
    val active: Boolean = true,
    val watchThreshold: Double? = null,
    val minDurationHours: Int? = null,
    val also: Condition? = null,
    val informational: Boolean = false,
)

/** Several dated events planned as one; each date is an ordinary [Event]. */
@Serializable
data class SeriesSummary(
    val seriesId: String,
    /** "weekly", "multi_day" or "schedule". */
    val kind: String,
    val name: String,
    val eventType: EventType,
    val displayName: String = "",
    val address: String = "",
    val timezone: String = "UTC",
    val schedule: WeeklySchedule? = null,
    val dateCount: Int = 0,
    val upcomingCount: Int = 0,
    val nextStart: String? = null,
    val nextEnd: String? = null,
    val upcomingNoGo: Int = 0,
    val upcomingWatch: Int = 0,
    val evaluating: Boolean = false,
)

@Serializable
data class Series(
    val seriesId: String,
    val kind: String,
    val name: String,
    val eventType: EventType,
    val displayName: String = "",
    val address: String = "",
    val timezone: String = "UTC",
    val schedule: WeeklySchedule? = null,
    val dateCount: Int = 0,
    val upcomingCount: Int = 0,
    val nextStart: String? = null,
    val nextEnd: String? = null,
    val upcomingNoGo: Int = 0,
    val upcomingWatch: Int = 0,
    val evaluating: Boolean = false,
    val events: List<Event> = emptyList(),
    val criteria: List<SeriesCriterion> = emptyList(),
)

// ---- Request bodies ----

/** A criterion to create, canonical units. */
@Serializable
data class CriterionInput(
    val variable: GridVariable,
    val operator: Operator,
    val threshold: Double,
    val watchThreshold: Double? = null,
    val minDurationHours: Int? = null,
    val also: Condition? = null,
    val informational: Boolean = false,
    val segmentId: String? = null,
    /** Kept from a suggestion rather than the user's own. */
    val suggested: Boolean = false,
)

@Serializable
data class CreateEventInput(
    val eventType: EventType,
    /** Blank gets a default from the type and place ("Wedding at Denver, CO"). */
    val name: String = "",
    val latitude: Double,
    val longitude: Double,
    val displayName: String = "",
    val address: String = "",
    val startTime: String,
    val endTime: String,
    val description: String = "",
    val criteria: List<CriterionInput> = emptyList(),
)

@Serializable
data class SuggestionRejection(
    val variable: GridVariable,
    val operator: Operator,
    val threshold: Double,
)
