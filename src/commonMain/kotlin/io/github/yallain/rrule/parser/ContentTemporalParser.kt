package io.github.yallain.rrule

/** Parses DATE and DATE-TIME lists after their content-line structure has been validated. */
internal object ContentTemporalParser {
    fun parse(
        input: String,
        line: ParsedContentLine,
        allowList: Boolean,
    ): List<ParsedTemporalValue> = parseTemporalValues(
        input = input,
        prepared = prepare(
            input = input,
            line = line,
            allowList = allowList,
            allowedValueTypes = temporalValueTypes,
        ),
    )

    fun parseRDate(input: String, line: ParsedContentLine): ParsedRDateValues {
        val prepared = prepare(
            input = input,
            line = line,
            allowList = true,
            allowedValueTypes = rDateValueTypes,
        )
        return if (prepared.valueType == "PERIOD") {
            ParsedRDateValues(periods = ContentPeriodParser.parse(input, line, prepared))
        } else {
            ParsedRDateValues(dates = parseTemporalValues(input, prepared))
        }
    }

    private fun prepare(
        input: String,
        line: ParsedContentLine,
        allowList: Boolean,
        allowedValueTypes: Set<String>,
    ): PreparedTemporalValues {
        validateParameters(input, line)
        val valueParameter = singleParameter(input, line, "VALUE")
        val valueType = valueParameter?.value?.uppercase() ?: "DATE-TIME"
        if (valueType !in allowedValueTypes) {
            contentParseFailure(
                input = input,
                property = "VALUE",
                invalidToken = valueParameter?.value,
                reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                position = valueParameter?.valueStart,
                detail = "VALUE on ${line.name} must be ${allowedValueTypes.joinToString(" or ")}",
            )
        }
        val timeZone = singleParameter(input, line, "TZID")
        validateTimeZoneParameter(input, timeZone, valueType)
        val tokens = splitValueList(input, line)
        if (!allowList && tokens.size != 1) {
            contentParseFailure(
                input = input,
                property = line.name,
                invalidToken = line.value,
                reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                position = line.valueStart,
                detail = "${line.name} accepts exactly one temporal value",
            )
        }
        return PreparedTemporalValues(
            propertyName = line.name,
            valueType = valueType,
            timeZone = timeZone,
            tokens = tokens,
        )
    }

    private fun parseTemporalValues(
        input: String,
        prepared: PreparedTemporalValues,
    ): List<ParsedTemporalValue> = prepared.tokens.map { token ->
            ParsedTemporalValue(
                value = parseToken(
                    input = input,
                    property = prepared.propertyName,
                    token = token,
                    valueType = prepared.valueType,
                    timeZone = prepared.timeZone,
                ),
                position = token.start,
            )
        }

    private fun validateParameters(input: String, line: ParsedContentLine) {
        line.parameters.firstOrNull { parameter ->
            parameter.name !in temporalParameters && !parameter.isExtensionParameter()
        }?.let { parameter ->
            contentParseFailure(
                input = input,
                property = parameter.name,
                invalidToken = parameter.rawName,
                reason = RecurrenceErrorReason.INVALID_COMBINATION,
                position = parameter.start,
                detail = "${parameter.rawName} is not valid on ${line.name}",
            )
        }
    }

    private fun singleParameter(
        input: String,
        line: ParsedContentLine,
        name: String,
    ): ContentParameter? {
        val matches = line.parameters.filter { it.name == name }
        if (matches.size > 1) {
            val duplicate = matches[1]
            contentParseFailure(
                input = input,
                property = name,
                invalidToken = duplicate.rawName,
                reason = RecurrenceErrorReason.DUPLICATE_PROPERTY,
                position = duplicate.start,
                detail = "$name must not occur more than once on ${line.name}",
            )
        }
        val parameter = matches.firstOrNull() ?: return null
        if (parameter.values.size != 1) {
            contentParseFailure(
                input = input,
                property = name,
                invalidToken = parameter.values.joinToString(","),
                reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                position = parameter.valueStarts.getOrElse(1) { parameter.valueStart },
                detail = "$name accepts exactly one parameter value on ${line.name}",
            )
        }
        return parameter
    }

