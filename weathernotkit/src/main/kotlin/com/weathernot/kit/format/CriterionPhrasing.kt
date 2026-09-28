package com.weathernot.kit.format

import com.weathernot.kit.models.Condition
import com.weathernot.kit.models.Criterion
import com.weathernot.kit.models.GridVariable
import com.weathernot.kit.models.Operator
import com.weathernot.kit.models.SeriesCriterion
import com.weathernot.kit.models.SuggestedCriterion
import com.weathernot.kit.models.UnitSystem
import kotlin.math.max
import kotlin.math.roundToInt

// Port of the web app's frontend/src/lib/criterionPhrasing.ts: criteria as
// sentences ("Warn me if wind gusts reach 20 mph or more"). The operator
// comes from each variable's wording; only temperature offers a choice.

data class Range(val min: Double, val max: Double, val step: Double)

/** A key point along a variable's range (canonical units) and what it means in practice. */
data class Mark(val value: Double, val hint: String)

data class Direction(val operator: Operator, val label: String)

class VariablePhrasing(
    /** What the sentence is about, lower-case: "wind gusts". */
    val subject: String,
    val defaultOperator: Operator,
    /** Operators offered as a choice (temperature only). */
    val directions: List<Direction> = emptyList(),
    /** Words before and after the value, per operator. */
    val words: Map<Operator, Pair<String, String>>,
    /** The slider's range, in display units. */
    val range: Map<UnitSystem, Range>,
    val marks: List<Mark>,
    /** Chosen from its key points alone -- a named level, not a number. */
    val levels: Boolean = false,
)

private const val MPH = 0.44704
private const val INCH = 25.4
private fun fahrenheit(f: Double) = (f - 32) * 5 / 9

private fun words(gt: Pair<String, String>, gte: Pair<String, String>, lt: Pair<String, String>, lte: Pair<String, String>) =
    mapOf(Operator.GT to gt, Operator.GTE to gte, Operator.LT to lt, Operator.LTE to lte)

private val RISING = words("go above" to "", "reach" to "or more", "stay below" to "", "stay at" to "or less")
private val RISES = words("goes above" to "", "reaches" to "or more", "stays below" to "", "stays at" to "or less")
private val CHANCE = words("is above" to "", "is" to "or more", "is below" to "", "is" to "or less")
private val FALLS = words("rises above" to "", "reaches" to "or higher", "drops below" to "", "drops to" to "or lower")

private fun both(range: Range) = mapOf(UnitSystem.IMPERIAL to range, UnitSystem.METRIC to range)
private fun ranges(imperial: Range, metric: Range) = mapOf(UnitSystem.IMPERIAL to imperial, UnitSystem.METRIC to metric)
private val PERCENT_RANGE = Range(0.0, 100.0, 5.0)

