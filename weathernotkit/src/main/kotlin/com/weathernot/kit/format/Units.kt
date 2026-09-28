package com.weathernot.kit.format

import com.weathernot.kit.models.GridVariable
import com.weathernot.kit.models.GridVariable.DARKNESS
import com.weathernot.kit.models.GridVariable.DEW_POINT
import com.weathernot.kit.models.GridVariable.HEAT_INDEX
import com.weathernot.kit.models.GridVariable.LIGHTNING_PROBABILITY
import com.weathernot.kit.models.GridVariable.NWS_ALERT
import com.weathernot.kit.models.GridVariable.PRECIP_AMOUNT
import com.weathernot.kit.models.GridVariable.PRECIP_PROBABILITY
import com.weathernot.kit.models.GridVariable.SMOKE
import com.weathernot.kit.models.GridVariable.SNOWFALL
import com.weathernot.kit.models.GridVariable.TEMP_2M
import com.weathernot.kit.models.GridVariable.UNKNOWN
import com.weathernot.kit.models.GridVariable.WIND_CHILL
import com.weathernot.kit.models.GridVariable.WIND_GUST
import com.weathernot.kit.models.GridVariable.WIND_SPEED
import com.weathernot.kit.models.Operator
import com.weathernot.kit.models.UnitSystem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Port of the web app's frontend/src/units.ts. The API's thresholds and
// values are always canonical (°C, m/s, mm, 0-1 chances); convert at this
// edge only, never store a display-unit value. Chances are shown and
// entered as whole percentages in both systems ("40" means 0.4).

private class Conversion(
    val label: String,
    val toDisplay: (Double) -> Double,
    val toCanonical: (Double) -> Double,
    val decimals: Int,
)

private val identity: (Double) -> Double = { it }

private val FAHRENHEIT = Conversion("°F", { it * 9 / 5 + 32 }, { (it - 32) * 5 / 9 }, 0)
private val CELSIUS = Conversion("°C", identity, identity, 1)
private val MPH = Conversion(" mph", { it / 0.44704 }, { it * 0.44704 }, 0)
private val METERS_PER_SECOND = Conversion(" m/s", identity, identity, 1)
private val INCHES = Conversion(" in", { it / 25.4 }, { it * 25.4 }, 2)
private val MILLIMETERS = Conversion(" mm", identity, identity, 0)
private val PERCENT = Conversion("%", { it * 100 }, { it / 100 }, 0)
private val SNOW_INCHES = Conversion(" in", { it / 25.4 }, { it * 25.4 }, 1)
private val SNOW_CENTIMETERS = Conversion(" cm", { it / 10 }, { it * 10 }, 1)
private val MICROGRAMS = Conversion(" µg/m³", identity, identity, 1)
private val MINUTES = Conversion(" min", identity, identity, 0)
private val LEVEL = Conversion("", identity, identity, 0)
private val PLAIN = Conversion("", identity, identity, 1)

val NWS_LEVEL_NAMES: Map<Int, String> = mapOf(1 to "advisory", 2 to "watch", 3 to "warning")

private fun conversionFor(unitSystem: UnitSystem, variable: GridVariable): Conversion {
    val metric = unitSystem == UnitSystem.METRIC
    return when (variable) {
        PRECIP_PROBABILITY, LIGHTNING_PROBABILITY -> PERCENT
        PRECIP_AMOUNT -> if (metric) MILLIMETERS else INCHES
        WIND_SPEED, WIND_GUST -> if (metric) METERS_PER_SECOND else MPH
        TEMP_2M, HEAT_INDEX, DEW_POINT, WIND_CHILL -> if (metric) CELSIUS else FAHRENHEIT
        SNOWFALL -> if (metric) SNOW_CENTIMETERS else SNOW_INCHES
        SMOKE -> MICROGRAMS
        NWS_ALERT -> LEVEL
        DARKNESS -> MINUTES
        UNKNOWN -> PLAIN
    }
}

/** JavaScript's `toFixed`: rounds half away from zero, no grouping. */
internal fun fixed(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)

fun unitLabel(unitSystem: UnitSystem, variable: GridVariable): String = conversionFor(unitSystem, variable).label.trim()

fun toDisplayValue(unitSystem: UnitSystem, variable: GridVariable, canonical: Double): Double =
    conversionFor(unitSystem, variable).toDisplay(canonical)

fun toCanonicalValue(unitSystem: UnitSystem, variable: GridVariable, display: Double): Double =
    conversionFor(unitSystem, variable).toCanonical(display)

