package io.github.yallain.rrule.apple

import platform.Foundation.NSDate

/** A half-open elapsed-time interval returned to Objective-C and Swift consumers. */
public class AppleRecurrenceInterval internal constructor(
    public val start: NSDate,
    public val endExclusive: NSDate,
)
