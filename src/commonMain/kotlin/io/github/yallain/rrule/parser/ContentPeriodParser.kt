package io.github.yallain.rrule

/** Parses PERIOD lists after their content-line parameters have been validated. */
internal object ContentPeriodParser {
    fun parse(
        input: String,
        line: ParsedContentLine,
        prepared: PreparedTemporalValues,
    ): List<ParsedPeriodValue> = prepared.tokens.map { token ->
        ParsedPeriodValue(
            value = parsePeriod(input, line, token, prepared.timeZone),
            position = token.start,
        )
    }

    private fun parsePeriod(
        input: String,
        line: ParsedContentLine,
        token: ContentValueToken,
        timeZone: ContentParameter?,
    ): RecurrencePeriod {
        val slash = token.value.indexOf('/')
        if (slash <= 0 || slash != token.value.lastIndexOf('/') || slash == token.value.lastIndex) {
            malformedPeriod(input, token, "A PERIOD must contain exactly one non-empty '/' separator")
        }
        val startToken = token.segment(line, 0, slash)
        val endOrDurationToken = token.segment(line, slash + 1, token.value.length)
        val start = parseDateTime(input, startToken, timeZone)
        return if (endOrDurationToken.value.startsWith('P') ||
            endOrDurationToken.value.startsWith("+P") ||
            endOrDurationToken.value.startsWith("-P")
        ) {
            val duration = parseDuration(input, endOrDurationToken)
            RecurrencePeriod.WithDuration(start, duration)
        } else {
            val end = parseDateTime(input, endOrDurationToken, timeZone)
            try {
                RecurrencePeriod.Explicit(start, end)
            } catch (error: RecurrenceValidationException) {
                contentParseFailure(
                    input = input,
                    property = error.propertyName ?: "RDATE",
                    invalidToken = endOrDurationToken.value,
                    reason = error.reason,
                    position = endOrDurationToken.start,
                    detail = error.detail,
                )
            }
        }
    }

    private fun parseDateTime(
        input: String,
        token: ContentValueToken,
        timeZone: ContentParameter?,
    ): RecurrenceDateTime = try {
        ContentTemporalParser.parseDateTime(input, "RDATE", token, timeZone)
    } catch (error: IllegalArgumentException) {
        if (error is RecurrenceParseException) throw error
        malformedPeriod(input, token, "A PERIOD endpoint must be an RFC 5545 DATE-TIME value")
    }

    private fun parseDuration(input: String, token: ContentValueToken): RecurrenceDuration {
        if (token.value.startsWith('-')) {
            contentParseFailure(
                input = input,
                property = "DURATION",
                invalidToken = token.value,
                reason = RecurrenceErrorReason.OUT_OF_RANGE,
                position = token.start,
                detail = "An RDATE PERIOD duration must be positive",
            )
        }
        val value = token.value.removePrefix("+")
        val weekMatch = weekDuration.matchEntire(value)
        if (weekMatch != null) {
            return constructDuration(input, token) {
                RecurrenceDuration(weeks = parseComponent(input, token, weekMatch.groupValues[1]))
            }
        }
        val dayMatch = dayDuration.matchEntire(value)
        if (dayMatch != null) {
            return buildDuration(
                input = input,
                token = token,
                days = dayMatch.groupValues[1],
                clockComponents = dayMatch.groupValues.drop(2),
            )
        }
        val timeMatch = timeDuration.matchEntire(value)
        if (timeMatch != null) {
            return buildDuration(
                input = input,
                token = token,
                days = "",
                clockComponents = timeMatch.groupValues.drop(1),
            )
        }
        malformedDuration(input, token, "A PERIOD duration must use RFC 5545 DURATION syntax")
    }

    private fun buildDuration(
        input: String,
        token: ContentValueToken,
        days: String,
        clockComponents: List<String>,
    ): RecurrenceDuration {
        val hours = clockComponents[0]
        val minutes = clockComponents[1].ifEmpty { clockComponents[3] }
        val seconds = clockComponents[2]
            .ifEmpty { clockComponents[4] }
            .ifEmpty { clockComponents[5] }
        return constructDuration(input, token) {
            RecurrenceDuration(
                days = days.toComponent(input, token),
                hours = hours.toComponent(input, token),
                minutes = minutes.toComponent(input, token),
                seconds = seconds.toComponent(input, token),
            )
        }
    }

    private inline fun constructDuration(
        input: String,
        token: ContentValueToken,
        constructor: () -> RecurrenceDuration,
    ): RecurrenceDuration = try {
        constructor()
    } catch (error: RecurrenceValidationException) {
            contentParseFailure(
                input = input,
                property = error.propertyName ?: "DURATION",
                invalidToken = token.value,
                reason = error.reason,
                position = token.start,
                detail = error.detail,
            )
    }

    private fun String.toComponent(input: String, token: ContentValueToken): Int =
        if (isEmpty()) 0 else parseComponent(input, token, this)

    private fun parseComponent(input: String, token: ContentValueToken, value: String): Int =
        value.toIntOrNull() ?: contentParseFailure(
            input = input,
            property = "DURATION",
            invalidToken = token.value,
            reason = RecurrenceErrorReason.OUT_OF_RANGE,
            position = token.start,
            detail = "A PERIOD duration component exceeds the supported integer range",
        )

    private fun ContentValueToken.segment(
        line: ParsedContentLine,
        fromIndex: Int,
        toIndex: Int,
    ): ContentValueToken = ContentValueToken(
        value = value.substring(fromIndex, toIndex),
        start = line.sourcePositionInValue(relativeStart + fromIndex),
        relativeStart = relativeStart + fromIndex,
    )

    private fun malformedPeriod(input: String, token: ContentValueToken, detail: String): Nothing =
        contentParseFailure(
            input = input,
            property = "RDATE",
            invalidToken = token.value,
            reason = RecurrenceErrorReason.MALFORMED_TOKEN,
            position = token.start,
            detail = detail,
        )

    private fun malformedDuration(input: String, token: ContentValueToken, detail: String): Nothing =
        contentParseFailure(
            input = input,
            property = "DURATION",
            invalidToken = token.value,
            reason = RecurrenceErrorReason.MALFORMED_TOKEN,
            position = token.start,
            detail = detail,
        )

    private const val CLOCK_DURATION =
        "(?:([0-9]+)H(?:([0-9]+)M(?:([0-9]+)S)?)?|([0-9]+)M(?:([0-9]+)S)?|([0-9]+)S)"

    private val weekDuration = Regex("^P([0-9]+)W$")
    private val dayDuration = Regex("^P([0-9]+)D(?:T$CLOCK_DURATION)?$")
    private val timeDuration = Regex("^PT$CLOCK_DURATION$")
}
