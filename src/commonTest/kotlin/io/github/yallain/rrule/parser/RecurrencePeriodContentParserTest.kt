package io.github.yallain.rrule.parser

import io.github.yallain.rrule.RecurrenceContentParser
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceDuration
import io.github.yallain.rrule.RecurrenceErrorReason
import io.github.yallain.rrule.RecurrenceParseException
import io.github.yallain.rrule.RecurrencePeriod
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecurrencePeriodContentParserTest {
    @Test
    fun parsesAndPreservesBothRfc5545PeriodFormsFromTheNormativeExample() {
        val content = "DTSTART:19960401T020000Z\r\n" +
            "RDATE;VALUE=PERIOD:19960403T020000Z/19960403T040000Z,\r\n" +
            " 19960404T010000Z/PT3H"

        val definition = RecurrenceContentParser.parse(content)

        assertEquals(emptySet(), definition.additionalDates)
        assertEquals(
            setOf(
                RecurrencePeriod.Explicit(
                    start = utc("1996-04-03T02:00:00Z"),
                    end = utc("1996-04-03T04:00:00Z"),
                ),
                RecurrencePeriod.WithDuration(
                    start = utc("1996-04-04T01:00:00Z"),
                    duration = RecurrenceDuration(hours = 3),
                ),
            ),
            definition.additionalPeriods,
        )
    }

    @Test
    fun appliesTzidToEveryEndpointAndPreservesNominalDurationComponents() {
        val definition = RecurrenceContentParser.parse(
            "DTSTART;TZID=Europe/Paris:20240329T090000\n" +
                "RDATE;VALUE=PERIOD;TZID=Europe/Paris:" +
                "20240330T090000/20240330T100000,20240331T090000/P1DT5H0M20S",
        )

        assertEquals(
            setOf(
                RecurrencePeriod.Explicit(
                    start = zoned(2024, 3, 30, 9),
                    end = zoned(2024, 3, 30, 10),
                ),
                RecurrencePeriod.WithDuration(
                    start = zoned(2024, 3, 31, 9),
                    duration = RecurrenceDuration(days = 1, hours = 5, seconds = 20),
                ),
            ),
            definition.additionalPeriods,
        )
    }

    @Test
    fun acceptsPositiveSignAndDeduplicatesIdenticalPeriodsAcrossLines() {
        val definition = RecurrenceContentParser.parse(
            "DTSTART:20240101T090000\n" +
                "RDATE;VALUE=PERIOD:20240102T090000/+PT90M\n" +
                "RDATE;VALUE=PERIOD:20240102T090000/PT90M",
        )

        assertEquals(
            setOf(
                RecurrencePeriod.WithDuration(
                    start = floating(2024, 1, 2, 9),
                    duration = RecurrenceDuration(minutes = 90),
                ),
            ),
            definition.additionalPeriods,
        )
    }

    @Test
    fun periodValueTypeRemainsExclusiveToRdate() {
        assertPeriodError(
            "DTSTART;VALUE=PERIOD:20240101T090000/PT1H",
            RecurrenceErrorReason.MALFORMED_TOKEN,
            "VALUE",
        )
        assertPeriodError(
            "DTSTART:20240101T090000\nEXDATE;VALUE=PERIOD:20240102T090000/PT1H",
            RecurrenceErrorReason.MALFORMED_TOKEN,
            "VALUE",
        )
    }

    @Test
    fun rejectsMalformedNonPositiveAndOverflowingDurationsWithPreciseDiagnostics() {
        val cases = listOf(
            "P0D" to RecurrenceErrorReason.OUT_OF_RANGE,
            "-PT1H" to RecurrenceErrorReason.OUT_OF_RANGE,
            "P1WT1H" to RecurrenceErrorReason.MALFORMED_TOKEN,
            "PT1H30S" to RecurrenceErrorReason.MALFORMED_TOKEN,
            "P2147483648D" to RecurrenceErrorReason.OUT_OF_RANGE,
        )

        for ((duration, reason) in cases) {
            val content = "DTSTART:20240101T090000\n" +
                "RDATE;VALUE=PERIOD:20240102T090000/$duration"
            val error = assertFailsWith<RecurrenceParseException>(content) {
                RecurrenceContentParser.parse(content)
            }
            assertEquals("DURATION", error.propertyName, content)
            assertEquals(reason, error.reason, content)
            assertEquals(content.indexOf(duration), error.position, content)
        }
    }

    @Test
    fun rejectsMalformedOrNonIncreasingExplicitPeriods() {
        assertPeriodError(
            "DTSTART:20240101T090000\nRDATE;VALUE=PERIOD:20240102T090000",
            RecurrenceErrorReason.MALFORMED_TOKEN,
            "RDATE",
        )
        assertPeriodError(
            "DTSTART:20240101T090000\n" +
                "RDATE;VALUE=PERIOD:20240102T090000/20240102T090000",
            RecurrenceErrorReason.OUT_OF_RANGE,
            "RDATE",
        )
        assertPeriodError(
            "DTSTART:20240101T090000\n" +
                "RDATE;VALUE=PERIOD:20240102T090000/20240102T100000Z",
            RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE,
            "RDATE",
        )
    }

    @Test
    fun rejectsPeriodStartsOutsideDtstartsTemporalDomainAndUtcValuesWithTzid() {
        assertPeriodError(
            "DTSTART:20240101T090000\nRDATE;VALUE=PERIOD:20240102T090000Z/PT1H",
            RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE,
            "RDATE",
        )
        assertPeriodError(
            "DTSTART;TZID=Europe/Paris:20240101T090000\n" +
                "RDATE;VALUE=PERIOD;TZID=Europe/Paris:20240102T090000Z/PT1H",
            RecurrenceErrorReason.INVALID_COMBINATION,
            "TZID",
        )
    }

    private fun assertPeriodError(
        content: String,
        reason: RecurrenceErrorReason,
        propertyName: String,
    ) {
        val error = assertFailsWith<RecurrenceParseException>(content) {
            RecurrenceContentParser.parse(content)
        }
        assertEquals(reason, error.reason, content)
        assertEquals(propertyName, error.propertyName, content)
        assertEquals(content, error.inputValue, content)
        assertTrue(error.position != null, content)
    }

    private fun floating(year: Int, month: Int, day: Int, hour: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, hour, 0))

    private fun zoned(year: Int, month: Int, day: Int, hour: Int): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(LocalDateTime(year, month, day, hour, 0), "Europe/Paris")

    private fun utc(value: String): RecurrenceDateTime.Utc = RecurrenceDateTime.Utc(Instant.parse(value))
}
