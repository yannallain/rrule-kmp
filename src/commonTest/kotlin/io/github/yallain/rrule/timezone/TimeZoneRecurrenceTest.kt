package io.github.yallain.rrule.timezone

import io.github.yallain.rrule.AmbiguousTimePolicy
import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.KotlinxRecurrenceTimeZoneResolver
import io.github.yallain.rrule.LocalTimeResolution
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceSet
import io.github.yallain.rrule.RecurrenceTimeZoneResolver
import io.github.yallain.rrule.RuleRecurrence
import io.github.yallain.rrule.Weekday
import io.github.yallain.rrule.toInstantOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class TimeZoneRecurrenceTest {
    @Test
    fun distinguishesValidNonexistentAndAmbiguousParisTimes() {
        assertEquals(
            LocalDateTime(2024, 3, 30, 2, 30),
            KotlinxRecurrenceTimeZoneResolver.localDateTimeAt(
                Instant.parse("2024-03-30T01:30:00Z"),
                "Europe/Paris",
            ),
        )
        assertEquals(
            LocalTimeResolution.Valid(Instant.parse("2024-03-30T01:30:00Z")),
            KotlinxRecurrenceTimeZoneResolver.resolve(
                LocalDateTime(2024, 3, 30, 2, 30),
                "Europe/Paris",
            ),
        )
        assertIs<LocalTimeResolution.Nonexistent>(
            KotlinxRecurrenceTimeZoneResolver.resolve(
                LocalDateTime(2024, 3, 31, 2, 30),
                "Europe/Paris",
            ),
        )
        assertEquals(
            LocalTimeResolution.Ambiguous(
                earlier = Instant.parse("2024-10-27T00:30:00Z"),
                later = Instant.parse("2024-10-27T01:30:00Z"),
            ),
            KotlinxRecurrenceTimeZoneResolver.resolve(
                LocalDateTime(2024, 10, 27, 2, 30),
                "Europe/Paris",
            ),
        )
    }

    @Test
    fun explicitNonexistentValuesUseTheRfcOffsetBeforeTheGap() {
        val local = LocalDateTime(2007, 3, 11, 2, 30)
        val zoned = RecurrenceDateTime.Zoned(local, "America/New_York")

        assertIs<LocalTimeResolution.Nonexistent>(
            KotlinxRecurrenceTimeZoneResolver.resolve(local, "America/New_York"),
        )
        assertEquals(
            Instant.parse("2007-03-11T07:30:00Z"),
            KotlinxRecurrenceTimeZoneResolver.nonexistentInstant(local, "America/New_York"),
        )
        assertEquals(Instant.parse("2007-03-11T07:30:00Z"), zoned.toInstantOrNull())
        assertEquals(
            listOf(zoned),
            RecurrenceSet(
                start = zoned,
                additionalDates = setOf(
                    RecurrenceDateTime.Utc(Instant.parse("2007-03-11T07:30:00Z")),
                ),
            ).occurrences().toList(),
        )
    }

    @Test
    fun skipsSpringGapWithoutCountingIt() {
        val recurrence = RuleRecurrence(
            start = zoned(2024, 3, 30, 2, 30),
            rule = RecurrenceRule(Frequency.DAILY, count = 3),
        )

        assertEquals(
            listOf(
                zoned(2024, 3, 30, 2, 30),
                zoned(2024, 4, 1, 2, 30),
                zoned(2024, 4, 2, 2, 30),
            ),
            recurrence.occurrences().toList(),
        )
    }

    @Test
    fun setPositionCountsOnlyValidLocalTimesAcrossASpringGap() {
        val recurrence = RuleRecurrence(
            start = zoned(2024, 3, 30, 0, 0),
            rule = RecurrenceRule(
                frequency = Frequency.DAILY,
                count = 2,
                byHour = setOf(1, 2, 3),
                byMinute = setOf(30),
                bySetPosition = setOf(2),
            ),
        )

        assertEquals(
            listOf(zoned(2024, 3, 30, 2, 30), zoned(2024, 3, 31, 3, 30)),
            recurrence.occurrences().toList(),
        )
    }

    @Test
    fun negativeSetPositionIgnoresTheSpringGapInForwardAndDescendingQueries() {
        val recurrence = RuleRecurrence(
            start = zoned(2024, 3, 30, 0, 0),
            rule = RecurrenceRule(
                frequency = Frequency.DAILY,
                byHour = setOf(1, 2, 3),
                byMinute = setOf(30),
                bySetPosition = setOf(-2),
            ),
        )

        assertEquals(
            listOf(
                zoned(2024, 3, 30, 2, 30),
                zoned(2024, 3, 31, 1, 30),
                zoned(2024, 4, 1, 2, 30),
            ),
            recurrence.occurrences().take(3).toList(),
        )
        assertEquals(
            zoned(2024, 3, 31, 1, 30),
            recurrence.before(zoned(2024, 4, 1, 2, 30), inclusive = false),
        )
    }

    @Test
    fun explicitGapDtStartCountsOnceAndNoOccurrencePrecedesItsResolvedInstant() {
        val start = newYork(2007, 3, 11, 2, 30)
        val minutelyRule = RecurrenceRule(Frequency.MINUTELY, interval = 30, count = 2)

        assertEquals(
            listOf(start, newYork(2007, 3, 11, 4, 0)),
            RuleRecurrence(start, minutelyRule).occurrences().toList(),
        )
        assertEquals(
            listOf(start, newYork(2007, 3, 11, 4, 0)),
            RecurrenceSet(start = start, rules = listOf(minutelyRule)).occurrences().toList(),
        )
        assertEquals(
            listOf(start, newYork(2007, 3, 12, 2, 30)),
            RuleRecurrence(
                start,
                RecurrenceRule(Frequency.DAILY, count = 2),
            ).occurrences().toList(),
        )

        val utcLowerBound = RecurrenceDateTime.Utc(Instant.parse("2007-03-11T07:15:00Z"))
        val sameZoneLowerBound = newYork(2007, 3, 11, 3, 15)
        assertEquals(start, RuleRecurrence(start, minutelyRule).after(utcLowerBound))
        assertEquals(start, RuleRecurrence(start, minutelyRule).after(sameZoneLowerBound))
        assertEquals(
            listOf(start),
            RuleRecurrence(start, minutelyRule).between(
                utcLowerBound,
                RecurrenceDateTime.Utc(Instant.parse("2007-03-11T07:45:00Z")),
            ),
        )
    }

    @Test
    fun nonexistentSameZoneQueryBoundMatchesItsEquivalentUtcInstant() {
        val start = newYork(2007, 3, 11, 1, 50)
        val gapBound = newYork(2007, 3, 11, 2, 30)
        val utcBound = RecurrenceDateTime.Utc(Instant.parse("2007-03-11T07:30:00Z"))
        val recurrence = RuleRecurrence(start, RecurrenceRule(Frequency.MINUTELY))
        val expectedPrevious = newYork(2007, 3, 11, 3, 29)

        assertEquals(expectedPrevious, recurrence.before(gapBound))
        assertEquals(expectedPrevious, recurrence.before(utcBound))
        assertEquals(
            recurrence.between(start, utcBound),
            recurrence.between(start, gapBound),
        )
    }

    @Test
    fun ambiguityPolicyDeterministicallyControlsUtcUntilComparison() {
        val until = RecurrenceDateTime.Utc(Instant.parse("2024-10-27T01:00:00Z"))
        val rule = RecurrenceRule(Frequency.DAILY, until = until)
        val start = zoned(2024, 10, 26, 2, 30)

        assertEquals(
            listOf(start, zoned(2024, 10, 27, 2, 30)),
            RuleRecurrence(start, rule, ambiguousTimePolicy = AmbiguousTimePolicy.EARLIER)
                .occurrences().toList(),
        )
        assertEquals(
            listOf(start),
            RuleRecurrence(start, rule, ambiguousTimePolicy = AmbiguousTimePolicy.LATER)
                .occurrences().toList(),
        )
    }

    @Test
    fun instantConversionUsesTheSameAmbiguityPolicyAsRecurrenceEvaluation() {
        val ambiguous = zoned(2024, 10, 27, 2, 30)

        assertEquals(
            Instant.parse("2024-10-27T00:30:00Z"),
            ambiguous.toInstantOrNull(ambiguousTimePolicy = AmbiguousTimePolicy.EARLIER),
        )
        assertEquals(
            Instant.parse("2024-10-27T01:30:00Z"),
            ambiguous.toInstantOrNull(ambiguousTimePolicy = AmbiguousTimePolicy.LATER),
        )
        assertNull(RecurrenceDateTime.Floating(LocalDateTime(2024, 1, 1, 9, 0)).toInstantOrNull())
    }

    @Test
    fun zonedQueriesAcceptFractionalBounds() {
        val start = zoned(2024, 1, 1, 0, 0)
        val recurrence = RuleRecurrence(start, RecurrenceRule(Frequency.SECONDLY, count = 2))
        val halfSecond = RecurrenceDateTime.Zoned(
            LocalDateTime(2024, 1, 1, 0, 0, 0, 500_000_000),
            "Europe/Paris",
        )

        assertEquals(start, recurrence.before(halfSecond))
        assertEquals(zoned(2024, 1, 1, 0, 0, 1), recurrence.after(halfSecond))
        assertEquals(
            listOf(zoned(2024, 1, 1, 0, 0, 1)),
            RecurrenceSet(start, rules = listOf(RecurrenceRule(Frequency.SECONDLY, count = 2)))
                .between(
                    halfSecond,
                    RecurrenceDateTime.Zoned(
                        LocalDateTime(2024, 1, 1, 0, 0, 1, 500_000_000),
                        "Europe/Paris",
                    ),
                ),
        )
    }

    @Test
    fun mixedAbsoluteBoundsRemainCorrectOnTheOppositeOverlapBranch() {
        val laterRecurrence = RuleRecurrence(
            start = zoned(2024, 10, 27, 0, 15),
            rule = RecurrenceRule(Frequency.MINUTELY, byMinute = setOf(15)),
            ambiguousTimePolicy = AmbiguousTimePolicy.LATER,
        )
        assertEquals(
            zoned(2024, 10, 27, 2, 15),
            laterRecurrence.after(RecurrenceDateTime.Utc(Instant.parse("2024-10-27T00:30:00Z"))),
        )

        val earlierRecurrence = RuleRecurrence(
            start = zoned(2024, 10, 27, 0, 45),
            rule = RecurrenceRule(Frequency.MINUTELY, byMinute = setOf(45)),
            ambiguousTimePolicy = AmbiguousTimePolicy.EARLIER,
        )
        assertEquals(
            zoned(2024, 10, 27, 2, 45),
            earlierRecurrence.before(RecurrenceDateTime.Utc(Instant.parse("2024-10-27T01:30:00Z"))),
        )
    }

    @Test
    fun utcUntilOnTheOppositeOverlapBranchFallsBackToInstantScanning() {
        val start = zoned(2024, 10, 27, 0, 45)
        val recurrence = RuleRecurrence(
            start = start,
            rule = RecurrenceRule(
                frequency = Frequency.MINUTELY,
                until = RecurrenceDateTime.Utc(Instant.parse("2024-10-27T01:30:00Z")),
                byMinute = setOf(45),
            ),
            ambiguousTimePolicy = AmbiguousTimePolicy.EARLIER,
        )

        assertEquals(
            listOf(start, zoned(2024, 10, 27, 1, 45), zoned(2024, 10, 27, 2, 45)),
            recurrence.occurrences().toList(),
        )
    }

    @Test
    fun finiteUtcUntilTerminatesWithAResolveOnlyCustomTimeZone() {
        val resolver = RecurrenceTimeZoneResolver { localDateTime, _ ->
            LocalTimeResolution.Valid(localDateTime.toInstant(UtcOffset.ZERO))
        }
        val start = RecurrenceDateTime.Zoned(
            LocalDateTime(2024, 1, 1, 9, 0), // Monday
            "Custom/UTC",
        )
        val recurrence = RuleRecurrence(
            start = start,
            rule = RecurrenceRule(
                frequency = Frequency.DAILY,
                interval = 7,
                until = RecurrenceDateTime.Utc(Instant.parse("2024-01-02T09:00:00Z")),
                byDay = listOf(ByDay(Weekday.TUESDAY)),
            ),
            timeZoneResolver = resolver,
        )

        assertEquals(emptyList(), recurrence.occurrences().toList())
        assertNull(
            recurrence.before(
                RecurrenceDateTime.Zoned(LocalDateTime(2400, 1, 1, 9, 0), "Custom/UTC"),
            ),
        )
    }

    @Test
    fun resolveOnlyCustomTimeZoneSupportsUtcAndDifferentZoneQueryBounds() {
        val resolver = RecurrenceTimeZoneResolver { localDateTime, _ ->
            LocalTimeResolution.Valid(localDateTime.toInstant(UtcOffset.ZERO))
        }
        val start = RecurrenceDateTime.Zoned(LocalDateTime(2024, 1, 1, 9, 0), "Custom/UTC")
        val recurrence = RuleRecurrence(
            start = start,
            rule = RecurrenceRule(Frequency.DAILY),
            timeZoneResolver = resolver,
        )
        val expectedJanuarySecond = RecurrenceDateTime.Zoned(
            LocalDateTime(2024, 1, 2, 9, 0),
            "Custom/UTC",
        )
        val expectedJanuaryThird = RecurrenceDateTime.Zoned(
            LocalDateTime(2024, 1, 3, 9, 0),
            "Custom/UTC",
        )

        assertEquals(
            listOf(expectedJanuarySecond, expectedJanuaryThird),
            recurrence.between(
                RecurrenceDateTime.Utc(Instant.parse("2024-01-02T08:30:00Z")),
                RecurrenceDateTime.Utc(Instant.parse("2024-01-04T09:00:00Z")),
            ),
        )
        assertEquals(
            expectedJanuaryThird,
            recurrence.after(RecurrenceDateTime.Utc(Instant.parse("2024-01-02T09:30:00Z"))),
        )
        assertEquals(
            expectedJanuaryThird,
            recurrence.before(RecurrenceDateTime.Utc(Instant.parse("2024-01-03T09:30:00Z"))),
        )
        assertEquals(
            expectedJanuaryThird,
            recurrence.after(
                RecurrenceDateTime.Zoned(LocalDateTime(2024, 1, 2, 9, 30), "Other/UTC"),
            ),
        )
        assertEquals(
            listOf(expectedJanuarySecond, expectedJanuaryThird),
            RecurrenceSet(
                start = start,
                rules = listOf(RecurrenceRule(Frequency.DAILY)),
                timeZoneResolver = resolver,
            ).between(
                RecurrenceDateTime.Utc(Instant.parse("2024-01-02T08:30:00Z")),
                RecurrenceDateTime.Utc(Instant.parse("2024-01-04T09:00:00Z")),
            ),
        )
    }

    @Test
    fun fallbackUntilScanNeverPassesSyntheticPreRfcDatesToACustomResolver() {
        val resolver = RecurrenceTimeZoneResolver { localDateTime, _ ->
            require(localDateTime.year in 0..9999)
            LocalTimeResolution.Valid(localDateTime.toInstant(UtcOffset.ZERO))
        }
        val start = RecurrenceDateTime.Zoned(LocalDateTime(0, 1, 1, 9, 0), "Custom/UTC")

        assertEquals(
            listOf(
                start,
                RecurrenceDateTime.Zoned(LocalDateTime(0, 1, 8, 9, 0), "Custom/UTC"),
            ),
            RuleRecurrence(
                start = start,
                rule = RecurrenceRule(
                    frequency = Frequency.WEEKLY,
                    until = RecurrenceDateTime.Utc(Instant.parse("0000-01-10T09:00:00Z")),
                ),
                timeZoneResolver = resolver,
            ).occurrences().toList(),
        )
    }

    private fun zoned(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
    ): RecurrenceDateTime.Zoned = RecurrenceDateTime.Zoned(
        LocalDateTime(year, month, day, hour, minute, second),
        "Europe/Paris",
    )

    private fun newYork(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): RecurrenceDateTime.Zoned = RecurrenceDateTime.Zoned(
        LocalDateTime(year, month, day, hour, minute),
        "America/New_York",
    )
}
