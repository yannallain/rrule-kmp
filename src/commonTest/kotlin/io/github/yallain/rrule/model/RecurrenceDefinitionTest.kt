package io.github.yallain.rrule.model

import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceDefinition
import io.github.yallain.rrule.RecurrenceErrorReason
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceValidationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecurrenceDefinitionTest {
    @Test
    fun defensivelyCopiesSourcesAndHasValueSemantics() {
        val rule = RecurrenceRule(Frequency.DAILY, count = 2)
        val rules = mutableListOf(rule)
        val exclusionRules = mutableListOf<RecurrenceRule>()
        val additionalDates = mutableSetOf<RecurrenceDateTime>(floating(2024, 1, 3))
        val excludedDates = mutableSetOf<RecurrenceDateTime>()
        val definition = RecurrenceDefinition(
            start = floating(2024, 1, 1),
            rules = rules,
            exclusionRules = exclusionRules,
            additionalDates = additionalDates,
            excludedDates = excludedDates,
        )

        rules.clear()
        exclusionRules += rule
        additionalDates.clear()
        excludedDates += floating(2024, 1, 1)

        val expected = RecurrenceDefinition(
            start = floating(2024, 1, 1),
            rules = listOf(rule),
            additionalDates = setOf(floating(2024, 1, 3)),
        )
        assertEquals(expected, definition)
        assertEquals(expected.hashCode(), definition.hashCode())
        assertTrue(definition.toString().contains("additionalDates"))
    }

    @Test
    fun rejectsDifferentDomainsButAcceptsAbsoluteValuesWithDifferentRepresentations() {
        val temporalError = assertFailsWith<RecurrenceValidationException> {
            RecurrenceDefinition(
                start = floating(2024, 1, 1),
                additionalDates = setOf(zoned(2024, 1, 2, "Europe/Paris")),
            )
        }
        assertEquals("RDATE", temporalError.propertyName)
        assertEquals(RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE, temporalError.reason)

        RecurrenceDefinition(
            start = zoned(2024, 1, 1, "Europe/Paris"),
            additionalDates = setOf(zoned(2024, 1, 2, "Europe/London")),
            excludedDates = setOf(
                RecurrenceDateTime.Utc(kotlin.time.Instant.parse("2024-01-03T08:00:00Z")),
            ),
        )
    }

    @Test
    fun rejectsStoredValuesOutsideTheRfcFourDigitYearDomain() {
        val invalidStart = assertFailsWith<RecurrenceValidationException> {
            RecurrenceDefinition(start = RecurrenceDateTime.DateOnly(LocalDate(-1, 12, 31)))
        }
        assertEquals("DTSTART", invalidStart.propertyName)

        val invalidRdate = assertFailsWith<RecurrenceValidationException> {
            RecurrenceDefinition(
                start = floating(2024, 1, 1),
                additionalDates = setOf(floating(10_000, 1, 1)),
            )
        }
        assertEquals("RDATE", invalidRdate.propertyName)

        val invalidExdate = assertFailsWith<RecurrenceValidationException> {
            RecurrenceDefinition(
                start = floating(2024, 1, 1),
                excludedDates = setOf(floating(-1, 12, 31)),
            )
        }
        assertEquals("EXDATE", invalidExdate.propertyName)
    }

    @Test
    fun rejectsSubsecondPrecisionOnStoredRfcValuesAtConstruction() {
        val fractional = RecurrenceDateTime.Floating(
            LocalDateTime(2024, 1, 1, 9, 0, 0, 500_000_000),
        )

        val invalidStart = assertFailsWith<RecurrenceValidationException> {
            RecurrenceDefinition(start = fractional)
        }
        assertEquals("DTSTART", invalidStart.propertyName)

        val invalidRdate = assertFailsWith<RecurrenceValidationException> {
            RecurrenceDefinition(
                start = floating(2024, 1, 1),
                additionalDates = setOf(fractional),
            )
        }
        assertEquals("RDATE", invalidRdate.propertyName)

        val invalidExdate = assertFailsWith<RecurrenceValidationException> {
            RecurrenceDefinition(
                start = floating(2024, 1, 1),
                excludedDates = setOf(fractional),
            )
        }
        assertEquals("EXDATE", invalidExdate.propertyName)
    }

    private fun floating(year: Int, month: Int, day: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, 9, 0))

    private fun zoned(year: Int, month: Int, day: Int, timeZoneId: String): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(LocalDateTime(year, month, day, 9, 0), timeZoneId)
}
