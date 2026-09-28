package com.weathernot.app.event

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weathernot.app.AppEnvironment
import com.weathernot.app.ui.UiMessage
import com.weathernot.app.ui.userMessage
import com.weathernot.kit.format.CriterionDraft
import com.weathernot.kit.format.criterionSentence
import com.weathernot.kit.format.draftToInput
import com.weathernot.kit.format.monthAt
import com.weathernot.kit.format.toApiString
import com.weathernot.kit.format.wallTimeToInstant
import com.weathernot.kit.format.wording
import com.weathernot.kit.format.zoneOf
import com.weathernot.kit.models.Alert
import com.weathernot.kit.models.Criterion
import com.weathernot.kit.models.CriterionInput
import com.weathernot.kit.models.CriterionTrend
import com.weathernot.kit.models.DecisionDeadline
import com.weathernot.kit.models.EvaluationResult
import com.weathernot.kit.models.Event
import com.weathernot.kit.models.EventNotifications
import com.weathernot.kit.models.EventSummary
import com.weathernot.kit.models.Segment
import com.weathernot.kit.models.StatusLink
import com.weathernot.kit.models.SuggestedCriterion
import com.weathernot.kit.models.SuggestionRejection
import com.weathernot.kit.models.Timeline
import com.weathernot.kit.models.UnitSystem
import com.weathernot.kit.networking.ApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class SeriesInfo(val id: String, val name: String, val position: Int, val count: Int)

/** Channel overrides for one event: null follows the profile. */
enum class NotifyChannel { EMAIL, SMS, IMPROVEMENTS }

class EventDetailViewModel(private val eventId: String, private val api: ApiClient) : ViewModel() {
    var event by mutableStateOf<Event?>(null)
        private set
    var criteria by mutableStateOf<List<Criterion>>(emptyList())
        private set
    var segments by mutableStateOf<List<Segment>>(emptyList())
        private set
    var results by mutableStateOf<List<EvaluationResult>>(emptyList())
        private set
    var alerts by mutableStateOf<List<Alert>>(emptyList())
        private set
    var summary by mutableStateOf<EventSummary?>(null)
        private set
    var timeline by mutableStateOf<Timeline?>(null)
        private set
    var trends by mutableStateOf<Map<String, CriterionTrend>>(emptyMap())
        private set
    var seriesInfo by mutableStateOf<SeriesInfo?>(null)
        private set

    /** Server failures: the banner. What's wrong with a field shows beside it instead. */
    var error by mutableStateOf<String?>(null)
    var loadFailed by mutableStateOf(false)
        private set
    var checking by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Criteria removed but still undoable: hidden now, deleted when the Undo goes. */
    var removing by mutableStateOf<Set<String>>(emptySet())
        private set

    var notifications by mutableStateOf<EventNotifications?>(null)
        private set
    var decision by mutableStateOf<DecisionDeadline?>(null)
        private set

