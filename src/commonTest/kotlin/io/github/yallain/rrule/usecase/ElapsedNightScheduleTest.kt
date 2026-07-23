package io.github.yallain.rrule.usecase

import io.github.yallain.rrule.RecurrenceContentParser
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.toInstantOrNull
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Acceptance coverage for the tagged, elapsed-duration schedule payload described in the README. */
class ElapsedNightScheduleTest {
    @Test
    fun previousNightIsIncludedAndAlwaysLastsNineElapsedHoursAcrossDst() {
        val recurrence = RecurrenceContentParser.parse(
            "DTSTART;TZID=Europe/Paris:20170101T210000\n" +
                "RRULE:FREQ=DAILY;WKST=MO",
        ).recurrenceSet()

        val transitions = listOf(
            ExpectedNight(
                localStart = LocalDateTime(2017, 3, 25, 21, 0),
                start = Instant.parse("2017-03-25T20:00:00Z"),
                end = Instant.parse("2017-03-26T05:00:00Z"),
                attendanceStart = LocalDateTime(2017, 3, 26, 0, 30),
                attendanceEnd = LocalDateTime(2017, 3, 26, 7, 30),
            ),
            ExpectedNight(
                localStart = LocalDateTime(2017, 10, 28, 21, 0),
                start = Instant.parse("2017-10-28T19:00:00Z"),
                end = Instant.parse("2017-10-29T04:00:00Z"),
                attendanceStart = LocalDateTime(2017, 10, 29, 0, 30),
                attendanceEnd = LocalDateTime(2017, 10, 29, 7, 30),
            ),
        )

        for (expected in transitions) {
            val attendanceStart = checkNotNull(zoned(expected.attendanceStart).toInstantOrNull())
            val attendanceEnd = checkNotNull(zoned(expected.attendanceEnd).toInstantOrNull())

            // The attendance begins after midnight, so the query looks back by the maximum
            // elapsed duration to include a night that started on the previous civil date.
            val occurrence = recurrence.between(
                startInclusive = RecurrenceDateTime.Utc(attendanceStart - 9.hours),
                endExclusive = RecurrenceDateTime.Utc(attendanceEnd),
            ).single() as RecurrenceDateTime.Zoned
            val start = checkNotNull(occurrence.toInstantOrNull())
            val end = start + 32_400.seconds
            val overlapStart = maxOf(start, attendanceStart)
            val overlapEnd = minOf(end, attendanceEnd)
            val breakStart = attendanceStart + 1.hours
            val breakEnd = breakStart + 30.minutes
            val breakOverlap = maxOf(
                Duration.ZERO,
                minOf(overlapEnd, breakEnd) - maxOf(overlapStart, breakStart),
            )
            val payableNightTime = overlapEnd - overlapStart - breakOverlap

            assertEquals(expected.localStart, occurrence.dateTime)
            assertEquals(expected.start, start)
            assertEquals(expected.end, end)
            assertEquals(9.hours, end - start)
            assertEquals(5.hours, payableNightTime)
        }
    }

    private fun zoned(value: LocalDateTime): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(value, "Europe/Paris")

    private data class ExpectedNight(
        val localStart: LocalDateTime,
        val start: Instant,
        val end: Instant,
        val attendanceStart: LocalDateTime,
        val attendanceEnd: LocalDateTime,
    )
}
