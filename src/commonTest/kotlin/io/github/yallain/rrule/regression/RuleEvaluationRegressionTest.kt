package io.github.yallain.rrule.regression

import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.RuleRecurrence
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleEvaluationRegressionTest {
    @Test
    fun week53SkipsYearsThatDoNotContainIt() {
        assertRule(
            "FREQ=YEARLY;COUNT=3;BYWEEKNO=53;BYDAY=MO",
            local(1998, 12, 28),
            local(2004, 12, 27),
            local(2009, 12, 28),
        )
    }

    @Test
    fun yearlyTimePartsExpandInChronologicalOrder() {
        assertRule(
            "FREQ=YEARLY;COUNT=3;BYHOUR=6,18",
            local(1997, 9, 2, 18),
            local(1998, 9, 2, 6),
            local(1998, 9, 2, 18),
        )
        assertRule(
            "FREQ=YEARLY;COUNT=3;BYMINUTE=6,18",
            local(1997, 9, 2, 9, 6),
            local(1997, 9, 2, 9, 18),
            local(1998, 9, 2, 9, 6),
        )
        assertRule(
            "FREQ=YEARLY;COUNT=3;BYSECOND=6,18",
            local(1997, 9, 2, 9, 0, 6),
            local(1997, 9, 2, 9, 0, 18),
            local(1998, 9, 2, 9, 0, 6),
        )
        assertRule(
            "FREQ=YEARLY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18;BYSECOND=6,18",
            local(1997, 9, 2, 18, 6, 6),
            local(1997, 9, 2, 18, 6, 18),
            local(1997, 9, 2, 18, 18, 6),
        )
    }

    @Test
    fun setPositionIsAppliedAfterAllPeriodExpansions() {
        assertRule(
            "FREQ=YEARLY;COUNT=3;BYMONTHDAY=15;BYHOUR=6,18;BYSETPOS=3,-3",
            local(1997, 11, 15, 18),
            local(1998, 2, 15, 6),
            local(1998, 11, 15, 18),
        )
        assertRule(
            "FREQ=HOURLY;COUNT=3;BYMINUTE=15,45;BYSECOND=15,45;BYSETPOS=3,-3",
            local(1997, 9, 2, 9, 15, 45),
            local(1997, 9, 2, 9, 45, 15),
            local(1997, 9, 2, 10, 15, 45),
        )
        assertRule(
            "FREQ=MINUTELY;COUNT=3;BYSECOND=15,30,45;BYSETPOS=3,-3",
            local(1997, 9, 2, 9, 0, 15),
            local(1997, 9, 2, 9, 0, 45),
            local(1997, 9, 2, 9, 1, 15),
        )
    }

    @Test
    fun smallerFrequenciesHonorYearDayFilters() {
        assertRule(
            "FREQ=HOURLY;COUNT=4;BYYEARDAY=1,100,200,365",
            local(1997, 12, 31, 0),
            local(1997, 12, 31, 1),
            local(1997, 12, 31, 2),
            local(1997, 12, 31, 3),
        )
        assertRule(
            "FREQ=MINUTELY;COUNT=4;BYYEARDAY=-365,-266,-166,-1",
            local(1997, 12, 31, 0, 0),
            local(1997, 12, 31, 0, 1),
            local(1997, 12, 31, 0, 2),
            local(1997, 12, 31, 0, 3),
        )
    }

    @Test
    fun subdailyFrequenciesHonorNegativeMonthDayFiltersAcrossMonthBoundaries() {
        assertRuleFrom(
            start = local(1997, 9, 30, 22),
            ruleValue = "FREQ=HOURLY;COUNT=4;BYMONTHDAY=-1",
            local(1997, 9, 30, 22),
            local(1997, 9, 30, 23),
            local(1997, 10, 31, 0),
            local(1997, 10, 31, 1),
        )
        assertRuleFrom(
            start = local(1997, 9, 30, 23, 58),
            ruleValue = "FREQ=MINUTELY;COUNT=4;BYMONTHDAY=-1",
            local(1997, 9, 30, 23, 58),
            local(1997, 9, 30, 23, 59),
            local(1997, 10, 31, 0, 0),
            local(1997, 10, 31, 0, 1),
        )
        assertRuleFrom(
            start = local(1997, 9, 30, 23, 59, 58),
            ruleValue = "FREQ=SECONDLY;COUNT=4;BYMONTHDAY=-1",
            local(1997, 9, 30, 23, 59, 58),
            local(1997, 9, 30, 23, 59, 59),
            local(1997, 10, 31, 0, 0, 0),
            local(1997, 10, 31, 0, 0, 1),
        )
    }

    @Test
    fun secondlyRulesJumpAcrossSparseCalendarFiltersWithoutLosingIntervalAlignment() {
        assertRule(
            "FREQ=SECONDLY;COUNT=3;BYMONTH=1,3",
            local(1998, 1, 1, 0, 0, 0),
            local(1998, 1, 1, 0, 0, 1),
            local(1998, 1, 1, 0, 0, 2),
        )
        assertRule(
            "FREQ=SECONDLY;INTERVAL=7;COUNT=3;BYMONTH=1,3",
            local(1998, 1, 1, 0, 0, 6),
            local(1998, 1, 1, 0, 0, 13),
            local(1998, 1, 1, 0, 0, 20),
        )
    }

    @Test
    fun reverseSecondlyQueryJumpsAcrossAnExcludedCalendarRange() {
        val recurrence = RuleRecurrence(
            start = local(1997, 9, 2),
            rule = RecurrenceRuleParser.parse("FREQ=SECONDLY;BYMONTH=1,3"),
        )

        assertEquals(local(1998, 3, 31, 23, 59, 59), recurrence.before(local(1999, 1, 1, 0, 0, 0)))

        val alignedRecurrence = RuleRecurrence(
            start = local(1997, 9, 2),
            rule = RecurrenceRuleParser.parse(
                "FREQ=SECONDLY;INTERVAL=7;BYMONTH=1,3;BYDAY=MO;BYHOUR=0;BYMINUTE=0;BYSECOND=3",
            ),
        )
        val upperBound = local(1999, 1, 1, 0, 0, 0)
        val forwardOracle = alignedRecurrence.between(local(1997, 9, 2), upperBound)

        assertEquals(forwardOracle.last(), alignedRecurrence.before(upperBound))
    }

    @Test
    fun untilIsAnInclusiveRfcBoundary() {
        assertRule(
            "FREQ=DAILY;UNTIL=19970905T080000",
            local(1997, 9, 2),
            local(1997, 9, 3),
            local(1997, 9, 4),
        )
        assertRule(
            "FREQ=DAILY;UNTIL=19970904T090000",
            local(1997, 9, 2),
            local(1997, 9, 3),
            local(1997, 9, 4),
        )
        assertRule("FREQ=DAILY;UNTIL=19970902T090000", local(1997, 9, 2))
        assertRule("FREQ=DAILY;UNTIL=19970901T090000")
    }

    @Test
    fun impossibleSelectionTerminatesAtTheRepresentableYearBoundary() {
        val recurrence = RuleRecurrence(
            start = local(9997, 9, 2),
            rule = RecurrenceRuleParser.parse("FREQ=YEARLY;COUNT=3;BYMONTH=2;BYMONTHDAY=31"),
        )

        assertEquals(emptyList(), recurrence.occurrences().toList())
    }

    private fun assertRule(ruleValue: String, vararg expected: RecurrenceDateTime.Floating) {
        assertRuleFrom(local(1997, 9, 2), ruleValue, *expected)
    }

    private fun assertRuleFrom(
        start: RecurrenceDateTime.Floating,
        ruleValue: String,
        vararg expected: RecurrenceDateTime.Floating,
    ) {
        val recurrence = RuleRecurrence(
            start = start,
            rule = RecurrenceRuleParser.parse(ruleValue),
        )
        assertEquals(expected.toList(), recurrence.occurrences().toList(), ruleValue)
    }

    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 9,
        minute: Int = 0,
        second: Int = 0,
    ): RecurrenceDateTime.Floating = RecurrenceDateTime.Floating(
        LocalDateTime(year, month, day, hour, minute, second),
    )
}
