package io.github.yallain.rrule.parser

import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.LocalTimeResolution
import io.github.yallain.rrule.RecurrenceContentParser
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceErrorReason
import io.github.yallain.rrule.RecurrenceParseException
import io.github.yallain.rrule.RecurrenceTimeZoneResolver
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecurrenceContentParserTest {
    @Test
    fun parsesTheSchedulePayloadAndBindsItDirectlyToARecurrenceSet() {
        val content = """
            DTSTART;TZID=Europe/Paris:20170101T210000
            RRULE:FREQ=DAILY;WKST=MO
        """.trimIndent()

        val definition = RecurrenceContentParser.parse(content)

        assertEquals(zoned(2017, 1, 1, 21), definition.start)
        assertEquals(1, definition.rules.size)
        assertEquals(Frequency.DAILY, definition.rules.single().frequency)
        assertEquals(
            listOf(zoned(2017, 1, 2, 21), zoned(2017, 1, 3, 21)),
            definition.recurrenceSet().between(
                zoned(2017, 1, 2, 0),
                zoned(2017, 1, 4, 0),
            ),
        )
    }

    @Test
    fun preservesDateOnlyFloatingUtcAndZonedStartSemantics() {
        assertEquals(
            RecurrenceDateTime.DateOnly(LocalDate(2024, 1, 2)),
            RecurrenceContentParser.parse("dtstart;value=date:20240102").start,
        )
        assertEquals(
            floating(2024, 1, 2, 9),
            RecurrenceContentParser.parse("DTSTART:20240102T090000").start,
        )
        assertEquals(
            utc("2024-01-02T09:00:00Z"),
            RecurrenceContentParser.parse("DTSTART;VALUE=DATE-TIME:20240102T090000Z").start,
        )
        assertEquals(
            zoned(2024, 1, 2, 9),
            RecurrenceContentParser.parse("DTSTART;TZID=\"Europe/Paris\":20240102T090000").start,
        )
    }

    @Test
    fun parsesCompleteRecurrenceSetSourcesAndAppliesExclusionPrecedence() {
        val definition = RecurrenceContentParser.parse(
            """
                DTSTART;TZID=Europe/Paris:20240101T090000
                RRULE:FREQ=DAILY;COUNT=3
                RDATE;TZID=Europe/Paris:20240105T090000,20240106T090000
                EXDATE;TZID=Europe/Paris:20240102T090000,20240103T090000
            """.trimIndent(),
        )

        assertEquals(1, definition.rules.size)
        assertEquals(emptyList(), definition.exclusionRules)
        assertEquals(setOf(zoned(2024, 1, 5, 9), zoned(2024, 1, 6, 9)), definition.additionalDates)
        assertEquals(
            setOf(zoned(2024, 1, 2, 9), zoned(2024, 1, 3, 9)),
            definition.excludedDates,
        )
        assertEquals(
            listOf(
                zoned(2024, 1, 1, 9),
                zoned(2024, 1, 5, 9),
                zoned(2024, 1, 6, 9),
            ),
            definition.recurrenceSet().occurrences().toList(),
        )
    }

    @Test
    fun mergesAndExcludesAbsoluteDatesAcrossUtcAndDifferentTzids() {
        val definition = RecurrenceContentParser.parse(
            """
                DTSTART;TZID=Europe/Paris:20240101T090000
                RRULE:FREQ=DAILY;COUNT=3
                RDATE:20240101T080000Z
                RDATE;TZID=America/New_York:20240104T030000
                EXDATE:20240102T080000Z
                EXDATE;TZID=America/New_York:20240103T030000
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                zoned(2024, 1, 1, 9),
                zonedIn(2024, 1, 4, 3, "America/New_York"),
            ),
            definition.recurrenceSet().between(
                utc("2024-01-01T00:00:00Z"),
                utc("2024-01-05T00:00:00Z"),
            ),
        )
    }

    @Test
    fun unfoldsCrLfAndTabOrSpaceContinuationsBeforeParsing() {
        val definition = RecurrenceContentParser.parse(
            "DTSTART:20240101T090000\r\n" +
                "RRULE:FREQ=DAILY;\r\n" +
                " COUNT=2\r\n" +
                "RDATE:20240103T090000,\r\n" +
                "\t20240104T090000",
        )

        assertEquals(2, definition.rules.single().count)
        assertEquals(
            setOf(floating(2024, 1, 3, 9), floating(2024, 1, 4, 9)),
            definition.additionalDates,
        )
    }

    @Test
    fun foldedRuleErrorsRetainTheirPhysicalSourcePosition() {
        val content = "DTSTART:20240101T090000\r\n" +
            "RRULE:FREQ=DAILY;BYHOUR=9,\r\n" +
            " noon"

        val error = assertFailsWith<RecurrenceParseException> {
            RecurrenceContentParser.parse(content)
        }

        assertEquals("noon", error.invalidToken)
        assertEquals(content.indexOf("noon"), error.position)
    }

    @Test
    fun foldedParameterErrorsRetainTheirPhysicalSourcePosition() {
        val content = "DTSTART;X-NOTE=\"valid\r\n" +
            " \u0001invalid\":20240101T090000"

        val error = assertFailsWith<RecurrenceParseException> {
            RecurrenceContentParser.parse(content)
        }

        assertEquals("\u0001", error.invalidToken)
        assertEquals(content.indexOf('\u0001'), error.position)
    }

    @Test
    fun blankPhysicalLineBreaksFoldAdjacency() {
        val content = "DTSTART:20240101T090000\r\n\r\n RRULE:FREQ=DAILY"

        val error = assertFailsWith<RecurrenceParseException> {
            RecurrenceContentParser.parse(content)
        }

        assertEquals(RecurrenceErrorReason.MALFORMED_TOKEN, error.reason)
        assertEquals(content.indexOf(" RRULE"), error.position)
    }

    @Test
    fun ignoresRepeatableIanaAndExperimentalParameters() {
        val definition = RecurrenceContentParser.parse(
            """
                DTSTART;X-SOURCE=api;X-QUERY=a=b;X-LIST="one,two",three;VENDOR-FLAG=one;VENDOR-FLAG=two:20240101T090000
                RRULE;X-SOURCE=api;VENDOR-NAME="night":FREQ=DAILY;COUNT=3
                RDATE;X-SOURCE=api:20240105T090000
                EXDATE;VENDOR-TRACE=42:20240102T090000
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                floating(2024, 1, 1, 9),
                floating(2024, 1, 3, 9),
                floating(2024, 1, 5, 9),
            ),
            definition.recurrenceSet().occurrences().toList(),
        )
    }

    @Test
    fun doesNotRequireAnIanaTimezoneWhenParsingApplicationOwnedTzids() {
        val definition = RecurrenceContentParser.parse(
            "DTSTART;TZID=Company/Night:20240101T210000\nRRULE:FREQ=DAILY;COUNT=2",
        )
        val resolver = RecurrenceTimeZoneResolver { localDateTime, _ ->
            LocalTimeResolution.Valid(localDateTime.toInstant(UtcOffset.ZERO))
        }

        assertEquals(
            listOf(
                customZoned(2024, 1, 1, 21),
                customZoned(2024, 1, 2, 21),
            ),
            definition.recurrenceSet(timeZoneResolver = resolver).occurrences().toList(),
        )
    }

    @Test
    fun reportsMissingDuplicateUnknownAndMalformedContentLines() {
        assertContentError(
            "RRULE:FREQ=DAILY",
            RecurrenceErrorReason.MISSING_REQUIRED_PROPERTY,
            "DTSTART",
        )
        assertContentError(
            "DTSTART:20240101T090000\nDTSTART:20240102T090000",
            RecurrenceErrorReason.DUPLICATE_PROPERTY,
            "DTSTART",
        )
        assertContentError(
            "DTSTART:20240101T090000\nSUMMARY:Night",
            RecurrenceErrorReason.UNKNOWN_PROPERTY,
            "SUMMARY",
        )
        assertContentError(
            "DTSTART:20240101T090000\nEXRULE:FREQ=DAILY",
            RecurrenceErrorReason.UNKNOWN_PROPERTY,
            "EXRULE",
        )
        assertContentError(
            "DTSTART:20240101T090000\nRRULE:FREQ=DAILY\nRRULE:FREQ=WEEKLY",
            RecurrenceErrorReason.DUPLICATE_PROPERTY,
            "RRULE",
        )
        assertContentError(
            "DTSTART:20240101T090000\nRRULE=FREQ=DAILY",
            RecurrenceErrorReason.MALFORMED_TOKEN,
            null,
        )
        assertContentError(
            " DTSTART:20240101T090000",
            RecurrenceErrorReason.MALFORMED_TOKEN,
            null,
        )
    }

    @Test
    fun rejectsInvalidDtStartParametersAndTemporalForms() {
        val cases = listOf(
            "DTSTART;TZID=:20240101T090000" to RecurrenceErrorReason.EMPTY_VALUE,
            "DTSTART;TZID=Europe/Paris:20240101T090000Z" to RecurrenceErrorReason.INVALID_COMBINATION,
            "DTSTART;VALUE=DATE;TZID=Europe/Paris:20240101" to RecurrenceErrorReason.INVALID_COMBINATION,
            "DTSTART;VALUE=PERIOD:20240101T090000" to RecurrenceErrorReason.MALFORMED_TOKEN,
            "DTSTART;LANGUAGE=fr:20240101T090000" to RecurrenceErrorReason.INVALID_COMBINATION,
            "DTSTART:20240230T090000" to RecurrenceErrorReason.MALFORMED_TOKEN,
            "DTSTART:20240101" to RecurrenceErrorReason.MALFORMED_TOKEN,
        )

        for ((content, reason) in cases) {
            val error = assertFailsWith<RecurrenceParseException> {
                RecurrenceContentParser.parse(content)
            }
            assertEquals(reason, error.reason, content)
            assertEquals(content, error.inputValue, content)
            assertTrue(error.position != null, content)
        }
    }

    @Test
    fun rejectsParametersWhereTheyAreNotAllowedAndDuplicateParameters() {
        assertContentError(
            "DTSTART;TZID=Europe/Paris;TZID=Europe/London:20240101T090000",
            RecurrenceErrorReason.DUPLICATE_PROPERTY,
            "TZID",
        )
        assertContentError(
            "DTSTART:20240101T090000\nRRULE;TZID=Europe/Paris:FREQ=DAILY",
            RecurrenceErrorReason.INVALID_COMBINATION,
            "TZID",
        )
        assertContentError(
            "DTSTART:20240101T090000\n" +
                "RDATE;VALUE=PERIOD;VALUE=DATE-TIME:20240102T090000/20240102T100000",
            RecurrenceErrorReason.DUPLICATE_PROPERTY,
            "VALUE",
        )
        assertContentError(
            "DTSTART;TZID=Europe/Paris,Europe/London:20240101T090000",
            RecurrenceErrorReason.MALFORMED_TOKEN,
            "TZID",
        )
        assertContentError(
            "DTSTART;X-BAD=ok\u0001:20240101T090000",
            RecurrenceErrorReason.MALFORMED_TOKEN,
            null,
        )
    }

    @Test
    fun rejectsValuesAndRulesThatAreIncompatibleWithDtStart() {
        assertContentError(
            "DTSTART;TZID=Europe/Paris:20240101T090000\nRDATE:20240102T090000",
            RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE,
            "RDATE",
        )
        assertContentError(
            "DTSTART;VALUE=DATE:20240101\nRRULE:FREQ=HOURLY;COUNT=2",
            RecurrenceErrorReason.INVALID_COMBINATION,
            "FREQ",
        )
        assertContentError(
            "DTSTART;TZID=Europe/Paris:20240101T090000\nRRULE:FREQ=DAILY;UNTIL=20240102T090000",
            RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE,
            "UNTIL",
        )

        val content = "DTSTART:20240101T090000\n" +
            "RDATE:20240102T090000\n" +
            "RDATE:20240103T090000Z"
        val error = assertFailsWith<RecurrenceParseException> {
            RecurrenceContentParser.parse(content)
        }
        assertEquals(RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE, error.reason)
        assertEquals("RDATE", error.propertyName)
        assertEquals(content.lastIndexOf("20240103T090000Z"), error.position)
    }

    private fun assertContentError(
        content: String,
        reason: RecurrenceErrorReason,
        property: String?,
    ) {
        val error = assertFailsWith<RecurrenceParseException> {
            RecurrenceContentParser.parse(content)
        }
        assertEquals(reason, error.reason, content)
        assertEquals(property, error.propertyName, content)
        assertEquals(content, error.inputValue, content)
    }

    private fun floating(year: Int, month: Int, day: Int, hour: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, hour, 0))

    private fun zoned(year: Int, month: Int, day: Int, hour: Int): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(LocalDateTime(year, month, day, hour, 0), "Europe/Paris")

    private fun customZoned(year: Int, month: Int, day: Int, hour: Int): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(LocalDateTime(year, month, day, hour, 0), "Company/Night")

    private fun zonedIn(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        timeZoneId: String,
    ): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(LocalDateTime(year, month, day, hour, 0), timeZoneId)

    private fun utc(value: String): RecurrenceDateTime.Utc = RecurrenceDateTime.Utc(Instant.parse(value))
}
