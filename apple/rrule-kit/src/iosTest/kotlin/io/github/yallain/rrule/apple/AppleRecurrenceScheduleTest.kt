package io.github.yallain.rrule.apple

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import platform.Foundation.NSDate

class AppleRecurrenceScheduleTest {
    @Test
    fun returnsFoundationDatesForZonedOccurrences() {
        val schedule = AppleRecurrenceParser.parse(
            """
            DTSTART;TZID=Europe/Paris:20240330T210000
            RRULE:FREQ=DAILY;COUNT=3
            """.trimIndent(),
        )

        val occurrences = schedule.occurrences(
            fromInclusive = date("2024-03-30T00:00:00Z"),
            toExclusive = date("2024-04-02T00:00:00Z"),
            maximumCount = 10,
        )

        assertEquals("Europe/Paris", schedule.timeZoneIdentifier)
        assertEquals(
            listOf(
                "2024-03-30T20:00:00Z",
                "2024-03-31T19:00:00Z",
                "2024-04-01T19:00:00Z",
            ).map(::epochSeconds),
            occurrences.map { it.epochSeconds() },
        )
    }

    @Test
    fun elapsedIntervalLooksBackBeforeWindowAcrossSpringTransition() {
        val schedule = dailyParisSchedule("20240330T210000")

        val result = schedule.intervals(
            overlappingStartInclusive = date("2024-03-31T04:30:00Z"),
            endExclusive = date("2024-03-31T06:00:00Z"),
            elapsedDurationSeconds = 32_400,
            maximumCount = 10,
        )

        val interval = result.intervals.single()
        assertEquals(false, result.hasMore)
        assertEquals(epochSeconds("2024-03-30T20:00:00Z"), interval.start.epochSeconds())
        assertEquals(epochSeconds("2024-03-31T05:00:00Z"), interval.endExclusive.epochSeconds())
        assertEquals(
            32_400.0,
            interval.endExclusive.timeIntervalSinceReferenceDate - interval.start.timeIntervalSinceReferenceDate,
        )
    }

    @Test
    fun elapsedIntervalRemainsNineHoursAcrossFallTransition() {
        val schedule = dailyParisSchedule("20241026T210000")

        val interval = schedule.intervals(
            overlappingStartInclusive = date("2024-10-26T18:00:00Z"),
            endExclusive = date("2024-10-27T06:00:00Z"),
            elapsedDurationSeconds = 32_400,
            maximumCount = 10,
        ).intervals.single()

        assertEquals(epochSeconds("2024-10-26T19:00:00Z"), interval.start.epochSeconds())
        assertEquals(epochSeconds("2024-10-27T04:00:00Z"), interval.endExclusive.epochSeconds())
        assertEquals(
            32_400.0,
            interval.endExclusive.timeIntervalSinceReferenceDate - interval.start.timeIntervalSinceReferenceDate,
        )
    }

    @Test
    fun intervalsUseHalfOpenOverlap() {
        val schedule = dailyParisSchedule("20240330T210000")

        assertTrue(
            schedule.intervals(
                overlappingStartInclusive = date("2024-03-31T05:00:00Z"),
                endExclusive = date("2024-03-31T06:00:00Z"),
                elapsedDurationSeconds = 32_400,
                maximumCount = 10,
            ).intervals.isEmpty(),
        )
    }

    @Test
    fun nextAndPreviousRespectInclusivity() {
        val schedule = dailyParisSchedule("20240330T210000")
        val occurrence = date("2024-03-31T19:00:00Z")

        assertEquals(
            occurrence.timeIntervalSinceReferenceDate,
            schedule.nextOccurrence(occurrence, inclusive = true)?.timeIntervalSinceReferenceDate,
        )
        assertEquals(
            epochSeconds("2024-04-01T19:00:00Z").toDouble(),
            schedule.nextOccurrence(occurrence, inclusive = false)?.epochSeconds()?.toDouble(),
        )
        assertEquals(
            occurrence.timeIntervalSinceReferenceDate,
            schedule.previousOccurrence(occurrence, inclusive = true)?.timeIntervalSinceReferenceDate,
        )
        assertEquals(
            epochSeconds("2024-03-30T20:00:00Z").toDouble(),
            schedule.previousOccurrence(occurrence, inclusive = false)?.epochSeconds()?.toDouble(),
        )
        assertNull(schedule.previousOccurrence(date("2024-03-30T19:00:00Z"), inclusive = true))
    }

    @Test
    fun intervalQueryReportsTruncationInsteadOfSilentlyDroppingResults() {
        val schedule = dailyParisSchedule("20240330T210000")

        val result = schedule.intervals(
            overlappingStartInclusive = date("2024-03-30T00:00:00Z"),
            endExclusive = date("2024-04-03T00:00:00Z"),
            elapsedDurationSeconds = 32_400,
            maximumCount = 1,
        )

        assertEquals(1, result.intervals.size)
        assertTrue(result.hasMore)
    }

    @Test
    fun intervalResultsExposeAReadOnlySnapshot() {
        val result = dailyParisSchedule("20240330T210000").intervals(
            overlappingStartInclusive = date("2024-03-30T00:00:00Z"),
            endExclusive = date("2024-04-03T00:00:00Z"),
            elapsedDurationSeconds = 32_400,
            maximumCount = 10,
        )

        assertEquals(3, result.intervals.size)
        assertFails {
            @Suppress("UNCHECKED_CAST")
            (result.intervals as MutableList<AppleRecurrenceInterval>).clear()
        }
        assertEquals(3, result.intervals.size)
    }

    private fun dailyParisSchedule(start: String): AppleRecurrenceSchedule =
        AppleRecurrenceParser.parse(
            """
            DTSTART;TZID=Europe/Paris:$start
            RRULE:FREQ=DAILY;COUNT=3
            """.trimIndent(),
        )

    private fun date(value: String): NSDate = NSDate(
        timeIntervalSinceReferenceDate = epochSeconds(value).toDouble() - APPLE_REFERENCE_DATE_OFFSET_SECONDS,
    )

    private fun NSDate.epochSeconds(): Long =
        (timeIntervalSinceReferenceDate + APPLE_REFERENCE_DATE_OFFSET_SECONDS).toLong()

    private fun epochSeconds(value: String): Long = Instant.parse(value).epochSeconds

    private companion object {
        const val APPLE_REFERENCE_DATE_OFFSET_SECONDS: Double = 978_307_200.0
    }
}
