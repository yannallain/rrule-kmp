package io.github.yallain.rrule

/**
 * Optional resolver capability for time zones whose local and instant timelines are one-to-one.
 *
 * Returning `true` from [hasLinearLocalTimeline] promises, for the requested `TZID` throughout the
 * RFC 5545 year range, that:
 *
 * - every local date-time resolves to exactly one instant;
 * - increasing local date-times resolve to increasing instants; and
 * - [RecurrenceTimeZoneResolver.localDateTimeAt] is the inverse of that resolution.
 *
 * Fixed-offset zones satisfy this contract. Zones with gaps or overlaps do not. The recurrence
 * engine uses the promise only to count an already-generated local prefix without resolving every
 * candidate. A custom resolver should return `false` whenever it cannot prove the contract.
 */
public interface LinearLocalTimeZoneResolver : RecurrenceTimeZoneResolver {
    /** Returns whether [timeZoneId] has a one-to-one, order-preserving local timeline. */
    public fun hasLinearLocalTimeline(timeZoneId: String): Boolean
}
