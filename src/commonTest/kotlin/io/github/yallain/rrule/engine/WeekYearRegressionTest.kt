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

class WeekYearRegressionTest {
    @Test
    fun positiveAndNegativeWeek53AddressOppositeEndsOfTheSameWeekYears() {
        val positive = recurrence(53)
        val negative = recurrence(-53)

        assertEquals(
            listOf(local(1998, 12, 28), local(2004, 12, 27), local(2009, 12, 28)),
            positive.occurrences().toList(),
        )
        assertEquals(
            listOf(local(1997, 12, 29), local(2003, 12, 29), local(2008, 12, 29)),
            negative.occurrences().toList(),
        )
    }

    @Test
    fun weekOneCanBeginInThePreviousCalendarYear() {
        val recurrence = RuleRecurrence(
            start = local(2014, 1, 1),
            rule = RecurrenceRule(
                frequency = Frequency.YEARLY,
                count = 4,
                byWeekNumber = setOf(1),
                byDay = listOf(ByDay(Weekday.MONDAY)),
            ),
        )

        assertEquals(
            listOf(
                local(2014, 12, 29),
                local(2016, 1, 4),
                local(2017, 1, 2),
                local(2018, 1, 1),
            ),
            recurrence.occurrences().toList(),
        )
    }

    @Test
    fun weekStartChangesBiweeklyGroupingAtTheBoundary() {
        val days = listOf(ByDay(Weekday.TUESDAY), ByDay(Weekday.SUNDAY))
        val mondayStart = RuleRecurrence(
            local(1997, 9, 2),
            RecurrenceRule(Frequency.WEEKLY, interval = 2, count = 3, byDay = days),
        )
        val sundayStart = RuleRecurrence(
            local(1997, 9, 2),
            RecurrenceRule(
                Frequency.WEEKLY,
                interval = 2,
                count = 3,
                weekStart = Weekday.SUNDAY,
                byDay = days,
            ),
        )

        assertEquals(
            listOf(local(1997, 9, 2), local(1997, 9, 7), local(1997, 9, 16)),
            mondayStart.occurrences().toList(),
        )
        assertEquals(
            listOf(local(1997, 9, 2), local(1997, 9, 14), local(1997, 9, 16)),
            sundayStart.occurrences().toList(),
        )
    }

    private fun recurrence(weekNumber: Int): RuleRecurrence = RuleRecurrence(
        start = local(1997, 9, 2),
        rule = RecurrenceRule(
            frequency = Frequency.YEARLY,
            count = 3,
            byWeekNumber = setOf(weekNumber),
            byDay = listOf(ByDay(Weekday.MONDAY)),
        ),
    )

    private fun local(year: Int, month: Int, day: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, 9, 0))
}
