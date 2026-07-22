package io.github.yallain.rrule

/**
 * Indexes locally countable recurrence prefixes without resolving or enumerating every candidate.
 *
 * This helper is used only when each local candidate is known to represent exactly one occurrence.
 * `BYSETPOS` and `BYWEEKNO` retain the general scan because their period-relative semantics need
 * additional cross-period or post-resolution accounting.
 */
internal class CountedRecurrenceIndexer(
    private val generator: PeriodGenerator,
    private val startLocal: LocalValue,
    private val frequency: Frequency,
    private val countLimit: Long,
) {
    private val constantCountAfterFirstPeriod = generator.constantCandidateCountAfterFirstPeriod()

    fun forwardStart(periodIndex: Long, lowerBound: LocalValue): CountedForwardStart? {
        val beforePeriod = countBeforePeriod(periodIndex) ?: return null
        if (beforePeriod >= countLimit) return CountedForwardStart(periodIndex, countLimit)
        val insidePeriod = generator.candidateCount(
            periodIndex = periodIndex,
            lowerBoundInclusive = startLocal.takeIf { periodIndex == 0L },
            upperBound = lowerBound,
            upperInclusive = false,
        ) ?: return null
        return CountedForwardStart(
            periodIndex = periodIndex,
            emittedBeforeLowerBound = cappedAdd(beforePeriod, insidePeriod),
        )
    }

    fun occurrenceCountThrough(
        firstPossiblyIntersectingPeriod: Long,
        finalPossiblyIntersectingPeriod: Long,
        upperBound: LocalValue,
        inclusive: Boolean,
    ): Long? {
        var total = countBeforePeriod(firstPossiblyIntersectingPeriod) ?: return null
        var periodIndex = firstPossiblyIntersectingPeriod
        while (periodIndex <= finalPossiblyIntersectingPeriod && total < countLimit) {
            val count = generator.candidateCount(
                periodIndex = periodIndex,
                lowerBoundInclusive = startLocal.takeIf { periodIndex == 0L },
                upperBound = upperBound,
                upperInclusive = inclusive,
            ) ?: break
            total = cappedAdd(total, count)
            if (periodIndex == Long.MAX_VALUE) break
            periodIndex++
        }
        return total
    }

    fun candidateAtOrdinal(oneBasedOrdinal: Long): LocalCandidate? {
        if (oneBasedOrdinal !in 1L..countLimit) return null
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
            emitted = cappedAdd(emitted, periodCount)
            if (emitted >= oneBasedOrdinal || periodIndex == Long.MAX_VALUE) return null
            periodIndex++
        }
    }

    private fun countBeforePeriod(exclusivePeriodIndex: Long): Long? {
        if (exclusivePeriodIndex <= 0L) return 0L
        val firstPeriodCount = periodCount(0L) ?: return null
        if (exclusivePeriodIndex == 1L || firstPeriodCount >= countLimit) return firstPeriodCount

        constantCountAfterFirstPeriod?.let { constantCount ->
            return cappedAdd(
                firstPeriodCount,
                cappedProduct(exclusivePeriodIndex - 1L, constantCount),
            )
        }

        if (!frequency.hasBoundedCalendarPeriodCount()) return null
        var total = firstPeriodCount
        var periodIndex = 1L
        while (periodIndex < exclusivePeriodIndex && total < countLimit) {
            val periodCount = periodCount(periodIndex) ?: return total
            total = cappedAdd(total, periodCount)
            periodIndex++
        }
        return total
    }

    private fun periodCount(periodIndex: Long): Long? = generator.candidateCount(
        periodIndex = periodIndex,
        lowerBoundInclusive = startLocal.takeIf { periodIndex == 0L },
    )

    private fun cappedAdd(left: Long, right: Long): Long =
        if (right >= countLimit - left) countLimit else left + right

    private fun cappedProduct(left: Long, right: Long): Long = when {
        left == 0L || right == 0L -> 0L
        left >= countLimit -> countLimit
        right >= countLimit -> countLimit
        left > countLimit / right -> countLimit
        else -> (left * right).coerceAtMost(countLimit)
    }
}

internal data class CountedForwardStart(
    val periodIndex: Long,
    val emittedBeforeLowerBound: Long,
)

private fun Frequency.hasBoundedCalendarPeriodCount(): Boolean = when (this) {
    Frequency.YEARLY, Frequency.MONTHLY, Frequency.WEEKLY, Frequency.DAILY -> true
    Frequency.HOURLY, Frequency.MINUTELY, Frequency.SECONDLY -> false
}
