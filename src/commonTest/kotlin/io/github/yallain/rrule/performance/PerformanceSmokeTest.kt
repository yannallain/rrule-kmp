package io.github.yallain.rrule.performance

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceSet
import io.github.yallain.rrule.RuleRecurrence
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Workload smoke tests: these verify bounded architecture, not environment-specific timings. */
class PerformanceSmokeTest {
    @Test
    fun generatesOneHundredThousandDailyOccurrencesLazily() {
        val start = LocalDateTime(2024, 1, 1, 9, 0)
        val recurrence = RuleRecurrence(
            RecurrenceDateTime.Floating(start),
            RecurrenceRule(Frequency.DAILY, count = 100_000),
        )

        var count = 0
        var last: RecurrenceDateTime.Floating? = null
        for (value in recurrence.occurrences()) {
            count++
            last = value as RecurrenceDateTime.Floating
        }

        assertEquals(100_000, count)
        assertEquals(start.date.plus(99_999, DateTimeUnit.DAY), last?.dateTime?.date)
    }

    @Test
    fun jumpsToNarrowMonthlyAndYearlyWindowsFarAfterDtStart() {
        val monthly = RuleRecurrence(
            local(1900, 1, 31),
            RecurrenceRule(Frequency.MONTHLY),
        )
        assertEquals(
            listOf(local(2500, 1, 31), local(2500, 3, 31)),
            monthly.between(local(2500, 1, 1), local(2500, 4, 1)),
        )

        val yearly = RuleRecurrence(
            local(1900, 1, 1),
            RecurrenceRule(
                Frequency.YEARLY,
                byMonth = setOf(2, 3, 11),
                byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.FRIDAY)),
                bySetPosition = setOf(1, -1),
            ),
        )
        val values = yearly.between(local(2500, 1, 1), local(2505, 1, 1))
        assertEquals(10, values.size)
        assertEquals(values.distinct(), values)
    }

    @Test
    fun skipsLongRunsOfEmptyPeriodsAndMergesMultipleRules() {
        val sparse = RuleRecurrence(
            local(1900, 1, 1),
            RecurrenceRule(
                Frequency.MONTHLY,
                count = 3,
                byMonth = setOf(2),
                byMonthDay = setOf(29),
            ),
        )
        assertEquals(
            listOf(local(1904, 2, 29), local(1908, 2, 29), local(1912, 2, 29)),
            sparse.occurrences().toList(),
        )

        val set = RecurrenceSet(
            start = local(2024, 1, 1),
            rules = listOf(
                RecurrenceRule(Frequency.DAILY),
                RecurrenceRule(Frequency.WEEKLY, byDay = listOf(ByDay(Weekday.MONDAY))),
                RecurrenceRule(Frequency.MONTHLY, byMonthDay = setOf(1, 15)),
            ),
        )
        val values = set.occurrences().take(20_000).toList()
        assertEquals(20_000, values.size)
        assertEquals(values.distinct(), values)
        assertTrue(values.zipWithNext().all { (left, right) ->
            (left as RecurrenceDateTime.Floating).dateTime <
                (right as RecurrenceDateTime.Floating).dateTime
        })
    }

    private fun local(year: Int, month: Int, day: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, 9, 0))
}