val PHRASING: Map<GridVariable, VariablePhrasing> = mapOf(
    GridVariable.WIND_GUST to VariablePhrasing(
        subject = "wind gusts",
        defaultOperator = Operator.GTE,
        words = RISING,
        range = ranges(Range(0.0, 60.0, 1.0), Range(0.0, 27.0, 0.5)),
        marks = listOf(
            Mark(15 * MPH, "Umbrellas and loose paper start to go"),
            Mark(20 * MPH, "Tent sidewalls start to flap"),
            Mark(35 * MPH, "Most rental tents must come down"),
            Mark(50 * MPH, "Damaging gusts — branches down"),
        ),
    ),
    GridVariable.WIND_SPEED to VariablePhrasing(
        subject = "sustained wind",
        defaultOperator = Operator.GTE,
        words = RISES,
        range = ranges(Range(0.0, 50.0, 1.0), Range(0.0, 22.0, 0.5)),
        marks = listOf(
            Mark(10 * MPH, "A steady breeze — flags fly, napkins blow away"),
            Mark(20 * MPH, "Hard to keep décor and signs up; drones struggle"),
            Mark(30 * MPH, "Hard to walk into; cranes and lifts usually stop"),
        ),
    ),
    GridVariable.PRECIP_PROBABILITY to VariablePhrasing(
        subject = "the chance of rain",
        defaultOperator = Operator.GT,
        words = CHANCE,
        range = both(PERCENT_RANGE),
        marks = listOf(
            Mark(0.2, "A slight chance — worth watching"),
            Mark(0.4, "A real chance — have covers or a plan B ready"),
            Mark(0.7, "Rain is likely"),
        ),
    ),
    GridVariable.LIGHTNING_PROBABILITY to VariablePhrasing(
        subject = "the chance of lightning",
        defaultOperator = Operator.GTE,
        words = CHANCE,
        range = both(PERCENT_RANGE),
        marks = listOf(
            Mark(0.1, "Storms possible in the area"),
            Mark(0.2, "Common cut-off for outdoor sports"),
            Mark(0.4, "Thunderstorms likely — plan to shelter"),
        ),
    ),
    GridVariable.PRECIP_AMOUNT to VariablePhrasing(
        subject = "rainfall",
        defaultOperator = Operator.GTE,
        words = RISES,
        range = ranges(Range(0.0, 1.0, 0.01), Range(0.0, 25.0, 0.5)),
        marks = listOf(
            Mark(0.01 * INCH, "Any measurable rain"),
            Mark(0.1 * INCH, "Light rain — umbrellas out"),
            Mark(0.3 * INCH, "Heavy rain — puddles and soggy grass"),
        ),
    ),
    GridVariable.TEMP_2M to VariablePhrasing(
        subject = "the temperature",
        defaultOperator = Operator.LT,
        directions = listOf(Direction(Operator.LT, "drops below"), Direction(Operator.GT, "rises above")),
        words = FALLS,
        range = ranges(Range(-10.0, 110.0, 1.0), Range(-25.0, 45.0, 0.5)),
        marks = listOf(
            Mark(fahrenheit(32.0), "Freezing — frost and ice"),
            Mark(fahrenheit(45.0), "Cold for guests — heaters and layers"),
            Mark(fahrenheit(90.0), "Hot — shade and water needed"),
        ),
    ),
    GridVariable.DEW_POINT to VariablePhrasing(
        subject = "the dew point",
        defaultOperator = Operator.GTE,
        words = RISES,
        range = ranges(Range(20.0, 85.0, 1.0), Range(-5.0, 30.0, 0.5)),
        marks = listOf(
            Mark(fahrenheit(55.0), "Comfortable to most people"),
            Mark(fahrenheit(65.0), "Muggy — sticky and uncomfortable"),
            Mark(fahrenheit(70.0), "Oppressive — heat stress with any exertion"),
        ),
    ),
    GridVariable.WIND_CHILL to VariablePhrasing(
        subject = "the wind chill",
        defaultOperator = Operator.LT,
        words = FALLS,
        range = ranges(Range(-30.0, 50.0, 1.0), Range(-35.0, 10.0, 0.5)),
        marks = listOf(
            Mark(fahrenheit(32.0), "Chilly — layers, hats and hand warmers"),
            Mark(fahrenheit(15.0), "Cold — exposed skin hurts; limit time outside"),
            Mark(fahrenheit(0.0), "Frostbite possible in about 30 minutes"),
        ),
    ),
    GridVariable.SNOWFALL to VariablePhrasing(
        subject = "snowfall",
        defaultOperator = Operator.GTE,
        words = words("goes above" to "an hour", "reaches" to "an hour or more", "stays below" to "an hour", "stays at" to "an hour or less"),
        range = ranges(Range(0.0, 3.0, 0.1), Range(0.0, 8.0, 0.5)),
        marks = listOf(
            Mark(0.1 * INCH, "Flurries — a dusting"),
            Mark(0.5 * INCH, "Steady snow — roads and walkways get slick"),
            Mark(1 * INCH, "Heavy snow — travel gets hard fast"),
        ),
    ),
    GridVariable.SMOKE to VariablePhrasing(
        subject = "wildfire smoke",
        defaultOperator = Operator.GTE,
        words = RISES,
        range = both(Range(0.0, 200.0, 5.0)),
        marks = listOf(
            Mark(12.1, "Moderate — unusually sensitive people may notice"),
            Mark(35.5, "Unhealthy for sensitive groups — kids, asthma, older adults"),
            Mark(55.5, "Unhealthy for everyone — cut back on exertion"),
            Mark(150.5, "Very unhealthy — move activities indoors"),
        ),
    ),
    GridVariable.NWS_ALERT to VariablePhrasing(
        subject = "National Weather Service alerts",
        defaultOperator = Operator.GTE,
        words = words("go above" to "", "reach" to "or higher", "stay below" to "", "stay at" to "or lower"),
        range = both(Range(1.0, 3.0, 1.0)),
        marks = listOf(
            Mark(1.0, "Advisory — a nuisance worth planning around (heat, wind, dense fog)"),
            Mark(2.0, "Watch — conditions are right for dangerous weather; be ready to act"),
            Mark(3.0, "Warning — dangerous weather is happening or about to"),
        ),
        levels = true,
    ),
    GridVariable.DARKNESS to VariablePhrasing(
        subject = "darkness",
        defaultOperator = Operator.GTE,
        words = words(
            "covers more than" to "of the event",
            "covers" to "or more of the event",
            "covers less than" to "of the event",
            "covers" to "or less of the event",
        ),
        range = both(Range(0.0, 240.0, 15.0)),
        marks = listOf(
            Mark(1.0, "Any of it after sunset — you'll need lights"),
            Mark(30.0, "Half an hour or more in the dark — plan lighting and a safe exit"),
            Mark(120.0, "Mostly after dark — a lit venue or an earlier start"),
        ),
    ),
    GridVariable.HEAT_INDEX to VariablePhrasing(
        subject = "the heat index",
        defaultOperator = Operator.GTE,
        words = RISES,
        range = ranges(Range(70.0, 125.0, 1.0), Range(20.0, 52.0, 0.5)),
        marks = listOf(
            Mark(fahrenheit(80.0), "Caution — fatigue with long exertion"),
            Mark(fahrenheit(90.0), "Extreme caution — heat cramps and exhaustion possible"),
            Mark(fahrenheit(103.0), "Danger — heat exhaustion likely; limit exertion"),
        ),
    ),
    GridVariable.UNKNOWN to VariablePhrasing(
        subject = "this condition",
        defaultOperator = Operator.GTE,
        words = RISES,
        range = both(Range(0.0, 100.0, 1.0)),
        marks = listOf(Mark(0.0, "")),
    ),
)

