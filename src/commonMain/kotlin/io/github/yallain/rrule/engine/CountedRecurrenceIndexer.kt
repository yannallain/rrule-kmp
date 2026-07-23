package io.github.yallain.rrule

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Indexes countable recurrence prefixes without enumerating every local candidate.
 *
 * A linear temporal domain can use the raw local-candidate count directly. A transition-aware
 * zoned domain supplies every nonexistent local-time range through the query bound; candidates in
 * those gaps are removed from the occurrence ordinal space. Overlaps do not change cardinality.
 *
 * `BYSETPOS` retains the general scan because its post-resolution semantics cannot be represented
 * by this prefix index.
 */
internal class CountedRecurrenceIndexer(
    private val generator: PeriodGenerator,
    private val startLocal: LocalValue,
    private val frequency: Frequency,
    private val countLimit: Long,
    private val periodFloor: (LocalValue) -> Long,
    private val periodCeiling: (LocalValue) -> Long,
    private val nonexistentRangesThrough: ((LocalDateTime) -> List<NonexistentLocalTimeRange>)? = null,
) {
    private val constantCountAfterFirstPeriod = generator.constantCandidateCountAfterFirstPeriod()

    fun forwardStart(periodIndex: Long, lowerBound: LocalValue): CountedForwardStart? {
        val occurrenceCount = prefixThrough(lowerBound, inclusive = false)?.occurrenceCount
            ?: return null
        return CountedForwardStart(
            periodIndex = periodIndex,
            emittedBeforeLowerBound = occurrenceCount.coerceAtMost(countLimit),
        )
    }

    fun prefixThrough(
        upperBound: LocalValue,
        inclusive: Boolean,
    ): CountedOccurrencePrefix? {
        val localCandidateCount = rawCandidateCountThrough(upperBound, inclusive) ?: return null
        val invalidRanges = invalidOrdinalRangesThrough(
            upperBound = upperBound,
            inclusive = inclusive,
            localCandidateCount = localCandidateCount,
        ) ?: return null
        val invalidCandidateCount = invalidRanges.fold(0L) { total, range ->
            saturatingAdd(total, range.size)
        }
        return CountedOccurrencePrefix(
            occurrenceCount = (localCandidateCount - invalidCandidateCount).coerceAtLeast(0L),
            localCandidateCount = localCandidateCount,
            invalidLocalOrdinalRanges = invalidRanges,
            candidateAtLocalOrdinal = ::rawCandidateAtOrdinal,
        )
    }

    private fun rawCandidateAtOrdinal(oneBasedOrdinal: Long): LocalCandidate? {
        if (oneBasedOrdinal <= 0L) return null
        val constantCount = constantCountAfterFirstPeriod
        if (constantCount != null) {
            val firstPeriodCount = periodCount(0L) ?: return null
            if (oneBasedOrdinal <= firstPeriodCount) {
                return generator.candidateAt(0L, oneBasedOrdinal, startLocal)
            }
            if (constantCount == 0L) return null
            val remainingOrdinal = oneBasedOrdinal - firstPeriodCount
            val periodIndex = (remainingOrdinal - 1L) / constantCount + 1L
            val ordinalInPeriod = (remainingOrdinal - 1L) % constantCount + 1L
            return generator.candidateAt(periodIndex, ordinalInPeriod)
        }

        if (!frequency.hasBoundedCalendarPeriodCount()) return null
        var emitted = 0L
        var periodIndex = 0L
        while (true) {
            val periodCount = periodCount(periodIndex) ?: return null
            if (oneBasedOrdinal - emitted <= periodCount) {
                return generator.candidateAt(
                    periodIndex = periodIndex,
                    ordinal = oneBasedOrdinal - emitted,
                    lowerBoundInclusive = startLocal.takeIf { periodIndex == 0L },
                )
            }
            emitted = saturatingAdd(emitted, periodCount)
            if (emitted >= oneBasedOrdinal || periodIndex == Long.MAX_VALUE) return null
            periodIndex++
        }
    }

    private fun rawCandidateCountThrough(
        upperBound: LocalValue,
        inclusive: Boolean,
    ): Long? {
        val firstPossiblyIntersectingPeriod = periodFloor(upperBound)
        var total = rawCandidateCountBeforePeriod(firstPossiblyIntersectingPeriod) ?: return null
        var periodIndex = firstPossiblyIntersectingPeriod
        val finalPossiblyIntersectingPeriod = periodCeiling(upperBound)
        while (periodIndex <= finalPossiblyIntersectingPeriod) {
            val count = generator.candidateCount(
                periodIndex = periodIndex,
                lowerBoundInclusive = startLocal.takeIf { periodIndex == 0L },
                upperBound = upperBound,
                upperInclusive = inclusive,
            ) ?: return null
            total = saturatingAdd(total, count)
            if (periodIndex == Long.MAX_VALUE) break
            periodIndex++
        }
        return total
    }

    private fun rawCandidateCountBeforePeriod(exclusivePeriodIndex: Long): Long? {
        if (exclusivePeriodIndex <= 0L) return 0L
        val firstPeriodCount = periodCount(0L) ?: return null
        if (exclusivePeriodIndex == 1L) return firstPeriodCount

        constantCountAfterFirstPeriod?.let { constantCount ->
            return saturatingAdd(
                firstPeriodCount,
                saturatingProduct(exclusivePeriodIndex - 1L, constantCount),
            )
        }

        if (!frequency.hasBoundedCalendarPeriodCount()) return null
        var total = firstPeriodCount
        var periodIndex = 1L
        while (periodIndex < exclusivePeriodIndex && total < Long.MAX_VALUE) {
            val periodCount = periodCount(periodIndex) ?: return total
            total = saturatingAdd(total, periodCount)
            periodIndex++
        }
        return total
    }

    private fun invalidOrdinalRangesThrough(
        upperBound: LocalValue,
        inclusive: Boolean,
        localCandidateCount: Long,
    ): List<LongRange>? {
        val provider = nonexistentRangesThrough ?: return emptyList()
        val startDateTime = startLocal.dateTime ?: return null
        val upperDateTime = upperBound.dateTime ?: return null
        if (upperDateTime < startDateTime || localCandidateCount == 0L) return emptyList()

        val gaps = runCatching { provider(upperDateTime) }.getOrNull() ?: return null
        if (gaps.zipWithNext().any { (left, right) ->
                left.startInclusive > right.startInclusive ||
                    left.endExclusive > right.startInclusive
            }
        ) {
            return null
        }

        val ranges = mutableListOf<LongRange>()
        var gapContainingStart: NonexistentLocalTimeRange? = null
        gaps.forEach { gap ->
            if (gap.contains(startDateTime)) gapContainingStart = gap
            if (gap.endExclusive <= startDateTime || gap.startInclusive > upperDateTime) {
                return@forEach
            }
            val lower = maxOf(startDateTime, gap.startInclusive)
            val upper = minOf(upperDateTime, gap.endExclusive)
            val upperIsInclusive = upper < gap.endExclusive && upper == upperDateTime && inclusive
            addInvalidOrdinalRange(
                destination = ranges,
                lowerExclusiveCount = rawCandidateCountThrough(localValue(lower), inclusive = false)
                    ?: return null,
                upperInclusiveCount = rawCandidateCountThrough(
                    localValue(upper),
                    inclusive = upperIsInclusive,
                ) ?: return null,
                localCandidateCount = localCandidateCount,
            )
        }

        var mergedRanges = mergeOrdinalRanges(ranges)
        gapContainingStart?.let { gap ->
            val projectedStart = projectAcrossGap(startDateTime, gap) ?: return null
            val specialUpper = minOf(upperDateTime, projectedStart)
            val specialUpperInclusive = specialUpper == projectedStart ||
                specialUpper == upperDateTime && inclusive
            addInvalidOrdinalRange(
                destination = ranges,
                lowerExclusiveCount = rawCandidateCountThrough(
                    startLocal,
                    inclusive = true,
                ) ?: return null,
                upperInclusiveCount = rawCandidateCountThrough(
                    localValue(specialUpper),
                    inclusive = specialUpperInclusive,
                ) ?: return null,
                localCandidateCount = localCandidateCount,
            )

            val countBeforeStart = rawCandidateCountThrough(startLocal, inclusive = false) ?: return null
            val countThroughStart = rawCandidateCountThrough(startLocal, inclusive = true) ?: return null
            mergedRanges = mergeOrdinalRanges(ranges)
            if (countThroughStart > countBeforeStart) {
                // DTSTART is resolved with RFC 5545's pre-gap offset even though an otherwise
                // identical generated candidate would be discarded as nonexistent.
                mergedRanges = mergedRanges.flatMap { range ->
                    range.removing(countThroughStart)
                }
            }
        }
        return mergedRanges
    }

    private fun addInvalidOrdinalRange(
        destination: MutableList<LongRange>,
        lowerExclusiveCount: Long,
        upperInclusiveCount: Long,
        localCandidateCount: Long,
    ) {
        if (lowerExclusiveCount == Long.MAX_VALUE) return
        val first = lowerExclusiveCount + 1L
        val last = minOf(upperInclusiveCount, localCandidateCount)
        if (first <= last) destination += first..last
    }

    private fun projectAcrossGap(
        startDateTime: LocalDateTime,
        gap: NonexistentLocalTimeRange,
    ): LocalDateTime? = runCatching {
        val gapDuration = gap.endExclusive.toInstant(TimeZone.UTC) -
            gap.startInclusive.toInstant(TimeZone.UTC)
        (startDateTime.toInstant(TimeZone.UTC) + gapDuration).toLocalDateTime(TimeZone.UTC)
    }.getOrNull()

    private fun periodCount(periodIndex: Long): Long? = generator.candidateCount(
        periodIndex = periodIndex,
        lowerBoundInclusive = startLocal.takeIf { periodIndex == 0L },
    )

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (right >= Long.MAX_VALUE - left) Long.MAX_VALUE else left + right

    private fun saturatingProduct(left: Long, right: Long): Long = when {
        left == 0L || right == 0L -> 0L
        left > Long.MAX_VALUE / right -> Long.MAX_VALUE
        else -> left * right
    }

    private fun localValue(dateTime: LocalDateTime): LocalValue =
        LocalValue(dateTime.date, dateTime)
}

