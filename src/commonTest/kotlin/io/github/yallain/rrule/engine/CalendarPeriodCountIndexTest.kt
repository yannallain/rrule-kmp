package io.github.yallain.rrule.engine

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.CalendarPeriodCountIndex
import io.github.yallain.rrule.CalendarPeriodCountCycleShape
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.PeriodGenerator
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CalendarPeriodCountIndexTest {
    @Test
    fun repeatedCountsAnswerPrefixesAndOrdinals() {
        val counts = listOf(0L, 2L, 1L, 3L)
        val index = CalendarPeriodCountIndex(
            shape = CalendarPeriodCountCycleShape(
                periodCount = counts.size,
                periodsPerBlock = 2,
            ),
            candidateCountAtOffset = { offset -> counts[(offset % counts.size).toInt()] },
        )

        assertEquals(0L, index.countForPeriods(0))
        assertEquals(0L, index.countForPeriods(1))
        assertEquals(2L, index.countForPeriods(2))
        assertEquals(6L, index.countForPeriods(4))
        assertEquals(8L, index.countForPeriods(6))

        assertEquals(
            CalendarPeriodCountIndex.Location(periodOffset = 1, ordinalInPeriod = 1),
            index.locate(oneBasedOrdinal = 1),
        )
        assertEquals(
            CalendarPeriodCountIndex.Location(periodOffset = 3, ordinalInPeriod = 3),
            index.locate(oneBasedOrdinal = 6),
        )
        assertEquals(
            CalendarPeriodCountIndex.Location(periodOffset = 5, ordinalInPeriod = 1),
            index.locate(oneBasedOrdinal = 7),
        )
    }

    @Test
    fun cappedAndNearbyQueriesDoNotBuildTheCompleteCycle() {
        var evaluations = 0
        val index = CalendarPeriodCountIndex(
            shape = CalendarPeriodCountCycleShape(
                periodCount = 146_097,
                periodsPerBlock = 366,
            ),
            candidateCountAtOffset = {
                evaluations++
                1L
            },
        )

        assertEquals(1L, index.countForPeriods(periods = 2_000_000L, maximumCount = 1L))
        assertEquals(1, evaluations)

        assertEquals(
            CalendarPeriodCountIndex.Location(periodOffset = 2L, ordinalInPeriod = 1L),
            index.locate(oneBasedOrdinal = 3L),
        )
        assertEquals(367, evaluations)

        index.locate(oneBasedOrdinal = 300L)
        assertEquals(367, evaluations)
    }

    @Test
    fun boundedAndRepeatedLookupsReuseInitializedBlocks() {
        var evaluations = 0
        val index = CalendarPeriodCountIndex(
            shape = CalendarPeriodCountCycleShape(
                periodCount = 10,
                periodsPerBlock = 3,
            ),
            candidateCountAtOffset = {
                evaluations++
                1L
            },
        )

        assertNull(index.locate(oneBasedOrdinal = 5L, maximumPeriodOffset = 2L))
        assertEquals(4, evaluations)
        assertNull(index.locate(oneBasedOrdinal = 5L, maximumPeriodOffset = 2L))
        assertEquals(4, evaluations)

        assertEquals(
            CalendarPeriodCountIndex.Location(periodOffset = 4L, ordinalInPeriod = 1L),
            index.locate(oneBasedOrdinal = 5L),
        )
        assertEquals(7, evaluations)
        index.locate(oneBasedOrdinal = 5L)
        assertEquals(7, evaluations)
    }

    @Test
    fun failedBlocksAreCachedAsAnUnavailableIndex() {
        var evaluations = 0
        val index = CalendarPeriodCountIndex(
            shape = CalendarPeriodCountCycleShape(
                periodCount = 10,
                periodsPerBlock = 3,
            ),
            candidateCountAtOffset = { offset ->
                evaluations++
                if (offset == 4L) null else 1L
            },
        )

        assertNull(index.locate(oneBasedOrdinal = 5L))
        assertEquals(5, evaluations)
        assertNull(index.locate(oneBasedOrdinal = 5L))
        assertEquals(5, evaluations)
    }

    @Test
    fun allZeroCyclesStayEmptyAndReuseInitializedBlocks() {
        var evaluations = 0
        val index = CalendarPeriodCountIndex(
            shape = CalendarPeriodCountCycleShape(
                periodCount = 12,
                periodsPerBlock = 4,
            ),
            candidateCountAtOffset = {
                evaluations++
                0L
            },
        )

        assertEquals(0L, index.countForPeriods(1_000_000L))
        assertEquals(12, evaluations)
        assertNull(index.locate(oneBasedOrdinal = 1L))
        assertEquals(12, evaluations)
        assertEquals(0L, index.countForPeriods(Long.MAX_VALUE))
        assertEquals(12, evaluations)
    }

    @Test
    fun calendarCandidateCountsRepeatAfterOneGregorianCycle() {
        val start = LocalDateTime(2000, 1, 1, 2, 30)
        val cases = listOf(
            CycleCase(
                rule = RecurrenceRule(
                    frequency = Frequency.DAILY,
                    byMonth = setOf(2, 3),
                    byMonthDay = setOf(1, 29, 31),
                ),
                expectedPeriodCount = 146_097L,
            ),
            CycleCase(
                rule = RecurrenceRule(
                    frequency = Frequency.DAILY,
                    interval = 3,
                    byMonth = setOf(2, 3),
                ),
                expectedPeriodCount = 48_699L,
            ),
            CycleCase(
                rule = RecurrenceRule(
                    frequency = Frequency.WEEKLY,
                    byMonth = setOf(2, 3),
                    byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.THURSDAY)),
                ),
                expectedPeriodCount = 20_871L,
            ),
            CycleCase(
                rule = RecurrenceRule(
                    frequency = Frequency.WEEKLY,
                    interval = 3,
                    byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.THURSDAY)),
                ),
                expectedPeriodCount = 6_957L,
            ),
            CycleCase(
                rule = RecurrenceRule(
                    frequency = Frequency.MONTHLY,
                    byMonthDay = setOf(29, 30, 31),
                    byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.THURSDAY)),
                ),
                expectedPeriodCount = 4_800L,
            ),
            CycleCase(
                rule = RecurrenceRule(
                    frequency = Frequency.MONTHLY,
                    interval = 18,
                    byMonthDay = setOf(29, 30, 31),
                ),
                expectedPeriodCount = 800L,
            ),
            CycleCase(
                rule = RecurrenceRule(
                    frequency = Frequency.YEARLY,
                    byMonth = setOf(2, 3),
                    byMonthDay = setOf(29, 31),
                    byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.THURSDAY)),
                ),
                expectedPeriodCount = 400L,
            ),
            CycleCase(
                rule = RecurrenceRule(
                    frequency = Frequency.YEARLY,
                    interval = 3,
                    byWeekNumber = setOf(1, 53, -1),
                    byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.SUNDAY)),
                    weekStart = Weekday.SUNDAY,
                ),
                expectedPeriodCount = 400L,
            ),
            CycleCase(
                rule = RecurrenceRule(
                    frequency = Frequency.YEARLY,
                    interval = 400,
                    byMonth = setOf(2),
                    byMonthDay = setOf(29),
                ),
                expectedPeriodCount = 1L,
            ),
        )

        cases.forEach { case ->
            val generator = PeriodGenerator(start.date, start, case.rule)
            val cycleShape = assertNotNull(
                generator.calendarCandidateCountCycleShapeAfterFirstPeriod(),
            )
            assertEquals(case.expectedPeriodCount, cycleShape.periodCount.toLong())

            listOf(1L, 2L, cycleShape.periodCount / 2L).forEach { periodIndex ->
                assertEquals(
                    generator.candidateCount(periodIndex),
                    generator.candidateCount(periodIndex + cycleShape.periodCount),
                    "${case.rule.frequency} candidate count at period $periodIndex",
                )
            }
        }
    }

    private data class CycleCase(
        val rule: RecurrenceRule,
        val expectedPeriodCount: Long,
    )
}
