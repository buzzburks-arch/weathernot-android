package com.weathernot.kit.networking

import com.weathernot.kit.models.Alert
import com.weathernot.kit.models.Condition
import com.weathernot.kit.models.CreateEventInput
import com.weathernot.kit.models.Criterion
import com.weathernot.kit.models.CriterionInput
import com.weathernot.kit.models.CriterionTrend
import com.weathernot.kit.models.DecisionDeadline
import com.weathernot.kit.models.EvaluationResult
import com.weathernot.kit.models.Event
import com.weathernot.kit.models.EventNotifications
import com.weathernot.kit.models.EventRecipient
import com.weathernot.kit.models.EventSummary
import com.weathernot.kit.models.EventType
import com.weathernot.kit.models.EventTypeInfo
import com.weathernot.kit.models.PlaceMatch
import com.weathernot.kit.models.Segment
import com.weathernot.kit.models.Series
import com.weathernot.kit.models.SeriesCriterion
import com.weathernot.kit.models.SeriesSummary
import com.weathernot.kit.models.StatusLink
import com.weathernot.kit.models.StatusMessage
import com.weathernot.kit.models.SuggestedCriterion
import com.weathernot.kit.models.SuggestionRejection
import com.weathernot.kit.models.Timeline
import com.weathernot.kit.models.UnitSystem
import com.weathernot.kit.models.UserPreferences
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * The WeatherNot API (`src/weathernot/api` in the weathernot repo), one
 * method per call the web app's `frontend/src/api/client.ts` makes.
 * Bearer-token auth, JSON in and out, snake_case on the wire.
 *
 * Partial updates build their bodies explicitly: for several fields the
 * API tells "null" (clear it) apart from "left out" (keep it), which a
 * data class with nullable fields can't say.
 */
