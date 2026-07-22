package io.github.yallain.rrule.regression

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceSet
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceSetRegressionTest {
    @Test
    fun mergesMultipleRulesAndDatesWithoutDuplicates() {
        val set = RecurrenceSet(
            start = local(1997, 9, 2),
            rules = listOf(
                yearlyRule(count = 2, days = listOf(Weekday.TUESDAY)),
                yearlyRule(count = 1, days = listOf(Weekday.THURSDAY)),
            ),
            additionalDates = setOf(local(1997, 9, 4), local(1997, 9, 9)),
        )

        assertEquals(
            listOf(local(1997, 9, 2), local(1997, 9, 4), local(1997, 9, 9)),
            set.occurrences().toList(),
        )
    }

    @Test
    fun exclusionRulesAndDatesTakePrecedence() {
        val inclusions = yearlyRule(count = 6, days = listOf(Weekday.TUESDAY, Weekday.THURSDAY))
        val expected = listOf(local(1997, 9, 2), local(1997, 9, 9), local(1997, 9, 16))

        assertEquals(
            expected,
            RecurrenceSet(
                start = local(1997, 9, 2),
                rules = listOf(inclusions),
                exclusionRules = listOf(yearlyRule(count = 3, days = listOf(Weekday.THURSDAY))),
            ).occurrences().toList(),
        )
        assertEquals(
            expected,
            RecurrenceSet(
                start = local(1997, 9, 2),
                rules = listOf(inclusions),
                excludedDates = setOf(
                    local(1997, 9, 18),
                    local(1997, 9, 4),
                    local(1997, 9, 11),
                ),
            ).occurrences().toList(),
        )
    }

    @Test
    fun exclusionOrderDoesNotAffectMonthlyRules() {
        val set = RecurrenceSet(
            start = local(2004, 1, 1),
            rules = listOf(
                RecurrenceRule(Frequency.MONTHLY, count = 5, byMonthDay = setOf(10)),
            ),
            excludedDates = setOf(local(2004, 4, 10), local(2004, 2, 10)),
        )

        assertEquals(
            listOf(local(2004, 1, 1), local(2004, 1, 10), local(2004, 3, 10), local(2004, 5, 10)),
            set.occurrences().toList(),
        )
    }

    @Test
    fun boundedQueriesSkipAFiniteExcludedPrefixOfAnInfiniteRule() {
        val set = RecurrenceSet(
            start = local(1997, 9, 2),
            rules = listOf(RecurrenceRule(Frequency.YEARLY)),
            exclusionRules = listOf(RecurrenceRule(Frequency.YEARLY, count = 10)),
        )

        assertEquals(
            listOf(local(2007, 9, 2), local(2008, 9, 2), local(2009, 9, 2)),
            set.between(local(2000, 9, 2), local(2010, 9, 2)),
        )
        assertEquals(local(2007, 9, 2), set.after(local(2000, 9, 2)))
        assertEquals(local(2014, 9, 2), set.before(local(2015, 9, 2)))
    }

    @Test
    fun supportsDatesBeforeTheUnixEpoch() {
        val set = RecurrenceSet(
            start = local(1960, 1, 1),
            rules = listOf(RecurrenceRule(Frequency.YEARLY, count = 2)),
        )

        assertEquals(listOf(local(1960, 1, 1), local(1961, 1, 1)), set.occurrences().toList())
    }

    private fun yearlyRule(count: Int, days: List<Weekday>): RecurrenceRule = RecurrenceRule(
        frequency = Frequency.YEARLY,
        count = count,
        byDay = days.map(::ByDay),
    )

    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 9,
    ): RecurrenceDateTime.Floating = RecurrenceDateTime.Floating(
        LocalDateTime(year, month, day, hour, 0),
    )
}
