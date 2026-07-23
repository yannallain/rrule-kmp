package io.github.yallain.rrule.apple

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import platform.Foundation.NSDate

class AppleRecurrenceErrorTest {
    @Test
    fun malformedContentBecomesFacadeException() {
        val error = assertFailsWith<AppleRecurrenceException> {
            AppleRecurrenceParser.parse(
                """
                DTSTART;TZID=Europe/Paris:20240330T210000
                RRULE:FREQ=NOT_A_FREQUENCY
                """.trimIndent(),
            )
        }

        assertEquals(AppleRecurrenceErrorCode.INVALID_CONTENT, error.code)
        assertEquals("NOT_A_FREQUENCY", error.invalidToken)
        assertEquals("FREQ", error.propertyName)
        assertEquals("MALFORMED_TOKEN", error.reason)
        assertTrue(error.inputValue.orEmpty().contains("RRULE:FREQ=NOT_A_FREQUENCY"))
        assertTrue(error.position != null)
        assertTrue(error.message.orEmpty().contains("NOT_A_FREQUENCY"))
    }

    @Test
    fun floatingStartIsRejectedByInstantFacade() {
        val error = assertFailsWith<AppleRecurrenceException> {
            AppleRecurrenceParser.parse(
                """
                DTSTART:20240330T210000
                RRULE:FREQ=DAILY
                """.trimIndent(),
            )
        }

        assertEquals(AppleRecurrenceErrorCode.UNSUPPORTED_TEMPORAL_DOMAIN, error.code)
        assertEquals("DTSTART", error.propertyName)
    }

    @Test
    fun invalidMaximumCountIsCatchable() {
        val schedule = AppleRecurrenceParser.parse(
            """
            DTSTART:20240330T200000Z
            RRULE:FREQ=DAILY
            """.trimIndent(),
        )

        val error = assertFailsWith<AppleRecurrenceException> {
            schedule.occurrences(
                fromInclusive = NSDate(timeIntervalSinceReferenceDate = -978_307_200.0),
                toExclusive = NSDate(timeIntervalSinceReferenceDate = -978_307_199.0),
                maximumCount = 0,
            )
        }

        assertEquals(AppleRecurrenceErrorCode.INVALID_ARGUMENT, error.code)
        assertTrue(error.message.orEmpty().contains("maximumCount"))
    }

    @Test
    fun maximumCountHasAMemorySafeUpperBound() {
        val schedule = AppleRecurrenceParser.parse(
            """
            DTSTART:20240330T200000Z
            RRULE:FREQ=DAILY
            """.trimIndent(),
        )
        val start = NSDate(timeIntervalSinceReferenceDate = -978_307_200.0)

        assertTrue(
            schedule.occurrences(
                fromInclusive = start,
                toExclusive = start,
                maximumCount = 100_000,
            ).isEmpty(),
        )
        val error = assertFailsWith<AppleRecurrenceException> {
            schedule.occurrences(
                fromInclusive = start,
                toExclusive = start,
                maximumCount = 100_001,
            )
        }
        assertEquals(AppleRecurrenceErrorCode.INVALID_ARGUMENT, error.code)
    }

    @Test
    fun occurrenceLimitFailsInsteadOfReturningPartialResults() {
        val schedule = AppleRecurrenceParser.parse(
            """
            DTSTART:20240101T000000Z
            RRULE:FREQ=SECONDLY;COUNT=3
            """.trimIndent(),
        )

        val error = assertFailsWith<AppleRecurrenceException> {
            schedule.occurrences(
                fromInclusive = NSDate(timeIntervalSinceReferenceDate = 725_760_000.0),
                toExclusive = NSDate(timeIntervalSinceReferenceDate = 725_760_060.0),
                maximumCount = 1,
            )
        }

        assertEquals(AppleRecurrenceErrorCode.RESULT_LIMIT_EXCEEDED, error.code)
        assertEquals(1, error.maximumCount)
    }

    @Test
    fun infiniteElapsedDurationIsRejectedAsInvalidArgument() {
        val schedule = AppleRecurrenceParser.parse(
            """
            DTSTART:20240330T200000Z
            RRULE:FREQ=DAILY
            """.trimIndent(),
        )

        val error = assertFailsWith<AppleRecurrenceException> {
            schedule.intervals(
                overlappingStartInclusive = NSDate(timeIntervalSinceReferenceDate = 0.0),
                endExclusive = NSDate(timeIntervalSinceReferenceDate = 1.0),
                elapsedDurationSeconds = Long.MAX_VALUE,
                maximumCount = 1,
            )
        }
        assertEquals(AppleRecurrenceErrorCode.INVALID_ARGUMENT, error.code)
    }
}