class ApiClient(
    val baseUrl: String,
    private val tokenStore: TokenStoring,
    engine: HttpClient,
) {
    @OptIn(ExperimentalSerializationApi::class)
    internal val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val http = engine

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the server turns the session down (the token is already cleared). */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    val isSignedIn: Boolean get() = tokenStore.token() != null

    fun signOut() = tokenStore.clear()

    // ---- auth ----

    @Serializable
    private data class EmailBody(val email: String)

    @Serializable
    private data class TokenBody(val token: String)

    @Serializable
    private data class SessionBody(val sessionToken: String)

    /**
     * Passwordless sign-in. No email provider is wired up server-side yet,
     * so the API hands the magic-link token straight back and this
     * verifies it at once -- the same shortcut the web app's login page
     * takes. Saves the session token.
     */
    suspend fun signIn(email: String) {
        val link = call<TokenBody>(HttpMethod.Post, "/auth/request-link", body = encode(EmailBody(email)), authed = false)
        val session = call<SessionBody>(HttpMethod.Post, "/auth/verify", body = encode(TokenBody(link.token)), authed = false)
        tokenStore.save(session.sessionToken)
    }

    suspend fun me(): UserPreferences = call(HttpMethod.Get, "/auth/me")

    private suspend fun updateMe(build: JsonObjectBuilder.() -> Unit): UserPreferences =
        call(HttpMethod.Patch, "/auth/me", body = buildJsonObject(build))

    suspend fun setUnitSystem(unitSystem: UnitSystem) = updateMe { put("unit_system", unitSystem.wire) }

    /** Also marks the first-sign-in question as answered. */
    suspend fun setEventTypes(types: List<EventType>) = updateMe {
        put("event_types", buildJsonArray { types.forEach { add(JsonPrimitive(it.wire)) } })
    }

    suspend fun skipEventTypesQuestion() = updateMe { put("event_types_asked", true) }

    suspend fun dismissEventTypesHint() = updateMe { put("event_types_hint_dismissed", true) }

    suspend fun setEmailEnabled(enabled: Boolean) = updateMe { put("email_enabled", enabled) }

    suspend fun setImprovements(enabled: Boolean) = updateMe { put("improvements", enabled) }

    /** Digest and quiet hours run on the viewer's clock, so both send [timezone]. */
    suspend fun setDigest(digest: Boolean, timezone: String) = updateMe {
        put("digest", digest)
        put("timezone", timezone)
    }

    /** "22:00"-style times; both null turns quiet hours off. */
    suspend fun setQuietHours(start: String?, end: String?, timezone: String) = updateMe {
        put("quiet_hours_start", start)
        put("quiet_hours_end", end)
        put("timezone", timezone)
    }

    /** Null removes the number, which also turns texts off server-side. */
    suspend fun setPhoneNumber(phoneNumber: String?) = updateMe { put("phone_number", phoneNumber) }

    suspend fun setSmsEnabled(enabled: Boolean) = updateMe { put("sms_enabled", enabled) }

    // ---- events ----

    suspend fun listEvents(): List<Event> = call(HttpMethod.Get, "/events")

    suspend fun getEvent(eventId: String): Event = call(HttpMethod.Get, "/events/$eventId")

    suspend fun createEvent(input: CreateEventInput): Event = call(HttpMethod.Post, "/events", body = encode(input))

    suspend fun deleteEvent(eventId: String) = send(HttpMethod.Delete, "/events/$eventId")

    @Serializable
    private data class DeletedBody(val deleted: Int)

    /** Every event that has ended (a series' dates stay with the series); how many went. */
    suspend fun deletePastEvents(): Int = call<DeletedBody>(HttpMethod.Delete, "/events/past").deleted

    suspend fun duplicateEvent(eventId: String): Event = call(HttpMethod.Post, "/events/$eventId/duplicate")

    /** Changes only what's given; a blank [name] resets it to the default from type and place. */
    suspend fun updateEvent(
        eventId: String,
        name: String? = null,
        displayName: String? = null,
        address: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        startTime: String? = null,
        endTime: String? = null,
        active: Boolean? = null,
        cancelled: Boolean? = null,
    ): Event = call(
        HttpMethod.Patch,
        "/events/$eventId",
        body = buildJsonObject {
            name?.let { put("name", it) }
            displayName?.let { put("display_name", it) }
            address?.let { put("address", it) }
            latitude?.let { put("latitude", it) }
            longitude?.let { put("longitude", it) }
            startTime?.let { put("start_time", it) }
            endTime?.let { put("end_time", it) }
            active?.let { put("active", it) }
            cancelled?.let { put("cancelled", it) }
        },
    )

    /** False pauses monitoring (no checks or alerts); true resumes it and re-checks. */
    suspend fun setEventActive(eventId: String, active: Boolean) = updateEvent(eventId, active = active)

    /** True marks the event called off; false puts it back on. */
    suspend fun setEventCancelled(eventId: String, cancelled: Boolean) = updateEvent(eventId, cancelled = cancelled)

    suspend fun listEventTypes(): List<EventTypeInfo> = call(HttpMethod.Get, "/events/types")

    @Serializable
    private data class TimeZoneBody(val timezone: String)

    /** The IANA zone an event at this point would have. */
    suspend fun timeZoneAt(latitude: Double, longitude: Double): String =
        call<TimeZoneBody>(HttpMethod.Get, "/events/timezone") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
        }.timezone

    /** Places matching a partly typed query, best first. A 503 means lookup isn't set up server-side. */
    suspend fun searchPlaces(query: String): List<PlaceMatch> =
        call(HttpMethod.Get, "/events/geocode/search") { parameter("q", query) }

    /** What's at a point, or null when there's nothing more specific than the country. */
    suspend fun placeAt(latitude: Double, longitude: Double): PlaceMatch? =
        call(HttpMethod.Get, "/events/geocode/reverse") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
        }

    // ---- suggestions ----

    suspend fun previewSuggestions(eventType: EventType, latitude: Double, longitude: Double, month: Int): List<SuggestedCriterion> =
        call(HttpMethod.Get, "/events/suggestions") {
            parameter("event_type", eventType.wire)
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("month", month)
        }

    /** Suggestions turned down at review time -- tunes future ones. */
    suspend fun rejectSuggestions(
        eventType: EventType,
        latitude: Double,
        longitude: Double,
        month: Int,
        eventId: String?,
        suggestions: List<SuggestionRejection>,
    ) = send(
        HttpMethod.Post,
        "/events/suggestions/rejections",
        body = buildJsonObject {
            put("event_type", eventType.wire)
            put("latitude", latitude)
            put("longitude", longitude)
            put("month", month)
            eventId?.let { put("event_id", it) }
            put("suggestions", json.encodeToJsonElement(suggestions))
        },
    )

    // ---- criteria and segments ----

    suspend fun listCriteria(eventId: String): List<Criterion> = call(HttpMethod.Get, "/events/$eventId/criteria")

    suspend fun addCriterion(eventId: String, input: CriterionInput): Criterion =
        call(HttpMethod.Post, "/events/$eventId/criteria", body = encode(input))

    /** Every field, so an edit also clears what was turned off (Watch level, duration, ...). */
    suspend fun updateCriterion(eventId: String, criterionId: String, input: CriterionInput) =
        send(HttpMethod.Patch, "/events/$eventId/criteria/$criterionId", body = fullCriterionBody(input, withSegment = true))

    suspend fun setCriterionActive(eventId: String, criterionId: String, active: Boolean) =
        send(HttpMethod.Patch, "/events/$eventId/criteria/$criterionId", body = buildJsonObject { put("active", active) })

    suspend fun deleteCriterion(eventId: String, criterionId: String) =
        send(HttpMethod.Delete, "/events/$eventId/criteria/$criterionId")

    suspend fun listSegments(eventId: String): List<Segment> = call(HttpMethod.Get, "/events/$eventId/segments")

    // ---- verdict, forecast and history ----

    suspend fun getEventSummary(eventId: String): EventSummary = call(HttpMethod.Get, "/events/$eventId/summary")

    suspend fun getTimeline(eventId: String): Timeline = call(HttpMethod.Get, "/events/$eventId/timeline")

    suspend fun listTrends(eventId: String): List<CriterionTrend> = call(HttpMethod.Get, "/events/$eventId/trends")

    suspend fun listEvaluations(eventId: String): List<EvaluationResult> = call(HttpMethod.Get, "/events/$eventId/evaluations")

    /** An explicit re-check; the server also re-checks on its own as forecasts land. */
    suspend fun evaluateEvent(eventId: String): List<EvaluationResult> = call(HttpMethod.Post, "/events/$eventId/evaluate")

    // ---- alerts, sharing, notifications ----

    suspend fun listAlerts(eventId: String): List<Alert> = call(HttpMethod.Get, "/events/$eventId/alerts")

    suspend fun setAlertNote(eventId: String, alertId: String, note: String) =
        send(HttpMethod.Put, "/events/$eventId/alerts/$alertId/note", body = buildJsonObject { put("note", note) })

    suspend fun createStatusLink(eventId: String): StatusLink = call(HttpMethod.Post, "/events/$eventId/status-link")

    suspend fun listStatusLinks(eventId: String): List<StatusLink> = call(HttpMethod.Get, "/events/$eventId/status-links")

    suspend fun revokeStatusLink(eventId: String, token: String) =
        send(HttpMethod.Delete, "/events/$eventId/status-links/$token")

    suspend fun getStatusMessage(eventId: String): String =
        call<StatusMessage>(HttpMethod.Get, "/events/$eventId/status-message").message

    suspend fun setStatusMessage(eventId: String, message: String): String =
        call<StatusMessage>(HttpMethod.Put, "/events/$eventId/status-message", body = buildJsonObject { put("message", message) }).message

    suspend fun getEventNotifications(eventId: String): EventNotifications = call(HttpMethod.Get, "/events/$eventId/notifications")

    /** Null for a channel means "as in my profile". */
    suspend fun setEventNotifications(eventId: String, email: Boolean?, sms: Boolean?, improvements: Boolean?): EventNotifications =
        call(
            HttpMethod.Put,
            "/events/$eventId/notifications",
            body = buildJsonObject {
                put("email", email)
                put("sms", sms)
                put("improvements", improvements)
            },
        )

    suspend fun addEventRecipient(eventId: String, name: String, email: String?, phone: String?): EventRecipient =
        call(
            HttpMethod.Post,
            "/events/$eventId/notifications/recipients",
            body = buildJsonObject {
                put("name", name)
                put("email", email)
                put("phone", phone)
            },
        )

    suspend fun deleteEventRecipient(eventId: String, recipientId: String) =
        send(HttpMethod.Delete, "/events/$eventId/notifications/recipients/$recipientId")

    suspend fun getDecisionDeadline(eventId: String): DecisionDeadline = call(HttpMethod.Get, "/events/$eventId/decision")

    /** Null clears it. */
    suspend fun setDecisionDeadline(eventId: String, decideBy: String?): DecisionDeadline =
        call(HttpMethod.Put, "/events/$eventId/decision", body = buildJsonObject { put("decide_by", decideBy) })

    // ---- series ----

    suspend fun listSeries(): List<SeriesSummary> = call(HttpMethod.Get, "/series")

    suspend fun getSeries(seriesId: String): Series = call(HttpMethod.Get, "/series/$seriesId")

    suspend fun deleteSeries(seriesId: String) = send(HttpMethod.Delete, "/series/$seriesId")

    suspend fun addSeriesCriterion(seriesId: String, input: CriterionInput): SeriesCriterion =
        call(HttpMethod.Post, "/series/$seriesId/criteria", body = encode(input.copy(segmentId = null)))

    suspend fun updateSeriesCriterion(seriesId: String, criterionId: String, input: CriterionInput) =
        send(HttpMethod.Patch, "/series/$seriesId/criteria/$criterionId", body = fullCriterionBody(input, withSegment = false))

    suspend fun setSeriesCriterionActive(seriesId: String, criterionId: String, active: Boolean) =
        send(HttpMethod.Patch, "/series/$seriesId/criteria/$criterionId", body = buildJsonObject { put("active", active) })

    suspend fun deleteSeriesCriterion(seriesId: String, criterionId: String) =
        send(HttpMethod.Delete, "/series/$seriesId/criteria/$criterionId")

    // ---- plumbing ----

    private fun fullCriterionBody(input: CriterionInput, withSegment: Boolean): JsonObject = buildJsonObject {
        put("operator", input.operator.wire)
        put("threshold", input.threshold)
        put("watch_threshold", input.watchThreshold)
        put("min_duration_hours", input.minDurationHours)
        put("also", input.also?.let { json.encodeToJsonElement(Condition.serializer(), it) } ?: JsonNull)
        put("informational", input.informational)
        if (withSegment) put("segment_id", input.segmentId)
    }

    private inline fun <reified T> encode(value: T): JsonElement = json.encodeToJsonElement(value)

    private suspend inline fun <reified T> call(
        method: HttpMethod,
        path: String,
        body: JsonElement? = null,
        authed: Boolean = true,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val text = send(method, path, body, authed, configure)
        return try {
            json.decodeFromString<T>(text)
        } catch (e: Exception) {
            throw ApiError.Decoding(e.message ?: e.toString())
        }
    }

    /** Makes the request and returns the body text; throws [ApiError] for anything but a 2xx. */
    suspend fun send(
        method: HttpMethod,
        path: String,
        body: JsonElement? = null,
        authed: Boolean = true,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): String {
        val token = tokenStore.token()
        if (authed && token == null) throw ApiError.NotAuthenticated
        val response = try {
            http.request("$baseUrl$path") {
                this.method = method
                if (authed) header("Authorization", "Bearer $token")
                if (body != null) setBody(TextContent(json.encodeToString(JsonElement.serializer(), body), ContentType.Application.Json))
                configure()
            }
        } catch (e: Exception) {
            throw ApiError.Transport(e.message ?: e.toString())
        }
        val text = response.bodyAsText()
        val status = response.status.value
        if (status == 401 && authed) {
            // Expired or revoked: forget it so the app falls back to sign-in.
            tokenStore.clear()
            _sessionExpired.tryEmit(Unit)
            throw ApiError.NotAuthenticated
        }
        if (status !in 200..299) {
            val detail = runCatching { json.decodeFromString<ServerErrorBody>(text).readable }.getOrNull()
            throw ApiError.Server(status, detail)
        }
        return text
    }
}
