package io.github.yallain.rrule.model

import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceDefinition
import io.github.yallain.rrule.RecurrenceDuration
import io.github.yallain.rrule.RecurrenceErrorReason
import io.github.yallain.rrule.RecurrencePeriod
import io.github.yallain.rrule.RecurrenceValidationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecurrencePeriodTest {
    @Test
    fun preservesNominalAndAccurateDurationComponentsWithoutNormalization() {
        val duration = RecurrenceDuration(days = 1, hours = 24, minutes = 90, seconds = 75)

        assertEquals(1, duration.days)
        assertEquals(24, duration.hours)
        assertEquals(90, duration.minutes)
        assertEquals(75, duration.seconds)
    }

    @Test
    fun validatesPositiveDurationAndExclusiveWeekForm() {
        val zero = assertFailsWith<RecurrenceValidationException> { RecurrenceDuration() }
        assertEquals("DURATION", zero.propertyName)
        assertEquals(RecurrenceErrorReason.OUT_OF_RANGE, zero.reason)

        val negative = assertFailsWith<RecurrenceValidationException> {
            RecurrenceDuration(seconds = -1)
        }
        assertEquals(RecurrenceErrorReason.OUT_OF_RANGE, negative.reason)

        val mixedWeeks = assertFailsWith<RecurrenceValidationException> {
            RecurrenceDuration(weeks = 1, days = 1)
        }
        assertEquals(RecurrenceErrorReason.INVALID_COMBINATION, mixedWeeks.reason)
    }

    @Test
    fun explicitPeriodsRequireDateTimesWithMatchingRepresentationsAndIncreasingEndpoints() {
        val dateOnly = assertFailsWith<RecurrenceValidationException> {
            RecurrencePeriod.WithDuration(
                start = RecurrenceDateTime.DateOnly(LocalDate(2024, 1, 1)),
                duration = RecurrenceDuration(days = 1),
            )
        }
        assertEquals(RecurrenceErrorReason.INVALID_COMBINATION, dateOnly.reason)

        val mixed = assertFailsWith<RecurrenceValidationException> {
            RecurrencePeriod.Explicit(
                start = floating(2024, 1, 1, 9),
                end = utc("2024-01-01T10:00:00Z"),
            )
        }
        assertEquals(RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE, mixed.reason)

        val differentTzid = assertFailsWith<RecurrenceValidationException> {
            RecurrencePeriod.Explicit(
                start = zoned(2024, 1, 1, 9, "Europe/Paris"),
                end = zoned(2024, 1, 1, 10, "Europe/London"),
            )
        }
        assertEquals(RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE, differentTzid.reason)

        val reversed = assertFailsWith<RecurrenceValidationException> {
            RecurrencePeriod.Explicit(
                start = floating(2024, 1, 1, 10),
                end = floating(2024, 1, 1, 9),
            )
        }
        assertEquals(RecurrenceErrorReason.OUT_OF_RANGE, reversed.reason)
    }

    @Test
    fun definitionDefensivelyCopiesPeriodsAndIncludesThemInValueSemantics() {
        val period = RecurrencePeriod.WithDuration(
            start = floating(2024, 1, 2, 9),
            duration = RecurrenceDuration(hours = 2),
        )
        val source = mutableSetOf<RecurrencePeriod>(period)
        val definition = RecurrenceDefinition(
            start = floating(2024, 1, 1, 9),
            additionalPeriods = source,
        )

        source.clear()

        val expected = RecurrenceDefinition(
            start = floating(2024, 1, 1, 9),
            additionalPeriods = setOf(period),
        )
        assertEquals(setOf(period), definition.additionalPeriods)
        assertEquals(expected, definition)
        assertEquals(expected.hashCode(), definition.hashCode())
        assertTrue(definition.toString().contains("additionalPeriods"))
    }

    @Test
    fun definitionRequiresPeriodStartsToUseItsTemporalDomain() {
        val error = assertFailsWith<RecurrenceValidationException> {
            RecurrenceDefinition(
                start = floating(2024, 1, 1, 9),
                additionalPeriods = setOf(
                    RecurrencePeriod.WithDuration(
                        start = utc("2024-01-02T09:00:00Z"),
                        duration = RecurrenceDuration(hours = 1),
                    ),
                ),
            )
        }

        assertEquals("RDATE", error.propertyName)
        assertEquals(RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE, error.reason)
    }

    private fun floating(year: Int, month: Int, day: Int, hour: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, hour, 0))

    private fun zoned(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        timeZoneId: String,
    ): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(LocalDateTime(year, month, day, hour, 0), timeZoneId)

    private fun utc(value: String): RecurrenceDateTime.Utc = RecurrenceDateTime.Utc(Instant.parse(value))
}
