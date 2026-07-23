package io.github.yallain.rrule.timezone

import io.github.yallain.rrule.KotlinxRecurrenceTimeZoneResolver
import io.github.yallain.rrule.NonexistentLocalTimeRange
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonexistentLocalTimeRangeDiscoveryTest {
    @Test
    fun returnsCompleteGapAtItsLowerBoundaryAndExcludesItsEnd() {
        val expected = gap(
            startInclusive = local(2024, 3, 31, 2, 0),
            endExclusive = local(2024, 3, 31, 3, 0),
        )

        assertEquals(
            listOf(expected),
            gapsAt(PARIS, expected.startInclusive),
        )
        assertEquals(
            listOf(expected),
            gapsAt(PARIS, local(2024, 3, 31, 2, 30)),
        )
        assertTrue(gapsAt(PARIS, expected.endExclusive).isEmpty())
        assertTrue(expected.contains(expected.startInclusive))
        assertFalse(expected.contains(expected.endExclusive))
    }

    @Test
    fun rangesAreSortedUniqueAndNonOverlapping() {
        val ranges = KotlinxRecurrenceTimeZoneResolver.nonexistentLocalTimeRanges(
            timeZoneId = PARIS,
            startInclusive = local(2020, 1, 1),
            endInclusive = local(2030, 1, 1),
        )

        assertTrue(ranges.isNotEmpty())
        assertEquals(ranges.sortedBy(NonexistentLocalTimeRange::startInclusive), ranges)
        assertEquals(ranges.size, ranges.toSet().size)
        assertTrue(
            ranges.zipWithNext().all { (left, right) ->
                left.endExclusive <= right.startInclusive
            },
        )
    }

    @Test
    fun fixedOffsetZoneHasNoGaps() {
        assertTrue(
            KotlinxRecurrenceTimeZoneResolver.nonexistentLocalTimeRanges(
                timeZoneId = "UTC",
                startInclusive = local(1900, 1, 1),
                endInclusive = local(2100, 1, 1),
            ).isEmpty(),
        )
    }

    @Test
    fun discoversKathmanduQuarterHourGap() {
        assertEquals(
            listOf(
                gap(
                    startInclusive = local(1986, 1, 1, 0, 0),
                    endExclusive = local(1986, 1, 1, 0, 15),
                ),
            ),
            gapsAt("Asia/Kathmandu", local(1986, 1, 1, 0, 5)),
        )
    }

    @Test
    fun lisbonRuleChangeWithoutOffsetChangeCreatesNoGap() {
        assertTrue(
            gapsAt("Europe/Lisbon", local(1992, 9, 27, 2, 0)).isEmpty(),
        )
    }

    @Test
    fun discoversLordHoweHalfHourGap() {
        assertEquals(
            listOf(
                gap(
                    startInclusive = local(2024, 10, 6, 2, 0),
                    endExclusive = local(2024, 10, 6, 2, 30),
                ),
            ),
            gapsAt("Australia/Lord_Howe", local(2024, 10, 6, 2, 15)),
        )
    }

    @Test
    fun discoversApiaSkippedCivilDate() {
        assertEquals(
            listOf(
                gap(
                    startInclusive = local(2011, 12, 30),
                    endExclusive = local(2011, 12, 31),
                ),
            ),
            gapsAt("Pacific/Apia", local(2011, 12, 30, 12, 0)),
        )
    }

    private fun gapsAt(
        timeZoneId: String,
        localDateTime: LocalDateTime,
    ): List<NonexistentLocalTimeRange> =
        KotlinxRecurrenceTimeZoneResolver.nonexistentLocalTimeRanges(
            timeZoneId = timeZoneId,
            startInclusive = localDateTime,
            endInclusive = localDateTime,
        )

    private fun gap(
        startInclusive: LocalDateTime,
        endExclusive: LocalDateTime,
    ): NonexistentLocalTimeRange = NonexistentLocalTimeRange(startInclusive, endExclusive)

    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): LocalDateTime = LocalDateTime(year, month, day, hour, minute)

    private companion object {
        const val PARIS: String = "Europe/Paris"
    }
}
