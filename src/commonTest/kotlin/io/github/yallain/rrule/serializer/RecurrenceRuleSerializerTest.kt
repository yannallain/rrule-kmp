package io.github.yallain.rrule.serializer

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.RecurrenceRuleSerializer
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceRuleSerializerTest {
    @Test
    fun serializesInStableCanonicalOrder() {
        val rule = RecurrenceRule(
            frequency = Frequency.YEARLY,
            interval = 2,
            until = RecurrenceDateTime.Floating(LocalDateTime(2030, 12, 31, 23, 59, 58)),
            weekStart = Weekday.SUNDAY,
            bySecond = setOf(30, 0),
            byMinute = setOf(45, 15),
            byHour = setOf(17, 9),
            byDay = listOf(ByDay(Weekday.FRIDAY), ByDay(Weekday.MONDAY)),
            byMonthDay = setOf(-1, 1),
            byYearDay = setOf(-1, 32),
            byWeekNumber = setOf(-1, 1),
            byMonth = setOf(12, 1),
            bySetPosition = setOf(-1, 1),
        )

        assertEquals(
            "FREQ=YEARLY;UNTIL=20301231T235958;INTERVAL=2;" +
                "BYSECOND=0,30;BYMINUTE=15,45;BYHOUR=9,17;" +
                "BYDAY=MO,FR;BYMONTHDAY=-1,1;BYYEARDAY=-1,32;" +
                "BYWEEKNO=-1,1;BYMONTH=1,12;BYSETPOS=-1,1;WKST=SU",
            RecurrenceRuleSerializer.serialize(rule),
        )
    }

    @Test
    fun omitsDefaultValues() {
        assertEquals(
            "FREQ=DAILY",
            RecurrenceRuleSerializer.serialize(RecurrenceRule(Frequency.DAILY)),
        )
    }

    @Test
    fun parseSerializeParsePreservesSemantics() {
        val inputs = listOf(
            "FREQ=DAILY;INTERVAL=2;COUNT=10",
            "FREQ=MONTHLY;BYDAY=1MO,-1FR",
            "FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU",
            "freq=weekly;wkst=su;byday=fr,mo,we;byhour=18,9",
            "FREQ=DAILY;UNTIL=20271231T235959Z",
        )

        for (input in inputs) {
            val parsed = RecurrenceRuleParser.parse(input)
            val reparsed = RecurrenceRuleParser.parse(RecurrenceRuleSerializer.serialize(parsed))
            assertEquals(parsed, reparsed, input)
        }
    }
}
