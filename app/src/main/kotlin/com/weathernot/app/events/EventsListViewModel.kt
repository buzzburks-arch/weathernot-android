package com.weathernot.app.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weathernot.app.ui.UiMessage
import com.weathernot.app.ui.userMessage
import com.weathernot.kit.models.Event
import com.weathernot.kit.models.EventType
import com.weathernot.kit.models.SeriesSummary
import com.weathernot.kit.networking.ApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant

class EventsListViewModel(private val api: ApiClient) : ViewModel() {
    var events by mutableStateOf<List<Event>?>(null)
        private set
    var series by mutableStateOf<List<SeriesSummary>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)
        private set
    var busyEventId by mutableStateOf<String?>(null)
        private set

    /** What "past" is measured against: when the list was last loaded. */
    var now by mutableStateOf(Instant.now())
        private set

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val anyEvaluating: Boolean
        get() = events.orEmpty().any { it.evaluating } || series.any { it.evaluating }

    init {
        refresh()
    }

    fun refresh(showSpinner: Boolean = false) {
        if (showSpinner) refreshing = true
        viewModelScope.launch {
            try {
                val eventsCall = async { api.listEvents() }
                val seriesCall = async { api.listSeries() }
                events = eventsCall.await()
                series = seriesCall.await()
                now = Instant.now()
                error = null
            } catch (e: Exception) {
                error = e.userMessage("Could not load events.")
            } finally {
                refreshing = false
            }
        }
    }

    /** The kinds of event they plan, most first -- a series counted once. */
    fun typesByUse(): List<EventType> {
        val counts = (events.orEmpty().filter { it.seriesId == null }.map { it.eventType } + series.map { it.eventType })
            .groupingBy { it }.eachCount()
        return counts.entries.sortedByDescending { it.value }.map { it.key }
    }

    val plannedCount: Int get() = events.orEmpty().count { it.seriesId == null } + series.size

    private fun act(event: Event, failure: String, block: suspend () -> Unit) {
        busyEventId = event.eventId
        error = null
        viewModelScope.launch {
            try {
                block()
                refresh()
            } catch (e: Exception) {
                error = e.userMessage(failure)
            } finally {
                busyEventId = null
            }
        }
    }

    fun togglePaused(event: Event) {
        val pausing = event.isActive
        act(event, "Could not update that event's status.") {
            api.setEventActive(event.eventId, !pausing)
            _messages.send(
                UiMessage(
                    text = "${if (pausing) "Paused monitoring for" else "Resumed monitoring for"} ${event.name}.",
                    actionLabel = "Undo",
                    onAction = { act(event, "Could not undo that.") { api.setEventActive(event.eventId, pausing) } },
                ),
            )
        }
    }

    fun toggleCancelled(event: Event) = act(event, "Could not update that event's status.") {
        api.setEventCancelled(event.eventId, !event.isCancelled)
    }

    fun duplicate(event: Event, onDuplicated: (String) -> Unit) {
        busyEventId = event.eventId
        viewModelScope.launch {
            try {
                onDuplicated(api.duplicateEvent(event.eventId).eventId)
            } catch (e: Exception) {
                error = e.userMessage("Could not duplicate that event.")
            } finally {
                busyEventId = null
            }
        }
    }

    fun delete(event: Event) = act(event, "Could not delete that event.") { api.deleteEvent(event.eventId) }

    fun deletePast(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val deleted = api.deletePastEvents()
                _messages.send(UiMessage("Deleted $deleted past event${if (deleted == 1) "" else "s"}."))
                refresh()
            } catch (e: Exception) {
                error = e.userMessage("Could not delete past events.")
            } finally {
                onDone()
            }
        }
    }
}
