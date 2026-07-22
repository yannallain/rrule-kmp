package io.github.yallain.rrule.apple

import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Instant
import platform.Foundation.NSDate

private const val NANOSECONDS_PER_SECOND: Double = 1_000_000_000.0
private const val UNIX_TO_APPLE_REFERENCE_DATE_SECONDS: Long = 978_307_200L

internal fun NSDate.toKotlinInstant(): Instant {
    val referenceDateSeconds = timeIntervalSinceReferenceDate
    if (!referenceDateSeconds.isFinite()) {
        invalidAppleRecurrenceArgument("Date must have a finite time interval")
    }

    val wholeReferenceDateSeconds = floor(referenceDateSeconds)
    if (
        wholeReferenceDateSeconds < Long.MIN_VALUE.toDouble() ||
        wholeReferenceDateSeconds >= Long.MAX_VALUE.toDouble()
    ) {
        invalidAppleRecurrenceArgument("Date exceeds the supported instant range")
    }
    val wholeReferenceDateSecondsAsLong = wholeReferenceDateSeconds.toLong()
    if (wholeReferenceDateSecondsAsLong > Long.MAX_VALUE - UNIX_TO_APPLE_REFERENCE_DATE_SECONDS) {
        invalidAppleRecurrenceArgument("Date exceeds the supported instant range")
    }
    val nanosecondAdjustment =
        ((referenceDateSeconds - wholeReferenceDateSeconds) * NANOSECONDS_PER_SECOND).roundToLong()
    return try {
        Instant.fromEpochSeconds(
            epochSeconds = wholeReferenceDateSecondsAsLong + UNIX_TO_APPLE_REFERENCE_DATE_SECONDS,
            nanosecondAdjustment = nanosecondAdjustment,
        )
    } catch (_: IllegalArgumentException) {
        invalidAppleRecurrenceArgument("Date exceeds the supported instant range")
    }
}

internal fun Instant.toFoundationDate(): NSDate {
    val wholeReferenceDateSeconds = epochSeconds - UNIX_TO_APPLE_REFERENCE_DATE_SECONDS
    val referenceDateSeconds = if (wholeReferenceDateSeconds < 0 && nanosecondsOfSecond != 0) {
        (wholeReferenceDateSeconds + 1).toDouble() -
            (NANOSECONDS_PER_SECOND - nanosecondsOfSecond.toDouble()) / NANOSECONDS_PER_SECOND
    } else {
        wholeReferenceDateSeconds.toDouble() +
            nanosecondsOfSecond.toDouble() / NANOSECONDS_PER_SECOND
    }
    return NSDate(
        timeIntervalSinceReferenceDate = referenceDateSeconds,
    )
}
