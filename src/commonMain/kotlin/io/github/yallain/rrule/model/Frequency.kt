package io.github.yallain.rrule

/** The base interval at which an RFC 5545 recurrence rule is evaluated. */
public enum class Frequency {
    SECONDLY,
    MINUTELY,
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}