    var links by mutableStateOf<List<StatusLink>?>(null)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        refresh()
        loadNotifications()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val eventCall = async { api.getEvent(eventId) }
                val criteriaCall = async { api.listCriteria(eventId) }
                val resultsCall = async { api.listEvaluations(eventId) }
                val alertsCall = async { api.listAlerts(eventId) }
                val segmentsCall = async { api.listSegments(eventId) }
                val loaded = eventCall.await()
                criteria = criteriaCall.await()
                results = resultsCall.await()
                alerts = alertsCall.await()
                segments = segmentsCall.await()
                event = loaded
                loadFailed = false
                // Alongside, not awaited: they may read the forecast, and the page shouldn't wait on that.
                launch { summary = runCatching { api.getEventSummary(eventId) }.getOrNull() }
                launch { timeline = runCatching { api.getTimeline(eventId) }.getOrNull() }
                launch { trends = runCatching { api.listTrends(eventId) }.getOrDefault(emptyList()).associateBy { it.criterionId } }
                val seriesId = loaded.seriesId
                if (seriesId != null && seriesInfo?.id != seriesId) loadSeriesInfo(seriesId)
            } catch (e: Exception) {
                if (event == null) loadFailed = true
                error = e.userMessage("Could not load this event.")
            }
        }
    }

    private fun loadSeriesInfo(seriesId: String) {
        viewModelScope.launch {
            runCatching { api.getSeries(seriesId) }.onSuccess { series ->
                seriesInfo = SeriesInfo(series.seriesId, series.name, series.events.indexOfFirst { it.eventId == eventId } + 1, series.dateCount)
            }
        }
    }

    /** Latest result per criterion, and each criterion's history oldest first. */
    fun latestResult(criterionId: String): EvaluationResult? =
        results.filter { it.criterionId == criterionId }.maxByOrNull { it.evaluatedAt }

    fun history(criterionId: String): List<EvaluationResult> =
        results.filter { it.criterionId == criterionId }.sortedBy { it.evaluatedAt }

    private fun act(failure: String, block: suspend () -> Unit) {
        error = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                error = e.userMessage(failure)
            }
        }
    }

    fun checkNow() {
        checking = true
        act("Could not check the weather right now. Try again in a moment.") {
            try {
                api.evaluateEvent(eventId)
                refresh()
            } finally {
                checking = false
            }
        }
    }

    fun togglePaused(undoing: Boolean = false) {
        val current = event ?: return
        val pausing = current.isActive
        busy = true
        act("Could not update this event's status.") {
            try {
                api.setEventActive(eventId, !pausing)
                refresh()
                if (!undoing) {
                    _messages.send(
                        UiMessage(
                            text = if (pausing) "Monitoring paused — no checks or alerts." else "Monitoring resumed.",
                            actionLabel = "Undo",
                            onAction = { togglePausedTo(pausing) },
                        ),
                    )
                }
            } finally {
                busy = false
            }
        }
    }

    private fun togglePausedTo(active: Boolean) = act("Could not undo that.") {
        api.setEventActive(eventId, active)
        refresh()
    }

    fun toggleCancelled() {
        val current = event ?: return
        busy = true
        act("Could not update this event's status.") {
            try {
                api.setEventCancelled(eventId, !current.isCancelled)
                refresh()
            } finally {
                busy = false
            }
        }
    }

    fun duplicate(onDuplicated: (String) -> Unit) {
        busy = true
        act("Could not duplicate this event.") {
            try {
                onDuplicated(api.duplicateEvent(eventId).eventId)
            } finally {
                busy = false
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        busy = true
        act("Could not delete this event.") {
            try {
                api.deleteEvent(eventId)
                onDeleted()
            } finally {
                busy = false
            }
        }
    }

    /** Saves name, place and window; times are the clock at the (possibly new) location. */
    suspend fun saveDetails(
        name: String,
        displayName: String,
        address: String,
        latitude: Double,
        longitude: Double,
        start: LocalDateTime,
        end: LocalDateTime,
    ): String? = try {
        // Asked afresh -- a location moved a moment ago may have a different zone.
        val zone = zoneOf(runCatching { api.timeZoneAt(latitude, longitude) }.getOrElse { event?.timezone })
        api.updateEvent(
            eventId,
            name = name.trim(),
            displayName = displayName.trim(),
            address = address,
            latitude = latitude,
            longitude = longitude,
            startTime = wallTimeToInstant(start, zone).toApiString(),
            endTime = wallTimeToInstant(end, zone).toApiString(),
        )
        refresh()
        null
    } catch (e: Exception) {
        e.userMessage("Could not update this event's details.")
    }

    // ---- criteria ----

    /** Returns an error to show in the editor, or null once saved. */
    suspend fun addCriterion(unitSystem: UnitSystem, draft: CriterionDraft): String? = try {
        api.addCriterion(eventId, draftToInput(unitSystem, draft))
        refresh()
        null
    } catch (e: Exception) {
        e.userMessage("Could not add the criterion.")
    }

    suspend fun editCriterion(unitSystem: UnitSystem, criterion: Criterion, draft: CriterionDraft): String? = try {
        // Everything but the variable, which an existing criterion keeps.
        api.updateCriterion(eventId, criterion.criterionId, draftToInput(unitSystem, draft.copy(variable = criterion.variable)))
        refresh()
        null
    } catch (e: Exception) {
        e.userMessage("Could not update the criterion.")
    }

    fun toggleCriterion(unitSystem: UnitSystem, criterion: Criterion, undoing: Boolean = false) {
        val pausing = criterion.active
        act("Could not update the criterion.") {
            api.setCriterionActive(eventId, criterion.criterionId, !pausing)
            refresh()
            if (!undoing) {
                _messages.send(
                    UiMessage(
                        text = "${if (pausing) "Paused" else "Resumed"}: ${criterionSentence(unitSystem, criterion.wording())}",
                        actionLabel = "Undo",
                        onAction = { toggleCriterion(unitSystem, criterion.copy(active = !pausing), undoing = true) },
                    ),
                )
            }
        }
    }

    /** Takes it off the page at once, with Undo; actually deleted once the Undo has gone. */
    fun removeCriterion(unitSystem: UnitSystem, criterion: Criterion) {
        val id = criterion.criterionId
        removing = removing + id
        viewModelScope.launch {
            _messages.send(
                UiMessage(
                    text = "Removed: ${criterionSentence(unitSystem, criterion.wording())}",
                    actionLabel = "Undo",
                    onAction = { removing = removing - id },
                    onDismiss = { commitRemoval(id) },
                ),
            )
        }
    }

    private fun commitRemoval(id: String) {
        if (id !in removing) return
        // The app scope: finishes even if this page is already gone.
        AppEnvironment.appScope.launch {
            try {
                api.deleteCriterion(eventId, id)
            } catch (e: Exception) {
                error = e.userMessage("Could not remove the criterion.")
            }
            removing = removing - id
            refresh()
        }
    }

    override fun onCleared() {
        // Leaving the page: carry out the removals still waiting on their Undo.
        removing.forEach { id -> AppEnvironment.appScope.launch { runCatching { api.deleteCriterion(eventId, id) } } }
    }

    // ---- suggestions ----

    /** What we'd suggest for its type, place and month, minus what the whole event already watches. */
    suspend fun loadSuggestions(): List<SuggestedCriterion>? {
        val current = event ?: return null
        val watched = criteria.filter { it.segmentId == null }.map { it.variable }.toSet()
        return try {
            api.previewSuggestions(current.eventType, current.latitude, current.longitude, monthAt(current.startTime, zoneOf(current.timezone)))
                .filter { it.variable !in watched }
        } catch (e: Exception) {
            error = e.userMessage("Couldn't load suggestions right now.")
            null
        }
    }

    /** Keeps the ticked ones (as suggested criteria) and records the rest as turned down. */
    suspend fun finishSuggestions(keep: List<SuggestedCriterion>, turnDown: List<SuggestedCriterion>): Boolean {
        val current = event ?: return false
        return try {
            for (s in keep) {
                api.addCriterion(eventId, CriterionInput(s.variable, s.operator, s.threshold, watchThreshold = s.watchThreshold, suggested = true))
            }
            if (turnDown.isNotEmpty()) {
                api.rejectSuggestions(
                    current.eventType, current.latitude, current.longitude, monthAt(current.startTime, zoneOf(current.timezone)), eventId,
                    turnDown.map { SuggestionRejection(it.variable, it.operator, it.threshold) },
                )
            }
            refresh()
            true
        } catch (e: Exception) {
            error = e.userMessage("Could not save your choices. Try again.")
            false
        }
    }

    // ---- alerts ----

    suspend fun saveNote(alertId: String, note: String): String? = try {
        api.setAlertNote(eventId, alertId, note)
        alerts = alerts.map { if (it.alertId == alertId) it.copy(note = note) else it }
        null
    } catch (e: Exception) {
        e.userMessage("Could not save the note.")
    }

    // ---- notifications ----

    private fun loadNotifications() {
        viewModelScope.launch {
            notifications = runCatching { api.getEventNotifications(eventId) }.getOrNull()
            decision = runCatching { api.getDecisionDeadline(eventId) }.getOrNull()
        }
    }

    fun setChannel(channel: NotifyChannel, value: Boolean?) {
        val current = notifications ?: return
        act("Couldn't save that. Try again.") {
            notifications = api.setEventNotifications(
                eventId,
                email = if (channel == NotifyChannel.EMAIL) value else current.email,
                sms = if (channel == NotifyChannel.SMS) value else current.sms,
                improvements = if (channel == NotifyChannel.IMPROVEMENTS) value else current.improvements,
            )
        }
    }

    suspend fun addRecipient(name: String, email: String, phone: String): String? = try {
        val added = api.addEventRecipient(eventId, name.trim(), email.trim().ifEmpty { null }, phone.trim().ifEmpty { null })
        notifications = notifications?.let { it.copy(recipients = it.recipients + added) }
        null
    } catch (e: Exception) {
        e.userMessage("Couldn't add them. Try again.")
    }

    fun removeRecipient(recipientId: String) = act("Couldn't remove them. Try again.") {
        api.deleteEventRecipient(eventId, recipientId)
        notifications = notifications?.let { n -> n.copy(recipients = n.recipients.filter { it.recipientId != recipientId }) }
    }

    /** A clock reading at the event's place, or null to clear it. */
    suspend fun setDecision(wall: LocalDateTime?): String? = try {
        val zone = zoneOf(event?.timezone)
        decision = api.setDecisionDeadline(eventId, wall?.let { wallTimeToInstant(it, zone).toApiString() })
        null
    } catch (e: Exception) {
        e.userMessage("Couldn't save that. Try again.")
    }

    // ---- sharing ----

    /** Opening the share sheet shows the newest link, making the first one if there's none. */
    fun loadSharing() {
        act("Couldn't load your share links.") {
            val existing = api.listStatusLinks(eventId)
            links = existing.ifEmpty { listOf(api.createStatusLink(eventId)) }
        }
        viewModelScope.launch { statusMessage = runCatching { api.getStatusMessage(eventId) }.getOrDefault("") }
    }

    fun newLink() = act("Couldn't make a new link.") {
        links = links.orEmpty() + api.createStatusLink(eventId)
    }

    fun revokeLink(token: String) = act("Couldn't revoke that link.") {
        api.revokeStatusLink(eventId, token)
        links = links.orEmpty().filter { it.token != token }
    }

    suspend fun saveStatusMessage(message: String): String? = try {
        statusMessage = api.setStatusMessage(eventId, message)
        null
    } catch (e: Exception) {
        e.userMessage("Couldn't save the message. Try again.")
    }
}
