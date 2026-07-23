package io.github.yallain.rrule

import java.time.ZoneId
import kotlin.time.Instant

internal actual fun platformTimeZoneTransitions(
    timeZoneId: String,
    startInclusive: Instant,
    endExclusive: Instant,
): List<PlatformTimeZoneTransition> {
    if (startInclusive >= endExclusive) return emptyList()
    val rules = ZoneId.of(timeZoneId).rules
    if (rules.isFixedOffset) return emptyList()

    val start = startInclusive.toJavaInstant()
    val end = endExclusive.toJavaInstant()
    val result = mutableListOf<PlatformTimeZoneTransition>()
    var transition = rules.nextTransition(start.minusNanos(1))
    while (transition != null && transition.instant < end) {
        if (transition.instant >= start) {
            result += PlatformTimeZoneTransition(
                instant = transition.instant.toKotlinInstant(),
                offsetBeforeSeconds = transition.offsetBefore.totalSeconds,
                offsetAfterSeconds = transition.offsetAfter.totalSeconds,
            )
        }
        transition = rules.nextTransition(transition.instant)
    }
    return result
}

private fun Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

private fun java.time.Instant.toKotlinInstant(): Instant =
    Instant.fromEpochSeconds(epochSecond, nano.toLong())