fun phrasingOf(variable: GridVariable): VariablePhrasing = PHRASING.getValue(variable)

/** The operator a criterion switched to [variable] gets: [current] if offered, else the default. */
fun operatorFor(variable: GridVariable, current: Operator? = null): Operator {
    val phrasing = phrasingOf(variable)
    if (current != null && phrasing.directions.any { it.operator == current }) return current
    return phrasing.defaultOperator
}

/** "wind gusts reach 20 mph or more" -- the rest of a "Warn me if ..." sentence. */
fun criterionClause(unitSystem: UnitSystem, variable: GridVariable, operator: Operator, threshold: Double): String {
    val phrasing = phrasingOf(variable)
    val (before, after) = phrasing.words.getValue(operator)
    return listOf(phrasing.subject, before, formatValue(unitSystem, variable, threshold), after)
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}

/** Enough of a saved or suggested criterion to word it. */
data class CriterionWording(
    val variable: GridVariable,
    val operator: Operator,
    val threshold: Double,
    val watchThreshold: Double? = null,
    val minDurationHours: Int? = null,
    val also: Condition? = null,
    val informational: Boolean = false,
)

fun Criterion.wording() = CriterionWording(variable, operator, threshold, watchThreshold, minDurationHours, also, informational)
fun SeriesCriterion.wording() = CriterionWording(variable, operator, threshold, watchThreshold, minDurationHours, also, informational)
fun SuggestedCriterion.wording() = CriterionWording(variable, operator, threshold, watchThreshold)

