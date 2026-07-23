package io.github.yallain.rrule.engine

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RuleRecurrence
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmptyRuleTerminationTest {
    @Test
    fun inheritedMonthDayCanMakeMonthlyAndYearlyRulesProvablyEmpty() {
        for (frequency in listOf(Frequency.MONTHLY, Frequency.YEARLY)) {
            val start = floating(2024, 1, 31)
            val recurrence = RuleRecurrence(
                start = start,
                rule = RecurrenceRule(frequency = frequency, byMonth = setOf(2)),
            )

            assertEquals(emptyList(), recurrence.occurrences().toList(), frequency.name)
            assertNull(recurrence.after(start), frequency.name)
            assertNull(recurrence.before(floating(2400, 1, 1)), frequency.name)
        }
    }

    @Test
    fun contradictoryMonthAndYearDayAreRejectedByCalendarCycleProof() {
        val start = floating(2024, 1, 1)
        val recurrence = RuleRecurrence(
            start = start,
            rule = RecurrenceRule(
                frequency = Frequency.YEARLY,
                byMonth = setOf(2),
                byYearDay = setOf(1),
            ),
        )

        assertEquals(emptyList(), recurrence.occurrences().toList())
        assertNull(recurrence.after(start))
    }

    @Test
    fun unreachableSubDailyClockFiltersAreProvablyEmpty() {
        val cases = listOf(
            RecurrenceRule(Frequency.HOURLY, interval = 2, byHour = setOf(10)),
            RecurrenceRule(Frequency.MINUTELY, interval = 60, byMinute = setOf(30)),
            RecurrenceRule(Frequency.SECONDLY, interval = 60, bySecond = setOf(30)),
        )

        for (rule in cases) {
            val start = floating(2024, 1, 1)
            val recurrence = RuleRecurrence(start, rule)
            assertNull(recurrence.after(start), rule.toString())
        }
    }

    @Test
    fun subDailyIntervalPhaseCanMakeAWeekdayFilterProvablyEmpty() {
        val start = floating(2024, 1, 1) // Monday
        val cases = listOf(
            RecurrenceRule(Frequency.HOURLY, interval = 168, byDay = listOf(ByDay(Weekday.TUESDAY))),
            RecurrenceRule(Frequency.MINUTELY, interval = 10_080, byDay = listOf(ByDay(Weekday.TUESDAY))),
            RecurrenceRule(Frequency.SECONDLY, interval = 604_800, byDay = listOf(ByDay(Weekday.TUESDAY))),
        )

        for (rule in cases) {
            val recurrence = RuleRecurrence(start, rule)
            assertEquals(emptyList(), recurrence.occurrences().toList(), rule.toString())
            assertNull(recurrence.after(start), rule.toString())
        }
    }

    @Test
    fun subDailyCalendarProofDoesNotRejectAReachablePhase() {
        val start = floating(2024, 1, 1) // Monday
        val recurrence = RuleRecurrence(
            start,
            RecurrenceRule(
                frequency = Frequency.HOURLY,
                interval = 25,
                count = 1,
                byDay = listOf(ByDay(Weekday.TUESDAY)),
                byHour = setOf(10),
            ),
        )

        assertEquals(listOf(floating(2024, 1, 2, 10)), recurrence.occurrences().toList())
    }

    @Test
    fun setPositionsBeyondEveryPeriodsMaximumCardinalityAreProvablyEmpty() {
        val start = floating(2024, 1, 1)
        val cases = listOf(
            RecurrenceRule(Frequency.SECONDLY, bySecond = setOf(0), bySetPosition = setOf(2)),
            RecurrenceRule(Frequency.MINUTELY, bySecond = setOf(0, 30), bySetPosition = setOf(-3)),
            RecurrenceRule(Frequency.HOURLY, byMinute = setOf(0), bySetPosition = setOf(2)),
            RecurrenceRule(Frequency.DAILY, byHour = setOf(9), bySetPosition = setOf(-2)),
        )

        for (rule in cases) {
            val recurrence = RuleRecurrence(start, rule)
            assertEquals(emptyList(), recurrence.occurrences().toList(), rule.toString())
            assertNull(recurrence.after(start), rule.toString())
        }
    }

    @Test
    fun leapSecondSelectorsUseTheRfcSecond59Fallback() {
        val start = floating(2024, 1, 1)
        assertEquals(
            listOf(floating(2024, 1, 1, hour = 9, second = 59)),
            RuleRecurrence(
                start,
                RecurrenceRule(Frequency.DAILY, count = 1, bySecond = setOf(60)),
            ).occurrences().toList(),
        )
        assertEquals(
            listOf(
                floating(2024, 1, 1, hour = 9, second = 59),
                floating(2024, 1, 2, hour = 9, second = 59),
            ),
            RuleRecurrence(
                start,
                RecurrenceRule(Frequency.DAILY, count = 2, bySecond = setOf(59, 60)),
            ).occurrences().toList(),
        )
    }

    private fun floating(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 9,
        second: Int = 0,
    ): RecurrenceDateTime.Floating = RecurrenceDateTime.Floating(
        LocalDateTime(year, month, day, hour, 0, second),
    )
}
