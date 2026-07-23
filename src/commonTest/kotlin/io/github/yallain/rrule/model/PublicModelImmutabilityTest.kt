package io.github.yallain.rrule.model

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceDefinition
import io.github.yallain.rrule.RecurrenceDuration
import io.github.yallain.rrule.RecurrencePeriod
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceSet
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PublicModelImmutabilityTest {
    @Test
    fun everyRuleCollectionRejectsMutationWithoutChangingValueSemantics() {
        val rule = ruleWithEveryCollection()
        val equalRule = ruleWithEveryCollection()
        val initialHashCode = rule.hashCode()

        listOf(
            rule.bySecond,
            rule.byMinute,
            rule.byHour,
            rule.byMonthDay,
            rule.byYearDay,
            rule.byWeekNumber,
            rule.byMonth,
            rule.bySetPosition,
        ).forEach(::assertSetCannotBeMutated)
        assertListCannotBeMutated(rule.byDay)

        assertEquals(equalRule, rule)
        assertEquals(initialHashCode, rule.hashCode())
    }

    @Test
    fun definitionCollectionsRejectMutationWithoutChangingValueSemantics() {
        val firstRule = RecurrenceRule(Frequency.DAILY, count = 4)
        val secondRule = RecurrenceRule(Frequency.WEEKLY, count = 2)
        val definition = RecurrenceDefinition(
            start = floating(2024, 1, 1),
            rules = listOf(firstRule, secondRule),
            exclusionRules = listOf(
                RecurrenceRule(Frequency.MONTHLY, count = 2),
                RecurrenceRule(Frequency.YEARLY, count = 2),
            ),
            additionalDates = setOf(floating(2024, 1, 10), floating(2024, 1, 11)),
            additionalPeriods = periods(),
            excludedDates = setOf(floating(2024, 1, 2), floating(2024, 1, 3)),
        )
        val equalDefinition = RecurrenceDefinition(
            start = definition.start,
            rules = definition.rules,
            exclusionRules = definition.exclusionRules,
            additionalDates = definition.additionalDates,
            additionalPeriods = definition.additionalPeriods,
            excludedDates = definition.excludedDates,
        )
        val initialHashCode = definition.hashCode()

        assertListCannotBeMutated(definition.rules)
        assertListCannotBeMutated(definition.exclusionRules)
        assertSetCannotBeMutated(definition.additionalDates)
        assertSetCannotBeMutated(definition.additionalPeriods)
        assertSetCannotBeMutated(definition.excludedDates)

        assertEquals(equalDefinition, definition)
        assertEquals(initialHashCode, definition.hashCode())
    }

    @Test
    fun recurrenceSetCollectionsRejectMutationWithoutChangingEvaluation() {
        val recurrenceSet = RecurrenceSet(
            start = floating(2024, 1, 1),
            rules = listOf(
                RecurrenceRule(Frequency.DAILY, count = 4),
                RecurrenceRule(Frequency.WEEKLY, count = 2),
            ),
            exclusionRules = listOf(
                RecurrenceRule(Frequency.MONTHLY, count = 2, byMonthDay = setOf(20, 21)),
                RecurrenceRule(Frequency.YEARLY, count = 2, byMonth = setOf(7, 8)),
            ),
            additionalDates = setOf(floating(2024, 1, 10), floating(2024, 1, 11)),
            additionalPeriods = periods(),
            excludedDates = setOf(floating(2024, 1, 2), floating(2024, 1, 3)),
        )
        val expected = recurrenceSet.between(
            startInclusive = floating(2024, 1, 1),
            endExclusive = floating(2024, 1, 12),
        )

        assertListCannotBeMutated(recurrenceSet.rules)
        assertListCannotBeMutated(recurrenceSet.exclusionRules)
        assertSetCannotBeMutated(recurrenceSet.additionalDates)
        assertSetCannotBeMutated(recurrenceSet.additionalPeriods)
        assertSetCannotBeMutated(recurrenceSet.excludedDates)

        assertEquals(
            listOf(
                floating(2024, 1, 1),
                floating(2024, 1, 4),
                floating(2024, 1, 6),
                floating(2024, 1, 7),
                floating(2024, 1, 8),
                floating(2024, 1, 10),
                floating(2024, 1, 11),
            ),
            expected,
        )
        assertEquals(
            expected,
            recurrenceSet.between(
                startInclusive = floating(2024, 1, 1),
                endExclusive = floating(2024, 1, 12),
            ),
        )
    }

    private fun ruleWithEveryCollection(): RecurrenceRule = RecurrenceRule(
        frequency = Frequency.YEARLY,
        count = 4,
        bySecond = setOf(1, 2),
        byMinute = setOf(3, 4),
        byHour = setOf(5, 6),
        byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.TUESDAY)),
        byMonthDay = setOf(7, 8),
        byYearDay = setOf(9, 10),
        byWeekNumber = setOf(11, 12),
        byMonth = setOf(1, 2),
        bySetPosition = setOf(1, 2),
    )

    private fun periods(): Set<RecurrencePeriod> = setOf(
        RecurrencePeriod.Explicit(
            start = floating(2024, 1, 6),
            end = RecurrenceDateTime.Floating(LocalDateTime(2024, 1, 6, 10, 0)),
        ),
        RecurrencePeriod.WithDuration(
            start = floating(2024, 1, 7),
            duration = RecurrenceDuration(hours = 1),
        ),
    )

    private fun assertListCannotBeMutated(values: List<*>) {
        check(values.size >= 2) { "The regression requires a multi-element collection" }
        assertFails {
            @Suppress("UNCHECKED_CAST")
            (values as MutableList<Any?>).clear()
        }
    }

    private fun assertSetCannotBeMutated(values: Set<*>) {
        check(values.size >= 2) { "The regression requires a multi-element collection" }
        assertFails {
            @Suppress("UNCHECKED_CAST")
            (values as MutableSet<Any?>).clear()
        }
    }

    private fun floating(year: Int, month: Int, day: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, 9, 0))
}
