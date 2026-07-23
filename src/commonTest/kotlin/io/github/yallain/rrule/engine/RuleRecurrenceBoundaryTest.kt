package io.github.yallain.rrule.engine

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RuleRecurrence
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class RuleRecurrenceBoundaryTest {
    @Test
    fun stopsAtTheFourDigitRfcYearBoundary() {
        assertEquals(
            listOf(local(9999, 12, 31, 9)),
            recurrence(
                local(9999, 12, 31, 9),
                RecurrenceRule(Frequency.DAILY, count = 2),
            ).occurrences().toList(),
        )

        val date = RecurrenceDateTime.DateOnly(LocalDate(9999, 12, 31))
        assertEquals(
            listOf(date),
            RuleRecurrence(date, RecurrenceRule(Frequency.DAILY, count = 2)).occurrences().toList(),
        )
    }

    @Test
    fun acceptsFractionalQueryBoundsAroundWholeSecondOccurrences() {
        val recurrence = recurrence(
            local(2024, 1, 1, 0),
            RecurrenceRule(Frequency.SECONDLY, count = 4),
        )
        val halfSecond = RecurrenceDateTime.Floating(
            LocalDateTime(2024, 1, 1, 0, 0, 0, 500_000_000),
        )
        val twoAndAHalfSeconds = RecurrenceDateTime.Floating(
            LocalDateTime(2024, 1, 1, 0, 0, 2, 500_000_000),
        )

        assertEquals(local(2024, 1, 1, 0), recurrence.before(halfSecond))
        assertEquals(local(2024, 1, 1, 0, 0, 1), recurrence.after(halfSecond))
        assertEquals(
            listOf(local(2024, 1, 1, 0, 0, 1), local(2024, 1, 1, 0, 0, 2)),
            recurrence.between(halfSecond, twoAndAHalfSeconds),
        )

        val utcRecurrence = RuleRecurrence(
            RecurrenceDateTime.Utc(Instant.parse("2024-01-01T00:00:00Z")),
            RecurrenceRule(Frequency.SECONDLY, count = 2),
        )
        val utcHalfSecond = RecurrenceDateTime.Utc(Instant.parse("2024-01-01T00:00:00.500Z"))
        assertEquals(
            RecurrenceDateTime.Utc(Instant.parse("2024-01-01T00:00:00Z")),
            utcRecurrence.before(utcHalfSecond),
        )
        assertEquals(
            RecurrenceDateTime.Utc(Instant.parse("2024-01-01T00:00:01Z")),
            utcRecurrence.after(utcHalfSecond),
        )
    }

    @Test
    fun boundsReverseQueriesAboveTheFourDigitRfcYearRange() {
        val recurrence = recurrence(
            local(9999, 12, 31, 23, 59, 58),
            RecurrenceRule(Frequency.SECONDLY),
        )
        val outOfRangeQuery = local(10_000, 1, 1, 0)

        assertEquals(
            local(9999, 12, 31, 23, 59, 59),
            recurrence.before(outOfRangeQuery),
        )
        assertNull(recurrence.after(outOfRangeQuery))
    }

    @Test
    fun weeklyRulesRetainTheFirstRfcYearWhenItsWeekStartsInThePreviousYear() {
        val dateStart = RecurrenceDateTime.DateOnly(LocalDate(0, 1, 1))
        assertEquals(
            listOf(
                dateStart,
                RecurrenceDateTime.DateOnly(LocalDate(0, 1, 8)),
            ),
            RuleRecurrence(
                dateStart,
                RecurrenceRule(Frequency.WEEKLY, count = 2),
            ).occurrences().toList(),
        )

        assertEquals(
            listOf(local(0, 1, 1, 9), local(0, 1, 8, 9)),
            recurrence(
                local(0, 1, 1, 9),
                RecurrenceRule(Frequency.WEEKLY, count = 2),
            ).occurrences().toList(),
        )
    }

    @Test
    fun countedWeekYearQueriesRemainExactAtBothRfcYearEdges() {
        val rule = RecurrenceRule(
            frequency = Frequency.YEARLY,
            count = 2_000_000_000,
            byWeekNumber = setOf(1, 53, -1),
            byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.SUNDAY)),
            weekStart = Weekday.SUNDAY,
        )
        val cases = listOf(
            BoundaryCase(
                start = local(0, 1, 1, 9),
                windowStart = local(399, 12, 1, 0),
                endExclusive = local(401, 1, 15, 0),
            ),
            BoundaryCase(
                start = local(9599, 1, 1, 9),
                windowStart = local(9998, 12, 1, 0),
                endExclusive = local(10_000, 1, 1, 0),
            ),
            BoundaryCase(
                start = local(9600, 1, 1, 9),
                windowStart = local(9998, 12, 1, 0),
                endExclusive = local(10_000, 1, 1, 0),
            ),
            BoundaryCase(
                start = local(9999, 1, 1, 9),
                windowStart = local(9999, 12, 1, 0),
                endExclusive = local(10_000, 1, 1, 0),
            ),
        )

        cases.forEach { case ->
            val counted = recurrence(case.start, rule)
            val oracle = recurrence(case.start, rule.copy(count = null))
            assertEquals(
                oracle.between(case.windowStart, case.endExclusive),
                counted.between(case.windowStart, case.endExclusive),
                "${case.start} bounded window",
            )
            assertEquals(
                oracle.before(case.endExclusive),
                counted.before(case.endExclusive),
                "${case.start} reverse lookup",
            )
            assertEquals(
                oracle.after(case.endExclusive),
                counted.after(case.endExclusive),
                "${case.start} forward lookup",
            )
        }
    }

    private fun recurrence(start: RecurrenceDateTime, rule: RecurrenceRule): RuleRecurrence =
        RuleRecurrence(start, rule)

    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
        second: Int = 0,
    ): RecurrenceDateTime.Floating = RecurrenceDateTime.Floating(
        LocalDateTime(year, month, day, hour, minute, second),
    )

    private data class BoundaryCase(
        val start: RecurrenceDateTime.Floating,
        val windowStart: RecurrenceDateTime.Floating,
        val endExclusive: RecurrenceDateTime.Floating,
    )
}
