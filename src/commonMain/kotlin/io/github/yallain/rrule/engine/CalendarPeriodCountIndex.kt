package io.github.yallain.rrule

/**
 * Shape of one repeating Gregorian calendar-count cycle.
 *
 * [periodsPerBlock] keeps each lazy block close to one calendar year of date evaluation. The first
 * period after `DTSTART` always has its own block so a small or already exhausted `COUNT` does not
 * initialize a larger block.
 */
internal data class CalendarPeriodCountCycleShape(
    val periodCount: Int,
    val periodsPerBlock: Int,
) {
    init {
        require(periodCount > 0)
        require(periodsPerBlock > 0)
    }
}

/**
 * Counts and locates candidates in a repeating sequence of calendar periods.
 *
 * Gregorian dates, weekdays, and leap years repeat every 400 years. This index partitions that
 * exact cycle into thread-safe lazy blocks. Nearby and count-exhausted queries initialize only the
 * blocks they touch; distant and repeated queries reuse initialized blocks and apply arithmetic
 * across complete cycles.
 *
 * @param shape cycle and lazy-block dimensions.
 * @param candidateCountAtOffset candidate count in the zero-based period after `DTSTART`'s
 * separately handled first period.
 */
internal class CalendarPeriodCountIndex(
    private val shape: CalendarPeriodCountCycleShape,
    private val candidateCountAtOffset: (Long) -> Long?,
) {
    private val blocks: Array<Lazy<CalendarPeriodCountBlock?>> =
        Array(blockCount()) { blockIndex ->
            lazy {
                CalendarPeriodCountBlock.build(
                    startOffset = blockStartOffset(blockIndex),
                    periodCount = blockPeriodCount(blockIndex),
                    candidateCountAtOffset = candidateCountAtOffset,
                )
            }
        }

    /**
     * Counts candidates in the first [periods] periods.
     *
     * When [maximumCount] is supplied, the result is capped at that value. A linear timeline can
     * therefore prove that `COUNT` is exhausted without evaluating the rest of the prefix.
     */
    fun countForPeriods(
        periods: Long,
        maximumCount: Long? = null,
    ): Long? {
        if (periods <= 0L || maximumCount == 0L) return 0L
        require(maximumCount == null || maximumCount > 0L)

        val cyclePeriodCount = shape.periodCount.toLong()
        if (periods < cyclePeriodCount) {
            return countWithinCycle(periods.toInt(), maximumCount)
        }

        val cycleCandidateCount = countWithinCycle(shape.periodCount, maximumCount) ?: return null
        if (maximumCount != null && cycleCandidateCount >= maximumCount) return maximumCount

        val completeCycles = periods / cyclePeriodCount
        var total = saturatingProduct(completeCycles, cycleCandidateCount)
        if (maximumCount != null && total >= maximumCount) return maximumCount

        val remainingPeriods = (periods % cyclePeriodCount).toInt()
        if (remainingPeriods == 0) return total
        val remainingMaximum = maximumCount?.minus(total)
        val remainder = countWithinCycle(remainingPeriods, remainingMaximum) ?: return null
        total = saturatingAdd(total, remainder)
        return maximumCount?.let { minOf(total, it) } ?: total
    }

    /**
     * Locates a one-based candidate ordinal in the infinitely repeated period sequence.
     *
     * [Location.periodOffset] is zero-based: offset zero is the first period represented by this
     * index. Empty periods are skipped without affecting the ordinal.
     */
    fun locate(
        oneBasedOrdinal: Long,
        maximumPeriodOffset: Long = Long.MAX_VALUE,
    ): Location? {
        if (oneBasedOrdinal <= 0L || maximumPeriodOffset < 0L) return null
        val periodsAllowedInFirstCycle = if (
            maximumPeriodOffset >= shape.periodCount.toLong() - 1L
        ) {
            shape.periodCount
        } else {
            (maximumPeriodOffset + 1L).toInt()
        }
        locateWithinCycle(oneBasedOrdinal, periodsAllowedInFirstCycle)?.let { return it }
        if (periodsAllowedInFirstCycle < shape.periodCount) return null

        val cycleCandidateCount = countWithinCycle(shape.periodCount) ?: return null
        if (cycleCandidateCount == 0L) return null
        val completeCycles = (oneBasedOrdinal - 1L) / cycleCandidateCount
        val ordinalInCycle = (oneBasedOrdinal - 1L) % cycleCandidateCount + 1L
        val locationInCycle = locateWithinCycle(
            oneBasedOrdinal = ordinalInCycle,
            periods = shape.periodCount,
        ) ?: return null
        val repeatedPeriodCount = positiveProductOrNull(
            completeCycles,
            shape.periodCount.toLong(),
        ) ?: return null
        if (locationInCycle.periodOffset > Long.MAX_VALUE - repeatedPeriodCount) return null
        val location = locationInCycle.copy(
            periodOffset = repeatedPeriodCount + locationInCycle.periodOffset,
        )
        return location.takeIf { it.periodOffset <= maximumPeriodOffset }
    }

    private fun countWithinCycle(
        periods: Int,
        maximumCount: Long? = null,
    ): Long? {
        if (periods <= 0 || maximumCount == 0L) return 0L
        var remainingPeriods = periods
        var total = 0L
        for (blockReference in blocks) {
            if (remainingPeriods == 0) break
            val block = blockReference.value ?: return null
            val count = block.countForPeriods(minOf(remainingPeriods, block.periodCount))
            total = saturatingAdd(total, count)
            if (maximumCount != null && total >= maximumCount) return maximumCount
            remainingPeriods -= minOf(remainingPeriods, block.periodCount)
        }
        return total
    }

    private fun locateWithinCycle(
        oneBasedOrdinal: Long,
        periods: Int,
    ): Location? {
        var remainingPeriods = periods
        var precedingCandidates = 0L
        for (blockReference in blocks) {
            if (remainingPeriods == 0) break
            val block = blockReference.value ?: return null
            val periodsFromBlock = minOf(remainingPeriods, block.periodCount)
            val blockCandidateCount = block.countForPeriods(periodsFromBlock)
            val ordinalInBlock = oneBasedOrdinal - precedingCandidates
            if (ordinalInBlock <= blockCandidateCount) {
                return block.locate(ordinalInBlock)
            }
            precedingCandidates = saturatingAdd(precedingCandidates, blockCandidateCount)
            remainingPeriods -= periodsFromBlock
        }
        return null
    }

    private fun blockCount(): Int {
        if (shape.periodCount == 1) return 1
        val periodsAfterFirst = shape.periodCount - 1
        return 1 + (periodsAfterFirst + shape.periodsPerBlock - 1) / shape.periodsPerBlock
    }

    private fun blockStartOffset(blockIndex: Int): Long =
        if (blockIndex == 0) 0L
        else 1L + (blockIndex - 1L) * shape.periodsPerBlock

    private fun blockPeriodCount(blockIndex: Int): Int {
        if (blockIndex == 0) return 1
        val startOffset = blockStartOffset(blockIndex).toInt()
        return minOf(shape.periodsPerBlock, shape.periodCount - startOffset)
    }

    internal data class Location(
        val periodOffset: Long,
        val ordinalInPeriod: Long,
    )
}

