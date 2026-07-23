package io.github.yallain.rrule

import kotlinx.datetime.DateTimeArithmeticException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

internal class TemporalSupport(
    val start: RecurrenceDateTime,
    private val resolver: RecurrenceTimeZoneResolver,
    private val ambiguityPolicy: AmbiguousTimePolicy,
) {
    val startDate: LocalDate = when (start) {
        is RecurrenceDateTime.DateOnly -> start.date
        is RecurrenceDateTime.Floating -> start.dateTime.date
        is RecurrenceDateTime.Utc -> start.instant.toLocalDateTime(TimeZone.UTC).date
        is RecurrenceDateTime.Zoned -> start.dateTime.date
    }

    val startDateTime: LocalDateTime? = when (start) {
        is RecurrenceDateTime.DateOnly -> null
        is RecurrenceDateTime.Floating -> start.dateTime
        is RecurrenceDateTime.Utc -> start.instant.toLocalDateTime(TimeZone.UTC)
        is RecurrenceDateTime.Zoned -> start.dateTime
    }

    // Written exactly once by validateStart() during engine construction, then read immutably.
    private var validatedAbsoluteStart: Instant? = null

    fun validateStart() {
        validateRfcTemporalValue("DTSTART", start)
        when (start) {
            is RecurrenceDateTime.DateOnly -> Unit
            is RecurrenceDateTime.Floating -> Unit
            is RecurrenceDateTime.Utc -> validatedAbsoluteStart = start.instant
            is RecurrenceDateTime.Zoned -> validatedAbsoluteStart = checkNotNull(
                resolveRecurrenceInstant(start, resolver, ambiguityPolicy, propertyName = "DTSTART"),
            )
        }
    }

    fun convert(candidate: LocalCandidate): ResolvedOccurrence? = when (candidate) {
        is DateCandidate -> ResolvedOccurrence(RecurrenceDateTime.DateOnly(candidate.date), candidate.date, null, null)
        is DateTimeCandidate -> when (val temporalStart = start) {
            is RecurrenceDateTime.DateOnly -> error("Date-only recurrences do not produce date-time candidates")
            is RecurrenceDateTime.Floating -> ResolvedOccurrence(
                RecurrenceDateTime.Floating(candidate.dateTime),
                null,
                candidate.dateTime,
                null,
            )
            is RecurrenceDateTime.Utc -> {
                val instant = candidate.dateTime.toInstant(TimeZone.UTC)
                ResolvedOccurrence(RecurrenceDateTime.Utc(instant), null, candidate.dateTime, instant)
            }
            is RecurrenceDateTime.Zoned -> if (candidate.dateTime == temporalStart.dateTime) {
                ResolvedOccurrence(
                    temporalStart,
                    null,
                    candidate.dateTime,
                    checkNotNull(validatedAbsoluteStart),
                )
            } else when (val resolution = resolve(candidate.dateTime, temporalStart.timeZoneId)) {
                LocalTimeResolution.Nonexistent -> null
                is LocalTimeResolution.Valid -> ResolvedOccurrence(
                    RecurrenceDateTime.Zoned(candidate.dateTime, temporalStart.timeZoneId),
                    null,
                    candidate.dateTime,
                    resolution.instant,
                )
                is LocalTimeResolution.Ambiguous -> ResolvedOccurrence(
                    RecurrenceDateTime.Zoned(candidate.dateTime, temporalStart.timeZoneId),
                    null,
                    candidate.dateTime,
                    select(resolution),
                )
            }
        }
    }

    fun isBeforeStart(occurrence: ResolvedOccurrence): Boolean = when (start) {
        is RecurrenceDateTime.DateOnly -> occurrence.date!! < start.date
        is RecurrenceDateTime.Floating -> occurrence.dateTime!! < start.dateTime
        is RecurrenceDateTime.Utc -> occurrence.instant!! < start.instant
        is RecurrenceDateTime.Zoned -> {
            val comparison = occurrence.instant!!.compareTo(checkNotNull(validatedAbsoluteStart))
            comparison < 0 || comparison == 0 && occurrence.value != start
        }
    }

    fun isAfterUntil(occurrence: ResolvedOccurrence, until: RecurrenceDateTime): Boolean = when (until) {
        is RecurrenceDateTime.DateOnly -> occurrence.date!! > until.date
        is RecurrenceDateTime.Floating -> occurrence.dateTime!! > until.dateTime
        is RecurrenceDateTime.Utc -> occurrence.instant!! > until.instant
        is RecurrenceDateTime.Zoned -> error("A Zoned UNTIL is not legal in an RRULE")
    }

    fun compare(left: RecurrenceDateTime, right: RecurrenceDateTime): Int = when {
        start.isAbsolute() && left.isAbsolute() && right.isAbsolute() ->
            absoluteInstant(left).compareTo(absoluteInstant(right))
        left is RecurrenceDateTime.DateOnly && right is RecurrenceDateTime.DateOnly -> left.date.compareTo(right.date)
        left is RecurrenceDateTime.Floating && right is RecurrenceDateTime.Floating ->
            left.dateTime.compareTo(right.dateTime)
        else -> incompatible(right)
    }

    /**
     * Binds a query bound once and returns a comparator for already resolved occurrences.
     *
     * Zoned query values can require resolver work. Binding the bound outside an occurrence loop
     * avoids resolving both the occurrence and the same bound again for every comparison.
     */
    fun occurrenceComparatorFor(bound: RecurrenceDateTime): (ResolvedOccurrence) -> Int =
        when (start) {
            is RecurrenceDateTime.DateOnly -> {
                if (bound !is RecurrenceDateTime.DateOnly) incompatible(bound)
                val comparator: (ResolvedOccurrence) -> Int = { occurrence ->
                    checkNotNull(occurrence.date).compareTo(bound.date)
                }
                comparator
            }
            is RecurrenceDateTime.Floating -> {
                if (bound !is RecurrenceDateTime.Floating) incompatible(bound)
                val comparator: (ResolvedOccurrence) -> Int = { occurrence ->
                    checkNotNull(occurrence.dateTime).compareTo(bound.dateTime)
                }
                comparator
            }
            is RecurrenceDateTime.Utc,
            is RecurrenceDateTime.Zoned,
            -> {
                if (!bound.isAbsolute()) incompatible(bound)
                val boundInstant = absoluteInstant(bound)
                val comparator: (ResolvedOccurrence) -> Int = { occurrence ->
                    checkNotNull(occurrence.instant).compareTo(boundInstant)
                }
                comparator
            }
        }

    fun localValueForLowerBound(value: RecurrenceDateTime): LocalValue? = when (start) {
        is RecurrenceDateTime.DateOnly -> {
            if (value !is RecurrenceDateTime.DateOnly) incompatible(value)
            LocalValue(value.date, null)
        }
        is RecurrenceDateTime.Floating -> {
            if (value !is RecurrenceDateTime.Floating) incompatible(value)
            LocalValue(value.dateTime.date, value.dateTime)
        }
        is RecurrenceDateTime.Utc -> {
            if (!value.isAbsolute()) incompatible(value)
            val local = absoluteInstant(value).toLocalDateTime(TimeZone.UTC)
            LocalValue(local.date, local)
        }
        is RecurrenceDateTime.Zoned -> {
            if (!value.isAbsolute()) incompatible(value)
            if (value is RecurrenceDateTime.Zoned && value.timeZoneId == start.timeZoneId) {
                LocalValue(value.dateTime.date, value.dateTime)
            } else {
                projectedLocalValueForBound(value, BoundDirection.LOWER)
            }
        }
    }

    /**
     * Returns a safe local ceiling for an absolute upper bound.
     *
     * A same-zone value in a DST gap is not represented by its raw local fields: RFC 5545 maps it
     * with the pre-gap offset, whose instant projects to fields after the gap. If reverse projection
     * is unavailable, `null` disables the period ceiling and lets instant comparison stop the scan.
     */
    fun localValueForUpperBound(value: RecurrenceDateTime): LocalValue? {
        val zonedStart = start as? RecurrenceDateTime.Zoned ?: return localValueForLowerBound(value)
        if (value !is RecurrenceDateTime.Zoned || value.timeZoneId != zonedStart.timeZoneId) {
            return projectedLocalValueForBound(value, BoundDirection.UPPER)
        }
        return when (resolve(value.dateTime, value.timeZoneId)) {
            LocalTimeResolution.Nonexistent -> {
                val instant = nonexistentInstant(value.dateTime, value.timeZoneId) ?: return null
                projectInstant(instant, zonedStart.timeZoneId)?.let { LocalValue(it.date, it) }
            }
            else -> LocalValue(value.dateTime.date, value.dateTime)
        }
    }

    fun localValueForUntil(until: RecurrenceDateTime): LocalValue? = when (start) {
        is RecurrenceDateTime.DateOnly -> LocalValue((until as RecurrenceDateTime.DateOnly).date, null)
        is RecurrenceDateTime.Floating -> {
            val dateTime = (until as RecurrenceDateTime.Floating).dateTime
            LocalValue(dateTime.date, dateTime)
        }
        is RecurrenceDateTime.Utc -> {
            val dateTime = (until as RecurrenceDateTime.Utc).instant.toLocalDateTime(TimeZone.UTC)
            LocalValue(dateTime.date, dateTime)
        }
        is RecurrenceDateTime.Zoned -> {
            projectedLocalValueForBound(until, BoundDirection.UPPER)
        }
    }

    fun validateQueryValue(value: RecurrenceDateTime) {
        validateValue(value, propertyName = "QUERY", requireRfcValue = false)
    }

    fun validateStoredValue(value: RecurrenceDateTime, propertyName: String) {
        validateValue(value, propertyName, requireRfcValue = true)
    }

    private fun validateValue(
        value: RecurrenceDateTime,
        propertyName: String,
        requireRfcValue: Boolean,
    ) {
        if (requireRfcValue) validateRfcTemporalValue(propertyName, value)
        val compatible = when (start) {
            is RecurrenceDateTime.DateOnly -> value is RecurrenceDateTime.DateOnly
            is RecurrenceDateTime.Floating -> value is RecurrenceDateTime.Floating
            is RecurrenceDateTime.Utc, is RecurrenceDateTime.Zoned -> value.isAbsolute()
        }
        if (!compatible) incompatible(value)
        when (value) {
            is RecurrenceDateTime.DateOnly -> Unit
            is RecurrenceDateTime.Floating, is RecurrenceDateTime.Utc -> Unit
            is RecurrenceDateTime.Zoned -> checkNotNull(
                resolveRecurrenceInstant(value, resolver, ambiguityPolicy, propertyName),
            )
        }
    }

    private fun absoluteInstant(value: RecurrenceDateTime): Instant =
        resolveRecurrenceInstant(value, resolver, ambiguityPolicy, propertyName = "QUERY")
            ?: incompatible(value)

    /**
     * Projects an absolute query or `UNTIL` bound to a conservative local search bound.
     *
     * An instant on the overlap branch not selected by [ambiguityPolicy] still has useful local
     * fields. A lower cursor is moved back by the overlap width when the selected branch lies after
     * the bound; an upper ceiling is moved forward when it lies before the bound. This retains every
     * possible matching local candidate while avoiding a scan from `DTSTART`.
     *
     * The optimization is used only when the resolver proves that the projected instant is one of
     * the candidates it reports for those local fields. Inconsistent or incomplete custom reverse
     * mappings keep the correctness-first `null` fallback.
     */
    private fun projectedLocalValueForBound(
        value: RecurrenceDateTime,
        direction: BoundDirection,
    ): LocalValue? {
        val zonedStart = start as? RecurrenceDateTime.Zoned ?: return null
        val instant = absoluteInstant(value)
        val local = projectInstant(instant, zonedStart.timeZoneId) ?: return null
        val resolution = resolve(local, zonedStart.timeZoneId)
        val selectedInstant = when (resolution) {
            LocalTimeResolution.Nonexistent -> return null
            is LocalTimeResolution.Valid -> {
                if (resolution.instant != instant) return null
                resolution.instant
            }
            is LocalTimeResolution.Ambiguous -> {
                if (instant != resolution.earlier && instant != resolution.later) return null
                select(resolution)
            }
        }
        if (selectedInstant == instant || resolution !is LocalTimeResolution.Ambiguous) {
            return LocalValue(local.date, local)
        }

        val overlapWidth = resolution.later - resolution.earlier
        val adjusted = when {
            direction == BoundDirection.LOWER && selectedInstant > instant ->
                shiftLocalDateTime(local, -overlapWidth)
            direction == BoundDirection.UPPER && selectedInstant < instant ->
                shiftLocalDateTime(local, overlapWidth)
            else -> local
        } ?: return null
        return LocalValue(adjusted.date, adjusted)
    }

    private fun shiftLocalDateTime(value: LocalDateTime, duration: Duration): LocalDateTime? =
        try {
            (value.toInstant(TimeZone.UTC) + duration).toLocalDateTime(TimeZone.UTC)
        } catch (_: DateTimeArithmeticException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun resolve(localDateTime: LocalDateTime, timeZoneId: String): LocalTimeResolution = try {
        resolver.resolve(localDateTime, timeZoneId)
    } catch (error: IllegalArgumentException) {
        throw RecurrenceValidationException(
            propertyName = "TZID",
            invalidToken = timeZoneId,
            reason = RecurrenceErrorReason.MALFORMED_TOKEN,
            detail = "TZID could not be resolved: ${error.message.orEmpty()}",
        )
    }

    private fun projectInstant(instant: Instant, timeZoneId: String): LocalDateTime? = try {
        resolver.localDateTimeAt(instant, timeZoneId)
    } catch (error: IllegalArgumentException) {
        throw RecurrenceValidationException(
            propertyName = "TZID",
            invalidToken = timeZoneId,
            reason = RecurrenceErrorReason.MALFORMED_TOKEN,
            detail = "TZID could not be resolved: ${error.message.orEmpty()}",
        )
    }

    private fun nonexistentInstant(localDateTime: LocalDateTime, timeZoneId: String): Instant? = try {
        resolver.nonexistentInstant(localDateTime, timeZoneId)
    } catch (error: IllegalArgumentException) {
        throw RecurrenceValidationException(
            propertyName = "TZID",
            invalidToken = timeZoneId,
            reason = RecurrenceErrorReason.MALFORMED_TOKEN,
            detail = "TZID could not be resolved: ${error.message.orEmpty()}",
        )
    }

    private fun select(resolution: LocalTimeResolution.Ambiguous): Instant = when (ambiguityPolicy) {
        AmbiguousTimePolicy.EARLIER -> resolution.earlier
        AmbiguousTimePolicy.LATER -> resolution.later
    }

    private fun incompatible(actual: RecurrenceDateTime): Nothing = throw RecurrenceValidationException(
        propertyName = "QUERY",
        invalidToken = actual.toString(),
        reason = RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE,
        detail = "Query values must use the recurrence temporal domain",
    )
}

private fun RecurrenceDateTime.isAbsolute(): Boolean =
    this is RecurrenceDateTime.Utc || this is RecurrenceDateTime.Zoned

private enum class BoundDirection {
    LOWER,
    UPPER,
}

internal data class ResolvedOccurrence(
    val value: RecurrenceDateTime,
    val date: LocalDate?,
    val dateTime: LocalDateTime?,
    val instant: Instant?,
)

internal data class LocalValue(val date: LocalDate, val dateTime: LocalDateTime?) : Comparable<LocalValue> {
    override fun compareTo(other: LocalValue): Int =
        if (dateTime != null && other.dateTime != null) dateTime.compareTo(other.dateTime)
        else date.compareTo(other.date)
}
