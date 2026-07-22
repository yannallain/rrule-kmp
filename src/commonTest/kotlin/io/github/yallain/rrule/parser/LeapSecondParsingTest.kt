package io.github.yallain.rrule.parser

import io.github.yallain.rrule.RecurrenceContentParser
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRuleParser
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Instant

class LeapSecondParsingTest {
    @Test
    fun interpretsSecond60As59InFloatingAndZonedContentValues() {
        val floating = RecurrenceContentParser.parse(
            """
                DTSTART:19970630T235960
                RRULE:FREQ=DAILY;UNTIL=19970701T235960
                RDATE:19970702T235960
                EXDATE:19970701T235960
            """.trimIndent(),
        )

        assertEquals(floating(1997, 6, 30, 23, 59, 59), floating.start)
        assertEquals(floating(1997, 7, 1, 23, 59, 59), floating.rules.single().until)
        assertEquals(setOf(floating(1997, 7, 2, 23, 59, 59)), floating.additionalDates)
        assertEquals(setOf(floating(1997, 7, 1, 23, 59, 59)), floating.excludedDates)

        assertEquals(
            RecurrenceDateTime.Zoned(LocalDateTime(1997, 6, 30, 23, 59, 59), "Europe/Paris"),
            RecurrenceContentParser.parse(
                "DTSTART;TZID=Europe/Paris:19970630T235960",
            ).start,
        )
    }

    @Test
    fun interpretsSecond60As59InUtcContentValues() {
        val definition = RecurrenceContentParser.parse(
            """
                DTSTART:19970630T235960Z
                RRULE:FREQ=DAILY;UNTIL=19970701T235960Z
                RDATE:19970702T235960Z
                EXDATE:19970701T235960Z
            """.trimIndent(),
        )

        assertEquals(utc("1997-06-30T23:59:59Z"), definition.start)
        assertEquals(utc("1997-07-01T23:59:59Z"), definition.rules.single().until)
        assertEquals(setOf(utc("1997-07-02T23:59:59Z")), definition.additionalDates)
        assertEquals(setOf(utc("1997-07-01T23:59:59Z")), definition.excludedDates)
    }

    @Test
    fun interpretsSecond60As59InStandaloneUntilAndStillRejects61() {
        assertEquals(
            floating(1997, 6, 30, 23, 59, 59),
            RecurrenceRuleParser.parse("FREQ=DAILY;UNTIL=19970630T235960").until,
        )
        assertEquals(
            utc("1997-06-30T23:59:59Z"),
            RecurrenceRuleParser.parse("FREQ=DAILY;UNTIL=19970630T235960Z").until,
        )
        assertFails {
            RecurrenceRuleParser.parse("FREQ=DAILY;UNTIL=19970630T235961")
        }
    }

    private fun floating(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): RecurrenceDateTime.Floating = RecurrenceDateTime.Floating(
        LocalDateTime(year, month, day, hour, minute, second),
    )

    private fun utc(value: String): RecurrenceDateTime.Utc =
        RecurrenceDateTime.Utc(Instant.parse(value))
}