/** Immutable cumulative candidate counts for one lazily initialized period block. */
private class CalendarPeriodCountBlock private constructor(
    private val startOffset: Long,
    private val cumulativeCandidateCounts: LongArray,
) {
    val periodCount: Int = cumulativeCandidateCounts.lastIndex
    val candidateCount: Long = cumulativeCandidateCounts.last()

    fun countForPeriods(periods: Int): Long {
        require(periods in 0..periodCount)
        return cumulativeCandidateCounts[periods]
    }

    fun locate(oneBasedOrdinal: Long): CalendarPeriodCountIndex.Location? {
        if (oneBasedOrdinal <= 0L || oneBasedOrdinal > candidateCount) return null
        var lower = 1
        var upper = cumulativeCandidateCounts.lastIndex
        while (lower < upper) {
            val middle = lower + (upper - lower) / 2
            if (cumulativeCandidateCounts[middle] >= oneBasedOrdinal) {
                upper = middle
            } else {
                lower = middle + 1
            }
        }
        return CalendarPeriodCountIndex.Location(
            periodOffset = startOffset + lower - 1L,
            ordinalInPeriod = oneBasedOrdinal - cumulativeCandidateCounts[lower - 1],
        )
    }

    companion object {
        fun build(
            startOffset: Long,
            periodCount: Int,
            candidateCountAtOffset: (Long) -> Long?,
        ): CalendarPeriodCountBlock? {
            val cumulativeCounts = LongArray(periodCount + 1)
            for (index in 0 until periodCount) {
                val count = candidateCountAtOffset(startOffset + index) ?: return null
                if (count < 0L) return null
                cumulativeCounts[index + 1] = saturatingAdd(cumulativeCounts[index], count)
            }
            return CalendarPeriodCountBlock(startOffset, cumulativeCounts)
        }
    }
}

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right >= Long.MAX_VALUE - left) Long.MAX_VALUE else left + right

private fun saturatingProduct(left: Long, right: Long): Long = when {
    left == 0L || right == 0L -> 0L
    left > Long.MAX_VALUE / right -> Long.MAX_VALUE
    else -> left * right
}
