package com.weathernot.kit

import com.weathernot.kit.format.CriterionWording
import com.weathernot.kit.format.blankDraft
import com.weathernot.kit.format.criterionSentence
import com.weathernot.kit.format.defaultWatch
import com.weathernot.kit.format.describeCriterion
import com.weathernot.kit.format.draftError
import com.weathernot.kit.format.draftToInput
import com.weathernot.kit.format.formatMargin
import com.weathernot.kit.format.formatValue
import com.weathernot.kit.format.formatVariableLabel
import com.weathernot.kit.format.hintFor
import com.weathernot.kit.format.sentVia
import com.weathernot.kit.format.sliderText
import com.weathernot.kit.format.thresholdInputError
import com.weathernot.kit.format.toDisplayString
import com.weathernot.kit.format.verdictLabel
import com.weathernot.kit.format.watchInputError
import com.weathernot.kit.models.Condition
import com.weathernot.kit.models.GoNoGo
import com.weathernot.kit.models.GridVariable
import com.weathernot.kit.models.Operator
import com.weathernot.kit.models.UnitSystem.IMPERIAL
import com.weathernot.kit.models.UnitSystem.METRIC
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FormatTest {
    @Test
    fun formatsValuesInEitherUnitSystem() {
        assertEquals("32°F", formatValue(IMPERIAL, GridVariable.TEMP_2M, 0.0))
        assertEquals("0.0°C", formatValue(METRIC, GridVariable.TEMP_2M, 0.0))
        assertEquals("20 mph", formatValue(IMPERIAL, GridVariable.WIND_GUST, 8.9408))
        assertEquals("8.9 m/s", formatValue(METRIC, GridVariable.WIND_GUST, 8.9408))
        assertEquals("40%", formatValue(METRIC, GridVariable.PRECIP_PROBABILITY, 0.4))
        assertEquals("0.10 in", formatValue(IMPERIAL, GridVariable.PRECIP_AMOUNT, 2.54))
        assertEquals("warning level", formatValue(IMPERIAL, GridVariable.NWS_ALERT, 3.0))
        assertEquals("no alert", formatValue(IMPERIAL, GridVariable.NWS_ALERT, 0.0))
        assertEquals("Wind gusts (mph)", formatVariableLabel(IMPERIAL, GridVariable.WIND_GUST))
        assertEquals("National Weather Service alerts", formatVariableLabel(IMPERIAL, GridVariable.NWS_ALERT))
        assertEquals("59", toDisplayString(IMPERIAL, GridVariable.TEMP_2M, 15.0))
    }

    @Test
    fun marginSaysWhichSideOfTheThreshold() {
        assertEquals("5 mph past the threshold", formatMargin(IMPERIAL, GridVariable.WIND_GUST, Operator.GTE, 20 * 0.44704, 25 * 0.44704))
        assertEquals("5 mph from the threshold", formatMargin(IMPERIAL, GridVariable.WIND_GUST, Operator.GTE, 20 * 0.44704, 15 * 0.44704))
        // A temperature gap converts by scale only: 15 °C limit, 11.8 °C forecast is 3.2 °C (~6 °F) past it.
        assertEquals("6°F past the threshold", formatMargin(IMPERIAL, GridVariable.TEMP_2M, Operator.LT, 15.0, 11.8))
        assertEquals("3.2°C past the threshold", formatMargin(METRIC, GridVariable.TEMP_2M, Operator.LT, 15.0, 11.8))
    }

    @Test
    fun wordsCriteriaLikeTheWebApp() {
        val gusts = CriterionWording(GridVariable.WIND_GUST, Operator.GTE, 20 * 0.44704)
        assertEquals("Warn me if wind gusts reach 20 mph or more.", criterionSentence(IMPERIAL, gusts))
        val withWatch = gusts.copy(threshold = 35 * 0.44704, watchThreshold = 20 * 0.44704)
        assertEquals("No-go if wind gusts reach 35 mph or more · watch from 20 mph.", criterionSentence(IMPERIAL, withWatch))
        assertEquals("Wind gusts reach 35 mph or more · watch from 20 mph", describeCriterion(IMPERIAL, withWatch))
        val cold = CriterionWording(
            GridVariable.TEMP_2M, Operator.LT, 15.0, minDurationHours = 3,
            also = Condition(GridVariable.PRECIP_PROBABILITY, Operator.GT, 0.4), informational = true,
        )
        assertEquals(
            "Just show me if the temperature drops below 59°F for 3+ hours and the chance of rain is above 40%.",
            criterionSentence(IMPERIAL, cold),
        )
    }

    @Test
    fun validatesThresholdsAndWatchLevels() {
        assertEquals("Threshold must be a number.", thresholdInputError(GridVariable.WIND_GUST, "abc"))
        assertEquals("A chance must be a percentage from 0 to 100.", thresholdInputError(GridVariable.PRECIP_PROBABILITY, "140"))
        assertNull(thresholdInputError(GridVariable.NWS_ALERT, "2"))
        assertEquals("The watch level has to be below the no-go level.", watchInputError(GridVariable.WIND_GUST, Operator.GTE, "20", "25"))
        assertEquals("The watch level has to be above the no-go level.", watchInputError(GridVariable.TEMP_2M, Operator.LT, "32", "30"))
        assertNull(watchInputError(GridVariable.WIND_GUST, Operator.GTE, "20", ""))
    }

    @Test
    fun draftsStartAtAMiddlingKeyPointAndSaveCanonical() {
        val draft = blankDraft(IMPERIAL, GridVariable.WIND_GUST)
        assertEquals("20", draft.threshold)
        assertEquals(Operator.GTE, draft.operator)
        assertEquals("3", blankDraft(IMPERIAL, GridVariable.NWS_ALERT).threshold)
        val input = draftToInput(IMPERIAL, draft.copy(watchThreshold = "15"))
        assertEquals(8.9408, input.threshold, 1e-9)
        assertEquals(15 * 0.44704, input.watchThreshold!!, 1e-9)
        assertEquals("Watch level: threshold must be a number.", draftError(draft.copy(watchThreshold = "x")))
    }

    @Test
    fun suggestsAWatchLevelAndHints() {
        assertEquals("15", defaultWatch(IMPERIAL, GridVariable.WIND_GUST, Operator.GTE, "20"))
        assertEquals("45", defaultWatch(IMPERIAL, GridVariable.TEMP_2M, Operator.LT, "32"))
        assertEquals("Tent sidewalls start to flap", hintFor(GridVariable.WIND_GUST, Operator.GTE, 25 * 0.44704))
        assertNull(hintFor(GridVariable.WIND_GUST, Operator.GTE, 1.0))
        assertEquals("0.3", sliderText(IMPERIAL, GridVariable.PRECIP_AMOUNT, 0.30000000000000004))
    }

    @Test
    fun labelsVerdictsAndDelivery() {
        assertEquals("No-go", verdictLabel(GoNoGo.NO_GO, "forecast"))
        assertEquals("High risk", verdictLabel(GoNoGo.NO_GO, "climatology"))
        assertEquals("Waiting for forecast", verdictLabel(GoNoGo.UNKNOWN, null))
        assertEquals("sent by email and text message", sentVia(listOf("email", "sms"), emptyList()))
        assertEquals("not sent — no email or phone on file", sentVia(emptyList(), emptyList()))
    }
}
