package io.github.yallain.rrule.apple

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import platform.Foundation.NSDate

class FoundationDateConversionsTest {
    @Test
    fun preservesFractionsAroundAppleReferenceDate() {
        assertRoundTrip(
            referenceDateSeconds = 0.000_000_010,
            expected = Instant.fromEpochSeconds(978_307_200L, 10L),
        )
        assertRoundTrip(
            referenceDateSeconds = -0.000_000_010,
            expected = Instant.fromEpochSeconds(978_307_199L, 999_999_990L),
        )
    }

    @Test
    fun normalizesNanosecondCarryWithoutLosingTheBoundary() {
        val date = NSDate(timeIntervalSinceReferenceDate = 0.999_999_999_6)

        assertEquals(Instant.fromEpochSeconds(978_307_201L), date.toKotlinInstant())
        assertEquals(1.0, date.toKotlinInstant().toFoundationDate().timeIntervalSinceReferenceDate)
    }

    @Test
    fun fractionalLowerBoundDoesNotIncludePreviousWholeSecond() {
        val schedule = AppleRecurrenceParser.parse(
            """
            DTSTART:20010101T000000Z
            RRULE:FREQ=DAILY;COUNT=1
            """.trimIndent(),
        )

        assertTrue(
            schedule.occurrences(
                fromInclusive = NSDate(timeIntervalSinceReferenceDate = 0.000_000_010),
                toExclusive = NSDate(timeIntervalSinceReferenceDate = 1.0),
                maximumCount = 1,
            ).isEmpty(),
        )
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeFoundationDates() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE).forEach { value ->
            val error = assertFailsWith<AppleRecurrenceException> {
                NSDate(timeIntervalSinceReferenceDate = value).toKotlinInstant()
            }

            assertEquals(AppleRecurrenceErrorCode.INVALID_ARGUMENT, error.code)
        }
    }

    private fun assertRoundTrip(referenceDateSeconds: Double, expected: Instant) {
        val date = NSDate(timeIntervalSinceReferenceDate = referenceDateSeconds)
        val instant = date.toKotlinInstant()

        assertEquals(expected, instant)
        assertEquals(referenceDateSeconds, instant.toFoundationDate().timeIntervalSinceReferenceDate)
    }
}
