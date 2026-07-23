package io.github.yallain.rrule.engine

import io.github.yallain.rrule.AmbiguousTimePolicy
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.KotlinxRecurrenceTimeZoneResolver
import io.github.yallain.rrule.LocalTimeResolution
import io.github.yallain.rrule.NonexistentLocalTimeRange
import io.github.yallain.rrule.NonexistentLocalTimeRangeProvider
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceTimeZoneResolver
import io.github.yallain.rrule.RuleRecurrence
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TransitionZoneCountedQueryIndexingTest {
    @Test
    fun farParisQueriesScaleWithTransitionsAndResultsInsteadOfElapsedSeconds() {
        val resolver = CountingGapAwareResolver()
        val recurrence = RuleRecurrence(
            start = zoned(2000, 1, 1, timeZoneId = PARIS),
            rule = RecurrenceRule(Frequency.SECONDLY, count = 2_000_000_000),
            timeZoneResolver = resolver,
        )
        val query = zoned(2050, 1, 1, timeZoneId = PARIS)

        resolver.resetBudgets()
        assertEquals(zoned(2050, 1, 1, second = 1, timeZoneId = PARIS), recurrence.after(query))
        resolver.assertWithinBudgets()

        resolver.resetBudgets()
        assertEquals(
            listOf(
                zoned(2050, 1, 1, timeZoneId = PARIS),
                zoned(2050, 1, 1, second = 1, timeZoneId = PARIS),
            ),
            recurrence.between(
                query,
                zoned(2050, 1, 1, second = 2, timeZoneId = PARIS),
            ),
        )
        resolver.assertWithinBudgets()

        resolver.resetBudgets()
        assertEquals(
            zoned(2049, 12, 31, hour = 23, minute = 59, second = 59, timeZoneId = PARIS),
            recurrence.before(query),
        )
        resolver.assertWithinBudgets()
    }

    @Test
    fun exhaustedCountSkipsAOneHourGapWithoutReplayingItsSeconds() {
        val resolver = CountingGapAwareResolver()
        val recurrence = RuleRecurrence(
            start = zoned(2024, 3, 31, hour = 1, minute = 59, second = 58, timeZoneId = PARIS),
            rule = RecurrenceRule(Frequency.SECONDLY, count = 5),
            timeZoneResolver = resolver,
        )
        val farBound = zoned(2050, 1, 1, timeZoneId = PARIS)

        resolver.resetBudgets()
        assertNull(recurrence.after(farBound))
        resolver.assertWithinBudgets()

        resolver.resetBudgets()
        assertEquals(
            zoned(2024, 3, 31, hour = 3, second = 2, timeZoneId = PARIS),
            recurrence.before(farBound),
        )
        resolver.assertWithinBudgets()
    }

    @Test
    fun explicitStartInsideAGapStillConsumesOneCount() {
        val resolver = CountingGapAwareResolver()
        val recurrence = RuleRecurrence(
            start = zoned(2024, 3, 31, hour = 2, minute = 30, timeZoneId = PARIS),
            rule = RecurrenceRule(Frequency.SECONDLY, count = 2),
            timeZoneResolver = resolver,
        )

        resolver.resetBudgets()
        assertEquals(
            zoned(2024, 3, 31, hour = 3, minute = 30, second = 1, timeZoneId = PARIS),
            recurrence.before(zoned(2050, 1, 1, timeZoneId = PARIS)),
        )
        resolver.assertWithinBudgets()
    }

    @Test
    fun lordHoweHalfHourGapUsesItsActualTransitionWidth() {
        val resolver = CountingGapAwareResolver()
        val recurrence = RuleRecurrence(
            start = zoned(2024, 10, 6, hour = 1, minute = 59, timeZoneId = LORD_HOWE),
            rule = RecurrenceRule(Frequency.MINUTELY, count = 4),
            timeZoneResolver = resolver,
        )

        resolver.resetBudgets()
        assertEquals(
            zoned(2024, 10, 6, hour = 2, minute = 32, timeZoneId = LORD_HOWE),
            recurrence.before(zoned(2050, 1, 1, timeZoneId = LORD_HOWE)),
        )
        resolver.assertWithinBudgets()
    }

    @Test
    fun apiaDateLineGapSkipsTheWholeMissingDay() {
        val resolver = CountingGapAwareResolver()
        val recurrence = RuleRecurrence(
            start = zoned(2011, 12, 29, hour = 23, minute = 59, timeZoneId = APIA),
            rule = RecurrenceRule(Frequency.MINUTELY, count = 3),
            timeZoneResolver = resolver,
        )

        resolver.resetBudgets()
        assertEquals(
            zoned(2011, 12, 31, minute = 1, timeZoneId = APIA),
            recurrence.before(zoned(2050, 1, 1, timeZoneId = APIA)),
        )
        resolver.assertWithinBudgets()
    }

    @Test
    fun kathmanduPoliticalGapSkipsItsMissingQuarterHour() {
        val resolver = CountingGapAwareResolver()
        val recurrence = RuleRecurrence(
            start = zoned(1985, 12, 31, hour = 23, minute = 58, timeZoneId = KATHMANDU),
            rule = RecurrenceRule(Frequency.MINUTELY, count = 5),
            timeZoneResolver = resolver,
        )

        resolver.resetBudgets()
        assertEquals(
            zoned(1986, 1, 1, minute = 17, timeZoneId = KATHMANDU),
            recurrence.before(zoned(2050, 1, 1, timeZoneId = KATHMANDU)),
        )
        resolver.assertWithinBudgets()
    }

    @Test
    fun overlapPolicyChangesTheInstantButNotCountCardinality() {
        val earlierResolver = CountingGapAwareResolver()
        val laterResolver = CountingGapAwareResolver()
        val rule = RecurrenceRule(Frequency.MINUTELY, count = 3)
        val start = zoned(2024, 10, 27, hour = 2, minute = 29, timeZoneId = PARIS)
        val earlier = RuleRecurrence(start, rule, earlierResolver, AmbiguousTimePolicy.EARLIER)
        val later = RuleRecurrence(start, rule, laterResolver, AmbiguousTimePolicy.LATER)
        val expected = zoned(2024, 10, 27, hour = 2, minute = 31, timeZoneId = PARIS)

        earlierResolver.resetBudgets()
        assertEquals(
            expected,
            earlier.before(RecurrenceDateTime.Utc(Instant.parse("2024-10-27T00:32:00Z"))),
        )
        earlierResolver.assertWithinBudgets()

        laterResolver.resetBudgets()
        assertEquals(
            expected,
            later.before(RecurrenceDateTime.Utc(Instant.parse("2024-10-27T01:32:00Z"))),
        )
        laterResolver.assertWithinBudgets()
    }

    @Test
    fun variableCalendarPrefixWithResolveOnlyResolverKeepsTheSingleScanFallback() {
        val resolver = CountingResolveOnlyResolver()
        val recurrence = RuleRecurrence(
            start = zoned(2000, 1, 1, timeZoneId = PARIS),
            rule = RecurrenceRule(
                frequency = Frequency.DAILY,
                count = 3,
                byMonth = setOf(2),
            ),
            timeZoneResolver = resolver,
        )

        resolver.resetBudgets()
        assertEquals(
            zoned(2000, 2, 3, timeZoneId = PARIS),
            recurrence.before(zoned(2050, 1, 1, timeZoneId = PARIS)),
        )
        resolver.assertWithinBudgets()
    }

    private class CountingGapAwareResolver(
        private val maximumResolutionCount: Int = 80,
        private val maximumGapQueryCount: Int = 12,
    ) : RecurrenceTimeZoneResolver, NonexistentLocalTimeRangeProvider {
        private var resolutionCount: Int = 0
        private var gapQueryCount: Int = 0

        override fun resolve(localDateTime: LocalDateTime, timeZoneId: String): LocalTimeResolution {
            resolutionCount++
            check(resolutionCount <= maximumResolutionCount) {
                "Counted transition-zone query exceeded the $maximumResolutionCount-resolution budget"
            }
            return KotlinxRecurrenceTimeZoneResolver.resolve(localDateTime, timeZoneId)
        }

        override fun localDateTimeAt(instant: Instant, timeZoneId: String): LocalDateTime =
            KotlinxRecurrenceTimeZoneResolver.localDateTimeAt(instant, timeZoneId)

        override fun nonexistentInstant(localDateTime: LocalDateTime, timeZoneId: String): Instant =
            KotlinxRecurrenceTimeZoneResolver.nonexistentInstant(localDateTime, timeZoneId)

        override fun nonexistentLocalTimeRanges(
            timeZoneId: String,
            startInclusive: LocalDateTime,
            endInclusive: LocalDateTime,
        ): List<NonexistentLocalTimeRange> {
            gapQueryCount++
            check(gapQueryCount <= maximumGapQueryCount) {
                "Counted transition-zone query exceeded the $maximumGapQueryCount-gap-query budget"
            }
            return KotlinxRecurrenceTimeZoneResolver.nonexistentLocalTimeRanges(
                timeZoneId,
                startInclusive,
                endInclusive,
            )
        }

        fun resetBudgets() {
            resolutionCount = 0
            gapQueryCount = 0
        }

        fun assertWithinBudgets() {
            assertTrue(resolutionCount <= maximumResolutionCount)
            assertTrue(gapQueryCount <= maximumGapQueryCount)
        }
    }

    private class CountingResolveOnlyResolver(
        private val maximumResolutionCount: Int = 80,
    ) : RecurrenceTimeZoneResolver {
        private var resolutionCount: Int = 0

        override fun resolve(localDateTime: LocalDateTime, timeZoneId: String): LocalTimeResolution {
            resolutionCount++
            check(resolutionCount <= maximumResolutionCount) {
                "Resolve-only query exceeded the $maximumResolutionCount-resolution budget"
            }
            return KotlinxRecurrenceTimeZoneResolver.resolve(localDateTime, timeZoneId)
        }

        override fun localDateTimeAt(instant: Instant, timeZoneId: String): LocalDateTime =
            KotlinxRecurrenceTimeZoneResolver.localDateTimeAt(instant, timeZoneId)

        override fun nonexistentInstant(localDateTime: LocalDateTime, timeZoneId: String): Instant =
            KotlinxRecurrenceTimeZoneResolver.nonexistentInstant(localDateTime, timeZoneId)

        fun resetBudgets() {
            resolutionCount = 0
        }

        fun assertWithinBudgets() {
            assertTrue(resolutionCount <= maximumResolutionCount)
        }
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
        const val PARIS: String = "Europe/Paris"
        const val LORD_HOWE: String = "Australia/Lord_Howe"
        const val APIA: String = "Pacific/Apia"
        const val KATHMANDU: String = "Asia/Kathmandu"
    }
}
