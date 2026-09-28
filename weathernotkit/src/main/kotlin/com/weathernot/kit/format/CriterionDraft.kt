package com.weathernot.kit.format

import com.weathernot.kit.models.Condition
import com.weathernot.kit.models.CriterionInput
import com.weathernot.kit.models.GridVariable
import com.weathernot.kit.models.Operator
import com.weathernot.kit.models.UnitSystem

// Port of the web app's frontend/src/lib/criterionDraft.ts: a criterion
// being added or edited, as its form holds it. Thresholds are the inputs'
// text in display units, converted to canonical only on save.

/** How long a threshold must hold, as the web app offers it (null: at any point). */
val DURATIONS: List<Int> = listOf(2, 3, 4, 6)

data class ConditionDraft(
    val variable: GridVariable,
    val operator: Operator,
    val threshold: String,
)

data class CriterionDraft(
    val variable: GridVariable,
    val operator: Operator,
    val threshold: String,
    /** The Watch (heads-up) level; "" for none. */
    val watchThreshold: String = "",
    val durationHours: Int? = null,
    val also: ConditionDraft? = null,
    val informational: Boolean = false,
    /** The part of the event it watches; null: the whole event. */
    val segmentId: String? = null,
) {
    val hasWatch: Boolean get() = watchThreshold.isNotBlank()
}

/** [variable] at a middling key point (20 mph gusts, 40% rain) -- or, for a named level, the most serious. */
private fun startingThreshold(unitSystem: UnitSystem, variable: GridVariable): String {
    val phrasing = phrasingOf(variable)
    val mark = if (phrasing.levels) phrasing.marks.last() else phrasing.marks[minOf(1, phrasing.marks.size - 1)]
    return toDisplayString(unitSystem, variable, mark.value)
}

/** A fresh criterion to fill in for [variable]: one level, at any point. */
fun blankDraft(unitSystem: UnitSystem, variable: GridVariable): CriterionDraft = CriterionDraft(
    variable = variable,
    operator = phrasingOf(variable).defaultOperator,
    threshold = startingThreshold(unitSystem, variable),
)

/** A saved or suggested criterion, to edit. */
fun draftFrom(unitSystem: UnitSystem, c: CriterionWording, segmentId: String? = null): CriterionDraft = CriterionDraft(
    variable = c.variable,
    operator = c.operator,
    threshold = toDisplayString(unitSystem, c.variable, c.threshold),
    watchThreshold = watchToDisplay(unitSystem, c.variable, c.watchThreshold),
    durationHours = c.minDurationHours,
    also = c.also?.let { ConditionDraft(it.variable, it.operator, toDisplayString(unitSystem, it.variable, it.threshold)) },
    informational = c.informational,
    segmentId = segmentId,
)

/** Why the draft can't be saved, or null if it can. */
fun draftError(d: CriterionDraft): String? {
    thresholdInputError(d.variable, d.threshold)?.let { return it }
    watchInputError(d.variable, d.operator, d.threshold, d.watchThreshold)?.let { return it }
    d.also?.let { also ->
        thresholdInputError(also.variable, also.threshold)?.let {
            return "Second condition: ${it.replaceFirstChar { c -> c.lowercase() }}"
        }
    }
    return null
}

/** The draft as the API takes it, canonical units. Call only once [draftError] is null. */
fun draftToInput(unitSystem: UnitSystem, d: CriterionDraft, suggested: Boolean = false): CriterionInput = CriterionInput(
    variable = d.variable,
    operator = d.operator,
    threshold = toCanonicalValue(unitSystem, d.variable, d.threshold.trim().toDouble()),
    watchThreshold = watchToCanonical(unitSystem, d.variable, d.watchThreshold),
    minDurationHours = d.durationHours,
    also = d.also?.let { Condition(it.variable, it.operator, toCanonicalValue(unitSystem, it.variable, it.threshold.trim().toDouble())) },
    informational = d.informational,
    segmentId = d.segmentId,
    suggested = suggested,
)
