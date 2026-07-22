package io.github.yallain.rrule.engine

import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.RuleRecurrence
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReverseQueryParityTest {
    @Test
    fun countedRulesMatchAForwardOracleForEveryFrequency() {
        val rules = listOf(
            "FREQ=YEARLY;COUNT=12;BYMONTH=2,9;BYMONTHDAY=2,20",
            "FREQ=MONTHLY;COUNT=12;BYMONTHDAY=2,20",
            "FREQ=WEEKLY;COUNT=12;BYDAY=TU,TH",
            "FREQ=DAILY;COUNT=12;BYHOUR=9,18",
            "FREQ=HOURLY;COUNT=12;BYMINUTE=0,30",
            "FREQ=MINUTELY;COUNT=12;BYSECOND=0,30",
            "FREQ=SECONDLY;INTERVAL=7;COUNT=12",
        )

        rules.forEach(::assertQueryParity)
    }

    @Test
    fun untilRulesMatchAForwardOracleForEveryFrequency() {
        val rules = listOf(
            "FREQ=YEARLY;UNTIL=20020902T090000",
            "FREQ=MONTHLY;UNTIL=19980302T090000",
            "FREQ=WEEKLY;UNTIL=19971002T090000;BYDAY=TU,TH",
            "FREQ=DAILY;UNTIL=19970907T090000",
            "FREQ=HOURLY;UNTIL=19970902T150000",
            "FREQ=MINUTELY;UNTIL=19970902T090500",
            "FREQ=SECONDLY;UNTIL=19970902T090005",
        )

        rules.forEach(::assertQueryParity)
    }

    @Test
    fun independentIteratorsDoNotShareEvaluationState() {
        val recurrence = RuleRecurrence(
            start = local(1997, 9, 2),
            rule = RecurrenceRuleParser.parse("FREQ=DAILY;COUNT=4"),
        )
        val first = recurrence.occurrences().iterator()
        val second = recurrence.occurrences().iterator()

        assertEquals(local(1997, 9, 2), first.next())
        assertEquals(local(1997, 9, 2), second.next())
        assertEquals(local(1997, 9, 3), second.next())
        assertEquals(local(1997, 9, 3), first.next())
        assertEquals(
            listOf(local(1997, 9, 4), local(1997, 9, 5)),
            first.asSequence().toList(),
        )
        assertEquals(
            listOf(local(1997, 9, 4), local(1997, 9, 5)),
            second.asSequence().toList(),
        )
    }

    private fun assertQueryParity(ruleValue: String) {
        val recurrence = RuleRecurrence(
            start = local(1997, 9, 2),
            rule = RecurrenceRuleParser.parse(ruleValue),
        )
        val forward = recurrence.occurrences().toList()

        forward.forEachIndexed { index, occurrence ->
            assertEquals(forward.getOrNull(index - 1), recurrence.before(occurrence), "$ruleValue before")
            assertEquals(occurrence, recurrence.before(occurrence, inclusive = true), "$ruleValue before inclusive")
            assertEquals(forward.getOrNull(index + 1), recurrence.after(occurrence), "$ruleValue after")
            assertEquals(occurrence, recurrence.after(occurrence, inclusive = true), "$ruleValue after inclusive")
        }

        val distantFuture = local(2200, 1, 1)
        assertEquals(forward.lastOrNull(), recurrence.before(distantFuture), "$ruleValue final before")
        assertNull(recurrence.after(distantFuture), "$ruleValue final after")
    }

    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 9,
        minute: Int = 0,
        second: Int = 0,
    ): RecurrenceDateTime.Floating = RecurrenceDateTime.Floating(
        LocalDateTime(year, month, day, hour, minute, second),
    )
}
