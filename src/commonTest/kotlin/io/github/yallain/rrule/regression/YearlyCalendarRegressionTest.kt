package io.github.yallain.rrule.regression

import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.RuleRecurrence
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class YearlyCalendarRegressionTest {
    @Test
    fun yearlyMixedPositiveAndNegativeOrdinalWeekdays() {
        assertCase(
            "FREQ=YEARLY;COUNT=3;BYDAY=1TU,-1TH",
            listOf(
                local(1997, 12, 25),
                local(1998, 1, 6),
                local(1998, 12, 31),
            ),
        )
        assertCase(
            "FREQ=YEARLY;COUNT=3;BYDAY=13TU,-13TH",
            listOf(
                local(1997, 10, 2),
                local(1998, 3, 31),
                local(1998, 10, 8),
            ),
        )
    }

    @Test
    fun weekNumbersCanReachAcrossBothCalendarYearEdges() {
        assertCase(
            "FREQ=YEARLY;COUNT=3;BYWEEKNO=1;BYDAY=MO",
            listOf(local(1997, 12, 29), local(1999, 1, 4), local(2000, 1, 3)),
        )
        assertCase(
            "FREQ=YEARLY;COUNT=3;BYWEEKNO=52;BYDAY=SU",
            listOf(local(1997, 12, 28), local(1998, 12, 27), local(2000, 1, 2)),
        )
        assertCase(
            "FREQ=YEARLY;COUNT=3;BYWEEKNO=-1;BYDAY=SU",
            listOf(local(1997, 12, 28), local(1999, 1, 3), local(2000, 1, 2)),
        )
    }

    @Test
    fun negativeYearDaysMatchTheirPositiveCounterpartsAcrossLeapBoundaries() {
        assertCase(
            "FREQ=YEARLY;COUNT=4;BYYEARDAY=-365,-266,-166,-1",
            listOf(
                local(1997, 12, 31),
                local(1998, 1, 1),
                local(1998, 4, 10),
                local(1998, 7, 19),
            ),
        )
    }

    private fun assertCase(rule: String, expected: List<RecurrenceDateTime.Floating>) {
        val recurrence = RuleRecurrence(
            local(1997, 9, 2),
            RecurrenceRuleParser.parse(rule),
        )
        assertEquals(expected, recurrence.occurrences().toList(), rule)
    }

    private fun local(year: Int, month: Int, day: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, 9, 0))
}
