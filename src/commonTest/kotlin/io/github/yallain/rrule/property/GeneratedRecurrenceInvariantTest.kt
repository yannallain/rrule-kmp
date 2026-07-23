package io.github.yallain.rrule.property

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.RecurrenceRuleSerializer
import io.github.yallain.rrule.RuleRecurrence
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratedRecurrenceInvariantTest {
    @Test
    fun seededValidRulesPreserveCoreInvariantsAndQueryParity() {
        val random = Random(SEED)

        repeat(CASE_COUNT) { caseIndex ->
            val start = randomStart(random)
            val rule = randomRule(random)
            val recurrence = RuleRecurrence(start, rule)
            val values = recurrence.occurrences().toList()
            val context = "seed=$SEED case=$caseIndex rule=$rule start=$start"

            assertEquals(rule.count, values.size, context)
            assertEquals(values.distinct(), values, context)
            assertEquals(values.sortedBy { (it as RecurrenceDateTime.Floating).dateTime }, values, context)
            assertEquals(rule, RecurrenceRuleParser.parse(RecurrenceRuleSerializer.serialize(rule)), context)

            values.forEachIndexed { index, value ->
                assertEquals(values.getOrNull(index - 1), recurrence.before(value), "$context before[$index]")
                assertEquals(value, recurrence.before(value, inclusive = true), "$context beforeInclusive[$index]")
                assertEquals(values.getOrNull(index + 1), recurrence.after(value), "$context after[$index]")
                assertEquals(value, recurrence.after(value, inclusive = true), "$context afterInclusive[$index]")
            }

            val lower = values[values.size / 3]
            val upper = values[values.size * 2 / 3]
            val lowerDateTime = (lower as RecurrenceDateTime.Floating).dateTime
            val upperDateTime = (upper as RecurrenceDateTime.Floating).dateTime
            val between = recurrence.between(lower, upper)
            assertTrue(between.all { value ->
                val dateTime = (value as RecurrenceDateTime.Floating).dateTime
                dateTime >= lowerDateTime && dateTime < upperDateTime
            }, context)
        }
    }

    private fun randomStart(random: Random): RecurrenceDateTime.Floating = RecurrenceDateTime.Floating(
        LocalDateTime(
            year = random.nextInt(1990, 2031),
            month = random.nextInt(1, 13),
            day = random.nextInt(1, 29),
            hour = random.nextInt(0, 24),
            minute = random.nextInt(0, 60),
            second = random.nextInt(0, 60),
        ),
    )

    private fun randomRule(random: Random): RecurrenceRule {
        val frequency = Frequency.entries.random(random)
        val interval = random.nextInt(1, 8)
        val count = random.nextInt(8, 25)
        return when (frequency) {
            Frequency.YEARLY -> RecurrenceRule(
                frequency = frequency,
                interval = interval,
                count = count,
                byMonth = setOf(random.nextInt(1, 13)),
                byMonthDay = setOf(random.nextInt(1, 29)),
            )
            Frequency.MONTHLY -> RecurrenceRule(
                frequency = frequency,
                interval = interval,
                count = count,
                byMonthDay = setOf(random.nextInt(1, 29), -1),
            )
            Frequency.WEEKLY -> RecurrenceRule(
                frequency = frequency,
                interval = interval,
                count = count,
                weekStart = Weekday.entries.random(random),
                byDay = listOf(ByDay(Weekday.entries.random(random))),
            )
            Frequency.DAILY -> RecurrenceRule(
                frequency = frequency,
                interval = interval,
                count = count,
                byHour = setOf(random.nextInt(0, 24)),
            )
            Frequency.HOURLY -> RecurrenceRule(
                frequency = frequency,
                interval = interval,
                count = count,
                byMinute = setOf(random.nextInt(0, 60), random.nextInt(0, 60)),
            )
            Frequency.MINUTELY -> RecurrenceRule(
                frequency = frequency,
                interval = interval,
                count = count,
                bySecond = setOf(random.nextInt(0, 60), random.nextInt(0, 60)),
            )
            Frequency.SECONDLY -> RecurrenceRule(
                frequency = frequency,
                interval = interval,
                count = count,
            )
        }
    }

    private companion object {
        const val SEED = 0x5545
        const val CASE_COUNT = 200
    }
}
