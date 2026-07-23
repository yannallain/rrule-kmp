package io.github.yallain.rrule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

internal class PeriodGenerator(
    private val startDate: LocalDate,
    private val startDateTime: LocalDateTime?,
    private val rule: RecurrenceRule,
) {
    private val startMonth = startDate.month.ordinal + 1
    private val explicitDateSelector =
        rule.byWeekNumber.isNotEmpty() || rule.byYearDay.isNotEmpty() ||
            rule.byMonthDay.isNotEmpty() || rule.byDay.isNotEmpty()

    private val effectiveByMonth: Set<Int> = when {
        rule.byMonth.isNotEmpty() -> rule.byMonth
        rule.frequency == Frequency.YEARLY && !explicitDateSelector -> setOf(startMonth)
        else -> emptySet()
    }
    private val effectiveByMonthDay: Set<Int> = when {
        rule.byMonthDay.isNotEmpty() -> rule.byMonthDay
        rule.frequency == Frequency.YEARLY && !explicitDateSelector -> setOf(startDate.day)
        rule.frequency == Frequency.MONTHLY && rule.byDay.isEmpty() -> setOf(startDate.day)
        else -> emptySet()
    }
    private val effectiveByDay: List<ByDay> = when {
        rule.byDay.isNotEmpty() -> rule.byDay
        rule.frequency == Frequency.WEEKLY -> listOf(ByDay(weekday(startDate)))
        else -> emptyList()
    }
    private val effectiveBySecond: Set<Int> = rule.bySecond.normalizeRfcSeconds()
    private val subDailyNavigator: SubDailyPeriodNavigator? = when (rule.frequency) {
        Frequency.HOURLY, Frequency.MINUTELY, Frequency.SECONDLY -> SubDailyPeriodNavigator(
            start = checkNotNull(startDateTime),
            rule = rule,
            hasDateFilter = hasDateFilter(),
            matchesDate = { date -> matchesDate(date, date.year) },
        )
        Frequency.YEARLY, Frequency.MONTHLY, Frequency.WEEKLY, Frequency.DAILY -> null
    }

    val isProvablyEmpty: Boolean =
        hasNoReachableSetPosition() ||
            hasImpossibleMonthDaySelection() ||
            subDailyNavigator?.hasNoReachableClockTime() == true ||
            hasNoReachableCalendarDate()

    /** Whether local candidates can be counted and addressed without evaluating `BYSETPOS`. */
    val supportsCandidateIndexing: Boolean = rule.bySetPosition.isEmpty()

    fun generate(
        periodIndex: Long,
        descending: Boolean = false,
        lowerBound: LocalValue? = null,
        upperBound: LocalValue? = null,
        isValidSetPositionCandidate: ((LocalCandidate) -> Boolean)? = null,
    ): Sequence<LocalCandidate>? {
        val anchor = anchor(periodIndex) ?: return null
        val dates = datesFor(anchor).filter { it.year in 0..9999 && matchesDate(it, anchor.periodYear) }
        fun candidatesInOrder(
            reverse: Boolean,
            applyBounds: Boolean,
        ): Sequence<LocalCandidate> = if (startDateTime == null) {
            val candidateDates = if (applyBounds) dates.filter { date ->
                (lowerBound == null || date >= lowerBound.date) &&
                    (upperBound == null || date <= upperBound.date)
            } else {
                dates
            }
            val orderedDates = if (reverse) candidateDates.asReversed() else candidateDates
            orderedDates.asSequence().map(::DateCandidate)
        } else {
            generateDateTimes(
                dates = dates,
                anchor = anchor,
                descending = reverse,
                lowerBound = lowerBound.takeIf { applyBounds }?.dateTime,
                upperBound = upperBound.takeIf { applyBounds }?.dateTime,
            )
        }
        if (rule.bySetPosition.isEmpty()) return candidatesInOrder(descending, applyBounds = true)

        fun validCandidates(reverse: Boolean): Sequence<LocalCandidate> {
            val candidates = candidatesInOrder(reverse = reverse, applyBounds = false)
            val validator = isValidSetPositionCandidate ?: return candidates
            return candidates.filter(validator)
        }
        val selected = applySetPosition(
            ascending = validCandidates(reverse = false),
            descending = validCandidates(reverse = true),
        )
        val bounded = selected.filter { candidate ->
            (lowerBound == null || candidate >= lowerBound) &&
                (upperBound == null || candidate <= upperBound)
        }
        return (if (descending) bounded.asReversed() else bounded).asSequence()
    }

    /**
     * Counts local candidates in one period without walking its date-time Cartesian product.
     *
     * The count deliberately precedes timezone resolution. Callers may use it as an occurrence
     * count only when their temporal domain guarantees that every local candidate is valid.
     */
    fun candidateCount(
        periodIndex: Long,
        lowerBoundInclusive: LocalValue? = null,
        upperBound: LocalValue? = null,
        upperInclusive: Boolean = false,
    ): Long? {
        if (!supportsCandidateIndexing) return null
        val space = candidateSpace(periodIndex) ?: return null
        val lowerRank = lowerBoundInclusive?.let { space.rank(it, inclusive = false) } ?: 0L
        val upperRank = upperBound?.let { space.rank(it, inclusive = upperInclusive) } ?: space.size
        return (upperRank - lowerRank).coerceAtLeast(0L)
    }

    /** Returns the one-based [ordinal] candidate after [lowerBoundInclusive] in a period. */
    fun candidateAt(
        periodIndex: Long,
        ordinal: Long,
        lowerBoundInclusive: LocalValue? = null,
    ): LocalCandidate? {
        if (!supportsCandidateIndexing || ordinal <= 0L) return null
        val space = candidateSpace(periodIndex) ?: return null
        val skipped = lowerBoundInclusive?.let { space.rank(it, inclusive = false) } ?: 0L
        return space.candidateAt(skipped + ordinal)
    }

    /**
     * Returns the exact unbounded cardinality shared by every period after the first, when it is
     * independent of calendar fields and limiting clock filters.
     */
    fun constantCandidateCountAfterFirstPeriod(): Long? {
        if (!supportsCandidateIndexing) return null
        when (rule.frequency) {
            Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY -> return null
            Frequency.SECONDLY, Frequency.MINUTELY, Frequency.HOURLY, Frequency.DAILY -> Unit
        }
        val hasVariableDateFilter =
            rule.normalizedNonUniversalByMonth().isNotEmpty() ||
                rule.byWeekNumber.isNotEmpty() ||
                rule.normalizedNonUniversalByYearDay().isNotEmpty() ||
                rule.normalizedNonUniversalByMonthDay().isNotEmpty() ||
                rule.normalizedNonUniversalByDay().isNotEmpty()
        if (hasVariableDateFilter) return null
        val hasLimitingClockFilter = when (rule.frequency) {
            Frequency.SECONDLY ->
                rule.normalizedNonUniversalByHour().isNotEmpty() ||
                    rule.normalizedNonUniversalByMinute().isNotEmpty() ||
                    rule.normalizedNonUniversalBySecond().isNotEmpty()
            Frequency.MINUTELY ->
                rule.normalizedNonUniversalByHour().isNotEmpty() ||
                    rule.normalizedNonUniversalByMinute().isNotEmpty()
            Frequency.HOURLY -> rule.normalizedNonUniversalByHour().isNotEmpty()
            Frequency.DAILY -> false
            Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY -> true
        }
        if (hasLimitingClockFilter) return null
        return candidateCount(periodIndex = 0L)
    }

    /**
     * Returns the number of periods in one exact Gregorian candidate-count cycle.
     *
     * The first period is handled separately because `DTSTART` can truncate it. Periods after the
     * first repeat once their anchor advances by 400 Gregorian years. Near the RFC year ceiling a
     * complete cycle may not fit; callers then retain their bounded scan fallback.
     */
    fun calendarCandidateCountCycleShapeAfterFirstPeriod(): CalendarPeriodCountCycleShape? {
        if (!supportsCandidateIndexing) return null
        val cycleUnits: Long
        val periodsPerBlock: Int
        when (rule.frequency) {
            Frequency.DAILY -> {
                cycleUnits = GREGORIAN_CYCLE_DAYS
                periodsPerBlock = 366
            }
            Frequency.WEEKLY -> {
                cycleUnits = GREGORIAN_CYCLE_WEEKS
                periodsPerBlock = 53
            }
            Frequency.MONTHLY -> {
                cycleUnits = GREGORIAN_CYCLE_MONTHS
                periodsPerBlock = 12
            }
            Frequency.YEARLY -> {
                cycleUnits = GREGORIAN_CYCLE_YEARS
                periodsPerBlock = 1
            }
            Frequency.HOURLY, Frequency.MINUTELY, Frequency.SECONDLY -> return null
        }
        val periodCount = cycleUnits / greatestCommonDivisor(
            rule.interval.toLong() % cycleUnits,
            cycleUnits,
        )
        if (periodCount >= Int.MAX_VALUE || anchor(periodCount) == null) return null
        return CalendarPeriodCountCycleShape(
            periodCount = periodCount.toInt(),
            periodsPerBlock = periodsPerBlock,
        )
    }

    private fun candidateSpace(periodIndex: Long): CountedCandidateSpace? {
        val anchor = anchor(periodIndex) ?: return null
        val dates = datesFor(anchor).filter { it.year in 0..9999 && matchesDate(it, anchor.periodYear) }
        if (startDateTime == null) return CountedCandidateSpace(dates)
        val clock = clockComponents(anchor) ?: return CountedCandidateSpace(dates, CountedCandidateClock.EMPTY)
        return CountedCandidateSpace(dates, clock)
    }

    private fun hasNoReachableCalendarDate(): Boolean {
        if (!hasDateFilter()) return false
        return when (rule.frequency) {
            Frequency.YEARLY -> hasNoCandidateAcrossPeriodCycle(400L)
            Frequency.MONTHLY -> hasNoCandidateAcrossPeriodCycle(4_800L)
            Frequency.WEEKLY -> hasNoCandidateAcrossPeriodCycle(20_871L)
            Frequency.DAILY -> hasNoCandidateAcrossPeriodCycle(GREGORIAN_CYCLE_DAYS)
            Frequency.HOURLY, Frequency.MINUTELY, Frequency.SECONDLY ->
                checkNotNull(subDailyNavigator).hasNoReachableCandidateInGregorianCycle()
        }
    }

    private fun hasNoReachableSetPosition(): Boolean {
        if (rule.bySetPosition.isEmpty()) return false
        val maximumDateCount = when (rule.frequency) {
            Frequency.YEARLY -> if (rule.byWeekNumber.isEmpty()) 366L else 371L
            Frequency.MONTHLY -> 31L
            Frequency.WEEKLY -> 7L
            Frequency.DAILY, Frequency.HOURLY, Frequency.MINUTELY, Frequency.SECONDLY -> 1L
        }
        val maximumCandidateCount = if (startDateTime == null) {
            maximumDateCount
        } else {
            val expandedHours = when (rule.frequency) {
                Frequency.YEARLY, Frequency.MONTHLY, Frequency.WEEKLY, Frequency.DAILY ->
                    rule.byHour.sizeOrDefault()
                else -> 1
            }
            val expandedMinutes = when (rule.frequency) {
                Frequency.YEARLY, Frequency.MONTHLY, Frequency.WEEKLY, Frequency.DAILY,
                Frequency.HOURLY,
                -> rule.byMinute.sizeOrDefault()
                else -> 1
            }
            val expandedSeconds = when (rule.frequency) {
                Frequency.SECONDLY -> 1
                else -> effectiveBySecond.sizeOrDefault()
            }
            maximumDateCount * expandedHours * expandedMinutes * expandedSeconds
        }
        return rule.bySetPosition.all { position ->
            val distance = if (position < 0) -position else position
            distance.toLong() > maximumCandidateCount
        }
    }

    private fun Set<Int>.sizeOrDefault(): Int = if (isEmpty()) 1 else size

    private fun hasDateFilter(): Boolean =
        effectiveByMonth.isNotEmpty() || rule.byWeekNumber.isNotEmpty() ||
            rule.byYearDay.isNotEmpty() || effectiveByMonthDay.isNotEmpty() ||
            effectiveByDay.isNotEmpty()

    private fun hasNoCandidateAcrossPeriodCycle(cycleUnits: Long): Boolean {
        val step = rule.interval.toLong() % cycleUnits
        val periodCount = cycleUnits / greatestCommonDivisor(step, cycleUnits)
        var periodIndex = 0L
        while (periodIndex < periodCount) {
            val anchor = anchor(periodIndex) ?: return true
            if (datesFor(anchor).any { matchesDate(it, anchor.periodYear) }) return false
            periodIndex++
        }
        return true
    }

    fun periodStart(periodIndex: Long): LocalCandidate? {
        val anchor = anchor(periodIndex) ?: return null
        val unboundedDate = if (rule.frequency == Frequency.YEARLY && rule.byWeekNumber.isNotEmpty()) {
            firstWeekStart(anchor.periodYear, rule.weekStart) ?: return null
        } else {
            anchor.date
        }
        // A week intersecting year 0000 may begin in year -1. This candidate is used only as an
        // optimization bound, so keep it inside the RFC domain accepted by custom resolvers.
        val date = maxOf(unboundedDate, FIRST_RFC_DATE)
        if (startDateTime == null) return DateCandidate(date)

        val hour: Int
        val minute: Int
        val second: Int
        when (rule.frequency) {
            Frequency.YEARLY, Frequency.MONTHLY, Frequency.WEEKLY, Frequency.DAILY -> {
                hour = 0
                minute = 0
                second = 0
            }
            Frequency.HOURLY -> {
                hour = anchor.hour
                minute = 0
                second = 0
            }
            Frequency.MINUTELY -> {
                hour = anchor.hour
                minute = anchor.minute
                second = 0
            }
            Frequency.SECONDLY -> {
                hour = anchor.hour
                minute = anchor.minute
                second = anchor.second
            }
        }
        return DateTimeCandidate(localDateTime(date, hour, minute, second))
    }

    /** Returns the next period that can survive the sub-daily limiting filters. */
    fun nextRelevantPeriodIndex(periodIndex: Long): Long? =
        subDailyNavigator?.nextRelevantPeriodIndex(periodIndex)
            ?: if (periodIndex == Long.MAX_VALUE) null else periodIndex + 1

    /** Returns the preceding period that can survive the sub-daily limiting filters. */
    fun previousRelevantPeriodIndex(periodIndex: Long): Long? =
        subDailyNavigator?.previousRelevantPeriodIndex(periodIndex)
            ?: if (periodIndex == 0L) null else periodIndex - 1

    private fun hasImpossibleMonthDaySelection(): Boolean {
        if (effectiveByMonthDay.isEmpty()) return false
        val reachableMonths = if (rule.frequency == Frequency.MONTHLY) {
            val step = greatestCommonDivisor(rule.interval.toLong(), 12L).toInt()
            (0 until 12 / step).mapTo(linkedSetOf()) { offset ->
                floorMod(
                    startMonth.toLong() - 1L + offset.toLong() * rule.interval.toLong(),
                    12L,
                ).toInt() + 1
            }
        } else {
            (1..12).toSet()
        }
        val candidateMonths = if (rule.byMonth.isEmpty()) reachableMonths else reachableMonths intersect rule.byMonth
        if (candidateMonths.isEmpty()) return true
        return candidateMonths.none { month ->
            val maximumLength = if (month == 2) 29 else daysInMonth(2000, month)
            effectiveByMonthDay.any { selector ->
                val magnitude = if (selector < 0) -selector else selector
                magnitude <= maximumLength
            }
        }
    }

    private fun greatestCommonDivisor(first: Long, second: Long): Long {
        var left = first
        var right = second
        while (right != 0L) {
            val remainder = left % right
            left = right
            right = remainder
        }
        return left
    }

    private fun anchor(periodIndex: Long): PeriodAnchor? = when (rule.frequency) {
        Frequency.YEARLY -> {
            val delta = positiveProductOrNull(periodIndex, rule.interval.toLong()) ?: return null
            val year = startDate.year.toLong() + delta
            if (year !in 0L..9999L) return null
            val date = runCatching { LocalDate(year.toInt(), 1, 1) }.getOrNull() ?: return null
            PeriodAnchor(date, 0, 0, 0, year.toInt())
        }
        Frequency.MONTHLY -> {
            val delta = positiveProductOrNull(periodIndex, rule.interval.toLong()) ?: return null
            val absoluteMonth = startDate.year.toLong() * 12L + startMonth - 1L + delta
            val year = floorDiv(absoluteMonth, 12L)
            val month = floorMod(absoluteMonth, 12L).toInt() + 1
            if (year !in 0L..9999L) return null
            val date = runCatching { LocalDate(year.toInt(), month, 1) }.getOrNull() ?: return null
            PeriodAnchor(date, 0, 0, 0, year.toInt())
        }
        Frequency.WEEKLY -> {
            val base = weekStart(startDate, rule.weekStart) ?: return null
            val weeks = positiveProductOrNull(periodIndex, rule.interval.toLong()) ?: return null
            val date = base.plusDaysOrNull(positiveProductOrNull(weeks, 7L) ?: return null) ?: return null
            val weekEnd = date.plusDaysOrNull(6) ?: return null
            if (weekEnd < FIRST_RFC_DATE || date > LAST_RFC_DATE) return null
            PeriodAnchor(date, 0, 0, 0, date.year)
        }
        Frequency.DAILY -> {
            val days = positiveProductOrNull(periodIndex, rule.interval.toLong()) ?: return null
            val date = startDate.plusDaysOrNull(days) ?: return null
            if (date.year !in 0..9999) return null
            PeriodAnchor(date, 0, 0, 0, date.year)
        }
        Frequency.HOURLY, Frequency.MINUTELY, Frequency.SECONDLY ->
            subDailyNavigator?.anchor(periodIndex)?.toPeriodAnchor(rule.frequency)
    }

    private fun datesFor(anchor: PeriodAnchor): List<LocalDate> = when (rule.frequency) {
        Frequency.YEARLY -> yearlyDates(anchor.periodYear)
        Frequency.MONTHLY -> {
            val next = if (anchor.date.month.ordinal == 11) {
                runCatching { LocalDate(anchor.date.year + 1, 1, 1) }.getOrNull()
            } else {
                runCatching { LocalDate(anchor.date.year, anchor.date.month.ordinal + 2, 1) }.getOrNull()
            }
            datesBetween(anchor.date, next)
        }
        Frequency.WEEKLY -> (0L..6L).mapNotNull(anchor.date::plusDaysOrNull)
        else -> listOf(anchor.date)
    }

    private fun yearlyDates(year: Int): List<LocalDate> {
        val start: LocalDate
        val end: LocalDate?
        if (rule.byWeekNumber.isNotEmpty()) {
            start = firstWeekStart(year, rule.weekStart) ?: return emptyList()
            end = firstWeekStart(year + 1, rule.weekStart)
        } else {
            start = runCatching { LocalDate(year, 1, 1) }.getOrNull() ?: return emptyList()
            end = runCatching { LocalDate(year + 1, 1, 1) }.getOrNull()
        }
        return datesBetween(start, end)
    }

    private fun datesBetween(start: LocalDate, endExclusive: LocalDate?): List<LocalDate> {
        if (endExclusive == null) return emptyList()
        val result = ArrayList<LocalDate>(366)
        var date: LocalDate? = start
        while (date != null && date < endExclusive) {
            result += date
            date = date.plusDaysOrNull(1)
        }
        return result
    }

    private fun matchesDate(date: LocalDate, periodYear: Int): Boolean {
        val month = date.month.ordinal + 1
        if (effectiveByMonth.isNotEmpty() && month !in effectiveByMonth) return false

        if (rule.byWeekNumber.isNotEmpty()) {
            val week = weekInfo(date, rule.weekStart) ?: return false
            if (week.weekYear != periodYear || !matchesSigned(week.weekNumber, week.weeksInYear, rule.byWeekNumber)) {
                return false
            }
        }

        if (rule.byYearDay.isNotEmpty() &&
            !matchesSigned(date.dayOfYear, daysInYear(date.year), rule.byYearDay)
        ) {
            return false
        }

        if (effectiveByMonthDay.isNotEmpty() &&
            !matchesSigned(date.day, daysInMonth(date.year, month), effectiveByMonthDay)
        ) {
            return false
        }

        if (effectiveByDay.isNotEmpty() && effectiveByDay.none { matchesByDay(date, it) }) return false
        return true
    }

    private fun matchesByDay(date: LocalDate, selector: ByDay): Boolean {
        if (weekday(date) != selector.weekday) return false
        val ordinal = selector.ordinal ?: return true
        return when {
            rule.frequency == Frequency.MONTHLY -> ordinal in weekdayOrdinalsInMonth(date)
            rule.frequency == Frequency.YEARLY && rule.byMonth.isNotEmpty() -> ordinal in weekdayOrdinalsInMonth(date)
            rule.frequency == Frequency.YEARLY -> ordinal in weekdayOrdinalsInYear(date)
            else -> false
        }
    }

    private fun weekdayOrdinalsInMonth(date: LocalDate): Set<Int> {
        val length = daysInMonth(date.year, date.month.ordinal + 1)
        val positive = (date.day - 1) / 7 + 1
        val negative = -((length - date.day) / 7 + 1)
        return setOf(positive, negative)
    }

    private fun weekdayOrdinalsInYear(date: LocalDate): Set<Int> {
        val positive = (date.dayOfYear - 1) / 7 + 1
        val negative = -((daysInYear(date.year) - date.dayOfYear) / 7 + 1)
        return setOf(positive, negative)
    }

    private fun matchesSigned(value: Int, length: Int, selectors: Set<Int>): Boolean =
        value in selectors || value - length - 1 in selectors

    private fun generateDateTimes(
        dates: List<LocalDate>,
        anchor: PeriodAnchor,
        descending: Boolean,
        lowerBound: LocalDateTime?,
        upperBound: LocalDateTime?,
    ): Sequence<LocalCandidate> {
        val clock = clockComponents(anchor) ?: return emptySequence()
        val hours = clock.hours
        val minutes = clock.minutes
        val seconds = clock.seconds

        val boundedDates = dates.filter { date ->
            (lowerBound == null || date >= lowerBound.date) &&
                (upperBound == null || date <= upperBound.date)
        }
        val orderedDates = if (descending) boundedDates.asReversed() else boundedDates
        val orderedHours = if (descending) hours.asReversed() else hours
        val orderedMinutes = if (descending) minutes.asReversed() else minutes
        val orderedSeconds = if (descending) seconds.asReversed() else seconds
        return sequence {
            for (date in orderedDates) {
                for (hour in orderedHours) {
                    for (minute in orderedMinutes) {
                        for (second in orderedSeconds) {
                            val dateTime = localDateTime(date, hour, minute, second)
                            if ((lowerBound == null || dateTime >= lowerBound) &&
                                (upperBound == null || dateTime <= upperBound)
                            ) {
                                yield(DateTimeCandidate(dateTime))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun clockComponents(anchor: PeriodAnchor): CountedCandidateClock? {
        val start = startDateTime ?: return null
        val hours: List<Int>
        val minutes: List<Int>
        val seconds: List<Int>
        when (rule.frequency) {
            Frequency.YEARLY, Frequency.MONTHLY, Frequency.WEEKLY, Frequency.DAILY -> {
                hours = (rule.byHour.ifEmpty { setOf(start.hour) }).sorted()
                minutes = (rule.byMinute.ifEmpty { setOf(start.minute) }).sorted()
                seconds = (effectiveBySecond.ifEmpty { setOf(start.second) }).sorted()
            }
            Frequency.HOURLY -> {
                if (rule.byHour.isNotEmpty() && anchor.hour !in rule.byHour) return null
                hours = listOf(anchor.hour)
                minutes = (rule.byMinute.ifEmpty { setOf(start.minute) }).sorted()
                seconds = (effectiveBySecond.ifEmpty { setOf(start.second) }).sorted()
            }
            Frequency.MINUTELY -> {
                if (rule.byHour.isNotEmpty() && anchor.hour !in rule.byHour) return null
                if (rule.byMinute.isNotEmpty() && anchor.minute !in rule.byMinute) return null
                hours = listOf(anchor.hour)
                minutes = listOf(anchor.minute)
                seconds = (effectiveBySecond.ifEmpty { setOf(start.second) }).sorted()
            }
            Frequency.SECONDLY -> {
                if (rule.byHour.isNotEmpty() && anchor.hour !in rule.byHour) return null
                if (rule.byMinute.isNotEmpty() && anchor.minute !in rule.byMinute) return null
                if (effectiveBySecond.isNotEmpty() && anchor.second !in effectiveBySecond) return null
                hours = listOf(anchor.hour)
                minutes = listOf(anchor.minute)
                seconds = listOf(anchor.second)
            }
        }
        return CountedCandidateClock(hours, minutes, seconds)
    }

    private fun applySetPosition(
        ascending: Sequence<LocalCandidate>,
        descending: Sequence<LocalCandidate>,
    ): List<LocalCandidate> {
        val positivePositions = rule.bySetPosition.filterTo(hashSetOf()) { it > 0 }
        val distancesFromEnd = rule.bySetPosition.filterTo(hashSetOf()) { it < 0 }.mapTo(hashSetOf()) { -it }
        val selected = linkedSetOf<LocalCandidate>()

        positivePositions.maxOrNull()?.let { lastRequestedPosition ->
            var position = 0
            for (candidate in ascending) {
                position++
                if (position in positivePositions) selected += candidate
                if (position == lastRequestedPosition) break
            }
        }

        distancesFromEnd.maxOrNull()?.let { lastRequestedDistance ->
            var distanceFromEnd = 0
            for (candidate in descending) {
                distanceFromEnd++
                if (distanceFromEnd in distancesFromEnd) selected += candidate
                if (distanceFromEnd == lastRequestedDistance) break
            }
        }
        return selected.sorted()
    }

    private companion object {
        const val GREGORIAN_CYCLE_YEARS: Long = 400L
        const val GREGORIAN_CYCLE_MONTHS: Long = 4_800L
        const val GREGORIAN_CYCLE_WEEKS: Long = 20_871L
        const val GREGORIAN_CYCLE_DAYS: Long = 146_097L
    }
}

internal data class PeriodAnchor(
    val date: LocalDate,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val periodYear: Int,
)

internal sealed interface LocalCandidate : Comparable<LocalCandidate> {
    override fun compareTo(other: LocalCandidate): Int = when {
        this is DateCandidate && other is DateCandidate -> date.compareTo(other.date)
        this is DateTimeCandidate && other is DateTimeCandidate -> dateTime.compareTo(other.dateTime)
        this is DateCandidate -> -1
        else -> 1
    }
}

private operator fun LocalCandidate.compareTo(bound: LocalValue): Int = when (this) {
    is DateCandidate -> date.compareTo(bound.date)
    is DateTimeCandidate -> dateTime.compareTo(checkNotNull(bound.dateTime))
}

internal data class DateCandidate(val date: LocalDate) : LocalCandidate
internal data class DateTimeCandidate(val dateTime: LocalDateTime) : LocalCandidate