/** The rounded display number alone, no unit -- to pre-fill an input. */
fun toDisplayString(unitSystem: UnitSystem, variable: GridVariable, canonical: Double): String {
    val conversion = conversionFor(unitSystem, variable)
    return fixed(conversion.toDisplay(canonical), conversion.decimals)
}

/** A canonical value as the viewer should see it: `formatValue(IMPERIAL, TEMP_2M, 0.0)` is "32°F". */
fun formatValue(unitSystem: UnitSystem, variable: GridVariable, canonical: Double): String {
    if (variable == NWS_ALERT) {
        val level = canonical.roundToInt()
        return if (level == 0) "no alert" else "${NWS_LEVEL_NAMES[level] ?: "alert"} level"
    }
    val conversion = conversionFor(unitSystem, variable)
    return toDisplayString(unitSystem, variable, canonical) + conversion.label
}

/** "Wind gusts (mph)", "Chance of rain (%)". */
fun formatVariableLabel(unitSystem: UnitSystem, variable: GridVariable): String {
    val unit = unitLabel(unitSystem, variable)
    return if (unit.isEmpty()) variable.label else "${variable.label} ($unit)"
}

/**
 * Signed distance from a value to the triggering side of a threshold:
 * positive is already past it, negative still clear.
 */
fun marginToThreshold(operator: Operator, threshold: Double, value: Double): Double =
    if (operator.isUp) value - threshold else threshold - value

/**
 * A difference between two canonical values, in display units: a 3.2 °C
 * gap is "6°F", not the 38°F that converting it as a temperature gives.
 */
fun formatDifference(unitSystem: UnitSystem, variable: GridVariable, canonicalDifference: Double): String {
    val conversion = conversionFor(unitSystem, variable)
    val display = conversion.toDisplay(canonicalDifference) - conversion.toDisplay(0.0)
    return fixed(display, conversion.decimals) + conversion.label
}

/** "5 mph past the threshold" / "5 mph from the threshold". */
fun formatMargin(unitSystem: UnitSystem, variable: GridVariable, operator: Operator, threshold: Double, value: Double): String {
    val margin = marginToThreshold(operator, threshold, value)
    val magnitude = formatDifference(unitSystem, variable, abs(margin))
    return if (margin >= 0) "$magnitude past the threshold" else "$magnitude from the threshold"
}

fun isProbability(variable: GridVariable): Boolean = variable == PRECIP_PROBABILITY || variable == LIGHTNING_PROBABILITY

/** A Watch-level input's text as the API takes it: canonical, or null (blank: no Watch level). */
fun watchToCanonical(unitSystem: UnitSystem, variable: GridVariable, text: String): Double? =
    if (text.isBlank()) null else text.trim().toDoubleOrNull()?.let { toCanonicalValue(unitSystem, variable, it) }

fun watchToDisplay(unitSystem: UnitSystem, variable: GridVariable, watch: Double?): String =
    if (watch == null) "" else toDisplayString(unitSystem, variable, watch)

/** Why a threshold input's text (display units) can't be saved, or null if it can. */
fun thresholdInputError(variable: GridVariable, text: String): String? {
    val value = text.trim().toDoubleOrNull() ?: return "Threshold must be a number."
    if (isProbability(variable) && (value < 0 || value > 100)) return "A chance must be a percentage from 0 to 100."
    if (variable == NWS_ALERT && value !in listOf(1.0, 2.0, 3.0)) return "Pick an NWS alert level: advisory, watch or warning."
    if ((variable == DARKNESS || variable == SNOWFALL || variable == SMOKE) && value < 0) return "This can't be negative."
    return null
}

/**
 * Why a Watch-level input can't go with this No-go threshold, or null if
 * it can (blank is no Watch level). It has to be reached first.
 */
fun watchInputError(variable: GridVariable, operator: Operator, thresholdText: String, watchText: String): String? {
    if (watchText.isBlank()) return null
    thresholdInputError(variable, watchText)?.let { return "Watch level: ${it.replaceFirstChar { c -> c.lowercase() }}" }
    val watch = watchText.trim().toDouble()
    val threshold = thresholdText.trim().toDoubleOrNull() ?: return null
    return when {
        operator.isUp && watch >= threshold -> "The watch level has to be below the no-go level."
        !operator.isUp && watch <= threshold -> "The watch level has to be above the no-go level."
        else -> null
    }
}
