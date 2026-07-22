package io.github.yallain.rrule

/**
 * Strict parser for recurrence-related iCalendar content lines.
 *
 * Supported properties are `DTSTART`, `RRULE`, `RDATE`, and `EXDATE`. Property and parameter
 * names are case-insensitive, and RFC line folding is handled before parsing. This parser
 * intentionally does not parse complete `VEVENT` components or embedded `VTIMEZONE` definitions.
 * Unrecognized IANA and `X-` parameters are ignored as required by RFC 5545; recognized standard
 * parameters are rejected when they are not valid for the containing property. Both explicit-end
 * and positive-duration `RDATE;VALUE=PERIOD` forms are preserved. The pre-RFC 5545 `EXRULE`
 * property is not supported.
 */
public object RecurrenceContentParser {
    /** Parses [value] into an immutable definition ready to bind with `recurrenceSet()`. */
    public fun parse(value: String): RecurrenceDefinition = try {
        parseValidated(value)
    } catch (error: RecurrenceValidationException) {
        throw RecurrenceParseException(
            inputValue = value,
            propertyName = error.propertyName,
            invalidToken = error.invalidToken,
            reason = error.reason,
            position = error.position ?: findValidationPosition(value, error),
            detail = error.detail,
        )
    }

    private fun parseValidated(input: String): RecurrenceDefinition {
        var start: ParsedTemporalValue? = null
        var rule: RecurrenceRule? = null
        val additionalDates = linkedMapOf<RecurrenceDateTime, Int>()
        val additionalPeriods = linkedMapOf<RecurrencePeriod, Int>()
        val excludedDates = linkedMapOf<RecurrenceDateTime, Int>()

        for (line in ContentLineParser.parse(input)) {
            when (line.name) {
                "DTSTART" -> {
                    if (start != null) {
                        contentParseFailure(
                            input = input,
                            property = "DTSTART",
                            invalidToken = "DTSTART",
                            reason = RecurrenceErrorReason.DUPLICATE_PROPERTY,
                            position = line.start,
                            detail = "DTSTART must not occur more than once",
                        )
                    }
                    start = ContentTemporalParser.parse(input, line, allowList = false).single()
                }
                "RRULE" -> {
                    if (rule != null) {
                        contentParseFailure(
                            input = input,
                            property = "RRULE",
                            invalidToken = "RRULE",
                            reason = RecurrenceErrorReason.DUPLICATE_PROPERTY,
                            position = line.start,
                            detail = "RRULE must not occur more than once in one recurrence definition",
                        )
                    }
                    rule = parseRule(input, line)
                }
                "RDATE" -> ContentTemporalParser.parseRDate(input, line).let { parsedValues ->
                    parsedValues.dates.forEach { parsed ->
                        if (parsed.value !in additionalDates) additionalDates[parsed.value] = parsed.position
                    }
                    parsedValues.periods.forEach { parsed ->
                        if (parsed.value !in additionalPeriods) additionalPeriods[parsed.value] = parsed.position
                    }
                }
                "EXDATE" -> ContentTemporalParser.parse(input, line, allowList = true).forEach { parsed ->
                    if (parsed.value !in excludedDates) excludedDates[parsed.value] = parsed.position
                }
                else -> contentParseFailure(
                    input = input,
                    property = line.name,
                    invalidToken = line.rawName,
                    reason = RecurrenceErrorReason.UNKNOWN_PROPERTY,
                    position = line.start,
                    detail = "Unsupported recurrence content property ${line.rawName}",
                )
            }
        }

        val recurrenceStart = start?.value ?: contentParseFailure(
            input = input,
            property = "DTSTART",
            reason = RecurrenceErrorReason.MISSING_REQUIRED_PROPERTY,
            position = if (input.isEmpty()) 0 else null,
            detail = "DTSTART is required",
        )
        validateExplicitValues(input, recurrenceStart, "RDATE", additionalDates)
        validateExplicitPeriods(input, recurrenceStart, additionalPeriods)
        validateExplicitValues(input, recurrenceStart, "EXDATE", excludedDates)
        return RecurrenceDefinition(
            start = recurrenceStart,
            rules = listOfNotNull(rule),
            additionalDates = additionalDates.keys,
            additionalPeriods = additionalPeriods.keys,
            excludedDates = excludedDates.keys,
        )
    }

    private fun validateExplicitPeriods(
        input: String,
        start: RecurrenceDateTime,
        periods: Map<RecurrencePeriod, Int>,
    ) {
        for ((period, position) in periods) {
            try {
                validateRecurrenceTemporalDomain(start, "RDATE", period.start)
            } catch (error: RecurrenceValidationException) {
                contentParseFailure(
                    input = input,
                    property = error.propertyName ?: "RDATE",
                    invalidToken = error.invalidToken,
                    reason = error.reason,
                    position = position,
                    detail = error.detail,
                )
            }
        }
    }

    private fun validateExplicitValues(
        input: String,
        start: RecurrenceDateTime,
        propertyName: String,
        values: Map<RecurrenceDateTime, Int>,
    ) {
        for ((value, position) in values) {
            try {
                validateRecurrenceTemporalDomain(start, propertyName, value)
            } catch (error: RecurrenceValidationException) {
                contentParseFailure(
                    input = input,
                    property = error.propertyName ?: propertyName,
                    invalidToken = error.invalidToken,
                    reason = error.reason,
                    position = position,
                    detail = error.detail,
                )
            }
        }
    }

    private fun parseRule(input: String, line: ParsedContentLine): RecurrenceRule {
        line.parameters.firstOrNull { !it.isExtensionParameter() }?.let { parameter ->
            contentParseFailure(
                input = input,
                property = parameter.name,
                invalidToken = parameter.rawName,
                reason = RecurrenceErrorReason.INVALID_COMBINATION,
                position = parameter.start,
                detail = "${parameter.rawName} is not valid on ${line.name}",
            )
        }
        return try {
            RecurrenceRuleParser.parse(line.value)
        } catch (error: RecurrenceParseException) {
            throw RecurrenceParseException(
                inputValue = input,
                propertyName = error.propertyName,
                invalidToken = error.invalidToken,
                reason = error.reason,
                position = error.position?.let(line::sourcePositionInValue) ?: line.valueStart,
                detail = error.detail,
            )
        }
    }

    private fun findValidationPosition(input: String, error: RecurrenceValidationException): Int? {
        error.invalidToken?.let { token ->
            input.indexOf(token, ignoreCase = true).takeIf { it >= 0 }?.let { return it }
        }
        error.propertyName?.let { property ->
            input.indexOf(property, ignoreCase = true).takeIf { it >= 0 }?.let { return it }
        }
        return null
    }
}
