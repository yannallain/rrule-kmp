package io.github.yallain.rrule

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toNSDate
import kotlinx.datetime.toNSTimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual fun platformTimeZoneTransitions(
    timeZoneId: String,
    startInclusive: Instant,
    endExclusive: Instant,
): List<PlatformTimeZoneTransition> {
    if (startInclusive >= endExclusive) return emptyList()
    val kotlinTimeZone = TimeZone.of(timeZoneId)
    if (kotlinTimeZone is FixedOffsetTimeZone) return emptyList()
    val foundationTimeZone = kotlinTimeZone.toNSTimeZone()

    val result = mutableListOf<PlatformTimeZoneTransition>()
    var cursorInstant = startInclusive - 1.seconds
    var cursor = cursorInstant.toNSDate()
    while (true) {
        val transitionDate = foundationTimeZone.nextDaylightSavingTimeTransitionAfterDate(cursor) ?: break
        val transitionInstant = transitionDate.toKotlinInstant()
        if (transitionInstant >= endExclusive) break
        check(transitionInstant > cursorInstant) {
            "Timezone transition enumeration did not advance for $timeZoneId"
        }
        // TZif transition instants have whole-second precision. A nanosecond probe can round back
        // to the transition when kotlinx-datetime falls back to Foundation's Double-based NSDate.
        val offsetBefore = kotlinTimeZone.offsetAt(transitionInstant - 1.seconds).totalSeconds
        val offsetAfter = kotlinTimeZone.offsetAt(transitionInstant).totalSeconds
        if (transitionInstant >= startInclusive && offsetBefore != offsetAfter) {
            result += PlatformTimeZoneTransition(
                instant = transitionInstant,
                offsetBeforeSeconds = offsetBefore,
                offsetAfterSeconds = offsetAfter,
            )
        }
        cursor = transitionDate
        cursorInstant = transitionInstant
    }
    return result
}
