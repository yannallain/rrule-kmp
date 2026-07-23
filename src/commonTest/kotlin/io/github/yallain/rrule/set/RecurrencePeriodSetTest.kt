package io.github.yallain.rrule.set

import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceDuration
import io.github.yallain.rrule.RecurrenceErrorReason
import io.github.yallain.rrule.RecurrencePeriod
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceSet
import io.github.yallain.rrule.RecurrenceValidationException
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class RecurrencePeriodSetTest {
    @Test
    fun periodStartsJoinTheLazySetAndRemainSubjectToDeduplicationAndExclusion() {
        val excludedPeriod = RecurrencePeriod.Explicit(
            start = floating(2024, 1, 2, 9),
            end = floating(2024, 1, 2, 10),
        )
        val additionalPeriod = RecurrencePeriod.WithDuration(
            start = floating(2024, 1, 4, 9),
            duration = RecurrenceDuration(hours = 2),
        )
        val set = RecurrenceSet(
            start = floating(2024, 1, 1, 9),
            rules = listOf(RecurrenceRule(Frequency.DAILY, count = 3)),
            excludedDates = setOf(excludedPeriod.start),
            additionalPeriods = setOf(excludedPeriod, additionalPeriod),
        )

        assertEquals(
            listOf(
                floating(2024, 1, 1, 9),
                floating(2024, 1, 3, 9),
                floating(2024, 1, 4, 9),
            ),
            set.occurrences().toList(),
        )
        assertEquals(floating(2024, 1, 4, 9), set.before(floating(2024, 1, 5, 9)))
        assertEquals(setOf(excludedPeriod, additionalPeriod), set.additionalPeriods)
    }

    @Test
    fun defensivelyCopiesPeriodSources() {
        val period = RecurrencePeriod.WithDuration(
            floating(2024, 1, 2, 9),
            RecurrenceDuration(hours = 1),
        )
        val source = mutableSetOf<RecurrencePeriod>(period)
        val set = RecurrenceSet(
            start = floating(2024, 1, 1, 9),
            additionalPeriods = source,
        )

        source.clear()

        assertEquals(setOf(period), set.additionalPeriods)
        assertEquals(
            listOf(floating(2024, 1, 1, 9), floating(2024, 1, 2, 9)),
            set.occurrences().toList(),
        )
    }

    @Test
    fun absolutePeriodStartsCanUseARepresentationDifferentFromDtstart() {
        val period = RecurrencePeriod.WithDuration(
            start = utc("2024-01-02T08:00:00Z"),
            duration = RecurrenceDuration(hours = 1),
        )
        val set = RecurrenceSet(
            start = zoned(2024, 1, 1, 9),
            additionalPeriods = setOf(period),
        )

        assertEquals(listOf(zoned(2024, 1, 1, 9), period.start), set.occurrences().toList())
    }

    @Test
    fun rejectsAnExplicitZonedPeriodWhoseLocalOrderingInvertsAfterGapResolution() {
        val period = RecurrencePeriod.Explicit(
            start = zoned(2024, 3, 31, 2, 30),
            end = zoned(2024, 3, 31, 3, 0),
        )

        val error = assertFailsWith<RecurrenceValidationException> {
            RecurrenceSet(
                start = zoned(2024, 3, 30, 9),
                additionalPeriods = setOf(period),
            )
        }

        assertEquals("RDATE", error.propertyName)
        assertEquals(RecurrenceErrorReason.OUT_OF_RANGE, error.reason)
    }

    private fun floating(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
    ): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, hour, 0))

    private fun zoned(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(LocalDateTime(year, month, day, hour, minute), "Europe/Paris")

    private fun utc(value: String): RecurrenceDateTime.Utc = RecurrenceDateTime.Utc(Instant.parse(value))
}
