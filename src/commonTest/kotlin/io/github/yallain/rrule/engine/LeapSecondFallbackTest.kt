package io.github.yallain.rrule.engine

import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RuleRecurrence
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LeapSecondFallbackTest {
    @Test
    fun second60SelectsSecond59AtEveryFrequency() {
        val start = floating(2024, 1, 1, 0, 0, 58)

        for (frequency in Frequency.entries) {
            val occurrence = RuleRecurrence(
                start = start,
                rule = RecurrenceRule(
                    frequency = frequency,
                    count = 1,
                    bySecond = setOf(60),
                ),
            ).occurrences().single()

            assertEquals(floating(2024, 1, 1, 0, 0, 59), occurrence, frequency.name)
        }
    }

    @Test
    fun second59And60CollapseBeforeBySetPositionIsApplied() {
        val start = floating(2024, 1, 1, 0, 0, 58)
        val selected = RuleRecurrence(
            start,
            RecurrenceRule(
                frequency = Frequency.DAILY,
                count = 2,
                bySecond = setOf(59, 60),
                bySetPosition = setOf(1, -1),
            ),
        )

        assertEquals(
            listOf(
                floating(2024, 1, 1, 0, 0, 59),
                floating(2024, 1, 2, 0, 0, 59),
            ),
            selected.occurrences().toList(),
        )

        val unreachable = RuleRecurrence(
            start,
            RecurrenceRule(
                frequency = Frequency.DAILY,
                bySecond = setOf(59, 60),
                bySetPosition = setOf(2),
            ),
        )
        assertEquals(emptyList(), unreachable.occurrences().toList())
        assertNull(unreachable.after(start))
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
}
