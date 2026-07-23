package io.github.yallain.rrule

import io.github.yallain.rrule.internal.collections.immutableListCopyOf
import io.github.yallain.rrule.internal.collections.immutableSetCopyOf

/**
 * Immutable recurrence-related content parsed from an iCalendar component or API payload.
 *
 * This model deliberately contains only recurrence properties. It is not a `VEVENT` model and
 * does not represent summaries, attendees, alarms, or embedded `VTIMEZONE` definitions.
 * Collection arguments are defensively copied into read-only implementations, and every value
 * must share [start]'s temporal domain. UTC and zoned DATE-TIME values share one absolute domain,
 * so an `RDATE` or `EXDATE` may use UTC or its own `TZID`; floating and date-only values remain
 * separate domains.
 */
public class RecurrenceDefinition(
    public val start: RecurrenceDateTime,
    rules: List<RecurrenceRule> = emptyList(),
    exclusionRules: List<RecurrenceRule> = emptyList(),
    additionalDates: Set<RecurrenceDateTime> = emptySet(),
    excludedDates: Set<RecurrenceDateTime> = emptySet(),
    additionalPeriods: Set<RecurrencePeriod> = emptySet(),
) {
    /** Inclusion rules in their source order. */
    public val rules: List<RecurrenceRule> = immutableListCopyOf(rules)

    /**
     * Programmatic exclusion rules in their source order.
     *
     * RFC 5545 removed the former `EXRULE` content property, so [RecurrenceContentParser] never
     * populates this collection. It remains useful when an application builds a recurrence set
     * from separate rule fields rather than from RFC content lines.
     */
    public val exclusionRules: List<RecurrenceRule> = immutableListCopyOf(exclusionRules)

    /** Explicit `RDATE` inclusions. */
    public val additionalDates: Set<RecurrenceDateTime> = immutableSetCopyOf(additionalDates)

    /**
     * Explicit `RDATE;VALUE=PERIOD` inclusions with their instance-specific end or duration.
     * Their starts participate in recurrence-set evaluation alongside [additionalDates].
     */
    public val additionalPeriods: Set<RecurrencePeriod> = immutableSetCopyOf(additionalPeriods)

    /** Explicit `EXDATE` exclusions. */
    public val excludedDates: Set<RecurrenceDateTime> = immutableSetCopyOf(excludedDates)

    init {
        validateRfcTemporalValue("DTSTART", start)
        this.rules.forEach { RecurrenceRuleValidator.validateForStart(it, start) }
        this.exclusionRules.forEach { RecurrenceRuleValidator.validateForStart(it, start) }
        this.additionalDates.forEach {
            validateRfcTemporalValue("RDATE", it)
            validateRecurrenceTemporalDomain(start, "RDATE", it)
        }
        this.additionalPeriods.forEach {
            validateRecurrenceTemporalDomain(start, "RDATE", it.start)
        }
        this.excludedDates.forEach {
            validateRfcTemporalValue("EXDATE", it)
            validateRecurrenceTemporalDomain(start, "EXDATE", it)
        }
    }

    /**
     * Binds this definition to the lazy recurrence-set engine.
     *
     * Timezone identifiers are resolved here rather than while parsing, allowing applications to
     * use an injected resolver for private identifiers or separately parsed `VTIMEZONE` data.
     */
    public fun recurrenceSet(
        timeZoneResolver: RecurrenceTimeZoneResolver = KotlinxRecurrenceTimeZoneResolver,
        ambiguousTimePolicy: AmbiguousTimePolicy = AmbiguousTimePolicy.EARLIER,
    ): RecurrenceSet = RecurrenceSet(
        start = start,
        rules = rules,
        exclusionRules = exclusionRules,
        additionalDates = additionalDates,
        additionalPeriods = additionalPeriods,
        excludedDates = excludedDates,
        timeZoneResolver = timeZoneResolver,
        ambiguousTimePolicy = ambiguousTimePolicy,
    )

    override fun equals(other: Any?): Boolean =
        this === other || other is RecurrenceDefinition &&
            start == other.start &&
            rules == other.rules &&
            exclusionRules == other.exclusionRules &&
            additionalDates == other.additionalDates &&
            additionalPeriods == other.additionalPeriods &&
            excludedDates == other.excludedDates

    override fun hashCode(): Int {
        var result = start.hashCode()
        result = 31 * result + rules.hashCode()
        result = 31 * result + exclusionRules.hashCode()
        result = 31 * result + additionalDates.hashCode()
        result = 31 * result + additionalPeriods.hashCode()
        result = 31 * result + excludedDates.hashCode()
        return result
    }

    override fun toString(): String =
        "RecurrenceDefinition(start=$start, rules=$rules, exclusionRules=$exclusionRules, " +
            "additionalDates=$additionalDates, additionalPeriods=$additionalPeriods, " +
            "excludedDates=$excludedDates)"
}
