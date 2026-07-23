package io.github.yallain.rrule

import kotlin.time.Instant

/** One platform timezone-database offset transition on the instant timeline. */
internal data class PlatformTimeZoneTransition(
    val instant: Instant,
    val offsetBeforeSeconds: Int,
    val offsetAfterSeconds: Int,
)

/** Returns every offset transition in `[startInclusive, endExclusive)`, in ascending order. */
internal expect fun platformTimeZoneTransitions(
    timeZoneId: String,
    startInclusive: Instant,
    endExclusive: Instant,
): List<PlatformTimeZoneTransition>
