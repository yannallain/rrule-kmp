package io.github.yallain.rrule

import kotlin.concurrent.Volatile

/**
 * Finds a finite prefix that contains the `COUNT`th valid transition-aware occurrence.
 *
 * Every probe is constrained to the current query. Large counts and long nonexistent-time ranges
 * therefore cannot make a nearby query chase the eventual final occurrence beyond its bound,
 * while the immutable raw-`COUNT` prefix is initialized once and reused across queries.
 */
internal class FiniteTransitionCountPrefixIndex(
    private val countLimit: Long,
    private val maximumPeriodIndex: (LocalValue) -> Long,
    private val rawCandidateAtOrdinalThroughPeriod: (Long, Long) -> LocalCandidate?,
    private val uncachedPrefixThrough: (LocalValue, Boolean) -> CountedOccurrencePrefix?,
) {
    private val initialRawCountPrefix: Lazy<InitialRawCountPrefix?> =
        lazy(::buildInitialRawCountPrefix)

    @Volatile
    private var completedPrefix: CompletedTransitionCountPrefix? = null

    fun prefixThrough(
        upperBound: LocalValue,
        inclusive: Boolean,
    ): CountedOccurrencePrefix? {
        completedPrefix
            ?.takeIf { it.finalOccurrence.isCoveredBy(upperBound, inclusive) }
            ?.let { return it.prefix }

        var rawOrdinal = countLimit
        var previousOccurrenceCount = -1L
        val finalPeriodIndex = maximumPeriodIndex(upperBound)
        if (!initialRawCountPrefix.isInitialized()) {
            val rawCountCandidate = rawCandidateAtOrdinalThroughPeriod(
                countLimit,
                finalPeriodIndex,
            ) ?: return uncachedPrefixThrough(upperBound, inclusive)
            if (!rawCountCandidate.toLocalValue().isCoveredBy(upperBound, inclusive)) {
                return uncachedPrefixThrough(upperBound, inclusive)
            }
        }

        val initialPrefix = initialRawCountPrefix.value
            ?: return uncachedPrefixThrough(upperBound, inclusive)
        if (!initialPrefix.rawCountCandidate.isCoveredBy(upperBound, inclusive)) {
            return uncachedPrefixThrough(upperBound, inclusive)
        }
        var prefix = initialPrefix.prefix
        while (true) {
            if (prefix.occurrenceCount >= countLimit) {
                return rememberCompletedPrefix(prefix)
            }

            val missingOccurrences = countLimit - prefix.occurrenceCount
            val deficitAdvance = saturatingAdd(rawOrdinal, missingOccurrences)
            val nextRawOrdinal = if (prefix.occurrenceCount <= previousOccurrenceCount) {
                maxOf(deficitAdvance, saturatingProduct(rawOrdinal, 3L))
            } else {
                deficitAdvance
            }
            if (nextRawOrdinal <= rawOrdinal) return null
            previousOccurrenceCount = prefix.occurrenceCount
            rawOrdinal = nextRawOrdinal

            val candidate = rawCandidateAtOrdinalThroughPeriod(rawOrdinal, finalPeriodIndex)
                ?: return uncachedPrefixThrough(upperBound, inclusive)
            if (!candidate.toLocalValue().isCoveredBy(upperBound, inclusive)) {
                return uncachedPrefixThrough(upperBound, inclusive)
            }
            prefix = uncachedPrefixThrough(candidate.toLocalValue(), true) ?: return null
        }
    }

    private fun rememberCompletedPrefix(
        prefix: CountedOccurrencePrefix,
    ): CountedOccurrencePrefix {
        val finalOccurrence = prefix
            .candidateAtOccurrenceOrdinal(countLimit)
            ?.toLocalValue()
            ?: return prefix
        completedPrefix = CompletedTransitionCountPrefix(
            finalOccurrence = finalOccurrence,
            prefix = prefix,
        )
        return prefix
    }

    private fun buildInitialRawCountPrefix(): InitialRawCountPrefix? {
        val candidate = rawCandidateAtOrdinalThroughPeriod(countLimit, Long.MAX_VALUE) ?: return null
        return InitialRawCountPrefix(
            rawCountCandidate = candidate.toLocalValue(),
            prefix = uncachedPrefixThrough(candidate.toLocalValue(), true) ?: return null,
        )
    }
}

private data class InitialRawCountPrefix(
    val rawCountCandidate: LocalValue,
    val prefix: CountedOccurrencePrefix,
)

private data class CompletedTransitionCountPrefix(
    val finalOccurrence: LocalValue,
    val prefix: CountedOccurrencePrefix,
)

private fun LocalCandidate.toLocalValue(): LocalValue = when (this) {
    is DateCandidate -> LocalValue(date, null)
    is DateTimeCandidate -> LocalValue(dateTime.date, dateTime)
}

private fun LocalValue.isCoveredBy(
    upperBound: LocalValue,
    inclusive: Boolean,
): Boolean = this < upperBound || inclusive && this == upperBound

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right >= Long.MAX_VALUE - left) Long.MAX_VALUE else left + right

private fun saturatingProduct(left: Long, right: Long): Long = when {
    left == 0L || right == 0L -> 0L
    left > Long.MAX_VALUE / right -> Long.MAX_VALUE
    else -> left * right
}
