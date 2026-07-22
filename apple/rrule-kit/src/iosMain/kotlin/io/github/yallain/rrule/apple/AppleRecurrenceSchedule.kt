package io.github.yallain.rrule.apple

import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceSet
import io.github.yallain.rrule.toInstantOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import platform.Foundation.NSDate

/**
 * A bounded, Foundation-friendly recurrence schedule for Objective-C and Swift consumers.
 *
 * Instances are created by [AppleRecurrenceParser]. All dates represent absolute instants, all
 * windows are half-open, and no unbounded Kotlin sequence crosses the native boundary.
 */
public class AppleRecurrenceSchedule internal constructor(
    private val recurrenceSet: RecurrenceSet,
    /** The `TZID` declared by `DTSTART`, or `null` when the start is UTC. */
    public val timeZoneIdentifier: String?,
) {
    /**
     * Returns every start in `[fromInclusive, toExclusive)` when the result fits [maximumCount].
     *
     * Throws [AppleRecurrenceException] with
     * [AppleRecurrenceErrorCode.RESULT_LIMIT_EXCEEDED] instead of returning a partial result.
     */
    @Throws(AppleRecurrenceException::class)
    public fun occurrences(
        fromInclusive: NSDate,
        toExclusive: NSDate,
        maximumCount: Int,
    ): List<NSDate> = appleRecurrenceBoundary(
        fallbackCode = AppleRecurrenceErrorCode.EVALUATION_FAILED,
    ) {
        requirePositiveMaximumCount(maximumCount)
        val start = fromInclusive.toKotlinInstant()
        val end = toExclusive.toKotlinInstant()
        val occurrences = recurrenceSet.between(
            startInclusive = start.asUtcRecurrenceValue(),
            endExclusive = end.asUtcRecurrenceValue(),
            limit = maximumCount + 1,
        )
        if (occurrences.size > maximumCount) {
            appleRecurrenceResultLimitExceeded(maximumCount)
        }
        occurrences.map { it.requireInstant().toFoundationDate() }
    }

    /** Returns the next start relative to [date], or `null` when the schedule is exhausted. */
    @Throws(AppleRecurrenceException::class)
    public fun nextOccurrence(
        after: NSDate,
        inclusive: Boolean,
    ): NSDate? = appleRecurrenceBoundary(
        fallbackCode = AppleRecurrenceErrorCode.EVALUATION_FAILED,
    ) {
        recurrenceSet.after(
            value = after.toKotlinInstant().asUtcRecurrenceValue(),
            inclusive = inclusive,
        )?.requireInstant()?.toFoundationDate()
    }

    /** Returns the previous start relative to [date], or `null` when none exists. */
    @Throws(AppleRecurrenceException::class)
    public fun previousOccurrence(
        before: NSDate,
        inclusive: Boolean,
    ): NSDate? = appleRecurrenceBoundary(
        fallbackCode = AppleRecurrenceErrorCode.EVALUATION_FAILED,
    ) {
        recurrenceSet.before(
            value = before.toKotlinInstant().asUtcRecurrenceValue(),
            inclusive = inclusive,
        )?.requireInstant()?.toFoundationDate()
    }

    /**
     * Returns elapsed-duration intervals that overlap `[overlappingStartInclusive, endExclusive)`.
     *
     * Starts before the requested window are included when their elapsed interval reaches into it.
     * The end is computed by adding exactly [elapsedDurationSeconds] on the instant timeline, so
     * daylight-saving transitions never change the duration.
     */
    @Throws(AppleRecurrenceException::class)
    public fun intervals(
        overlappingStartInclusive: NSDate,
        endExclusive: NSDate,
        elapsedDurationSeconds: Long,
        maximumCount: Int,
    ): AppleRecurrenceIntervalQueryResult = appleRecurrenceBoundary(
        fallbackCode = AppleRecurrenceErrorCode.EVALUATION_FAILED,
    ) {
        if (elapsedDurationSeconds <= 0) {
            invalidArgument("elapsedDurationSeconds must be positive")
        }
        requirePositiveMaximumCount(maximumCount)

        val windowStart = overlappingStartInclusive.toKotlinInstant()
        val windowEnd = endExclusive.toKotlinInstant()
        if (windowStart >= windowEnd) {
            return@appleRecurrenceBoundary AppleRecurrenceIntervalQueryResult(
                intervals = emptyList(),
                hasMore = false,
            )
        }

        val duration = elapsedDurationSeconds.seconds
        if (!duration.isFinite()) invalidArgument("elapsedDurationSeconds is too large")
        val queryStart = subtractDuration(windowStart, duration)
        val queryLimit = maximumCount + NON_OVERLAPPING_BOUNDARY_ALLOWANCE + 1
        val overlapping = recurrenceSet.between(
            startInclusive = queryStart.asUtcRecurrenceValue(),
            endExclusive = windowEnd.asUtcRecurrenceValue(),
            limit = queryLimit,
        ).asSequence()
            .map { it.requireInstant() }
            .map { start -> start to addDuration(start, duration) }
            .filter { (start, end) -> start < windowEnd && end > windowStart }
            .take(maximumCount + 1)
            .map { (start, end) ->
                AppleRecurrenceInterval(
                    start = start.toFoundationDate(),
                    endExclusive = end.toFoundationDate(),
                )
            }
            .toList()
        AppleRecurrenceIntervalQueryResult(
            intervals = overlapping.take(maximumCount),
            hasMore = overlapping.size > maximumCount,
        )
    }

    private fun requirePositiveMaximumCount(maximumCount: Int) {
        if (maximumCount !in 1..MAXIMUM_RESULT_COUNT) {
            invalidArgument("maximumCount must be between 1 and $MAXIMUM_RESULT_COUNT")
        }
    }

    private fun invalidArgument(message: String): Nothing =
        invalidAppleRecurrenceArgument(message)

    private fun subtractDuration(instant: Instant, duration: Duration): Instant = try {
        instant - duration
    } catch (_: IllegalArgumentException) {
        invalidArgument("elapsedDurationSeconds exceeds the supported instant range")
    }

    private fun addDuration(instant: Instant, duration: Duration): Instant = try {
        instant + duration
    } catch (_: IllegalArgumentException) {
        invalidArgument("elapsedDurationSeconds exceeds the supported instant range")
    }

    private fun RecurrenceDateTime.requireInstant(): Instant =
        checkNotNull(toInstantOrNull(
            timeZoneResolver = recurrenceSet.timeZoneResolver,
            ambiguousTimePolicy = recurrenceSet.ambiguousTimePolicy,
        )) {
            "The Apple facade encountered a non-instant recurrence value"
        }

    private fun Instant.asUtcRecurrenceValue(): RecurrenceDateTime = RecurrenceDateTime.Utc(this)

    private companion object {
        const val MAXIMUM_RESULT_COUNT: Int = 100_000
        const val NON_OVERLAPPING_BOUNDARY_ALLOWANCE: Int = 1
    }
}
