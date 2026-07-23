package io.github.yallain.rrule.engine

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.LinearLocalTimeZoneResolver
import io.github.yallain.rrule.LocalTimeResolution
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceTimeZoneResolver
import io.github.yallain.rrule.RuleRecurrence
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CountedQueryIndexingTest {
    @Test
    fun farFutureSecondlyQueryJumpsOverItsCountedPrefix() {
        val recurrence = RuleRecurrence(
            start = floating(2000, 1, 1, 0, 0, 0),
            rule = RecurrenceRule(
                frequency = Frequency.SECONDLY,
                count = 2_000_000_000,
            ),
        )

        assertEquals(
            floating(2050, 1, 1, 0, 0, 1),
            recurrence.after(floating(2050, 1, 1, 0, 0, 0)),
        )
    }

    @Test
    fun farFutureMinutelyExpansionCountsEveryExpandedSecond() {
        val recurrence = RuleRecurrence(
            start = floating(2000, 1, 1, 0, 0, 0),
            rule = RecurrenceRule(
                frequency = Frequency.MINUTELY,
                count = 100_000_000,
                bySecond = setOf(0, 30),
            ),
        )

        assertEquals(
            floating(2050, 1, 1, 0, 0, 30),
            recurrence.after(floating(2050, 1, 1, 0, 0, 0)),
        )
    }

    @Test
    fun utcAndDateOnlyDomainsUseTheSameExactPrefixIndex() {
        val utcRecurrence = RuleRecurrence(
            start = RecurrenceDateTime.Utc(Instant.parse("2000-01-01T00:00:00Z")),
            rule = RecurrenceRule(Frequency.SECONDLY, count = 2_000_000_000),
        )
        val dateRecurrence = RuleRecurrence(
            start = RecurrenceDateTime.DateOnly(LocalDate(2000, 1, 1)),
            rule = RecurrenceRule(Frequency.DAILY, count = 3_000_000),
        )

        assertEquals(
            RecurrenceDateTime.Utc(Instant.parse("2050-01-01T00:00:01Z")),
            utcRecurrence.after(RecurrenceDateTime.Utc(Instant.parse("2050-01-01T00:00:00Z"))),
        )
        assertEquals(
            RecurrenceDateTime.DateOnly(LocalDate(9000, 1, 2)),
            dateRecurrence.after(RecurrenceDateTime.DateOnly(LocalDate(9000, 1, 1))),
        )
    }

    @Test
    fun linearZonedResolverOptsIntoBoundedCountedQueries() {
        val resolver = CountingLinearResolver()
        val recurrence = RuleRecurrence(
            start = zoned(2000, 1, 1, 0, 0, 0),
            rule = RecurrenceRule(Frequency.SECONDLY, count = 2_000_000_000),
            timeZoneResolver = resolver,
        )

        resolver.resolutionCount = 0
        assertEquals(
            zoned(2050, 1, 1, 0, 0, 1),
            recurrence.after(zoned(2050, 1, 1, 0, 0, 0)),
        )
        assertTrue(resolver.resolutionCount < 20, "Resolved ${resolver.resolutionCount} local values")
    }

    @Test
    fun universalLimitingSelectorsStillUseTheExactPrefixIndex() {
        val resolver = CountingLinearResolver(maximumResolutionCount = 50)
        val recurrence = RuleRecurrence(
            start = zoned(2000, 1, 1, 0, 0, 0),
            rule = RecurrenceRule(
                frequency = Frequency.SECONDLY,
                count = 2_000_000_000,
                bySecond = (0..60).toSet(),
                byMinute = (0..59).toSet(),
                byHour = (0..23).toSet(),
                byDay = Weekday.entries.map(::ByDay),
                byMonth = (1..12).toSet(),
            ),
            timeZoneResolver = resolver,
        )

        resolver.resolutionCount = 0
        assertEquals(
            zoned(2050, 1, 1, 0, 0, 1),
            recurrence.after(zoned(2050, 1, 1, 0, 0, 0)),
        )
        assertTrue(resolver.resolutionCount < 20, "Resolved ${resolver.resolutionCount} local values")
    }

    @Test
    fun completeSignedCalendarLimitersKeepBidirectionalCountIndexing() {
        val resolver = CountingLinearResolver(maximumResolutionCount = 50)
        val recurrence = RuleRecurrence(
            start = zoned(2000, 1, 1, 0, 0, 0),
            rule = RecurrenceRule(
                frequency = Frequency.SECONDLY,
                count = 2_000_000_000,
                bySecond = (0..58).toSet() + 60,
                byMinute = (0..59).toSet(),
                byHour = (0..23).toSet(),
                byDay = Weekday.entries.map(::ByDay),
                byMonthDay = (1..28).toSet() + setOf(-3, -2, -1),
                byYearDay = (1..365).toSet() + -1,
                byMonth = (1..12).toSet(),
            ),
            timeZoneResolver = resolver,
        )
        val query = zoned(2050, 1, 1, 0, 0, 0)

        assertEquals(zoned(2050, 1, 1, 0, 0, 1), recurrence.after(query))
        assertTrue(resolver.resolutionCount < 20, "Resolved ${resolver.resolutionCount} local values")

        resolver.resolutionCount = 0
        assertEquals(zoned(2049, 12, 31, 23, 59, 59), recurrence.before(query))
        assertTrue(resolver.resolutionCount < 20, "Resolved ${resolver.resolutionCount} local values")
    }

    @Test
    fun denseCountedYearPrunesItsCartesianPrefixBeforeResolution() {
        val resolver = CountingLinearResolver()
        val recurrence = RuleRecurrence(
            start = zoned(2024, 1, 1, 0, 0, 0),
            rule = RecurrenceRule(
                frequency = Frequency.YEARLY,
                count = 40_000_000,
                byDay = Weekday.entries.map(::ByDay),
                byHour = (0..23).toSet(),
                byMinute = (0..59).toSet(),
                bySecond = (0..59).toSet(),
            ),
            timeZoneResolver = resolver,
        )

        resolver.resolutionCount = 0
        assertEquals(
            listOf(
                zoned(2024, 12, 31, 23, 59, 58),
                zoned(2024, 12, 31, 23, 59, 59),
            ),
            recurrence.between(
                zoned(2024, 12, 31, 23, 59, 58),
                zoned(2025, 1, 1, 0, 0, 0),
            ),
        )
        assertTrue(resolver.resolutionCount < 30, "Resolved ${resolver.resolutionCount} local values")
    }

    @Test
    fun variableCalendarPrefixesMatchUncountedFarWindowOracles() {
        val start = floating(2000, 1, 1, 9, 0, 0)
        val windowStart = floating(2500, 1, 1, 0, 0, 0)
        val windowEnd = floating(2501, 1, 1, 0, 0, 0)
        val rules = listOf(
            RecurrenceRule(
                frequency = Frequency.DAILY,
                count = 2_000_000_000,
                byMonth = setOf(2, 3),
                byMonthDay = setOf(1, 29, 31),
            ),
            RecurrenceRule(
                frequency = Frequency.WEEKLY,
                count = 2_000_000_000,
                byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.THURSDAY)),
            ),
            RecurrenceRule(
                frequency = Frequency.MONTHLY,
                count = 2_000_000_000,
                byMonthDay = setOf(29, 30, 31),
            ),
            RecurrenceRule(
                frequency = Frequency.YEARLY,
                count = 2_000_000_000,
                byMonth = setOf(2, 3),
                byMonthDay = setOf(29, 31),
            ),
            RecurrenceRule(
                frequency = Frequency.YEARLY,
                count = 2_000_000_000,
                byWeekNumber = setOf(1, 13, 53, -1),
                byDay = listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.SUNDAY)),
            ),
        )

        rules.forEach { rule ->
            val counted = RuleRecurrence(start, rule)
            val oracle = RuleRecurrence(start, rule.copy(count = null))
            assertEquals(
                oracle.between(windowStart, windowEnd),
                counted.between(windowStart, windowEnd),
                "${rule.frequency} forward window",
            )
            assertEquals(
                oracle.before(windowEnd),
                counted.before(windowEnd),
                "${rule.frequency} reverse lookup",
            )
        }
    }

    @Test
    fun smallCalendarCountsExhaustWithoutReplayingToTheQueryYear() {
        val start = floating(2000, 1, 1, 9, 0, 0)
        val distantQuery = floating(9000, 1, 1, 0, 0, 0)
        val cases = listOf(
            RecurrenceRule(Frequency.DAILY, count = 1, byMonth = setOf(1)),
            RecurrenceRule(
                Frequency.WEEKLY,
                count = 1,
                byDay = listOf(ByDay(Weekday.SATURDAY)),
            ),
            RecurrenceRule(Frequency.MONTHLY, count = 1, byMonthDay = setOf(1)),
            RecurrenceRule(Frequency.YEARLY, count = 1, byMonth = setOf(1)),
            RecurrenceRule(
                Frequency.YEARLY,
                count = 1,
                byWeekNumber = setOf(1),
                byDay = listOf(ByDay(Weekday.SATURDAY)),
            ),
        )

        cases.forEach { rule ->
            val recurrence = RuleRecurrence(start, rule)
            val expected = RuleRecurrence(start, rule.copy(count = null)).occurrences().first()
            assertEquals(expected, recurrence.before(distantQuery), "${rule.frequency} before")
            assertNull(recurrence.after(distantQuery), "${rule.frequency} after")
        }
    }

    @Test
    fun reverseQueryJumpsDirectlyToTheFinalCountedOccurrence() {
        val resolver = CountingLinearResolver()
        val recurrence = RuleRecurrence(
            start = zoned(2000, 1, 1, 0, 0, 0),
            rule = RecurrenceRule(Frequency.SECONDLY, count = 3),
            timeZoneResolver = resolver,
        )

        resolver.resolutionCount = 0
        assertEquals(
            zoned(2000, 1, 1, 0, 0, 2),
            recurrence.before(zoned(2050, 1, 1, 0, 0, 0)),
        )
        assertTrue(resolver.resolutionCount < 12, "Resolved ${resolver.resolutionCount} local values")
        assertEquals(
            zoned(2000, 1, 1, 0, 0, 2),
            recurrence.before(zoned(2000, 1, 1, 0, 0, 2), inclusive = true),
        )
        assertEquals(
            zoned(2000, 1, 1, 0, 0, 1),
            recurrence.before(zoned(2000, 1, 1, 0, 0, 2), inclusive = false),
        )
        assertNull(recurrence.after(zoned(2000, 1, 1, 0, 0, 2)))
    }

    @Test
    fun arbitraryResolverStillReplaysGapCandidatesForCountCorrectness() {
        val resolver = RecurrenceTimeZoneResolver { dateTime, _ ->
            if (dateTime.second == 1) {
                LocalTimeResolution.Nonexistent
            } else {
                LocalTimeResolution.Valid(dateTime.toInstant(UtcOffset.ZERO))
            }
        }
        val recurrence = RuleRecurrence(
            start = zoned(2024, 1, 1, 0, 0, 0),
            rule = RecurrenceRule(Frequency.SECONDLY, count = 3),
            timeZoneResolver = resolver,
        )

        assertEquals(
            zoned(2024, 1, 1, 0, 0, 2),
            recurrence.after(zoned(2024, 1, 1, 0, 0, 0)),
        )
        assertEquals(
            listOf(
                zoned(2024, 1, 1, 0, 0, 0),
                zoned(2024, 1, 1, 0, 0, 2),
                zoned(2024, 1, 1, 0, 0, 3),
            ),
            recurrence.occurrences().toList(),
        )
    }

    private class CountingLinearResolver(
        private val maximumResolutionCount: Int = Int.MAX_VALUE,
    ) : LinearLocalTimeZoneResolver {
        var resolutionCount: Int = 0

        override fun hasLinearLocalTimeline(timeZoneId: String): Boolean = true

        override fun resolve(localDateTime: LocalDateTime, timeZoneId: String): LocalTimeResolution {
            resolutionCount++
            check(resolutionCount <= maximumResolutionCount) {
                "Counted query exceeded the $maximumResolutionCount-resolution budget"
            }
            return LocalTimeResolution.Valid(localDateTime.toInstant(UtcOffset.ZERO))
        }

        override fun localDateTimeAt(instant: Instant, timeZoneId: String): LocalDateTime =
            instant.toLocalDateTime(TimeZone.UTC)
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

    private fun zoned(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): RecurrenceDateTime.Zoned = RecurrenceDateTime.Zoned(
        LocalDateTime(year, month, day, hour, minute, second),
        "Test/UTC",
    )
}
