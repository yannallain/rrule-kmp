package io.github.yallain.rrule.engine

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.LocalTimeResolution
import io.github.yallain.rrule.NonexistentLocalTimeRange
import io.github.yallain.rrule.NonexistentLocalTimeRangeProvider
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceTimeZoneResolver
import io.github.yallain.rrule.RuleRecurrence
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CalendarTransitionCountIndexingTest {
    @Test
    fun variableCalendarCountsJumpAcrossAnnualGapsInsteadOfReplayingThePrefix() {
        val start = zoned(
            year = 2000,
            month = 1,
            day = 1,
            hour = 2,
            minute = 30,
            timeZoneId = ANNUAL_GAP_ZONE,
        )
        val windowStart = zoned(
            2500,
            1,
            1,
            hour = 2,
            minute = 30,
            timeZoneId = ANNUAL_GAP_ZONE,
        )
        val windowEnd = zoned(
            2501,
            1,
            1,
            hour = 2,
            minute = 30,
            timeZoneId = ANNUAL_GAP_ZONE,
        )
        val rules = listOf(
            RecurrenceRule(
                frequency = Frequency.DAILY,
                count = 2_000_000_000,
                byMonth = setOf(3),
            ),
            RecurrenceRule(
                frequency = Frequency.WEEKLY,
                count = 2_000_000_000,
                byDay = listOf(ByDay(Weekday.SATURDAY), ByDay(Weekday.SUNDAY)),
            ),
            RecurrenceRule(
                frequency = Frequency.MONTHLY,
                count = 2_000_000_000,
                byMonthDay = setOf(30, 31),
            ),
            RecurrenceRule(
                frequency = Frequency.YEARLY,
                count = 2_000_000_000,
                byMonth = setOf(3),
                byMonthDay = setOf(30, 31),
            ),
            RecurrenceRule(
                frequency = Frequency.YEARLY,
                count = 2_000_000_000,
                byWeekNumber = setOf(13, 14),
                byDay = listOf(ByDay(Weekday.SATURDAY), ByDay(Weekday.SUNDAY)),
            ),
        )

        rules.forEach { rule ->
            val indexedResolver = CountingAnnualGapResolver()
            val indexed = RuleRecurrence(start, rule, indexedResolver)
            val oracle = RuleRecurrence(
                start = start,
                rule = rule.copy(count = null),
                timeZoneResolver = CountingAnnualGapResolver(maximumResolutionCount = Int.MAX_VALUE),
            )

            indexedResolver.resetBudgets()
            assertEquals(
                oracle.between(windowStart, windowEnd),
                indexed.between(windowStart, windowEnd),
                "${rule.frequency} forward window",
            )
            indexedResolver.assertIndexed()

            indexedResolver.resetBudgets()
            assertEquals(
                oracle.before(windowEnd),
                indexed.before(windowEnd),
                "${rule.frequency} reverse lookup",
            )
            indexedResolver.assertIndexed()
        }
    }

    @Test
    fun exhaustedSparseCalendarCountCachesItsFiniteTransitionPrefix() {
        val resolver = CountingAnnualGapResolver(
            maximumResolutionCount = 40,
            maximumGapQueryCount = 2,
            maximumGapEndYear = 2001,
        )
        val recurrence = RuleRecurrence(
            start = zoned(
                2000,
                3,
                29,
                hour = 2,
                minute = 30,
                timeZoneId = ANNUAL_GAP_ZONE,
            ),
            rule = RecurrenceRule(
                frequency = Frequency.DAILY,
                count = 3,
                byMonth = setOf(3),
            ),
            timeZoneResolver = resolver,
        )
        val distantQuery = zoned(
            9000,
            1,
            1,
            hour = 2,
            minute = 30,
            timeZoneId = ANNUAL_GAP_ZONE,
        )

        resolver.resetBudgets()
        assertNull(recurrence.after(distantQuery))
        resolver.assertIndexed()

        resolver.resetBudgets()
        assertEquals(
            zoned(
                2001,
                3,
                1,
                hour = 2,
                minute = 30,
                timeZoneId = ANNUAL_GAP_ZONE,
            ),
            recurrence.before(distantQuery),
        )
        resolver.assertNoGapQueries()
    }

    @Test
    fun finiteCountPrefixNeverLooksPastTheCurrentQueryBound() {
        val resolver = CountingDailyGapResolver(maximumGapEndYear = 2001)
        val start = zoned(
            2000,
            1,
            1,
            hour = 2,
            minute = 30,
            timeZoneId = DAILY_GAP_ZONE,
        )
        val recurrence = RuleRecurrence(
            start = start,
            rule = RecurrenceRule(
                frequency = Frequency.DAILY,
                count = 2,
            ),
            timeZoneResolver = resolver,
        )
        val query = zoned(2001, 1, 1, hour = 12, timeZoneId = DAILY_GAP_ZONE)

        resolver.resetBudgets()
        assertEquals(start, recurrence.before(query))
        resolver.assertQueryBounded()
    }

    private class CountingAnnualGapResolver(
        private val maximumResolutionCount: Int = 250,
        private val maximumGapQueryCount: Int = 4,
        private val maximumGapEndYear: Int = 9_999,
    ) : RecurrenceTimeZoneResolver, NonexistentLocalTimeRangeProvider {
        private var resolutionCount: Int = 0
        private var gapQueryCount: Int = 0
        private var gapRangeExceeded: Boolean = false

        override fun resolve(localDateTime: LocalDateTime, timeZoneId: String): LocalTimeResolution {
            resolutionCount++
            check(resolutionCount <= maximumResolutionCount) {
                "Calendar-cycle query exceeded the $maximumResolutionCount-resolution budget"
            }
            return if (annualGap(localDateTime.year).contains(localDateTime)) {
                LocalTimeResolution.Nonexistent
            } else {
                LocalTimeResolution.Valid(localDateTime.toInstant(UtcOffset.ZERO))
            }
        }

        override fun localDateTimeAt(instant: Instant, timeZoneId: String): LocalDateTime =
            instant.toLocalDateTime(TimeZone.UTC)

        override fun nonexistentInstant(localDateTime: LocalDateTime, timeZoneId: String): Instant =
            localDateTime.toInstant(UtcOffset.ZERO)

        override fun nonexistentLocalTimeRanges(
            timeZoneId: String,
            startInclusive: LocalDateTime,
            endInclusive: LocalDateTime,
        ): List<NonexistentLocalTimeRange> {
            gapQueryCount++
            check(gapQueryCount <= maximumGapQueryCount) {
                "Calendar-cycle query exceeded the $maximumGapQueryCount-gap-query budget"
            }
            if (endInclusive.year > maximumGapEndYear) {
                gapRangeExceeded = true
                return emptyList()
            }
            return (startInclusive.year..endInclusive.year)
                .map(::annualGap)
                .filter { gap ->
                    gap.endExclusive > startInclusive && gap.startInclusive <= endInclusive
                }
        }

        fun resetBudgets() {
            resolutionCount = 0
            gapQueryCount = 0
            gapRangeExceeded = false
        }

        fun assertIndexed() {
            assertTrue(resolutionCount <= maximumResolutionCount)
            assertTrue(gapQueryCount in 1..maximumGapQueryCount)
            assertTrue(!gapRangeExceeded, "Gap enumeration exceeded year $maximumGapEndYear")
        }

        fun assertNoGapQueries() {
            assertTrue(resolutionCount <= maximumResolutionCount)
            assertEquals(0, gapQueryCount)
            assertTrue(!gapRangeExceeded, "Gap enumeration exceeded year $maximumGapEndYear")
        }
    }

    private class CountingDailyGapResolver(
        private val maximumGapEndYear: Int,
    ) : RecurrenceTimeZoneResolver, NonexistentLocalTimeRangeProvider {
        private var gapQueryCount: Int = 0
        private var resolutionCount: Int = 0
        private var gapRangeExceeded: Boolean = false

        override fun resolve(localDateTime: LocalDateTime, timeZoneId: String): LocalTimeResolution {
            resolutionCount++
            check(resolutionCount <= 40) {
                "Query-bounded transition lookup exceeded the resolution budget"
            }
            return if (isGeneratedGap(localDateTime)) {
                LocalTimeResolution.Nonexistent
            } else {
                LocalTimeResolution.Valid(localDateTime.toInstant(UtcOffset.ZERO))
            }
        }

        override fun localDateTimeAt(instant: Instant, timeZoneId: String): LocalDateTime =
            instant.toLocalDateTime(TimeZone.UTC)

        override fun nonexistentInstant(localDateTime: LocalDateTime, timeZoneId: String): Instant =
            localDateTime.toInstant(UtcOffset.ZERO)

        override fun nonexistentLocalTimeRanges(
            timeZoneId: String,
            startInclusive: LocalDateTime,
            endInclusive: LocalDateTime,
        ): List<NonexistentLocalTimeRange> {
            gapQueryCount++
            check(gapQueryCount <= 20) {
                "Query-bounded transition lookup exceeded the gap-query budget"
            }
            if (endInclusive.year > maximumGapEndYear) {
                gapRangeExceeded = true
                return emptyList()
            }

            var date = maxOf(startInclusive.date, LocalDate(2000, 1, 2))
            val finalDate = minOf(endInclusive.date, LocalDate(8999, 12, 31))
            return buildList {
                while (date <= finalDate) {
                    add(
                        NonexistentLocalTimeRange(
                            startInclusive = LocalDateTime(date.year, date.month, date.day, 2, 0),
                            endExclusive = LocalDateTime(date.year, date.month, date.day, 3, 0),
                        ),
                    )
                    date = date.plus(1, DateTimeUnit.DAY)
                }
            }
        }

        fun resetBudgets() {
            gapQueryCount = 0
            resolutionCount = 0
            gapRangeExceeded = false
        }

        fun assertQueryBounded() {
            assertTrue(gapQueryCount in 1..20)
            assertTrue(resolutionCount <= 40)
            assertTrue(!gapRangeExceeded, "Gap enumeration exceeded year $maximumGapEndYear")
        }

        private fun isGeneratedGap(value: LocalDateTime): Boolean =
            value.date >= LocalDate(2000, 1, 2) &&
                value.date <= LocalDate(8999, 12, 31) &&
                value.hour == 2
    }

    private fun zoned(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        timeZoneId: String,
    ): RecurrenceDateTime.Zoned = RecurrenceDateTime.Zoned(
        LocalDateTime(year, month, day, hour, minute, second),
        timeZoneId,
    )

    private companion object {
        const val ANNUAL_GAP_ZONE: String = "Test/AnnualGap"
        const val DAILY_GAP_ZONE: String = "Test/DailyGap"

        fun annualGap(year: Int): NonexistentLocalTimeRange = NonexistentLocalTimeRange(
            startInclusive = LocalDateTime(year, 3, 31, 2, 0),
            endExclusive = LocalDateTime(year, 3, 31, 3, 0),
        )
    }
}
