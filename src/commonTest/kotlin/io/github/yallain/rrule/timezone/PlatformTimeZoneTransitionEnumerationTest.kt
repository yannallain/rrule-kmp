package io.github.yallain.rrule.timezone

import io.github.yallain.rrule.PlatformTimeZoneTransition
import io.github.yallain.rrule.platformTimeZoneTransitions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlatformTimeZoneTransitionEnumerationTest {
    @Test
    fun transitionAtStartIsIncludedAndTransitionAtEndIsExcluded() {
        val transitionInstant = Instant.parse("2024-03-31T01:00:00Z")
        val expected = PlatformTimeZoneTransition(
            instant = transitionInstant,
            offsetBeforeSeconds = 3_600,
            offsetAfterSeconds = 7_200,
        )

        assertEquals(
            listOf(expected),
            platformTimeZoneTransitions(
                timeZoneId = PARIS,
                startInclusive = transitionInstant,
                endExclusive = transitionInstant + 1.nanoseconds,
            ),
        )
        assertTrue(
            platformTimeZoneTransitions(
                timeZoneId = PARIS,
                startInclusive = transitionInstant - 1.seconds,
                endExclusive = transitionInstant,
            ).isEmpty(),
        )
        assertTrue(
            platformTimeZoneTransitions(
                timeZoneId = PARIS,
                startInclusive = transitionInstant + 1.nanoseconds,
                endExclusive = transitionInstant + 1.seconds,
            ).isEmpty(),
        )
        assertTrue(
            platformTimeZoneTransitions(
                timeZoneId = PARIS,
                startInclusive = transitionInstant,
                endExclusive = transitionInstant,
            ).isEmpty(),
        )
    }

    @Test
    fun transitionsAreSortedUniqueAndRepresentOffsetChanges() {
        val transitions = platformTimeZoneTransitions(
            timeZoneId = PARIS,
            startInclusive = Instant.parse("2020-01-01T00:00:00Z"),
            endExclusive = Instant.parse("2030-01-01T00:00:00Z"),
        )

        assertTrue(transitions.isNotEmpty())
        assertEquals(transitions.sortedBy(PlatformTimeZoneTransition::instant), transitions)
        assertEquals(transitions.size, transitions.map { it.instant }.toSet().size)
        assertTrue(
            transitions.all { transition ->
                transition.offsetBeforeSeconds != transition.offsetAfterSeconds
            },
        )
    }

    @Test
    fun fixedOffsetZoneHasNoTransitions() {
        assertTrue(
            platformTimeZoneTransitions(
                timeZoneId = "UTC",
                startInclusive = Instant.parse("1900-01-01T00:00:00Z"),
                endExclusive = Instant.parse("2100-01-01T00:00:00Z"),
            ).isEmpty(),
        )
    }

    @Test
    fun enumeratesKathmanduPoliticalOffsetChange() {
        val transitionInstant = Instant.parse("1985-12-31T18:30:00Z")

        assertEquals(
            listOf(
                PlatformTimeZoneTransition(
                    instant = transitionInstant,
                    offsetBeforeSeconds = 19_800,
                    offsetAfterSeconds = 20_700,
                ),
            ),
            platformTimeZoneTransitions(
                timeZoneId = "Asia/Kathmandu",
                startInclusive = transitionInstant - 1.hours,
                endExclusive = transitionInstant + 1.hours,
            ),
        )
    }

    @Test
    fun filtersLisbonRuleChangeThatDidNotMoveTheClock() {
        assertTrue(
            platformTimeZoneTransitions(
                timeZoneId = "Europe/Lisbon",
                startInclusive = Instant.parse("1992-09-27T00:00:00Z"),
                endExclusive = Instant.parse("1992-09-27T02:00:00Z"),
            ).isEmpty(),
        )
    }

    @Test
    fun enumeratesLordHoweHalfHourTransitions() {
        assertEquals(
            listOf(
                PlatformTimeZoneTransition(
                    instant = Instant.parse("2024-04-06T15:00:00Z"),
                    offsetBeforeSeconds = 39_600,
                    offsetAfterSeconds = 37_800,
                ),
                PlatformTimeZoneTransition(
                    instant = Instant.parse("2024-10-05T15:30:00Z"),
                    offsetBeforeSeconds = 37_800,
                    offsetAfterSeconds = 39_600,
                ),
            ),
            platformTimeZoneTransitions(
                timeZoneId = "Australia/Lord_Howe",
                startInclusive = Instant.parse("2024-01-01T00:00:00Z"),
                endExclusive = Instant.parse("2025-01-01T00:00:00Z"),
            ),
        )
    }

    @Test
    fun enumeratesApiaSkippedDayTransition() {
        val transitionInstant = Instant.parse("2011-12-30T10:00:00Z")

        assertEquals(
            listOf(
                PlatformTimeZoneTransition(
                    instant = transitionInstant,
                    offsetBeforeSeconds = -36_000,
                    offsetAfterSeconds = 50_400,
                ),
            ),
            platformTimeZoneTransitions(
                timeZoneId = "Pacific/Apia",
                startInclusive = transitionInstant - 1.hours,
                endExclusive = transitionInstant + 1.hours,
            ),
        )
    }

    private companion object {
        const val PARIS: String = "Europe/Paris"
    }
}
