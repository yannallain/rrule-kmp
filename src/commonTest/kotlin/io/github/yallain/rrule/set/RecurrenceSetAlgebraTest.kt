package io.github.yallain.rrule.set

import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceSet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class RecurrenceSetAlgebraTest {
    @Test
    fun dateOnlySetOrdersRdatesBeforeDtStartAndAppliesExclusions() {
        val beforeStart = date(2023, 12, 31)
        val start = date(2024, 1, 1)
        val excluded = date(2024, 1, 2)
        val set = RecurrenceSet(
            start = start,
            rules = listOf(RecurrenceRule(Frequency.DAILY, count = 3)),
            additionalDates = setOf(beforeStart, excluded),
            excludedDates = setOf(excluded),
        )

        assertEquals(listOf(beforeStart, start, date(2024, 1, 3)), set.occurrences().toList())
        assertEquals(beforeStart, set.before(start))
        assertEquals(start, set.after(start, inclusive = true))
    }

    @Test
    fun utcSetUsesInstantOrderingForAllQueryDirections() {
        val start = utc("2024-01-01T23:00:00Z")
        val excluded = utc("2024-01-02T23:00:00Z")
        val set = RecurrenceSet(
            start = start,
            rules = listOf(RecurrenceRule(Frequency.DAILY, count = 3)),
            additionalDates = setOf(utc("2024-01-01T22:00:00Z")),
            excludedDates = setOf(excluded),
        )

        assertEquals(
            listOf(utc("2024-01-01T22:00:00Z"), start, utc("2024-01-03T23:00:00Z")),
            set.occurrences().toList(),
        )
        assertEquals(start, set.before(excluded))
        assertEquals(utc("2024-01-03T23:00:00Z"), set.after(start))
        assertNull(set.after(utc("2024-01-03T23:00:00Z")))
    }

    @Test
    fun excludedCandidateIsSkippedByInclusiveAndExclusiveQueries() {
        val excluded = floating(2024, 1, 2)
        val set = RecurrenceSet(
            start = floating(2024, 1, 1),
            rules = listOf(RecurrenceRule(Frequency.DAILY, count = 4)),
            excludedDates = setOf(excluded),
        )

        assertEquals(floating(2024, 1, 1), set.before(excluded, inclusive = true))
        assertEquals(floating(2024, 1, 3), set.after(excluded, inclusive = true))
        assertEquals(floating(2024, 1, 3), set.after(floating(2024, 1, 1)))
    }

    @Test
    fun constructorDefensivelyCopiesEveryMutableCollection() {
        val rule = RecurrenceRule(Frequency.DAILY, count = 2)
        val rules = mutableListOf(rule)
        val exclusionRules = mutableListOf<RecurrenceRule>()
        val additionalDates = mutableSetOf(floating(2024, 1, 2))
        val excludedDates = mutableSetOf<RecurrenceDateTime>()
        val set = RecurrenceSet(
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

        assertEquals(listOf(rule), set.rules)
        assertEquals(emptyList(), set.exclusionRules)
        assertEquals(setOf(floating(2024, 1, 2)), set.additionalDates)
        assertEquals(emptySet(), set.excludedDates)
        assertEquals(
            listOf(floating(2024, 1, 1), floating(2024, 1, 2)),
            set.occurrences().toList(),
        )
    }

    private fun date(year: Int, month: Int, day: Int): RecurrenceDateTime.DateOnly =
        RecurrenceDateTime.DateOnly(LocalDate(year, month, day))

    private fun floating(year: Int, month: Int, day: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, 9, 0))

    private fun utc(value: String): RecurrenceDateTime.Utc = RecurrenceDateTime.Utc(Instant.parse(value))
}
