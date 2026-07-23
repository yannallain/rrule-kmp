package io.github.yallain.rrule.differential

import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.RuleRecurrence

/**
 * Diagnostic adapter for occurrence lists exported by an independent RFC 5545 implementation.
 *
 * External implementations intentionally run outside the library build. Their output is supplied here so
 * a difference can be reviewed against RFC 5545 instead of automatically treated as a failure.
 */
internal object DifferentialHarness {
    fun compare(
        case: DifferentialCase,
        referenceEngine: String,
        referenceOccurrences: List<RecurrenceDateTime>,
    ): DifferentialReport {
        val kotlinOccurrences = RuleRecurrence(
            case.start,
            RecurrenceRuleParser.parse(case.ruleValue),
        ).occurrences().take(case.occurrenceCount).toList()
        val differences = (0 until maxOf(kotlinOccurrences.size, referenceOccurrences.size)).mapNotNull { index ->
            val kotlinValue = kotlinOccurrences.getOrNull(index)
            val referenceValue = referenceOccurrences.getOrNull(index)
            if (kotlinValue == referenceValue) null else DifferentialDifference(index, kotlinValue, referenceValue)
        }
        return DifferentialReport(case.name, referenceEngine, differences)
    }
}

internal data class DifferentialCase(
    val name: String,
    val start: RecurrenceDateTime,
    val ruleValue: String,
    val occurrenceCount: Int,
)

internal data class DifferentialReport(
    val caseName: String,
    val referenceEngine: String,
    val differences: List<DifferentialDifference>,
) {
    val matches: Boolean get() = differences.isEmpty()
}

internal data class DifferentialDifference(
    val index: Int,
    val kotlinValue: RecurrenceDateTime?,
    val referenceValue: RecurrenceDateTime?,
)
