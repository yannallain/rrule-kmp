package io.github.yallain.rrule.property

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RuleRecurrence
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurrencePropertyTest {
    @Test
    fun generatedFiniteRulesAreSortedUniqueAndCountBounded() {
        val starts = listOf(
            local(1999, 12, 31),
            local(2000, 2, 29),
            local(2024, 1, 31),
        )
        val rules = buildList {
            for (interval in 1..5) {
                add(RecurrenceRule(Frequency.DAILY, interval = interval, count = 40))
                add(
                    RecurrenceRule(
                        Frequency.WEEKLY,
                        interval = interval,
                        count = 40,
                        byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.FRIDAY)),
                    ),
                )
                add(
                    RecurrenceRule(
                        Frequency.MONTHLY,
                        interval = interval,
                        count = 40,
                        byMonthDay = setOf(1, 15, -1),
                    ),
                )
                add(
                    RecurrenceRule(
                        Frequency.YEARLY,
                        interval = interval,
                        count = 40,
                        byMonth = setOf(2, 6, 12),
                        byMonthDay = setOf(1, -1),
                    ),
                )
            }
        }

        for (start in starts) {
            for (rule in rules) {
                val values = RuleRecurrence(start, rule).occurrences().toList()
                assertTrue(values.size <= rule.count!!)
                assertEquals(values.distinct(), values, rule.toString())
                assertEquals(values.sortedBy { (it as RecurrenceDateTime.Floating).dateTime }, values, rule.toString())
            }
        }
    }

    @Test
    fun boundedQueriesAlwaysRespectTheHalfOpenRangeAndLimit() {
        val recurrence = RuleRecurrence(
            local(2000, 1, 1),
            RecurrenceRule(
                Frequency.MONTHLY,
                byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.WEDNESDAY)),
            ),
        )

        for (year in 2000..2040 step 4) {
            val lower = local(year, 2, 1)
            val upper = local(year, 3, 1)
            val values = recurrence.between(lower, upper, limit = 5)
            assertTrue(values.size <= 5)
            assertTrue(values.all {
                val dateTime = (it as RecurrenceDateTime.Floating).dateTime
                dateTime >= lower.dateTime && dateTime < upper.dateTime
            })
            assertEquals(values.distinct(), values)
        }
    }

    private fun local(year: Int, month: Int, day: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, 9, 0))
}