internal class CountedOccurrencePrefix(
    val occurrenceCount: Long,
    private val localCandidateCount: Long,
    private val invalidLocalOrdinalRanges: List<LongRange>,
    private val candidateAtLocalOrdinal: (Long) -> LocalCandidate?,
) {
    fun candidateAtOccurrenceOrdinal(oneBasedOrdinal: Long): LocalCandidate? {
        if (oneBasedOrdinal !in 1L..occurrenceCount) return null
        var localOrdinal = oneBasedOrdinal
        for (invalidRange in invalidLocalOrdinalRanges) {
            if (invalidRange.first > localOrdinal) break
            localOrdinal = saturatingAdd(localOrdinal, invalidRange.size)
        }
        if (localOrdinal > localCandidateCount) return null
        return candidateAtLocalOrdinal(localOrdinal)
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (right >= Long.MAX_VALUE - left) Long.MAX_VALUE else left + right
}

internal data class CountedForwardStart(
    val periodIndex: Long,
    val emittedBeforeLowerBound: Long,
)

private fun Frequency.hasBoundedCalendarPeriodCount(): Boolean = when (this) {
    Frequency.YEARLY, Frequency.MONTHLY, Frequency.WEEKLY, Frequency.DAILY -> true
    Frequency.HOURLY, Frequency.MINUTELY, Frequency.SECONDLY -> false
}

private val LongRange.size: Long
    get() = last - first + 1L

private fun LongRange.removing(value: Long): List<LongRange> = when {
    value !in this -> listOf(this)
    first == last -> emptyList()
    value == first -> listOf((first + 1L)..last)
    value == last -> listOf(first..(last - 1L))
    else -> listOf(first..(value - 1L), (value + 1L)..last)
}

private fun mergeOrdinalRanges(ranges: List<LongRange>): List<LongRange> {
    if (ranges.size < 2) return ranges
    val sorted = ranges.sortedBy(LongRange::first)
    val merged = ArrayList<LongRange>(sorted.size)
    var current = sorted.first()
    for (next in sorted.drop(1)) {
        val overlapsOrTouches = current.last == Long.MAX_VALUE || next.first <= current.last + 1L
        if (overlapsOrTouches) {
            current = current.first..maxOf(current.last, next.last)
        } else {
            merged += current
            current = next
        }
    }
    merged += current
    return merged
}
