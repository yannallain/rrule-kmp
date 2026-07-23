package io.github.yallain.rrule

import io.github.yallain.rrule.internal.collections.immutableListCopyOf
import io.github.yallain.rrule.internal.collections.immutableSetCopyOf

/**
 * A complete iCalendar recurrence set evaluated as a lazy ordered merge.
 *
 * `DTSTART`, [additionalDates], every [additionalPeriods] start, and all [rules] are inclusions.
 * [excludedDates] and [exclusionRules] take precedence, and duplicate instances are emitted once.
 * When [start] is absolute, UTC and TZID-bearing explicit dates may be mixed and are compared by
 * resolved instant. PERIOD end/duration metadata remains available through [additionalPeriods].
 * Every collection-valued property is a detached, read-only snapshot safe to share across callers.
 */
public class RecurrenceSet(
    public val start: RecurrenceDateTime,
    rules: List<RecurrenceRule> = emptyList(),
    exclusionRules: List<RecurrenceRule> = emptyList(),
    additionalDates: Set<RecurrenceDateTime> = emptySet(),
    excludedDates: Set<RecurrenceDateTime> = emptySet(),
    public val timeZoneResolver: RecurrenceTimeZoneResolver = KotlinxRecurrenceTimeZoneResolver,
    public val ambiguousTimePolicy: AmbiguousTimePolicy = AmbiguousTimePolicy.EARLIER,
    additionalPeriods: Set<RecurrencePeriod> = emptySet(),
) : Recurrence {
    public val rules: List<RecurrenceRule> = immutableListCopyOf(rules)
    public val exclusionRules: List<RecurrenceRule> = immutableListCopyOf(exclusionRules)
    public val additionalDates: Set<RecurrenceDateTime> = immutableSetCopyOf(additionalDates)
    public val additionalPeriods: Set<RecurrencePeriod> = immutableSetCopyOf(additionalPeriods)
    public val excludedDates: Set<RecurrenceDateTime> = immutableSetCopyOf(excludedDates)

    private val temporal = TemporalSupport(start, timeZoneResolver, ambiguousTimePolicy)
    private val inclusionRecurrences: List<RuleRecurrence>
    private val exclusionRecurrences: List<RuleRecurrence>
    private val resolvableAdditionalDates: List<RecurrenceDateTime>
    private val resolvableExcludedDates: List<RecurrenceDateTime>

    init {
        temporal.validateStart()
        this.additionalPeriods.forEach(::validateAdditionalPeriod)
        resolvableAdditionalDates = (this.additionalDates.asSequence() +
            this.additionalPeriods.asSequence().map(RecurrencePeriod::start))
            .map { value ->
                temporal.validateStoredValue(value, propertyName = "RDATE")
                value
            }
            .distinct()
            .sortedWith(temporal::compare)
            .toList()
        resolvableExcludedDates = this.excludedDates
            .map { value ->
                temporal.validateStoredValue(value, propertyName = "EXDATE")
                value
            }
            .sortedWith(temporal::compare)
        inclusionRecurrences = this.rules
            .filterNot { inclusion ->
                this.exclusionRules.any(inclusion::isProvablySemanticallyEquivalentTo)
            }
            .map(::bind)
        exclusionRecurrences = this.exclusionRules.map(::bind)
    }

    override fun occurrences(): Sequence<RecurrenceDateTime> = sequenceFrom(null, null)

    override fun between(
        startInclusive: RecurrenceDateTime,
        endExclusive: RecurrenceDateTime,
        limit: Int?,
    ): List<RecurrenceDateTime> {
        require(limit == null || limit >= 0) { "limit must not be negative" }
        if (limit == 0 || temporal.compare(startInclusive, endExclusive) >= 0) return emptyList()
        val result = ArrayList<RecurrenceDateTime>()
        for (occurrence in sequenceFrom(startInclusive, endExclusive)) {
            result += occurrence
            if (limit != null && result.size == limit) break
        }
        return result
    }

    override fun after(value: RecurrenceDateTime, inclusive: Boolean): RecurrenceDateTime? {
        for (occurrence in sequenceFrom(value, null)) {
            val comparison = temporal.compare(occurrence, value)
            if (comparison > 0 || inclusive && comparison == 0) return occurrence
        }
        return null
    }

    override fun before(value: RecurrenceDateTime, inclusive: Boolean): RecurrenceDateTime? {
        temporal.validateQueryValue(value)
        var upperBound = value
        var upperInclusive = inclusive
        while (true) {
            val candidate = latestInclusion(upperBound, upperInclusive) ?: return null
            if (!isExcluded(candidate)) return candidate
            upperBound = candidate
            upperInclusive = false
        }
    }

    private fun latestInclusion(
        upperBound: RecurrenceDateTime,
        upperInclusive: Boolean,
    ): RecurrenceDateTime? {
        var latest: RecurrenceDateTime? = null

        fun consider(candidate: RecurrenceDateTime?) {
            candidate ?: return
            val currentLatest = latest
            if (currentLatest == null || temporal.compare(candidate, currentLatest) > 0) latest = candidate
        }

        if (isAtOrBefore(start, upperBound, upperInclusive)) consider(start)
        consider(latestAdditionalDate(upperBound, upperInclusive))
        inclusionRecurrences.forEach { recurrence ->
            consider(recurrence.before(upperBound, upperInclusive))
        }
        return latest
    }

    private fun latestAdditionalDate(
        upperBound: RecurrenceDateTime,
        upperInclusive: Boolean,
    ): RecurrenceDateTime? {
        var lowerIndex = 0
        var upperIndex = resolvableAdditionalDates.lastIndex
        var result: RecurrenceDateTime? = null
        while (lowerIndex <= upperIndex) {
            val middleIndex = lowerIndex + (upperIndex - lowerIndex) / 2
            val candidate = resolvableAdditionalDates[middleIndex]
            if (isAtOrBefore(candidate, upperBound, upperInclusive)) {
                result = candidate
                lowerIndex = middleIndex + 1
            } else {
                upperIndex = middleIndex - 1
            }
        }
        return result
    }

    private fun isExcluded(candidate: RecurrenceDateTime): Boolean {
        if (isExplicitlyExcluded(candidate)) return true
        return exclusionRecurrences.any { recurrence ->
            val exclusion = recurrence.before(candidate, inclusive = true)
            exclusion != null && temporal.compare(exclusion, candidate) == 0
        }
    }

    private fun isExplicitlyExcluded(candidate: RecurrenceDateTime): Boolean {
        var lowerIndex = 0
        var upperIndex = resolvableExcludedDates.lastIndex
        while (lowerIndex <= upperIndex) {
            val middleIndex = lowerIndex + (upperIndex - lowerIndex) / 2
            val comparison = temporal.compare(resolvableExcludedDates[middleIndex], candidate)
            when {
                comparison < 0 -> lowerIndex = middleIndex + 1
                comparison > 0 -> upperIndex = middleIndex - 1
                else -> return true
            }
        }
        return false
    }

    private fun isAtOrBefore(
        candidate: RecurrenceDateTime,
        upperBound: RecurrenceDateTime,
        upperInclusive: Boolean,
    ): Boolean {
        val comparison = temporal.compare(candidate, upperBound)
        return comparison < 0 || upperInclusive && comparison == 0
    }

    private fun sequenceFrom(
        lowerBound: RecurrenceDateTime?,
        upperBound: RecurrenceDateTime?,
        upperInclusive: Boolean = false,
    ): Sequence<RecurrenceDateTime> {
        lowerBound?.let(temporal::validateQueryValue)
        upperBound?.let(temporal::validateQueryValue)
        fun insideWindow(value: RecurrenceDateTime): Boolean {
            if (lowerBound != null && temporal.compare(value, lowerBound) < 0) return false
            if (upperBound != null) {
                val comparison = temporal.compare(value, upperBound)
                if (comparison > 0 || comparison == 0 && !upperInclusive) return false
            }
            return true
        }
        val inclusionSources = buildList {
            if (insideWindow(start)) add(sequenceOf(start))
            val dates = resolvableAdditionalDates
                .asSequence()
                .filter(::insideWindow)
            add(dates)
            inclusionRecurrences.forEach { recurrence ->
                add(recurrence.occurrencesInWindow(lowerBound, upperBound, upperInclusive))
            }
        }
        val exclusionSources = buildList {
            exclusionRecurrences.forEach { recurrence ->
                add(recurrence.occurrencesInWindow(lowerBound, upperBound, upperInclusive))
            }
        }
        val mergedInclusions = merge(inclusionSources)

        return sequence {
            // Iterator state must belong to this sequence iteration. Keeping it outside the
            // builder would make a nominally cold Sequence share one consumed exclusion cursor.
            val mergedExclusions = merge(exclusionSources).iterator()
            var nextExclusion = mergedExclusions.nextOrNull()
            for (candidate in mergedInclusions) {
                while (nextExclusion != null && temporal.compare(nextExclusion, candidate) < 0) {
                    nextExclusion = mergedExclusions.nextOrNull()
                }
                val excludedByRule = nextExclusion != null && temporal.compare(nextExclusion, candidate) == 0
                if (!excludedByRule && !isExplicitlyExcluded(candidate)) yield(candidate)
            }
        }
    }

    private fun merge(sources: List<Sequence<RecurrenceDateTime>>): Sequence<RecurrenceDateTime> = sequence {
        val heap = MergeHeap<MergeNode> { left, right ->
            val comparison = temporal.compare(left.value, right.value)
            if (comparison != 0) comparison else left.sourceIndex.compareTo(right.sourceIndex)
        }
        sources.forEachIndexed { index, source ->
            val iterator = source.iterator()
            if (iterator.hasNext()) heap.add(MergeNode(iterator.next(), iterator, index))
        }

        var previous: RecurrenceDateTime? = null
        while (heap.isNotEmpty) {
            val node = heap.removeFirst()
            if (previous == null || temporal.compare(previous, node.value) != 0) {
                previous = node.value
                yield(node.value)
            }
            // Advance only after the current minimum has been consumed. This preserves true
            // pull-based laziness for take(1), limit=1, and other short-circuiting consumers.
            if (node.iterator.hasNext()) {
                heap.add(node.copy(value = node.iterator.next()))
            }
        }
    }

    private fun bind(rule: RecurrenceRule): RuleRecurrence = RuleRecurrence(
        start = start,
        rule = rule,
        timeZoneResolver = timeZoneResolver,
        ambiguousTimePolicy = ambiguousTimePolicy,
    )

    private fun validateAdditionalPeriod(period: RecurrencePeriod) {
        temporal.validateStoredValue(period.start, propertyName = "RDATE")
        if (period is RecurrencePeriod.Explicit) {
            temporal.validateStoredValue(period.end, propertyName = "RDATE")
            if (temporal.compare(period.start, period.end) >= 0) {
                throw RecurrenceValidationException(
                    propertyName = "RDATE",
                    invalidToken = period.end.toString(),
                    reason = RecurrenceErrorReason.OUT_OF_RANGE,
                    detail = "An explicit RDATE PERIOD start must resolve before its end",
                )
            }
        }
    }

    private data class MergeNode(
        val value: RecurrenceDateTime,
        val iterator: Iterator<RecurrenceDateTime>,
        val sourceIndex: Int,
    )

    private fun Iterator<RecurrenceDateTime>.nextOrNull(): RecurrenceDateTime? =
        if (hasNext()) next() else null
}
