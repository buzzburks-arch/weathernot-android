package com.weathernot.app.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weathernot.app.ui.PickedPlace
import com.weathernot.app.ui.userMessage
import com.weathernot.kit.format.CriterionDraft
import com.weathernot.kit.format.defaultEventType
import com.weathernot.kit.format.draftError
import com.weathernot.kit.format.draftFrom
import com.weathernot.kit.format.draftToInput
import com.weathernot.kit.format.toApiString
import com.weathernot.kit.format.wallTimeToInstant
import com.weathernot.kit.format.wording
import com.weathernot.kit.format.zoneOf
import com.weathernot.kit.models.CreateEventInput
import com.weathernot.kit.models.EventType
import com.weathernot.kit.models.SuggestionRejection
import com.weathernot.kit.models.UnitSystem
import com.weathernot.kit.networking.ApiClient
import com.weathernot.kit.networking.ApiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** One row of the "What to watch" checklist. */
data class DraftItem(
    /** What it's about, so the same suggestion coming back keeps its tick and edits. */
    val key: String,
    val draft: CriterionDraft,
    val suggested: Boolean,
    val reason: String? = null,
    val checked: Boolean = true,
)

enum class SuggestionStatus { WAITING, LOADING, READY, ERROR }

val STEPS = listOf("What & when", "Where", "What to watch")

class NewEventViewModel(
    private val api: ApiClient,
    preferredTypes: List<EventType>,
    private val unitSystem: UnitSystem,
) : ViewModel() {
    var step by mutableStateOf(0)
        private set
    var eventType by mutableStateOf(defaultEventType(preferredTypes))
    var name by mutableStateOf("")
    var description by mutableStateOf("")

    // Tomorrow afternoon as a starting point; a clock reading at the event's place.
    var start by mutableStateOf(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(16, 0)))
        private set
    var end by mutableStateOf(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(18, 0)))

    var place by mutableStateOf<PickedPlace?>(null)
        private set
    var placeName by mutableStateOf("")

    /** The event's zone once there's a place; the phone's own until then. */
    var zone by mutableStateOf<ZoneId>(ZoneId.systemDefault())
        private set

    var drafts by mutableStateOf<List<DraftItem>>(emptyList())
    var suggestionStatus by mutableStateOf(SuggestionStatus.WAITING)
        private set

    var tried by mutableStateOf<Set<Int>>(emptySet())
        private set
    var submitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    private var suggestionsJob: Job? = null

    fun stepError(index: Int): String? = when {
        index == 0 && !end.isAfter(start) -> "The end has to be after the start."
        index == 1 && place == null -> "Search for the place, or enter its coordinates."
        else -> null
    }

    fun shownError(index: Int): String? = if (index in tried) stepError(index) else null

    fun setType(type: EventType) {
        eventType = type
        loadSuggestions()
    }

    fun changeStart(value: LocalDateTime) {
        val monthChanged = value.monthValue != start.monthValue
        // Moving the start keeps the event's length.
        end = end.plus(java.time.Duration.between(start, value))
        start = value
        if (monthChanged) loadSuggestions()
    }

    fun pickPlace(picked: PickedPlace) {
        place = picked
        if (picked.name.isNotBlank()) placeName = picked.name
        viewModelScope.launch {
            zone = zoneOf(runCatching { api.timeZoneAt(picked.latitude, picked.longitude) }.getOrNull() ?: ZoneId.systemDefault().id)
        }
        loadSuggestions()
    }

    fun next() {
        tried = tried + step
        if (stepError(step) == null && step < STEPS.lastIndex) step++
    }

    fun back() {
        error = null
        if (step > 0) step--
    }

    /** Suggestions for the type, place and month; suggested rows still suggested keep their tick and edits. */
    private fun loadSuggestions() {
        val picked = place ?: return
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch {
            delay(300)
            suggestionStatus = SuggestionStatus.LOADING
            try {
                val suggestions = api.previewSuggestions(eventType, picked.latitude, picked.longitude, start.monthValue)
                val previous = drafts.associateBy { it.key }
                val suggested = suggestions.map { s ->
                    val key = "suggested:${s.variable.wire}:${s.operator.wire}"
                    previous[key] ?: DraftItem(key, draftFrom(unitSystem, s.wording()), suggested = true, reason = s.reason)
                }
                drafts = suggested + drafts.filter { !it.suggested }
                suggestionStatus = SuggestionStatus.READY
            } catch (_: Exception) {
                suggestionStatus = SuggestionStatus.ERROR
            }
        }
    }

    fun addOwn(draft: CriterionDraft) {
        drafts = drafts + DraftItem("manual:${UUID.randomUUID()}", draft, suggested = false)
    }

    fun update(key: String, change: (DraftItem) -> DraftItem) {
        drafts = drafts.map { if (it.key == key) change(it) else it }
    }

    fun draftsError(): String? = drafts.filter { it.checked }.firstNotNullOfOrNull { draftError(it.draft) }

    fun submit(onCreated: (String) -> Unit) {
        tried = STEPS.indices.toSet()
        val firstInvalid = STEPS.indices.firstOrNull { stepError(it) != null }
        if (firstInvalid != null) {
            step = firstInvalid
            return
        }
        if (draftsError() != null) {
            step = STEPS.lastIndex
            return
        }
        val picked = place ?: return
        error = null
        submitting = true
        viewModelScope.launch {
            try {
                // Asked afresh: the looked-up zone may still be catching up with a place picked a moment ago.
                val eventZone = zoneOf(runCatching { api.timeZoneAt(picked.latitude, picked.longitude) }.getOrNull() ?: zone.id)
                val created = api.createEvent(
                    CreateEventInput(
                        eventType = eventType,
                        name = name.trim(),
                        latitude = picked.latitude,
                        longitude = picked.longitude,
                        displayName = placeName.trim(),
                        address = picked.address,
                        startTime = wallTimeToInstant(start, eventZone).toApiString(),
                        endTime = wallTimeToInstant(end, eventZone).toApiString(),
                        description = description.trim(),
                        criteria = drafts.filter { it.checked }.map { draftToInput(unitSystem, it.draft, suggested = it.suggested) },
                    ),
                )
                recordUnticked(picked, created.eventId)
                onCreated(created.eventId)
            } catch (e: Exception) {
                error = if (e is ApiError.Server && e.statusCode == 422 && e.detail != null) {
                    e.detail
                } else {
                    e.userMessage("Could not create the event. Check the fields and try again.")
                }
                submitting = false
            }
        }
    }

    /** Suggestions left unticked, recorded as turned down -- best effort; a failure costs nothing. */
    private fun recordUnticked(picked: PickedPlace, eventId: String) {
        val turnedDown = drafts.filter { it.suggested && !it.checked && draftError(it.draft) == null }
            .map { draftToInput(unitSystem, it.draft) }
            .map { SuggestionRejection(it.variable, it.operator, it.threshold) }
        if (turnedDown.isEmpty()) return
        com.weathernot.app.AppEnvironment.appScope.launch {
            runCatching { api.rejectSuggestions(eventType, picked.latitude, picked.longitude, start.monthValue, eventId, turnedDown) }
        }
    }
}
