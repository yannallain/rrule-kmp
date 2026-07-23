package io.github.yallain.rrule.engine

import io.github.yallain.rrule.AmbiguousTimePolicy
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.KotlinxRecurrenceTimeZoneResolver
import io.github.yallain.rrule.LinearLocalTimeZoneResolver
import io.github.yallain.rrule.LocalTimeResolution
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceTimeZoneResolver
import io.github.yallain.rrule.RuleRecurrence
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TransitionZoneCountedIndexDifferentialTest {
    @Test
    fun uncountedRuleDoesNotProbeLinearIndexCapability() {
        val start = zoned(2024, 1, 1, 9, timeZoneId = PARIS)
        val resolver = object : LinearLocalTimeZoneResolver {
            override fun hasLinearLocalTimeline(timeZoneId: String): Boolean =
                error("Uncounted recurrences must not inspect indexing capabilities")

            override fun resolve(
                localDateTime: LocalDateTime,
                timeZoneId: String,
            ): LocalTimeResolution = KotlinxRecurrenceTimeZoneResolver.resolve(
                localDateTime,
                timeZoneId,
            )

            override fun localDateTimeAt(
                instant: Instant,
                timeZoneId: String,
            ): LocalDateTime = KotlinxRecurrenceTimeZoneResolver.localDateTimeAt(
                instant,
                timeZoneId,
            )

            override fun nonexistentInstant(
                localDateTime: LocalDateTime,
                timeZoneId: String,
            ): Instant = KotlinxRecurrenceTimeZoneResolver.nonexistentInstant(
                localDateTime,
                timeZoneId,
            )
        }

        val recurrence = RuleRecurrence(
            start = start,
            rule = RecurrenceRule(Frequency.DAILY),
            timeZoneResolver = resolver,
        )

        assertEquals(start, recurrence.occurrences().first())
    }

    @Test
    fun forwardGapsMatchTheForcedScanReferenceForEveryQueryApi() {
        val scenarios = listOf(
            Scenario(
                name = "Paris one-hour gap",
                start = zoned(2024, 3, 31, 1, 58, timeZoneId = PARIS),
                rule = RecurrenceRule(Frequency.MINUTELY, count = 6),
                queries = instants(
                    "2024-03-31T00:57:00Z",
                    "2024-03-31T00:59:00Z",
                    "2024-03-31T01:00:30Z",
                    "2024-03-31T01:03:00Z",
                    "2024-03-31T01:04:00Z",
                ),
                windows = windows(
                    "2024-03-31T00:57:00Z" to "2024-03-31T01:04:00Z",
                    "2024-03-31T00:59:00Z" to "2024-03-31T01:02:00Z",
                ),
            ),
            Scenario(
                name = "Paris DTSTART inside a gap",
                start = zoned(2024, 3, 31, 2, 30, timeZoneId = PARIS),
                rule = RecurrenceRule(Frequency.MINUTELY, count = 4),
                queries = instants(
                    "2024-03-31T01:29:00Z",
                    "2024-03-31T01:30:00Z",
                    "2024-03-31T01:31:30Z",
                    "2024-03-31T01:33:00Z",
                    "2024-03-31T01:34:00Z",
                ),
                windows = windows(
                    "2024-03-31T01:29:00Z" to "2024-03-31T01:34:00Z",
                    "2024-03-31T01:30:00Z" to "2024-03-31T01:33:00Z",
                ),
            ),
            Scenario(
                name = "Lord Howe half-hour gap",
                start = zoned(2024, 10, 6, 1, 58, timeZoneId = LORD_HOWE),
                rule = RecurrenceRule(Frequency.MINUTELY, count = 5),
                queries = instants(
                    "2024-10-05T15:27:00Z",
                    "2024-10-05T15:29:00Z",
                    "2024-10-05T15:30:30Z",
                    "2024-10-05T15:32:00Z",
                    "2024-10-05T15:33:00Z",
                ),
                windows = windows(
                    "2024-10-05T15:27:00Z" to "2024-10-05T15:33:00Z",
                    "2024-10-05T15:29:00Z" to "2024-10-05T15:32:00Z",
                ),
            ),
            Scenario(
                name = "Apia skipped calendar day",
                start = zoned(2011, 12, 29, 23, timeZoneId = APIA),
                rule = RecurrenceRule(Frequency.HOURLY, count = 5),
                queries = instants(
                    "2011-12-30T08:59:00Z",
                    "2011-12-30T09:00:00Z",
                    "2011-12-30T09:30:00Z",
                    "2011-12-30T10:00:00Z",
                    "2011-12-30T11:30:00Z",
                    "2011-12-30T13:00:00Z",
                    "2011-12-30T14:00:00Z",
                ),
                windows = windows(
                    "2011-12-30T08:59:00Z" to "2011-12-30T14:00:00Z",
                    "2011-12-30T09:30:00Z" to "2011-12-30T12:00:00Z",
                ),
            ),
        )

        scenarios.forEach(::assertEquivalentForBothPolicies)
    }

    @Test
    fun fallOverlapMatchesTheForcedScanReferenceForBothPolicies() {
        assertEquivalentForBothPolicies(
            Scenario(
                name = "Paris one-hour overlap",
                start = zoned(2024, 10, 27, 2, 28, timeZoneId = PARIS),
                rule = RecurrenceRule(Frequency.MINUTELY, count = 5),
                queries = instants(
                    "2024-10-27T00:27:00Z",
                    "2024-10-27T00:29:00Z",
                    "2024-10-27T00:30:30Z",
                    "2024-10-27T00:33:00Z",
                    "2024-10-27T01:27:00Z",
                    "2024-10-27T01:29:00Z",
                    "2024-10-27T01:30:30Z",
                    "2024-10-27T01:33:00Z",
                ),
                windows = windows(
                    "2024-10-27T00:27:00Z" to "2024-10-27T00:33:00Z",
                    "2024-10-27T01:27:00Z" to "2024-10-27T01:33:00Z",
                    "2024-10-27T00:30:00Z" to "2024-10-27T01:31:00Z",
                ),
            ),
        )
    }

    private fun assertEquivalentForBothPolicies(scenario: Scenario) {
        AmbiguousTimePolicy.entries.forEach { policy ->
            assertEquivalent(scenario, policy)
        }
    }

    private fun assertEquivalent(
        scenario: Scenario,
        policy: AmbiguousTimePolicy,
    ) {
        val indexed = RuleRecurrence(
            start = scenario.start,
            rule = scenario.rule,
            timeZoneResolver = KotlinxRecurrenceTimeZoneResolver,
            ambiguousTimePolicy = policy,
        )
        val forcedScan = RuleRecurrence(
            start = scenario.start,
            rule = scenario.rule,
            timeZoneResolver = ForcedScanResolver,
            ambiguousTimePolicy = policy,
        )
        val context = "${scenario.name}, policy=$policy"
        val boundedOccurrenceCount = checkNotNull(scenario.rule.count) + 1

        assertEquals(
            expected = forcedScan.occurrences().take(boundedOccurrenceCount).toList(),
            actual = indexed.occurrences().take(boundedOccurrenceCount).toList(),
            message = "$context: occurrences",
        )

        scenario.queries.forEach { query ->
            listOf(false, true).forEach { inclusive ->
                assertEquals(
                    expected = forcedScan.after(query, inclusive),
                    actual = indexed.after(query, inclusive),
                    message = "$context: after($query, inclusive=$inclusive)",
                )
                assertEquals(
                    expected = forcedScan.before(query, inclusive),
                    actual = indexed.before(query, inclusive),
                    message = "$context: before($query, inclusive=$inclusive)",
                )
            }
        }

        scenario.windows.forEach { window ->
            assertEquals(
                expected = forcedScan.between(window.startInclusive, window.endExclusive),
                actual = indexed.between(window.startInclusive, window.endExclusive),
                message = "$context: between($window)",
            )
            assertEquals(
                expected = forcedScan.between(
                    window.startInclusive,
                    window.endExclusive,
                    limit = 2,
                ),
                actual = indexed.between(
                    window.startInclusive,
                    window.endExclusive,
                    limit = 2,
                ),
                message = "$context: between($window, limit=2)",
            )
        }
    }

    /**
     * Delegates all public timezone behavior while intentionally withholding the engine's
     * internal transition-range capability. This keeps the correctness-first scan path active.
     */
    private object ForcedScanResolver : RecurrenceTimeZoneResolver {
        override fun resolve(
            localDateTime: LocalDateTime,
            timeZoneId: String,
        ): LocalTimeResolution = KotlinxRecurrenceTimeZoneResolver.resolve(
            localDateTime,
            timeZoneId,
        )

        override fun localDateTimeAt(
            instant: Instant,
            timeZoneId: String,
        ): LocalDateTime = KotlinxRecurrenceTimeZoneResolver.localDateTimeAt(
            instant,
            timeZoneId,
        )

        override fun nonexistentInstant(
            localDateTime: LocalDateTime,
            timeZoneId: String,
        ): Instant = KotlinxRecurrenceTimeZoneResolver.nonexistentInstant(
            localDateTime,
            timeZoneId,
        )
    }

    private data class Scenario(
        val name: String,
        val start: RecurrenceDateTime.Zoned,
        val rule: RecurrenceRule,
        val queries: List<RecurrenceDateTime.Utc>,
        val windows: List<QueryWindow>,
    )

    private data class QueryWindow(
        val startInclusive: RecurrenceDateTime.Utc,
        val endExclusive: RecurrenceDateTime.Utc,
    )

    private fun instants(vararg values: String): List<RecurrenceDateTime.Utc> =
        values.map(::utc)

    private fun windows(
        vararg values: Pair<String, String>,
    ): List<QueryWindow> = values.map { (start, end) ->
        QueryWindow(utc(start), utc(end))
    }

    private fun utc(value: String): RecurrenceDateTime.Utc =
        RecurrenceDateTime.Utc(Instant.parse(value))

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
    }
}