/** " for 3+ hours", or "" at any point. */
fun durationWords(hours: Int?): String = if (hours == null || hours == 0) "" else " for $hours+ hour${if (hours == 1) "" else "s"}"

private fun fullClause(unitSystem: UnitSystem, c: CriterionWording): String {
    var text = criterionClause(unitSystem, c.variable, c.operator, c.threshold) + durationWords(c.minDurationHours)
    c.also?.let { text += " and ${criterionClause(unitSystem, it.variable, it.operator, it.threshold)}" }
    c.watchThreshold?.let { text += " · watch from ${formatValue(unitSystem, c.variable, it)}" }
    return text
}

/** "Wind gusts reach 35 mph or more · watch from 20 mph" -- a criterion on its own, as in a list. */
fun describeCriterion(unitSystem: UnitSystem, c: CriterionWording): String =
    fullClause(unitSystem, c).replaceFirstChar { it.uppercase() }

/** "Warn me if", "No-go if" once it has a Watch level, "Just show me if" when informational. */
fun leadWords(watchThreshold: Double?, informational: Boolean): String = when {
    informational -> "Just show me if"
    watchThreshold == null -> "Warn me if"
    else -> "No-go if"
}

/** "Warn me if wind gusts reach 20 mph or more." */
fun criterionSentence(unitSystem: UnitSystem, c: CriterionWording): String =
    "${leadWords(c.watchThreshold, c.informational)} ${fullClause(unitSystem, c)}."

fun canonicalOf(unitSystem: UnitSystem, variable: GridVariable, text: String): Double? =
    text.trim().toDoubleOrNull()?.let { toCanonicalValue(unitSystem, variable, it) }

/**
 * A starting Watch level for a No-go threshold (display-unit text): the
 * nearest key point reached before it, else a quarter of the range before it.
 */
fun defaultWatch(unitSystem: UnitSystem, variable: GridVariable, operator: Operator, thresholdText: String): String {
    val threshold = canonicalOf(unitSystem, variable, thresholdText) ?: return ""
    val up = operator.isUp
    val before = phrasingOf(variable).marks.filter { if (up) it.value < threshold else it.value > threshold }
    val mark = if (up) before.lastOrNull() else before.firstOrNull()
    if (mark != null) return toDisplayString(unitSystem, variable, mark.value)
    val (min, maxValue, step) = phrasingOf(variable).range.getValue(unitSystem)
    val shift = max(step, ((maxValue - min) / 4 / step).roundToInt() * step)
    val shifted = thresholdText.trim().toDouble() + if (up) -shift else shift
    return trimNumber(shifted)
}

/** The hint for the key point a threshold has reached, or null short of the first. */
fun hintFor(variable: GridVariable, operator: Operator, threshold: Double): String? {
    val marks = phrasingOf(variable).marks
    val near = 1e-6
    return if (operator.isUp) {
        marks.lastOrNull { it.value <= threshold + near }?.hint
    } else {
        marks.firstOrNull { it.value >= threshold - near }?.hint
    }?.takeIf { it.isNotEmpty() }
}

/** The slider's value for a display-unit text, clamped into its range. */
fun sliderValue(unitSystem: UnitSystem, variable: GridVariable, text: String): Double {
    val (min, maxValue) = phrasingOf(variable).range.getValue(unitSystem)
    val value = text.trim().toDoubleOrNull() ?: return min
    return value.coerceIn(min, maxValue)
}

/** A slider position as input text: "20", "0.5", "0.25" -- no float noise. */
fun sliderText(unitSystem: UnitSystem, variable: GridVariable, position: Double): String {
    val step = phrasingOf(variable).range.getValue(unitSystem).step
    val snapped = (position / step).roundToInt() * step
    return trimNumber(snapped)
}

/** 20.0 -> "20", 0.30000000000000004 -> "0.3" (JavaScript's `String(number)` for ordinary values). */
fun trimNumber(value: Double): String {
    val rounded = fixed(value, 6).trimEnd('0').trimEnd('.')
    return if (rounded == "-0") "0" else rounded
}
