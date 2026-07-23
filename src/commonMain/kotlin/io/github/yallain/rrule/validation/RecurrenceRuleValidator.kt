package io.github.yallain.rrule

internal object RecurrenceRuleValidator {
    fun validate(rule: RecurrenceRule) {
        requireGreaterThanZero("INTERVAL", rule.interval)
        rule.count?.let { requireGreaterThanZero("COUNT", it) }
        if (rule.count != null && rule.until != null) {
            invalid(
                property = "COUNT",
                token = "COUNT,UNTIL",
                reason = RecurrenceErrorReason.MUTUALLY_EXCLUSIVE,
                detail = "COUNT and UNTIL must not occur in the same rule",
            )
        }
        if (rule.until is RecurrenceDateTime.Zoned) {
            invalid(
                property = "UNTIL",
                token = rule.until.toString(),
                reason = RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE,
                detail = "UNTIL cannot carry a TZID; zoned DTSTART values require a UTC UNTIL",
            )
        }
        rule.until?.let { validateRfcTemporalValue("UNTIL", it) }

        requireRange("BYSECOND", rule.bySecond, 0..60)
        requireRange("BYMINUTE", rule.byMinute, 0..59)
        requireRange("BYHOUR", rule.byHour, 0..23)
        requireRange("BYMONTH", rule.byMonth, 1..12)
        requireSignedRange("BYMONTHDAY", rule.byMonthDay, 31)
        requireSignedRange("BYYEARDAY", rule.byYearDay, 366)
        requireSignedRange("BYWEEKNO", rule.byWeekNumber, 53)
        requireSignedRange("BYSETPOS", rule.bySetPosition, 366)

        if (rule.byWeekNumber.isNotEmpty() && rule.frequency != Frequency.YEARLY) {
            invalid(
                property = "BYWEEKNO",
                token = rule.byWeekNumber.first().toString(),
                reason = RecurrenceErrorReason.INVALID_COMBINATION,
                detail = "BYWEEKNO is only valid with FREQ=YEARLY",
            )
        }

        if (rule.byMonthDay.isNotEmpty() && rule.frequency == Frequency.WEEKLY) {
            invalid(
                property = "BYMONTHDAY",
                token = rule.byMonthDay.first().toString(),
                reason = RecurrenceErrorReason.INVALID_COMBINATION,
                detail = "BYMONTHDAY is not valid with FREQ=WEEKLY",
            )
        }
        if (rule.byYearDay.isNotEmpty() &&
            rule.frequency in setOf(Frequency.DAILY, Frequency.WEEKLY, Frequency.MONTHLY)
        ) {
            invalid(
                property = "BYYEARDAY",
                token = rule.byYearDay.first().toString(),
                reason = RecurrenceErrorReason.INVALID_COMBINATION,
                detail = "BYYEARDAY is not valid with FREQ=DAILY, WEEKLY, or MONTHLY",
            )
        }

        val ordinalDay = rule.byDay.firstOrNull { it.ordinal != null }
        if (ordinalDay != null && rule.frequency !in setOf(Frequency.MONTHLY, Frequency.YEARLY)) {
            invalid(
                property = "BYDAY",
                token = ordinalDay.toRfcToken(),
                reason = RecurrenceErrorReason.INVALID_COMBINATION,
                detail = "Numeric BYDAY is only valid with FREQ=MONTHLY or FREQ=YEARLY",
            )
        }
        if (ordinalDay != null && rule.frequency == Frequency.YEARLY && rule.byWeekNumber.isNotEmpty()) {
            invalid(
                property = "BYDAY",
                token = ordinalDay.toRfcToken(),
                reason = RecurrenceErrorReason.INVALID_COMBINATION,
                detail = "Numeric BYDAY is not valid with a YEARLY BYWEEKNO rule",
            )
        }

        if (rule.bySetPosition.isNotEmpty() && !rule.hasOtherByPart()) {
            invalid(
                property = "BYSETPOS",
                token = rule.bySetPosition.first().toString(),
                reason = RecurrenceErrorReason.INVALID_COMBINATION,
                detail = "BYSETPOS requires at least one other BYxxx rule part",
            )
        }
    }

    fun validateForStart(rule: RecurrenceRule, start: RecurrenceDateTime) {
        validateUntilForStart(rule, start)
        validateDateOnlyFrequency(rule, start)
    }

    private fun validateUntilForStart(rule: RecurrenceRule, start: RecurrenceDateTime) {
        val until = rule.until ?: return
        val compatible = when (start) {
            is RecurrenceDateTime.DateOnly -> until is RecurrenceDateTime.DateOnly
            is RecurrenceDateTime.Floating -> until is RecurrenceDateTime.Floating
            is RecurrenceDateTime.Utc -> until is RecurrenceDateTime.Utc
            is RecurrenceDateTime.Zoned -> until is RecurrenceDateTime.Utc
        }
        if (!compatible) {
            invalid(
                property = "UNTIL",
                token = until.toString(),
                reason = RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE,
                detail = "UNTIL is incompatible with the DTSTART temporal type",
            )
        }
    }

    private fun validateDateOnlyFrequency(rule: RecurrenceRule, start: RecurrenceDateTime) {
        if (start !is RecurrenceDateTime.DateOnly) return
        if (rule.frequency in setOf(Frequency.SECONDLY, Frequency.MINUTELY, Frequency.HOURLY)) {
            invalid(
                property = "FREQ",
                token = rule.frequency.name,
                reason = RecurrenceErrorReason.INVALID_COMBINATION,
                detail = "A DATE DTSTART requires a day-based or larger frequency",
            )
        }
    }

    private fun RecurrenceRule.hasOtherByPart(): Boolean =
        bySecond.isNotEmpty() || byMinute.isNotEmpty() || byHour.isNotEmpty() ||
            byDay.isNotEmpty() || byMonthDay.isNotEmpty() || byYearDay.isNotEmpty() ||
            byWeekNumber.isNotEmpty() || byMonth.isNotEmpty()

    private fun requireGreaterThanZero(property: String, value: Int) {
        if (value <= 0) {
            invalid(property, value.toString(), RecurrenceErrorReason.OUT_OF_RANGE, "$property must be greater than zero")
        }
    }

    private fun requireRange(property: String, values: Set<Int>, range: IntRange) {
        values.firstOrNull { it !in range }?.let {
            invalid(property, it.toString(), RecurrenceErrorReason.OUT_OF_RANGE, "$property must be in ${range.first}..${range.last}")
        }
    }

    private fun requireSignedRange(property: String, values: Set<Int>, maximum: Int) {
        values.firstOrNull { it == 0 || it !in -maximum..maximum }?.let {
            invalid(
                property,
                it.toString(),
                RecurrenceErrorReason.OUT_OF_RANGE,
                "$property must be in -$maximum..-1 or 1..$maximum",
            )
        }
    }

    private fun invalid(
        property: String,
        token: String,
        reason: RecurrenceErrorReason,
        detail: String,
    ): Nothing = throw RecurrenceValidationException(
        propertyName = property,
        invalidToken = token,
        reason = reason,
        detail = detail,
    )
}

internal fun ByDay.toRfcToken(): String = buildString {
    ordinal?.let(::append)
    append(weekday.rfcCode)
}