    private fun validateTimeZoneParameter(
        input: String,
        timeZone: ContentParameter?,
        valueType: String,
    ) {
        if (valueType == "DATE" && timeZone != null) {
            contentParseFailure(
                input = input,
                property = "TZID",
                invalidToken = timeZone.value,
                reason = RecurrenceErrorReason.INVALID_COMBINATION,
                position = timeZone.start,
                detail = "A DATE value must not carry a TZID",
            )
        }
    }

    private fun parseToken(
        input: String,
        property: String,
        token: ContentValueToken,
        valueType: String,
        timeZone: ContentParameter?,
    ): RecurrenceDateTime = try {
        if (valueType == "DATE") {
            if (!RfcTemporalValueParser.isDate(token.value)) malformedTemporal(input, property, token, valueType)
            RecurrenceDateTime.DateOnly(RfcTemporalValueParser.parseDate(token.value))
        } else {
            parseDateTime(input, property, token, timeZone)
        }
    } catch (error: IllegalArgumentException) {
        if (error is RecurrenceParseException) throw error
        malformedTemporal(input, property, token, valueType)
    }

    internal fun parseDateTime(
        input: String,
        property: String,
        token: ContentValueToken,
        timeZone: ContentParameter?,
    ): RecurrenceDateTime = when {
        RfcTemporalValueParser.isUtcDateTime(token.value) -> {
            if (timeZone != null) {
                contentParseFailure(
                    input = input,
                    property = "TZID",
                    invalidToken = timeZone.value,
                    reason = RecurrenceErrorReason.INVALID_COMBINATION,
                    position = timeZone.start,
                    detail = "A UTC DATE-TIME value must not carry a TZID",
                )
            }
            RecurrenceDateTime.Utc(RfcTemporalValueParser.parseUtcDateTime(token.value))
        }
        RfcTemporalValueParser.isLocalDateTime(token.value) -> {
            val local = RfcTemporalValueParser.parseLocalDateTime(token.value)
            if (timeZone == null) RecurrenceDateTime.Floating(local) else RecurrenceDateTime.Zoned(local, timeZone.value)
        }
        else -> malformedTemporal(input, property, token, "DATE-TIME")
    }

    private fun malformedTemporal(
        input: String,
        property: String,
        token: ContentValueToken,
        valueType: String,
    ): Nothing = contentParseFailure(
        input = input,
        property = property,
        invalidToken = token.value,
        reason = RecurrenceErrorReason.MALFORMED_TOKEN,
        position = token.start,
        detail = "$property contains an invalid RFC 5545 $valueType value",
    )

    private fun splitValueList(input: String, line: ParsedContentLine): List<ContentValueToken> = buildList {
        var relativeStart = 0
        while (relativeStart <= line.value.length) {
            val comma = line.value.indexOf(',', relativeStart).let { if (it < 0) line.value.length else it }
            val token = line.value.substring(relativeStart, comma)
            if (token.isEmpty()) {
                contentParseFailure(
                    input = input,
                    property = line.name,
                    invalidToken = token,
                    reason = RecurrenceErrorReason.EMPTY_VALUE,
                    position = line.sourcePositionInValue(relativeStart),
                    detail = "${line.name} contains an empty list item",
                )
            }
            add(
                ContentValueToken(
                    value = token.uppercase(),
                    start = line.sourcePositionInValue(relativeStart),
                    relativeStart = relativeStart,
                ),
            )
            if (comma == line.value.length) break
            relativeStart = comma + 1
        }
    }

    private val temporalParameters = setOf("VALUE", "TZID")
    private val temporalValueTypes = setOf("DATE", "DATE-TIME")
    private val rDateValueTypes = temporalValueTypes + "PERIOD"
}

internal data class ContentValueToken(
    val value: String,
    val start: Int,
    val relativeStart: Int,
)

internal data class PreparedTemporalValues(
    val propertyName: String,
    val valueType: String,
    val timeZone: ContentParameter?,
    val tokens: List<ContentValueToken>,
)

internal data class ParsedTemporalValue(
    val value: RecurrenceDateTime,
    val position: Int,
)

internal data class ParsedPeriodValue(
    val value: RecurrencePeriod,
    val position: Int,
)

internal data class ParsedRDateValues(
    val dates: List<ParsedTemporalValue> = emptyList(),
    val periods: List<ParsedPeriodValue> = emptyList(),
)
