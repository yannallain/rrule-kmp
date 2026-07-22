package io.github.yallain.rrule.engine

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.KotlinxRecurrenceTimeZoneResolver
import io.github.yallain.rrule.LocalTimeResolution
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceTimeZoneResolver
import io.github.yallain.rrule.RuleRecurrence
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Regression coverage for adversarial, but valid, per-period expansion cardinalities. */
class PeriodGeneratorLazinessTest {
    @Test
    fun maximumCardinalityYearStreamsItsPrefix() {
        val recurrence = RuleRecurrence(
            start = floating(2024, 1, 1, 0, 0, 0),
            rule = maximumCardinalityYear(count = 3),
        )

        assertEquals(
            listOf(
                floating(2024, 1, 1, 0, 0, 0),
                floating(2024, 1, 1, 0, 0, 1),
                floating(2024, 1, 1, 0, 0, 2),
            ),
            recurrence.occurrences().toList(),
        )
    }

    @Test
    fun positiveSetPositionDoesNotMaterializeTheRestOfAMaximumCardinalityYear() {
        val recurrence = RuleRecurrence(
            start = floating(2024, 1, 1, 0, 0, 0),
            rule = maximumCardinalityYear(count = 1).copy(bySetPosition = setOf(1)),
        )

        assertEquals(listOf(floating(2024, 1, 1, 0, 0, 0)), recurrence.occurrences().toList())
    }

    @Test
    fun negativeSetPositionStreamsFromTheEndOfAMaximumCardinalityYear() {
        val recurrence = RuleRecurrence(
            start = floating(2024, 1, 1, 0, 0, 0),
            rule = maximumCardinalityYear(count = 1).copy(bySetPosition = setOf(-1)),
        )

        assertEquals(listOf(floating(2024, 12, 31, 23, 59, 59)), recurrence.occurrences().toList())
    }

    @Test
    fun mixedSetPositionsReadOnlyTheRequestedPrefixAndSuffix() {
        val recurrence = RuleRecurrence(
            start = floating(2024, 1, 1, 0, 0, 0),
            rule = maximumCardinalityYear(count = 2).copy(bySetPosition = setOf(1, -1)),
        )

        assertEquals(
            listOf(
                floating(2024, 1, 1, 0, 0, 0),
                floating(2024, 12, 31, 23, 59, 59),
            ),
            recurrence.occurrences().toList(),
        )
    }

    @Test
    fun boundedQueriesDoNotResolveCandidatesOutsideTheirDenseYearWindow() {
        var resolutionCount = 0
        val resolver = RecurrenceTimeZoneResolver { dateTime, _ ->
            resolutionCount++
            LocalTimeResolution.Valid(dateTime.toInstant(UtcOffset.ZERO))
        }
        val recurrence = RuleRecurrence(
            start = zoned(2024, 1, 1, 0, 0, 0),
            rule = maximumCardinalityYear(count = null),
            timeZoneResolver = resolver,
        )

        resolutionCount = 0
        assertEquals(
            zoned(2024, 12, 31, 23, 59, 59),
            recurrence.before(zoned(2025, 1, 1, 0, 0, 0)),
        )
        assertTrue(resolutionCount < 20, "Reverse query resolved $resolutionCount candidates")

        resolutionCount = 0
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
        assertTrue(resolutionCount < 30, "Forward query resolved $resolutionCount candidates")
    }

    @Test
    fun forwardQueryAboveTheRfcYearCeilingDoesNotScanTheFinalDenseYear() {
        var resolutionCount = 0
        val resolver = RecurrenceTimeZoneResolver { dateTime, _ ->
            resolutionCount++
            LocalTimeResolution.Valid(dateTime.toInstant(UtcOffset.ZERO))
        }
        val recurrence = RuleRecurrence(
            start = zoned(2024, 1, 1, 0, 0, 0),
            rule = maximumCardinalityYear(count = null),
            timeZoneResolver = resolver,
        )

        resolutionCount = 0
        assertNull(recurrence.after(zoned(10_000, 1, 1, 0, 0, 0)))
        assertTrue(resolutionCount < 5, "Out-of-range query resolved $resolutionCount candidates")
    }

    @Test
    fun lateYearDtStartPrunesEarlierCandidatesBeforeResolvingThem() {
        var resolutionCount = 0
        val resolver = RecurrenceTimeZoneResolver { dateTime, _ ->
            resolutionCount++
            LocalTimeResolution.Valid(dateTime.toInstant(UtcOffset.ZERO))
        }
        val start = zoned(2024, 12, 31, 23, 59, 59)
        val recurrence = RuleRecurrence(
            start = start,
            rule = maximumCardinalityYear(count = 1),
            timeZoneResolver = resolver,
        )

        resolutionCount = 0
        assertEquals(listOf(start), recurrence.occurrences().toList())
        assertTrue(resolutionCount < 5, "DTSTART prefix resolved $resolutionCount candidates")
    }

    @Test
    fun alignedUtcBoundsPruneDenseZonedCandidates() {
        var resolutionCount = 0
        val resolver = object : RecurrenceTimeZoneResolver {
            override fun resolve(localDateTime: LocalDateTime, timeZoneId: String): LocalTimeResolution {
                resolutionCount++
                return KotlinxRecurrenceTimeZoneResolver.resolve(localDateTime, timeZoneId)
            }

            override fun localDateTimeAt(instant: Instant, timeZoneId: String): LocalDateTime =
                KotlinxRecurrenceTimeZoneResolver.localDateTimeAt(instant, timeZoneId)

            override fun nonexistentInstant(localDateTime: LocalDateTime, timeZoneId: String): Instant =
                KotlinxRecurrenceTimeZoneResolver.nonexistentInstant(localDateTime, timeZoneId)
        }
        val start = paris(2024, 1, 1, 0, 0, 0)
        val recurrence = RuleRecurrence(
            start = start,
            rule = maximumCardinalityYear(count = null),
            timeZoneResolver = resolver,
        )

        resolutionCount = 0
        assertEquals(
            listOf(
                paris(2024, 12, 31, 23, 59, 58),
                paris(2024, 12, 31, 23, 59, 59),
            ),
            recurrence.between(
                RecurrenceDateTime.Utc(Instant.parse("2024-12-31T22:59:58Z")),
                RecurrenceDateTime.Utc(Instant.parse("2024-12-31T23:00:00Z")),
            ),
        )
        assertTrue(resolutionCount < 30, "UTC-bounded query resolved $resolutionCount candidates")
    }

    private fun maximumCardinalityYear(count: Int?): RecurrenceRule = RecurrenceRule(
        frequency = Frequency.YEARLY,
        count = count,
        byDay = Weekday.entries.map(::ByDay),
        byHour = (0..23).toSet(),
        byMinute = (0..59).toSet(),
        bySecond = (0..59).toSet(),
    )

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

    private fun paris(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): RecurrenceDateTime.Zoned = RecurrenceDateTime.Zoned(
        LocalDateTime(year, month, day, hour, minute, second),
        "Europe/Paris",
    )
}
