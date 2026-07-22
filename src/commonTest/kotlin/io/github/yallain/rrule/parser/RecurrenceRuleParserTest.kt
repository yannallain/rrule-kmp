package io.github.yallain.rrule.parser

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceErrorReason
import io.github.yallain.rrule.RecurrenceParseException
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecurrenceRuleParserTest {
    @Test
    fun parsesAllSupportedRulePartsCaseInsensitively() {
        val rule = RecurrenceRuleParser.parse(
            "freq=yearly;interval=2;count=4;wkst=su;" +
                "bysecond=0,30;byminute=5,35;byhour=9,17;" +
                "byday=mo,fr;BYMONTHDAY=1,-1;BYYEARDAY=32,-1;" +
                "BYWEEKNO=1,-1;BYMONTH=1,12;BYSETPOS=1,-1",
        )

        assertEquals(Frequency.YEARLY, rule.frequency)
        assertEquals(2, rule.interval)
        assertEquals(4, rule.count)
        assertEquals(Weekday.SUNDAY, rule.weekStart)
        assertEquals(setOf(0, 30), rule.bySecond)
        assertEquals(setOf(5, 35), rule.byMinute)
        assertEquals(setOf(9, 17), rule.byHour)
        assertEquals(
            listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.FRIDAY)),
            rule.byDay,
        )
        assertEquals(setOf(1, -1), rule.byMonthDay)
        assertEquals(setOf(32, -1), rule.byYearDay)
        assertEquals(setOf(1, -1), rule.byWeekNumber)
        assertEquals(setOf(1, 12), rule.byMonth)
        assertEquals(setOf(1, -1), rule.bySetPosition)
    }

    @Test
    fun normalizesDuplicateListValues() {
        val rule = RecurrenceRuleParser.parse("FREQ=WEEKLY;BYDAY=MO,MO,FR;BYHOUR=9,9")

        assertEquals(listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.FRIDAY)), rule.byDay)
        assertEquals(setOf(9), rule.byHour)
    }

    @Test
    fun parsesEachUntilTemporalKindRepresentableInAnRruleValue() {
        assertEquals(
            RecurrenceDateTime.DateOnly(LocalDate(2025, 2, 3)),
            RecurrenceRuleParser.parse("FREQ=DAILY;UNTIL=20250203").until,
        )
        assertEquals(
            RecurrenceDateTime.Floating(LocalDateTime(2025, 2, 3, 4, 5, 6)),
            RecurrenceRuleParser.parse("FREQ=DAILY;UNTIL=20250203T040506").until,
        )
        assertEquals(
            RecurrenceDateTime.Utc(Instant.parse("2025-02-03T04:05:06Z")),
            RecurrenceRuleParser.parse("FREQ=DAILY;UNTIL=20250203T040506Z").until,
        )
    }

    @Test
    fun rejectsMissingUnknownDuplicateEmptyAndMalformedPartsWithContext() {
        assertParseError("INTERVAL=2", RecurrenceErrorReason.MISSING_REQUIRED_PROPERTY, "FREQ")
        assertParseError("FREQ=DAILY;X-THING=1", RecurrenceErrorReason.UNKNOWN_PROPERTY, "X-THING")
        assertParseError("FREQ=DAILY;FREQ=WEEKLY", RecurrenceErrorReason.DUPLICATE_PROPERTY, "FREQ")
        assertParseError("FREQ=DAILY;COUNT=", RecurrenceErrorReason.EMPTY_VALUE, "COUNT")
        assertParseError("FREQ=DAILY;COUNT=many", RecurrenceErrorReason.MALFORMED_TOKEN, "COUNT")
        assertParseError("FREQ=DAILY;;COUNT=2", RecurrenceErrorReason.EMPTY_VALUE, null)
    }

    @Test
    fun convertsModelValidationFailuresToParseFailures() {
        val error = assertFailsWith<RecurrenceParseException> {
            RecurrenceRuleParser.parse("FREQ=DAILY;COUNT=1;UNTIL=20250101T000000Z")
        }

        assertEquals(RecurrenceErrorReason.MUTUALLY_EXCLUSIVE, error.reason)
        assertEquals("COUNT", error.propertyName)
        assertEquals("FREQ=DAILY;COUNT=1;UNTIL=20250101T000000Z", error.inputValue)
        assertNotNull(error.position)
    }

    @Test
    fun reportsTheInvalidTokenPosition() {
        val input = "FREQ=DAILY;BYHOUR=9,noon"
        val error = assertFailsWith<RecurrenceParseException> {
            RecurrenceRuleParser.parse(input)
        }

        assertEquals("noon", error.invalidToken)
        assertEquals(input.indexOf("noon"), error.position)
        assertTrue(error.message.orEmpty().contains("noon"))
        assertIs<IllegalArgumentException>(error)
    }

    @Test
    fun reportsTheExactPositionOfTokensRejectedByModelValidation() {
        val rangeInput = "FREQ=DAILY;BYHOUR=9,24"
        val rangeError = assertFailsWith<RecurrenceParseException> {
            RecurrenceRuleParser.parse(rangeInput)
        }
        assertEquals("24", rangeError.invalidToken)
        assertEquals(rangeInput.indexOf("24"), rangeError.position)

        val ordinalInput = "FREQ=MONTHLY;BYDAY=MO,+54MO"
        val ordinalError = assertFailsWith<RecurrenceParseException> {
            RecurrenceRuleParser.parse(ordinalInput)
        }
        assertEquals("54", ordinalError.invalidToken)
        assertEquals(ordinalInput.indexOf("+54MO"), ordinalError.position)
    }

    @Test
    fun rejectsMalformedSeparatorsListsIntegersAndCalendarValuesAtTheExactToken() {
        val cases = listOf(
            ErrorCase("FREQ=DAILY;COUNT=2147483648", "COUNT", "2147483648"),
            ErrorCase("FREQ=DAILY;BYHOUR=1,,2", "BYHOUR", "", expectedPosition = 20),
            ErrorCase("FREQ=DAILY;BYHOUR=,1", "BYHOUR", "", expectedPosition = 18),
            ErrorCase("FREQ=DAILY;BYHOUR=1,", "BYHOUR", "", expectedPosition = 20),
            ErrorCase("FREQ=DAILY;INTERVAL=1=2", null, "INTERVAL=1=2", expectedPosition = 11),
            ErrorCase("FREQ=DAILY;UNTIL=20250230T090000", "UNTIL", "20250230T090000"),
            ErrorCase("FREQ=DAILY;UNTIL=20240101T240000", "UNTIL", "20240101T240000"),
            ErrorCase("FREQ=DAILY;BYDAY=1", "BYDAY", "1"),
            ErrorCase("FREQ=DAILY;WKST=MON", "WKST", "MON"),
        )

        for (case in cases) {
            val error = assertFailsWith<RecurrenceParseException> {
                RecurrenceRuleParser.parse(case.input)
            }
            assertEquals(case.property, error.propertyName, case.input)
            assertEquals(case.token, error.invalidToken, case.input)
            assertEquals(case.expectedPosition ?: case.input.indexOf(case.token), error.position, case.input)
        }
    }

    @Test
    fun enforcesSignedAndUnsignedIntegerGrammarPerRulePart() {
        val invalidUnsignedValues = listOf(
            "COUNT=+1",
            "COUNT=-1",
            "INTERVAL=+2",
            "BYSECOND=+1",
            "BYMINUTE=-1",
            "BYHOUR=+9",
            "BYMONTH=-1",
        )
        for (rulePart in invalidUnsignedValues) {
            val input = "FREQ=YEARLY;$rulePart"
            val error = assertFailsWith<RecurrenceParseException> {
                RecurrenceRuleParser.parse(input)
            }
            assertEquals(RecurrenceErrorReason.MALFORMED_TOKEN, error.reason, input)
            assertEquals(rulePart.substringBefore('='), error.propertyName, input)
        }

        val signed = RecurrenceRuleParser.parse(
            "FREQ=YEARLY;BYMONTHDAY=+1,-1;BYYEARDAY=+2,-2;" +
                "BYWEEKNO=+3,-3;BYSETPOS=+1,-1",
        )
        assertEquals(setOf(1, -1), signed.byMonthDay)
        assertEquals(setOf(2, -2), signed.byYearDay)
        assertEquals(setOf(3, -3), signed.byWeekNumber)
        assertEquals(setOf(1, -1), signed.bySetPosition)
        val ordinalDays = RecurrenceRuleParser.parse("FREQ=YEARLY;BYDAY=+1MO,-1FR").byDay
        assertEquals(listOf(ByDay(Weekday.FRIDAY, -1), ByDay(Weekday.MONDAY, 1)), ordinalDays)
    }

    @Test
    fun acceptsEachNumericByPartAtItsRfcDigitWidth() {
        val rule = RecurrenceRuleParser.parse(
            "FREQ=YEARLY;BYSECOND=00,60;BYMINUTE=00,59;BYHOUR=00,23;" +
                "BYMONTHDAY=+01,-31;BYYEARDAY=+001,-366;BYWEEKNO=+01,-53;" +
                "BYMONTH=01,12;BYSETPOS=+001,-366",
        )

        assertEquals(setOf(0, 60), rule.bySecond)
        assertEquals(setOf(0, 59), rule.byMinute)
        assertEquals(setOf(0, 23), rule.byHour)
        assertEquals(setOf(1, -31), rule.byMonthDay)
        assertEquals(setOf(1, -366), rule.byYearDay)
        assertEquals(setOf(1, -53), rule.byWeekNumber)
        assertEquals(setOf(1, 12), rule.byMonth)
        assertEquals(setOf(1, -366), rule.bySetPosition)

        assertEquals(
            listOf(ByDay(Weekday.SUNDAY, -53), ByDay(Weekday.MONDAY, 1)),
            RecurrenceRuleParser.parse("FREQ=YEARLY;BYDAY=+01MO,-53SU").byDay,
        )
    }

    @Test
    fun rejectsOverwideNumericByPartsAtTheExactListToken() {
        val cases = listOf(
            WidthErrorCase("FREQ=DAILY;BYSECOND=0,000", "BYSECOND", "000"),
            WidthErrorCase("FREQ=DAILY;BYMINUTE=0,000", "BYMINUTE", "000"),
            WidthErrorCase("FREQ=DAILY;BYHOUR=0,000", "BYHOUR", "000"),
            WidthErrorCase("FREQ=MONTHLY;BYMONTHDAY=1,+001", "BYMONTHDAY", "+001"),
            WidthErrorCase("FREQ=YEARLY;BYYEARDAY=1,-0001", "BYYEARDAY", "-0001"),
            WidthErrorCase("FREQ=YEARLY;BYWEEKNO=1,+001", "BYWEEKNO", "+001"),
            WidthErrorCase("FREQ=YEARLY;BYMONTH=1,001", "BYMONTH", "001"),
            WidthErrorCase("FREQ=YEARLY;BYMONTH=1;BYSETPOS=1,-0001", "BYSETPOS", "-0001"),
            WidthErrorCase("FREQ=MONTHLY;BYDAY=MO,+001TU", "BYDAY", "+001"),
        )

        for (case in cases) {
            val error = assertFailsWith<RecurrenceParseException> {
                RecurrenceRuleParser.parse(case.input)
            }

            assertEquals(RecurrenceErrorReason.MALFORMED_TOKEN, error.reason, case.input)
            assertEquals(case.property, error.propertyName, case.input)
            assertEquals(case.token, error.invalidToken, case.input)
            assertEquals(case.input.indexOf(case.token), error.position, case.input)
            assertTrue(error.message.orEmpty().contains("at most"), case.input)
        }
    }

    private fun assertParseError(
        input: String,
        reason: RecurrenceErrorReason,
        propertyName: String?,
    ) {
        val error = assertFailsWith<RecurrenceParseException> {
            RecurrenceRuleParser.parse(input)
        }
        assertEquals(reason, error.reason)
        assertEquals(propertyName, error.propertyName)
        assertEquals(input, error.inputValue)
    }

    private data class ErrorCase(
        val input: String,
        val property: String?,
        val token: String,
        val expectedPosition: Int? = null,
    )

    private data class WidthErrorCase(
        val input: String,
        val property: String,
        val token: String,
    )
}
