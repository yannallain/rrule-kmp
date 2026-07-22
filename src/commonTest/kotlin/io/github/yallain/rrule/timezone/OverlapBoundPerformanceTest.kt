package io.github.yallain.rrule.timezone

import io.github.yallain.rrule.AmbiguousTimePolicy
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.KotlinxRecurrenceTimeZoneResolver
import io.github.yallain.rrule.LocalTimeResolution
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceTimeZoneResolver
import io.github.yallain.rrule.RuleRecurrence
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class OverlapBoundPerformanceTest {
    @Test
    fun secondlyQueriesJumpToBothOppositeOverlapBranches() {
        assertOppositeOverlapBranchQueries(
            frequency = Frequency.SECONDLY,
            maximumResolutionCalls = 10_000,
            earlierPolicyBefore = paris(2024, 10, 27, 2, 59, 59),
            earlierPolicyBetween = listOf(
                paris(2024, 10, 27, 2, 59, 58),
                paris(2024, 10, 27, 2, 59, 59),
            ),
            laterPolicyAfter = paris(2024, 10, 27, 2, 0, 0),
            laterPolicyBetween = listOf(
                paris(2024, 10, 27, 2, 0, 0),
                paris(2024, 10, 27, 2, 0, 1),
            ),
            earlierBetweenStart = utc("2024-10-27T00:59:58Z"),
            laterBetweenEnd = utc("2024-10-27T01:00:02Z"),
        )
    }

    @Test
    fun minutelyQueriesJumpToBothOppositeOverlapBranches() {
        assertOppositeOverlapBranchQueries(
            frequency = Frequency.MINUTELY,
            maximumResolutionCalls = 200,
            earlierPolicyBefore = paris(2024, 10, 27, 2, 59),
            earlierPolicyBetween = listOf(
                paris(2024, 10, 27, 2, 58),
                paris(2024, 10, 27, 2, 59),
            ),
            laterPolicyAfter = paris(2024, 10, 27, 2, 0),
            laterPolicyBetween = listOf(
                paris(2024, 10, 27, 2, 0),
                paris(2024, 10, 27, 2, 1),
            ),
            earlierBetweenStart = utc("2024-10-27T00:58:00Z"),
            laterBetweenEnd = utc("2024-10-27T01:02:00Z"),
        )
    }

    @Test
    fun customResolverOverlapWidthControlsConservativePadding() {
        val recurrenceZoneId = "Custom/HalfHourOverlap"
        val resolverZoneId = "Australia/Lord_Howe"

        assertBounded(
            frequency = Frequency.MINUTELY,
            policy = AmbiguousTimePolicy.LATER,
            maximumResolutionCalls = 100,
            expected = zoned(2024, 4, 7, 1, 30, timeZoneId = recurrenceZoneId),
            recurrenceZoneId = recurrenceZoneId,
            resolverZoneId = resolverZoneId,
        ) { after(utc("2024-04-06T14:45:00Z")) }
        assertBounded(
            frequency = Frequency.MINUTELY,
            policy = AmbiguousTimePolicy.EARLIER,
            maximumResolutionCalls = 100,
            expected = zoned(2024, 4, 7, 1, 59, timeZoneId = recurrenceZoneId),
            recurrenceZoneId = recurrenceZoneId,
            resolverZoneId = resolverZoneId,
        ) { before(utc("2024-04-06T15:15:00Z")) }
    }

    private fun assertOppositeOverlapBranchQueries(
        frequency: Frequency,
        maximumResolutionCalls: Int,
        earlierPolicyBefore: RecurrenceDateTime.Zoned,
        earlierPolicyBetween: List<RecurrenceDateTime.Zoned>,
        laterPolicyAfter: RecurrenceDateTime.Zoned,
        laterPolicyBetween: List<RecurrenceDateTime.Zoned>,
        earlierBetweenStart: RecurrenceDateTime.Utc,
        laterBetweenEnd: RecurrenceDateTime.Utc,
    ) {
        val laterBranchBound = utc("2024-10-27T01:30:00Z")
        assertBounded(
            frequency,
            AmbiguousTimePolicy.EARLIER,
            maximumResolutionCalls,
            paris(2024, 10, 27, 3, 0),
        ) { after(laterBranchBound) }
        assertBounded(
            frequency,
            AmbiguousTimePolicy.EARLIER,
            maximumResolutionCalls,
            earlierPolicyBefore,
        ) { before(laterBranchBound) }
        assertBounded(
            frequency,
            AmbiguousTimePolicy.EARLIER,
            maximumResolutionCalls,
            earlierPolicyBetween,
        ) { between(earlierBetweenStart, laterBranchBound) }

        val earlierBranchBound = utc("2024-10-27T00:30:00Z")
        assertBounded(
            frequency,
            AmbiguousTimePolicy.LATER,
            maximumResolutionCalls,
            laterPolicyAfter,
        ) { after(earlierBranchBound) }
        assertBounded(
            frequency,
            AmbiguousTimePolicy.LATER,
            maximumResolutionCalls,
            paris(2024, 10, 27, 1, 59, if (frequency == Frequency.SECONDLY) 59 else 0),
        ) { before(earlierBranchBound) }
        assertBounded(
            frequency,
            AmbiguousTimePolicy.LATER,
            maximumResolutionCalls,
            laterPolicyBetween,
        ) { between(earlierBranchBound, laterBetweenEnd) }
    }

    private fun <T> assertBounded(
        frequency: Frequency,
        policy: AmbiguousTimePolicy,
        maximumResolutionCalls: Int,
        expected: T,
        recurrenceZoneId: String = "Europe/Paris",
        resolverZoneId: String = recurrenceZoneId,
        query: RuleRecurrence.() -> T,
    ) {
        val resolver = InstrumentedResolver(maximumResolutionCalls, resolverZoneId)
        val recurrence = RuleRecurrence(
            start = zoned(2000, 1, 1, 0, 0, timeZoneId = recurrenceZoneId),
            rule = RecurrenceRule(frequency),
            timeZoneResolver = resolver,
            ambiguousTimePolicy = policy,
        )

        assertEquals(expected, recurrence.query())
        assertTrue(
            resolver.resolutionCalls <= maximumResolutionCalls,
            "Expected at most $maximumResolutionCalls local resolutions, got ${resolver.resolutionCalls}",
        )
        assertTrue(
            resolver.projectionCalls <= 2,
            "Expected at most two reverse projections, got ${resolver.projectionCalls}",
        )
    }

    private class InstrumentedResolver(
        private val resolutionBudget: Int,
        private val resolverZoneId: String,
    ) : RecurrenceTimeZoneResolver {
        var resolutionCalls: Int = 0
            private set
        var projectionCalls: Int = 0
            private set

        override fun resolve(localDateTime: LocalDateTime, timeZoneId: String): LocalTimeResolution {
            resolutionCalls++
            check(resolutionCalls <= resolutionBudget) {
                "Query scanned from DTSTART instead of using its overlap-aware local bound"
            }
            return KotlinxRecurrenceTimeZoneResolver.resolve(localDateTime, resolverZoneId)
        }

        override fun localDateTimeAt(instant: Instant, timeZoneId: String): LocalDateTime {
            projectionCalls++
            return KotlinxRecurrenceTimeZoneResolver.localDateTimeAt(instant, resolverZoneId)
        }

        override fun nonexistentInstant(localDateTime: LocalDateTime, timeZoneId: String): Instant =
            KotlinxRecurrenceTimeZoneResolver.nonexistentInstant(localDateTime, resolverZoneId)
    }

    private fun paris(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
    ): RecurrenceDateTime.Zoned = zoned(year, month, day, hour, minute, second, "Europe/Paris")

    private fun zoned(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
        timeZoneId: String,
    ): RecurrenceDateTime.Zoned = RecurrenceDateTime.Zoned(
        LocalDateTime(year, month, day, hour, minute, second),
        timeZoneId,
    )

    private fun utc(value: String): RecurrenceDateTime.Utc = RecurrenceDateTime.Utc(Instant.parse(value))
}
