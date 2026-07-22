package io.github.yallain.rrule.engine

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceValidationException
import io.github.yallain.rrule.RuleRecurrence
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class RuleRecurrenceTest {
    @Test
    fun evaluatesDailyIntervalCountAndRuleFiltering() {
        val recurrence = recurrence(
            start = local(2024, 1, 1, 9, 30),
            rule = RecurrenceRule(Frequency.DAILY, interval = 2, count = 3),
        )
        assertEquals(
            listOf(
                local(2024, 1, 1, 9, 30),
                local(2024, 1, 3, 9, 30),
                local(2024, 1, 5, 9, 30),
            ),
            recurrence.occurrences().toList(),
        )

        val filtered = recurrence(
            start = local(2024, 1, 1, 9),
            rule = RecurrenceRule(
                Frequency.DAILY,
                count = 2,
                byDay = listOf(ByDay(Weekday.TUESDAY)),
            ),
        )
        assertEquals(
            listOf(local(2024, 1, 2, 9), local(2024, 1, 9, 9)),
            filtered.occurrences().toList(),
        )
    }

    @Test
    fun standaloneRuleDoesNotForceMismatchingDtStart() {
        val recurrence = recurrence(
            start = local(2024, 1, 1, 9), // Monday
            rule = RecurrenceRule(
                Frequency.WEEKLY,
                count = 2,
                byDay = listOf(ByDay(Weekday.WEDNESDAY)),
            ),
        )

        assertEquals(
            listOf(local(2024, 1, 3, 9), local(2024, 1, 10, 9)),
            recurrence.occurrences().toList(),
        )
    }

    @Test
    fun weeklyIntervalHonorsConfigurableWeekStart() {
        val start = local(1997, 8, 5, 9)
        val days = listOf(ByDay(Weekday.TUESDAY), ByDay(Weekday.SUNDAY))

        assertEquals(
            listOf(
                local(1997, 8, 5, 9),
                local(1997, 8, 10, 9),
                local(1997, 8, 19, 9),
                local(1997, 8, 24, 9),
            ),
            recurrence(start, RecurrenceRule(Frequency.WEEKLY, 2, count = 4, byDay = days))
                .occurrences().toList(),
        )
        assertEquals(
            listOf(
                local(1997, 8, 5, 9),
                local(1997, 8, 17, 9),
                local(1997, 8, 19, 9),
                local(1997, 8, 31, 9),
            ),
            recurrence(
                start,
                RecurrenceRule(Frequency.WEEKLY, 2, count = 4, weekStart = Weekday.SUNDAY, byDay = days),
            ).occurrences().toList(),
        )
    }

    @Test
    fun skipsInvalidMonthDaysWithoutCountingThem() {
        val recurrence = recurrence(
            start = local(2024, 1, 31, 9),
            rule = RecurrenceRule(Frequency.MONTHLY, count = 4),
        )

        assertEquals(
            listOf(
                local(2024, 1, 31, 9),
                local(2024, 3, 31, 9),
                local(2024, 5, 31, 9),
                local(2024, 7, 31, 9),
            ),
            recurrence.occurrences().toList(),
        )
    }

    @Test
    fun supportsNegativeMonthDaysAndOrdinalWeekdays() {
        assertEquals(
            listOf(
                local(2024, 1, 31, 9),
                local(2024, 2, 29, 9),
                local(2024, 3, 31, 9),
            ),
            recurrence(
                local(2024, 1, 1, 9),
                RecurrenceRule(Frequency.MONTHLY, count = 3, byMonthDay = setOf(-1)),
            ).occurrences().toList(),
        )

        assertEquals(
            listOf(
                local(2024, 1, 1, 9),
                local(2024, 1, 26, 9),
                local(2024, 2, 5, 9),
                local(2024, 2, 23, 9),
            ),
            recurrence(
                local(2024, 1, 1, 9),
                RecurrenceRule(
                    Frequency.MONTHLY,
                    count = 4,
                    byDay = listOf(ByDay(Weekday.MONDAY, 1), ByDay(Weekday.FRIDAY, -1)),
                ),
            ).occurrences().toList(),
        )
    }

    @Test
    fun supportsLeapDayAndNegativeYearDay() {
        assertEquals(
            listOf(local(2020, 2, 29, 9), local(2024, 2, 29, 9), local(2028, 2, 29, 9)),
            recurrence(
                local(2020, 2, 29, 9),
                RecurrenceRule(Frequency.YEARLY, count = 3),
            ).occurrences().toList(),
        )
        assertEquals(
            listOf(local(2020, 12, 31, 9), local(2021, 12, 31, 9), local(2022, 12, 31, 9)),
            recurrence(
                local(2020, 6, 1, 9),
                RecurrenceRule(Frequency.YEARLY, count = 3, byYearDay = setOf(-1)),
            ).occurrences().toList(),
        )
    }

    @Test
    fun supportsWeekNumbersAcrossCalendarYearBoundaries() {
        val recurrence = recurrence(
            local(2020, 1, 1, 9),
            RecurrenceRule(
                Frequency.YEARLY,
                count = 3,
                byWeekNumber = setOf(1),
                byDay = listOf(ByDay(Weekday.MONDAY)),
            ),
        )

        assertEquals(
            listOf(local(2021, 1, 4, 9), local(2022, 1, 3, 9), local(2023, 1, 2, 9)),
            recurrence.occurrences().toList(),
        )

        val unbounded = recurrence(
            local(2020, 1, 1, 9),
            RecurrenceRule(
                Frequency.YEARLY,
                byWeekNumber = setOf(1),
                byDay = listOf(ByDay(Weekday.MONDAY)),
            ),
        )
        assertEquals(
            listOf(local(2024, 12, 30, 9)),
            unbounded.between(local(2024, 12, 29, 9), local(2024, 12, 31, 9)),
        )
        assertEquals(
            local(2024, 12, 30, 9),
            unbounded.before(local(2024, 12, 31, 9)),
        )
    }

    @Test
    fun appliesNegativeSetPositionAfterSortingThePeriod() {
        val weekdays = listOf(
            Weekday.MONDAY,
            Weekday.TUESDAY,
            Weekday.WEDNESDAY,
            Weekday.THURSDAY,
            Weekday.FRIDAY,
        ).map(::ByDay)
        val recurrence = recurrence(
            local(2024, 1, 1, 9),
            RecurrenceRule(
                Frequency.MONTHLY,
                count = 3,
                byDay = weekdays,
                bySetPosition = setOf(-1),
            ),
        )

        assertEquals(
            listOf(local(2024, 1, 31, 9), local(2024, 2, 29, 9), local(2024, 3, 29, 9)),
            recurrence.occurrences().toList(),
        )
    }

    @Test
    fun expandsAndLimitsTimePartsWithoutSecondBySecondBruteForce() {
        val daily = recurrence(
            local(2024, 1, 1, 9, 15, 10),
            RecurrenceRule(
                Frequency.DAILY,
                count = 5,
                byHour = setOf(9, 17),
                byMinute = setOf(0, 30),
                bySecond = setOf(0),
            ),
        )
        assertEquals(
            listOf(
                local(2024, 1, 1, 9, 30),
                local(2024, 1, 1, 17, 0),
                local(2024, 1, 1, 17, 30),
                local(2024, 1, 2, 9, 0),
                local(2024, 1, 2, 9, 30),
            ),
            daily.occurrences().toList(),
        )

        assertEquals(
            listOf(
                local(2024, 1, 1, 9, 30),
                local(2024, 1, 1, 11, 0),
                local(2024, 1, 1, 11, 30),
            ),
            recurrence(
                local(2024, 1, 1, 9, 15, 20),
                RecurrenceRule(Frequency.HOURLY, interval = 2, count = 3, byMinute = setOf(0, 30), bySecond = setOf(0)),
            ).occurrences().toList(),
        )

        assertEquals(
            listOf(
                local(2024, 1, 1, 9, 0, 30),
                local(2024, 1, 1, 9, 15, 0),
                local(2024, 1, 1, 9, 15, 30),
            ),
            recurrence(
                local(2024, 1, 1, 9, 0, 10),
                RecurrenceRule(Frequency.MINUTELY, interval = 15, count = 3, bySecond = setOf(0, 30)),
            ).occurrences().toList(),
        )

        assertEquals(
            listOf(
                local(2024, 1, 1, 9, 0, 10),
                local(2024, 1, 1, 9, 0, 30),
                local(2024, 1, 1, 9, 0, 50),
            ),
            recurrence(
                local(2024, 1, 1, 9, 0, 10),
                RecurrenceRule(Frequency.SECONDLY, interval = 20, count = 3),
            ).occurrences().toList(),
        )
    }

    @Test
    fun untilIsInclusiveAndQueriesStopAtTheirBounds() {
        val recurrence = recurrence(
            local(2024, 1, 1, 9),
            RecurrenceRule(
                Frequency.DAILY,
                until = local(2024, 1, 3, 9),
            ),
        )
        assertEquals(3, recurrence.occurrences().count())
        assertEquals(
            listOf(local(2024, 1, 2, 9), local(2024, 1, 3, 9)),
            recurrence.between(local(2024, 1, 2, 9), local(2024, 1, 4, 9)),
        )
        assertEquals(local(2024, 1, 2, 9), recurrence.after(local(2024, 1, 1, 9)))
        assertEquals(local(2024, 1, 1, 9), recurrence.after(local(2024, 1, 1, 9), inclusive = true))
        assertEquals(local(2024, 1, 2, 9), recurrence.before(local(2024, 1, 3, 9)))
        assertEquals(local(2024, 1, 3, 9), recurrence.before(local(2024, 1, 3, 9), inclusive = true))
        assertNull(recurrence.after(local(2024, 1, 3, 9)))

        val counted = recurrence(
            local(2024, 1, 1, 9),
            RecurrenceRule(Frequency.DAILY, count = 3),
        )
        assertEquals(
            listOf(local(2024, 1, 3, 9)),
            counted.between(local(2024, 1, 3, 9), local(2024, 1, 10, 9)),
        )
        assertNull(counted.after(local(2024, 1, 3, 9)))
    }

    @Test
    fun finiteUntilTerminatesWhenEveryPeriodIsEmpty() {
        val recurrence = recurrence(
            start = local(2024, 1, 1, 9), // Monday
            rule = RecurrenceRule(
                frequency = Frequency.DAILY,
                interval = 7,
                until = local(2024, 1, 2, 9),
                byDay = listOf(ByDay(Weekday.TUESDAY)),
            ),
        )

        assertEquals(emptyList(), recurrence.occurrences().toList())
        assertNull(recurrence.after(local(2400, 1, 1, 9)))
    }

    @Test
    fun jumpsToANarrowUnboundedWindowFarAfterStart() {
        val recurrence = recurrence(
            local(2000, 1, 1, 9),
            RecurrenceRule(Frequency.DAILY),
        )

        assertEquals(
            listOf(local(2400, 2, 28, 9), local(2400, 2, 29, 9)),
            recurrence.between(local(2400, 2, 28, 9), local(2400, 3, 1, 9)),
        )

        val noCandidatesInWindow = recurrence(
            local(2000, 1, 1, 9),
            RecurrenceRule(
                Frequency.MONTHLY,
                byMonth = setOf(2),
                byMonthDay = setOf(30),
            ),
        )
        assertEquals(
            emptyList(),
            noCandidatesInWindow.between(local(2400, 1, 1, 9), local(2400, 4, 1, 9)),
        )
        assertNull(noCandidatesInWindow.before(local(2000, 4, 1, 9)))
        assertNull(noCandidatesInWindow.after(local(2400, 1, 1, 9)))
    }

    @Test
    fun beforeJumpsToTheUpperBoundForUncountedSubDailyRules() {
        val recurrence = recurrence(
            start = local(2000, 1, 1, 0),
            rule = RecurrenceRule(
                frequency = Frequency.SECONDLY,
                bySecond = setOf(0),
            ),
        )

        assertEquals(
            local(2400, 1, 1, 0),
            recurrence.before(local(2400, 1, 1, 0, 0, 30)),
        )
        assertEquals(
            local(2400, 1, 1, 0),
            recurrence.before(local(2400, 1, 1, 0), inclusive = true),
        )

        val finite = recurrence(
            start = local(2000, 1, 1, 0),
            rule = RecurrenceRule(
                frequency = Frequency.SECONDLY,
                until = local(2000, 1, 1, 0, 1),
                bySecond = setOf(0),
            ),
        )
        assertEquals(
            local(2000, 1, 1, 0, 1),
            finite.before(local(2400, 1, 1, 0)),
        )
    }

    @Test
    fun preservesDateOnlyAndUtcTemporalKinds() {
        val dateRecurrence = RuleRecurrence(
            RecurrenceDateTime.DateOnly(LocalDate(2024, 1, 1)),
            RecurrenceRule(Frequency.DAILY, count = 2),
        )
        assertEquals(
            listOf(
                RecurrenceDateTime.DateOnly(LocalDate(2024, 1, 1)),
                RecurrenceDateTime.DateOnly(LocalDate(2024, 1, 2)),
            ),
            dateRecurrence.occurrences().toList(),
        )

        val utcRecurrence = RuleRecurrence(
            RecurrenceDateTime.Utc(Instant.parse("2024-01-01T23:00:00Z")),
            RecurrenceRule(Frequency.HOURLY, interval = 2, count = 2),
        )
        assertEquals(
            listOf(
                RecurrenceDateTime.Utc(Instant.parse("2024-01-01T23:00:00Z")),
                RecurrenceDateTime.Utc(Instant.parse("2024-01-02T01:00:00Z")),
            ),
            utcRecurrence.occurrences().toList(),
        )
    }

    @Test
    fun validatesStartDependentRestrictions() {
        val dateStart = RecurrenceDateTime.DateOnly(LocalDate(2024, 1, 1))
        assertEquals(
            listOf(dateStart, RecurrenceDateTime.DateOnly(LocalDate(2024, 1, 2))),
            RuleRecurrence(
                dateStart,
                RecurrenceRule(
                    Frequency.DAILY,
                    count = 2,
                    byHour = setOf(9),
                    byMinute = setOf(30),
                    bySecond = setOf(45),
                ),
            ).occurrences().toList(),
        )
        assertFailsWith<RecurrenceValidationException> {
            recurrence(
                local(2024, 1, 1, 9),
                RecurrenceRule(
                    Frequency.DAILY,
                    until = RecurrenceDateTime.Utc(Instant.parse("2024-01-02T09:00:00Z")),
                ),
            )
        }
        assertFailsWith<RecurrenceValidationException> {
            recurrence(local(2024, 1, 1, 9), RecurrenceRule(Frequency.DAILY))
                .after(RecurrenceDateTime.DateOnly(LocalDate(2024, 1, 2)))
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
}
