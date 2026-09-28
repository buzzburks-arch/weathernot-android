package com.weathernot.kit

import com.weathernot.kit.models.Alert
import com.weathernot.kit.models.Criterion
import com.weathernot.kit.models.EvaluationResult
import com.weathernot.kit.models.Event
import com.weathernot.kit.models.EventSummary
import com.weathernot.kit.models.EventType
import com.weathernot.kit.models.GoNoGo
import com.weathernot.kit.models.GridVariable
import com.weathernot.kit.models.Operator
import com.weathernot.kit.models.Timeline
import com.weathernot.kit.models.UserPreferences
import com.weathernot.kit.models.WatchPhase
import com.weathernot.kit.networking.ApiClient
import com.weathernot.kit.networking.InMemoryTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Payloads captured from a running weathernot API (2026-09-28).
class ModelDecodingTest {
    private val json = ApiClient("http://x", InMemoryTokenStore(), HttpClient(MockEngine { respondOk() })).json

    @Test
    fun decodesAnEvent() {
        val event = json.decodeFromString<Event>(
            """{"event_id":"e554949a","owner_id":"android-dev@example.com","event_type":"wedding","name":"Sam & Alex's wedding",
            "latitude":40.7454,"longitude":-111.874,"display_name":"Liberty Park","address":"Liberty Park, Salt Lake City, Utah",
            "timezone":"America/Denver","series_id":null,"start_time":"2026-09-29T14:00:00Z","end_time":"2026-09-29T18:00:00Z",
            "created_at":"2026-09-28T11:07:46.266717Z","status":"active","recurrence_rule":null,"description":"","go_no_go":"no_go",
            "verdict_mode":"forecast","verdict_reason":null,"phase":"forecast","evaluating":false}""",
        )
        assertEquals(EventType.WEDDING, event.eventType)
        assertEquals("America/Denver", event.timezone)
        assertEquals(GoNoGo.NO_GO, event.goNoGo)
        assertEquals(WatchPhase.FORECAST, event.phase)
        assertTrue(event.isActive)
        assertNull(event.seriesId)
    }

    @Test
    fun unknownEnumValuesFallBackInsteadOfFailing() {
        val event = json.decodeFromString<Event>(
            """{"event_id":"e","event_type":"regatta","name":"Race","latitude":1,"longitude":2,
            "start_time":"2026-09-29T14:00:00Z","end_time":"2026-09-29T18:00:00Z","go_no_go":"maybe","phase":"someday"}""",
        )
        assertEquals(EventType.OTHER, event.eventType)
        assertEquals(GoNoGo.UNKNOWN, event.goNoGo)
        assertEquals(WatchPhase.FORECAST, event.phase)

        val criterion = json.decodeFromString<Criterion>(
            """{"criterion_id":"c","variable":"hail_size","operator":"gte","threshold":2.0}""",
        )
        assertEquals(GridVariable.UNKNOWN, criterion.variable)
    }

    @Test
    fun decodesCriteriaAndResults() {
        val criteria = json.decodeFromString<List<Criterion>>(
            """[{"criterion_id":"1364","event_id":"e","variable":"wind_gust","operator":"gte","threshold":8.9,"source":"ai_suggested",
            "active":true,"watch_threshold":4.5,"min_duration_hours":null,"also":null,"informational":false,"segment_id":null,"from_series":false},
            {"criterion_id":"7307","event_id":"e","variable":"temp_2m","operator":"lt","threshold":15.0,"source":"manual","active":true,
            "watch_threshold":null,"min_duration_hours":3,"also":{"variable":"wind_speed","operator":"gt","threshold":5},"informational":true,
            "segment_id":"s1","from_series":true}]""",
        )
        assertEquals(4.5, criteria[0].watchThreshold)
        assertEquals(Operator.LT, criteria[1].operator)
        assertEquals(3, criteria[1].minDurationHours)
        assertEquals(GridVariable.WIND_SPEED, criteria[1].also?.variable)
        assertEquals("s1", criteria[1].segmentId)

        val results = json.decodeFromString<List<EvaluationResult>>(
            """[{"result_id":"r1","event_id":"e","criterion_id":"1364","evaluated_at":"2026-09-28T11:07:46.638285Z","mode":"forecast",
            "value":3.5417223,"triggered":false,"confidence":"high","level":"go","frequency":null,"stability":null},
            {"result_id":"r2","event_id":"e","criterion_id":"9","evaluated_at":"2026-09-28T11:07:46Z","mode":"forecast",
            "value":null,"triggered":false,"confidence":null,"level":null,"frequency":null,"stability":null},
            {"result_id":"r3","event_id":"e","criterion_id":"9","evaluated_at":"2026-09-28T11:07:46Z","mode":"forecast",
            "value":20,"triggered":true,"confidence":"low","level":null}]""",
        )
        assertEquals(GoNoGo.GO, results[0].goNoGo)
        assertEquals(GoNoGo.UNKNOWN, results[1].goNoGo)
        assertEquals(GoNoGo.NO_GO, results[2].goNoGo)
    }

    @Test
    fun decodesSummaryTimelineAndAlerts() {
        val summary = json.decodeFromString<EventSummary>(
            """{"verdict":"no_go","mode":"forecast","headline":"No-go — cold","detail":"Temperature down to 53°F expected 8–10 am.",
            "basis":"Forecast, high confidence","checked_at":"2026-09-28T11:07:46.638285Z","driving_criterion_ids":["7307"],
            "confidence":"high","stability":null,"phase":"forecast","next_check_at":"2026-09-28T17:07:46.638285Z",
            "forecast_begins_at":"2026-09-15T14:00:00Z"}""",
        )
        assertEquals(listOf("7307"), summary.drivingCriterionIds)
        assertEquals("No-go — cold", summary.headline)

        val timeline = json.decodeFromString<Timeline>(
            """{"start":"2026-09-29T14:00:00Z","end":"2026-09-29T18:00:00Z","rows":[{"variable":"precip_probability",
            "cells":[{"time":"2026-09-29T12:00:00Z","value":0.0,"level":null},{"time":"2026-09-29T13:00:00Z","value":0.01,"level":"watch"}]}]}""",
        )
        assertEquals(GoNoGo.WATCH, timeline.rows[0].cells[1].level)

        val alert = json.decodeFromString<Alert>(
            """{"alert_id":"a","event_id":"e","criterion_id":"7307","result_id":"r","sent_at":"2026-09-28T11:07:46.638285Z",
            "reason":"sent via email","title":"Cold risk","body":"Temperature down to 53°F.","channels":["email"],"held":[],
            "level":"no_go","mode":"forecast","value":11.8,"confidence":"high","note":"","kind":"alert"}""",
        )
        assertEquals(GoNoGo.NO_GO, alert.level)
        assertEquals(listOf("email"), alert.channels)
    }

    @Test
    fun decodesPreferences() {
        val prefs = json.decodeFromString<UserPreferences>(
            """{"email":"android-dev@example.com","unit_system":"metric","phone_number":null,"sms_enabled":false,
            "event_types":["wedding","track_meet"],"event_types_asked":false,"event_types_hint_dismissed":false,"email_enabled":true,
            "quiet_hours_start":null,"quiet_hours_end":null,"timezone":null,"digest":false,"improvements":false}""",
        )
        assertEquals(listOf(EventType.WEDDING, EventType.TRACK_MEET), prefs.eventTypes)
        assertFalse(prefs.eventTypesAsked)
    }
}
