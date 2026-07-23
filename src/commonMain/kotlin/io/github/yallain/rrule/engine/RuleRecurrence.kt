package io.github.yallain.rrule

import kotlinx.datetime.LocalDateTime

/**
 * Lazy evaluation of one [RecurrenceRule] bound to its recurrence start.
 *
 * A standalone rule emits `DTSTART` only when it satisfies the rule. Use [RecurrenceSet] when the
 * complete iCalendar recurrence set (which includes `DTSTART` independently) is required.
 */
public class RuleRecurrence(
    public val start: RecurrenceDateTime,
    public val rule: RecurrenceRule,
    public val timeZoneResolver: RecurrenceTimeZoneResolver = KotlinxRecurrenceTimeZoneResolver,
    public val ambiguousTimePolicy: AmbiguousTimePolicy = AmbiguousTimePolicy.EARLIER,
) : Recurrence {
    private val temporal = TemporalSupport(start, timeZoneResolver, ambiguousTimePolicy)
    private val generator: PeriodGenerator
    private val countedIndexer: CountedRecurrenceIndexer?
    private val countedIndexRequiresInstantSearch: Boolean

    init {
        RecurrenceRuleValidator.validateForStart(rule, start)
        temporal.validateStart()
        generator = PeriodGenerator(temporal.startDate, temporal.startDateTime, rule)
        val canIndexCount = rule.count != null &&
            rule.byWeekNumber.isEmpty() &&
            generator.supportsCandidateIndexing
        val hasLinearTimeline = canIndexCount && hasLinearCandidateTimeline()
        val hasConstantTransitionPrefix = canIndexCount &&
            !hasLinearTimeline &&
            generator.constantCandidateCountAfterFirstPeriod() != null
        val nonexistentRangesThrough = if (
            canIndexCount && !hasLinearTimeline && hasConstantTransitionPrefix
        ) {
            nonexistentRangesThrough()
        } else {
            null
        }
        countedIndexRequiresInstantSearch = nonexistentRangesThrough != null
        countedIndexer = if (
            canIndexCount && (hasLinearTimeline || nonexistentRangesThrough != null)
        ) {
            CountedRecurrenceIndexer(
                generator = generator,
                startLocal = LocalValue(temporal.startDate, temporal.startDateTime),
                frequency = rule.frequency,
                countLimit = checkNotNull(rule.count).toLong(),
                periodFloor = ::periodSearchStart,
                periodCeiling = ::periodCeiling,
                nonexistentRangesThrough = nonexistentRangesThrough,
            )
        } else {
            null
        }
    }

    override fun occurrences(): Sequence<RecurrenceDateTime> = occurrencesInWindow(null, null)

    internal fun occurrencesFrom(lowerBound: RecurrenceDateTime): Sequence<RecurrenceDateTime> =
        occurrencesInWindow(lowerBound, null)

    internal fun occurrencesInWindow(
        lowerBound: RecurrenceDateTime?,
        upperBound: RecurrenceDateTime?,
        upperInclusive: Boolean = false,
    ): Sequence<RecurrenceDateTime> = sequenceFrom(lowerBound, upperBound, upperInclusive)

    override fun between(
        startInclusive: RecurrenceDateTime,
        endExclusive: RecurrenceDateTime,
        limit: Int?,
    ): List<RecurrenceDateTime> {
        require(limit == null || limit >= 0) { "limit must not be negative" }
        if (limit == 0) return emptyList()
        if (temporal.compare(startInclusive, endExclusive) >= 0) return emptyList()
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
        if (generator.isProvablyEmpty) return null
        val upperLocal = temporal.localValueForUpperBound(value)
        val untilLocal = rule.until?.let(temporal::localValueForUntil)
        if (rule.count != null && upperLocal != null) {
            val indexer = countedIndexer
            if (indexer != null) {
                val prefix = indexer.prefixThrough(upperLocal, inclusive)
                if (prefix != null) {
                    val occurrenceCount = minOf(prefix.occurrenceCount, rule.count.toLong())
                    if (occurrenceCount == 0L) return null
                    val indexedResult = if (countedIndexRequiresInstantSearch) {
                        previousTransitionAwareOccurrence(
                            prefix = prefix,
                            maximumOrdinal = occurrenceCount,
                            upperBound = value,
                            upperInclusive = inclusive,
                        )
                    } else {
                        directIndexedOccurrence(prefix, occurrenceCount, value, inclusive)
                    }
                    if (indexedResult.complete) return indexedResult.value
                }
            }
        }
        val requiresForwardScan =
            rule.count != null || upperLocal == null || (rule.until != null && untilLocal == null)
        if (!requiresForwardScan) {
            return previousUncountedOccurrence(value, inclusive, checkNotNull(upperLocal), untilLocal)
        }

        var previous: RecurrenceDateTime? = null
        for (occurrence in sequenceFrom(null, value, upperInclusive = inclusive)) {
            previous = occurrence
        }
        return previous
    }

    private fun sequenceFrom(
        lowerBound: RecurrenceDateTime?,
        upperBound: RecurrenceDateTime?,
        upperInclusive: Boolean = false,
    ): Sequence<RecurrenceDateTime> {
        lowerBound?.let(temporal::validateQueryValue)
        upperBound?.let(temporal::validateQueryValue)
        val lowerLocal = lowerBound?.let(temporal::localValueForLowerBound)
        val upperLocal = upperBound
            ?.let(temporal::localValueForUpperBound)
        if (generator.isProvablyEmpty) return emptySequence()
        val startPrecedesLowerBound =
            lowerBound != null && temporal.compare(start, lowerBound) < 0
        if (startPrecedesLowerBound && lowerLocal != null && lowerLocal.date > LAST_RFC_DATE) {
            return emptySequence()
        }
        val countedForwardStart = if (
            rule.count != null && lowerLocal != null && startPrecedesLowerBound
        ) {
            val periodIndex = periodSearchStart(lowerLocal)
            countedIndexer?.forwardStart(periodIndex, lowerLocal)
        } else {
            null
        }
        val countLimit = rule.count?.toLong()
        if (countedForwardStart != null && countLimit != null &&
            countedForwardStart.emittedBeforeLowerBound >= countLimit
        ) {
            return emptySequence()
        }
        val initialPeriod = when {
            countedForwardStart != null -> countedForwardStart.periodIndex
            rule.count == null && lowerLocal != null && startPrecedesLowerBound -> periodSearchStart(lowerLocal)
            else -> 0L
        }
        val untilLocal = rule.until?.let(temporal::localValueForUntil)
        val maximumPeriod = listOfNotNull(upperLocal, untilLocal)
            .minOfOrNull(::periodCeiling)
        val requiresPeriodUntilCheck = rule.until != null && untilLocal == null
        val startLocal = LocalValue(temporal.startDate, temporal.startDateTime)
        val queryCandidateLowerBound = when {
            rule.count != null && countedForwardStart == null -> null
            lowerBound == null -> null
            !startPrecedesLowerBound -> null
            else -> lowerLocal
        }
        val candidateLowerBound = listOfNotNull(startLocal, queryCandidateLowerBound).max()
        val candidateUpperBound = buildList {
            upperLocal?.let(::add)
            untilLocal?.let(::add)
        }.minOrNull()
        return sequence {
            var periodIndex = initialPeriod
            var emitted = countedForwardStart?.emittedBeforeLowerBound ?: 0L
            while (true) {
                if (maximumPeriod != null && periodIndex > maximumPeriod) return@sequence
                val until = rule.until
                if (until != null && requiresPeriodUntilCheck) {
                    val periodStart = generator.periodStart(periodIndex) ?: return@sequence
                    val resolvedPeriodStart = temporal.convert(periodStart)
                    if (resolvedPeriodStart != null && temporal.isAfterUntil(resolvedPeriodStart, until)) {
                        return@sequence
                    }
                }
                val resolvedDuringSetPosition = if (
                    start is RecurrenceDateTime.Zoned && rule.bySetPosition.isNotEmpty()
                ) {
                    mutableMapOf<LocalCandidate, ResolvedOccurrence>()
                } else {
                    null
                }
                val candidates = generator.generate(
                    periodIndex = periodIndex,
                    lowerBound = candidateLowerBound,
                    upperBound = candidateUpperBound,
                    isValidSetPositionCandidate = resolvedDuringSetPosition?.let { resolvedCandidates ->
                        { candidate ->
                            temporal.convert(candidate)?.let { resolved ->
                                resolvedCandidates[candidate] = resolved
                                true
                            } ?: false
                        }
                    },
                ) ?: return@sequence
                for (candidate in candidates) {
                    val occurrence = resolvedDuringSetPosition?.get(candidate)
                        ?: temporal.convert(candidate)
                        ?: continue
                    if (temporal.isBeforeStart(occurrence)) continue
                    if (until != null && temporal.isAfterUntil(occurrence, until)) return@sequence
                    if (upperBound != null) {
                        val upperComparison = temporal.compare(occurrence.value, upperBound)
                        if (upperComparison > 0 || upperComparison == 0 && !upperInclusive) return@sequence
                    }
                    emitted++
                    val insideQuery = lowerBound == null || temporal.compare(occurrence.value, lowerBound) >= 0
                    if (insideQuery) yield(occurrence.value)
                    if (rule.count != null && emitted >= rule.count.toLong()) return@sequence
                }
                periodIndex = generator.nextRelevantPeriodIndex(periodIndex) ?: return@sequence
            }
        }
    }

    private fun previousUncountedOccurrence(
        upperBound: RecurrenceDateTime,
        upperInclusive: Boolean,
        upperLocal: LocalValue,
        untilLocal: LocalValue?,
    ): RecurrenceDateTime? {
        var periodIndex = listOfNotNull(upperLocal, untilLocal).minOf(::periodCeiling)
        val candidateUpperBound = buildList {
            add(upperLocal)
            untilLocal?.let(::add)
        }.minOrNull()

        while (true) {
            val resolvedDuringSetPosition = if (
                start is RecurrenceDateTime.Zoned && rule.bySetPosition.isNotEmpty()
            ) {
                mutableMapOf<LocalCandidate, ResolvedOccurrence>()
            } else {
                null
            }
            val candidates = generator.generate(
                periodIndex = periodIndex,
                descending = true,
                upperBound = candidateUpperBound,
                isValidSetPositionCandidate = resolvedDuringSetPosition?.let { resolvedCandidates ->
                    { candidate ->
                        temporal.convert(candidate)?.let { resolved ->
                            resolvedCandidates[candidate] = resolved
                            true
                        } ?: false
                    }
                },
            )
            if (candidates != null) {
                for (candidate in candidates) {
                    val occurrence = resolvedDuringSetPosition?.get(candidate)
                        ?: temporal.convert(candidate)
                        ?: continue
                    if (temporal.isBeforeStart(occurrence)) continue
                    val until = rule.until
                    if (until != null && temporal.isAfterUntil(occurrence, until)) continue

                    val comparison = temporal.compare(occurrence.value, upperBound)
                    if (comparison < 0 || upperInclusive && comparison == 0) return occurrence.value
                }
            }
            periodIndex = generator.previousRelevantPeriodIndex(periodIndex) ?: return null
        }
    }

    private fun directIndexedOccurrence(
        prefix: CountedOccurrencePrefix,
        ordinal: Long,
        upperBound: RecurrenceDateTime,
        upperInclusive: Boolean,
    ): IndexedPreviousResult {
        val candidate = prefix.candidateAtOccurrenceOrdinal(ordinal)
            ?: return IndexedPreviousResult.INCOMPLETE
        val occurrence = temporal.convert(candidate)
            ?: return IndexedPreviousResult.INCOMPLETE
        if (temporal.isBeforeStart(occurrence)) return IndexedPreviousResult.INCOMPLETE
        val comparison = temporal.compare(occurrence.value, upperBound)
        if (comparison > 0 || comparison == 0 && !upperInclusive) {
            return IndexedPreviousResult.INCOMPLETE
        }
        return IndexedPreviousResult(complete = true, value = occurrence.value)
    }

    /**
     * Finds the final indexed occurrence at or before an absolute bound.
     *
     * Local fields alone are insufficient around an overlap because query projection deliberately
     * includes both possible branches. The selected ambiguity policy nevertheless keeps resolved
     * occurrences ordered, so a logarithmic search over exact occurrence ordinals is sufficient.
     */
    private fun previousTransitionAwareOccurrence(
        prefix: CountedOccurrencePrefix,
        maximumOrdinal: Long,
        upperBound: RecurrenceDateTime,
        upperInclusive: Boolean,
    ): IndexedPreviousResult {
        val upperInstant = resolveRecurrenceInstant(
            value = upperBound,
            resolver = timeZoneResolver,
            ambiguityPolicy = ambiguousTimePolicy,
            propertyName = "QUERY",
        ) ?: return IndexedPreviousResult.INCOMPLETE
        var lowerOrdinal = 1L
        var upperOrdinal = maximumOrdinal
        var previous: RecurrenceDateTime? = null
        while (lowerOrdinal <= upperOrdinal) {
            val ordinal = lowerOrdinal + (upperOrdinal - lowerOrdinal) / 2L
            val candidate = prefix.candidateAtOccurrenceOrdinal(ordinal)
                ?: return IndexedPreviousResult.INCOMPLETE
            val occurrence = temporal.convert(candidate)
                ?: return IndexedPreviousResult.INCOMPLETE
            if (temporal.isBeforeStart(occurrence)) return IndexedPreviousResult.INCOMPLETE

            val comparison = checkNotNull(occurrence.instant).compareTo(upperInstant)
            if (comparison < 0 || comparison == 0 && upperInclusive) {
                previous = occurrence.value
                if (ordinal == Long.MAX_VALUE) break
                lowerOrdinal = ordinal + 1L
            } else {
                if (ordinal == 1L) break
                upperOrdinal = ordinal - 1L
            }
        }
        return IndexedPreviousResult(complete = true, value = previous)
    }

    private fun periodCeiling(bound: LocalValue): Long {
        val searchStart = periodSearchStart(bound)
        // A BYWEEKNO year can begin in the preceding December. The lower-bound search already
        // backs up one period, so an upper ceiling needs two periods of padding to include the next
        // week-year when its first days precede the bound.
        val padding = if (rule.frequency == Frequency.YEARLY && rule.byWeekNumber.isNotEmpty()) 2L else 1L
        return if (searchStart > Long.MAX_VALUE - padding) Long.MAX_VALUE else searchStart + padding
    }

    private fun periodSearchStart(lower: LocalValue): Long {
        // Query bounds are not stored RFC values and may use years beyond 9999. Clamp only the
        // search cursor so a reverse query above the RFC range starts at the final legal period
        // instead of stepping backward through every out-of-range sub-daily period.
        val searchBound = if (lower.date > LAST_RFC_DATE) {
            LocalValue(
                date = LAST_RFC_DATE,
                dateTime = lower.dateTime?.let { LAST_RFC_DATE_TIME },
            )
        } else {
            lower
        }
        if (searchBound.date < temporal.startDate) return 0L
        val interval = rule.interval.toLong()
        return when (rule.frequency) {
            Frequency.YEARLY -> {
                val raw = (searchBound.date.year.toLong() - temporal.startDate.year.toLong()) / interval
                if (rule.byWeekNumber.isEmpty()) raw.coerceAtLeast(0) else (raw - 1).coerceAtLeast(0)
            }
            Frequency.MONTHLY -> {
                val lowerMonth = searchBound.date.year.toLong() * 12 + searchBound.date.month.ordinal
                val startMonth = temporal.startDate.year.toLong() * 12 + temporal.startDate.month.ordinal
                ((lowerMonth - startMonth) / interval).coerceAtLeast(0)
            }
            Frequency.WEEKLY -> {
                val lowerWeek = weekStart(searchBound.date, rule.weekStart) ?: return 0
                val startWeek = weekStart(temporal.startDate, rule.weekStart) ?: return 0
                ((lowerWeek.toEpochDays() - startWeek.toEpochDays()) / (7L * interval)).coerceAtLeast(0)
            }
            Frequency.DAILY ->
                ((searchBound.date.toEpochDays() - temporal.startDate.toEpochDays()) / interval).coerceAtLeast(0)
            Frequency.HOURLY -> subDailyPeriod(searchBound.dateTime, unitsPerDay = 24)
            Frequency.MINUTELY -> subDailyPeriod(searchBound.dateTime, unitsPerDay = 1_440)
            Frequency.SECONDLY -> subDailyPeriod(searchBound.dateTime, unitsPerDay = 86_400)
        }
    }

    private fun subDailyPeriod(lower: LocalDateTime?, unitsPerDay: Int): Long {
        val startDateTime = temporal.startDateTime ?: return 0
        lower ?: return 0
        val dayDifference = lower.date.toEpochDays() - startDateTime.date.toEpochDays()
        val lowerUnit = lower.unitOfDay(unitsPerDay)
        val startUnit = startDateTime.unitOfDay(unitsPerDay)
        val total = dayDifference * unitsPerDay + lowerUnit - startUnit
        return (total / rule.interval).coerceAtLeast(0)
    }

    private fun hasLinearCandidateTimeline(): Boolean = when (val temporalStart = start) {
        is RecurrenceDateTime.DateOnly,
        is RecurrenceDateTime.Floating,
        is RecurrenceDateTime.Utc,
        -> true
        is RecurrenceDateTime.Zoned ->
            (timeZoneResolver as? LinearLocalTimeZoneResolver)
                ?.hasLinearLocalTimeline(temporalStart.timeZoneId) == true
    }

    private fun nonexistentRangesThrough(): ((LocalDateTime) -> List<NonexistentLocalTimeRange>)? {
        val zonedStart = start as? RecurrenceDateTime.Zoned ?: return null
        val provider: NonexistentLocalTimeRangeProvider = when {
            timeZoneResolver === KotlinxRecurrenceTimeZoneResolver ->
                NonexistentLocalTimeRangeProvider(
                    KotlinxRecurrenceTimeZoneResolver::nonexistentLocalTimeRanges,
                )
            timeZoneResolver is NonexistentLocalTimeRangeProvider -> timeZoneResolver
            else -> return null
        }
        return { upperBound ->
            provider.nonexistentLocalTimeRanges(
                timeZoneId = zonedStart.timeZoneId,
                startInclusive = zonedStart.dateTime,
                // Stored recurrence candidates cannot extend past the RFC year range. Avoid
                // asking platform timezone databases to enumerate irrelevant, possibly
                // unsupported transition years for an unbounded query value.
                endInclusive = minOf(upperBound, LAST_RFC_DATE_TIME),
            )
        }
    }
}

private data class IndexedPreviousResult(
    val complete: Boolean,
    val value: RecurrenceDateTime?,
) {
    companion object {
        val INCOMPLETE: IndexedPreviousResult = IndexedPreviousResult(
            complete = false,
            value = null,
        )
    }
}

private fun LocalDateTime.unitOfDay(unitsPerDay: Int): Long = when (unitsPerDay) {
    24 -> hour.toLong()
    1_440 -> hour * 60L + minute
    86_400 -> hour * 3_600L + minute * 60L + second
    else -> error("Unsupported sub-daily unit count: $unitsPerDay")
}

/** Binds this rule to [start] for lazy standalone evaluation. */
public fun RecurrenceRule.recurrence(
    start: RecurrenceDateTime,
    timeZoneResolver: RecurrenceTimeZoneResolver = KotlinxRecurrenceTimeZoneResolver,
    ambiguousTimePolicy: AmbiguousTimePolicy = AmbiguousTimePolicy.EARLIER,
): RuleRecurrence = RuleRecurrence(start, this, timeZoneResolver, ambiguousTimePolicy)
