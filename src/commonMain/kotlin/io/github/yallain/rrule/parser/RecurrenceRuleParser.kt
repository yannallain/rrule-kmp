package io.github.yallain.rrule

/** Strict parser for the value portion of an RFC 5545 `RRULE` property. */
public object RecurrenceRuleParser {
    /** Parses [value], throwing [RecurrenceParseException] with structured context on failure. */
    public fun parse(value: String): RecurrenceRule = try {
        parseValidated(value)
    } catch (error: RecurrenceValidationException) {
        throw RecurrenceParseException(
            inputValue = value,
            propertyName = error.propertyName,
            invalidToken = error.invalidToken,
            reason = error.reason,
            position = validationErrorPosition(value, error),
            detail = error.detail,
        )
    }

    private fun validationErrorPosition(
        input: String,
        error: RecurrenceValidationException,
    ): Int? {
        error.position?.let { return it }
        val part = error.propertyName?.let { parseParts(input)[it] } ?: return null
        val invalidToken = error.invalidToken ?: return part.valueStart
        return part.positionOf(invalidToken) ?: part.valueStart
    }

    private fun parseValidated(value: String): RecurrenceRule {
        if (value.isEmpty()) {
            fail(value, reason = RecurrenceErrorReason.EMPTY_VALUE, position = 0, detail = "RRULE value must not be empty")
        }
        val parts = parseParts(value)
        val frequencyPart = parts["FREQ"] ?: fail(
            value,
            property = "FREQ",
            reason = RecurrenceErrorReason.MISSING_REQUIRED_PROPERTY,
            detail = "FREQ is required",
        )

        return RecurrenceRule(
            frequency = parseFrequency(value, frequencyPart),
            interval = parts["INTERVAL"]?.let { parseScalarInt(value, "INTERVAL", it) } ?: 1,
            count = parts["COUNT"]?.let { parseScalarInt(value, "COUNT", it) },
            until = parts["UNTIL"]?.let { parseUntil(value, it) },
            weekStart = parts["WKST"]?.let { parseWeekday(value, "WKST", it) } ?: Weekday.MONDAY,
            bySecond = parts["BYSECOND"]?.let { parseIntSet(value, "BYSECOND", it) }.orEmpty(),
            byMinute = parts["BYMINUTE"]?.let { parseIntSet(value, "BYMINUTE", it) }.orEmpty(),
            byHour = parts["BYHOUR"]?.let { parseIntSet(value, "BYHOUR", it) }.orEmpty(),
            byDay = parts["BYDAY"]?.let { parseByDay(value, it) }.orEmpty(),
            byMonthDay = parts["BYMONTHDAY"]?.let { parseIntSet(value, "BYMONTHDAY", it) }.orEmpty(),
            byYearDay = parts["BYYEARDAY"]?.let { parseIntSet(value, "BYYEARDAY", it) }.orEmpty(),
            byWeekNumber = parts["BYWEEKNO"]?.let { parseIntSet(value, "BYWEEKNO", it) }.orEmpty(),
            byMonth = parts["BYMONTH"]?.let { parseIntSet(value, "BYMONTH", it) }.orEmpty(),
            bySetPosition = parts["BYSETPOS"]?.let { parseIntSet(value, "BYSETPOS", it) }.orEmpty(),
        )
    }

    private fun parseParts(input: String): Map<String, Part> {
        if (input.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, Part>()
        var start = 0
        while (start <= input.length) {
            val delimiter = input.indexOf(';', start).let { if (it < 0) input.length else it }
            val segment = input.substring(start, delimiter)
            if (segment.isEmpty()) {
                fail(
                    input,
                    reason = RecurrenceErrorReason.EMPTY_VALUE,
                    position = start,
                    detail = "Empty RRULE rule part",
                )
            }
            val equals = segment.indexOf('=')
            if (equals <= 0 || equals != segment.lastIndexOf('=')) {
                fail(
                    input,
                    invalidToken = segment,
                    reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                    position = start,
                    detail = "Each rule part must contain exactly one '=' separator",
                )
            }
            val rawName = segment.substring(0, equals)
            val name = rawName.uppercase()
            val rawValue = segment.substring(equals + 1)
            if (rawValue.isEmpty()) {
                fail(
                    input,
                    property = name,
                    invalidToken = rawValue,
                    reason = RecurrenceErrorReason.EMPTY_VALUE,
                    position = start + equals + 1,
                    detail = "$name must not be empty",
                )
            }
            if (name !in supportedProperties) {
                fail(
                    input,
                    property = name,
                    invalidToken = rawName,
                    reason = RecurrenceErrorReason.UNKNOWN_PROPERTY,
                    position = start,
                    detail = "Unsupported RRULE property $rawName",
                )
            }
            if (name in result) {
                fail(
                    input,
                    property = name,
                    invalidToken = rawName,
                    reason = RecurrenceErrorReason.DUPLICATE_PROPERTY,
                    position = start,
                    detail = "$name must not occur more than once",
                )
            }
            result[name] = Part(rawValue, start + equals + 1)
            if (delimiter == input.length) break
            start = delimiter + 1
        }
        return result
    }

    private fun parseFrequency(input: String, part: Part): Frequency =
        Frequency.entries.firstOrNull { it.name == part.value.uppercase() }
            ?: fail(
                input,
                property = "FREQ",
                invalidToken = part.value,
                reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                position = part.valueStart,
                detail = "Unknown recurrence frequency",
            )

    private fun parseScalarInt(input: String, property: String, part: Part): Int {
        if (',' in part.value) {
            fail(
                input,
                property,
                part.value,
                RecurrenceErrorReason.MALFORMED_TOKEN,
                part.valueStart,
                "$property accepts one integer",
            )
        }
        return parseIntToken(input, property, part.value, part.valueStart)
    }

    private fun parseIntSet(input: String, property: String, part: Part): Set<Int> =
        splitList(input, property, part).mapTo(linkedSetOf()) { token ->
            parseIntToken(input, property, token.value, token.start)
        }

    private fun parseByDay(input: String, part: Part): List<ByDay> =
        splitList(input, "BYDAY", part).map { token ->
            val match = byDayPattern.matchEntire(token.value.uppercase()) ?: fail(
                input,
                "BYDAY",
                token.value,
                RecurrenceErrorReason.MALFORMED_TOKEN,
                token.start,
                "BYDAY values must be weekdays with an optional signed ordinal",
            )
            val ordinal = match.groupValues[1].takeIf(String::isNotEmpty)?.let {
                parseIntToken(input, "BYDAY", it, token.start)
            }
            val weekday = Weekday.fromRfcCode(match.groupValues[2])!!
            ByDay(weekday, ordinal)
        }

    private fun parseWeekday(input: String, property: String, part: Part): Weekday =
        Weekday.fromRfcCode(part.value) ?: fail(
            input,
            property,
            part.value,
            RecurrenceErrorReason.MALFORMED_TOKEN,
            part.valueStart,
            "$property must be one of MO,TU,WE,TH,FR,SA,SU",
        )

    private fun parseUntil(input: String, part: Part): RecurrenceDateTime {
        val token = part.value.uppercase()
        return try {
            when {
                RfcTemporalValueParser.isDate(token) ->
                    RecurrenceDateTime.DateOnly(RfcTemporalValueParser.parseDate(token))
                RfcTemporalValueParser.isLocalDateTime(token) ->
                    RecurrenceDateTime.Floating(RfcTemporalValueParser.parseLocalDateTime(token))
                RfcTemporalValueParser.isUtcDateTime(token) ->
                    RecurrenceDateTime.Utc(RfcTemporalValueParser.parseUtcDateTime(token))
                else -> fail(
                    input,
                    "UNTIL",
                    part.value,
                    RecurrenceErrorReason.MALFORMED_TOKEN,
                    part.valueStart,
                    "UNTIL must use yyyyMMdd, yyyyMMdd'T'HHmmss, or yyyyMMdd'T'HHmmss'Z'",
                )
            }
        } catch (error: IllegalArgumentException) {
            if (error is RecurrenceParseException) throw error
            fail(
                input,
                "UNTIL",
                part.value,
                RecurrenceErrorReason.MALFORMED_TOKEN,
                part.valueStart,
                "UNTIL contains an invalid calendar date or time",
            )
        }
    }

    private fun parseIntToken(input: String, property: String, token: String, position: Int): Int {
        val syntax = integerTokenSyntax.getValue(property)
        val hasSign = token.firstOrNull() == '+' || token.firstOrNull() == '-'
        if (hasSign && !syntax.allowsSign) {
            fail(
                input,
                property,
                token,
                RecurrenceErrorReason.MALFORMED_TOKEN,
                position,
                "$property does not permit a signed integer",
            )
        }
        val digits = if (hasSign) token.drop(1) else token
        if (digits.isEmpty() || digits.any { it !in '0'..'9' }) {
            fail(
                input,
                property,
                token,
                RecurrenceErrorReason.MALFORMED_TOKEN,
                position,
                "$property integers must contain ASCII decimal digits",
            )
        }
        syntax.maximumDigits?.takeIf { digits.length > it }?.let { maximumDigits ->
            fail(
                input,
                property,
                token,
                RecurrenceErrorReason.MALFORMED_TOKEN,
                position,
                "$property integers permit at most $maximumDigits digits, excluding an optional sign",
            )
        }
        return token.toIntOrNull() ?: fail(
            input,
            property,
            token,
            RecurrenceErrorReason.MALFORMED_TOKEN,
            position,
            "$property contains a malformed integer",
        )
    }

    private fun splitList(input: String, property: String, part: Part): List<ListToken> {
        val tokens = part.listTokens()
        tokens.firstOrNull { it.value.isEmpty() }?.let { token ->
            fail(
                input,
                property,
                token.value,
                RecurrenceErrorReason.EMPTY_VALUE,
                token.start,
                "$property contains an empty list item",
            )
        }
        return tokens
    }

    private data class Part(val value: String, val valueStart: Int) {
        fun positionOf(invalidToken: String): Int? {
            val tokens = listTokens()
            tokens.firstOrNull { it.value.equals(invalidToken, ignoreCase = true) }
                ?.let { return it.start }

            val invalidInteger = invalidToken.toIntOrNull()
            if (invalidInteger != null) {
                tokens.firstOrNull { it.value.toIntOrNull() == invalidInteger }
                    ?.let { return it.start }
            }

            return tokens.firstOrNull { it.value.contains(invalidToken, ignoreCase = true) }?.start
        }

        fun listTokens(): List<ListToken> = buildList {
            var relativeStart = 0
            while (relativeStart <= value.length) {
                val comma = value.indexOf(',', relativeStart).let { if (it < 0) value.length else it }
                add(ListToken(value.substring(relativeStart, comma), valueStart + relativeStart))
                if (comma == value.length) break
                relativeStart = comma + 1
            }
        }
    }

    private data class ListToken(val value: String, val start: Int)

    private data class IntegerTokenSyntax(
        val allowsSign: Boolean,
        val maximumDigits: Int? = null,
    )

    private val byDayPattern = Regex("^([+-]?[0-9]+)?(MO|TU|WE|TH|FR|SA|SU)$")
    private val integerTokenSyntax = mapOf(
        "COUNT" to IntegerTokenSyntax(allowsSign = false),
        "INTERVAL" to IntegerTokenSyntax(allowsSign = false),
        "BYSECOND" to IntegerTokenSyntax(allowsSign = false, maximumDigits = 2),
        "BYMINUTE" to IntegerTokenSyntax(allowsSign = false, maximumDigits = 2),
        "BYHOUR" to IntegerTokenSyntax(allowsSign = false, maximumDigits = 2),
        "BYDAY" to IntegerTokenSyntax(allowsSign = true, maximumDigits = 2),
        "BYMONTHDAY" to IntegerTokenSyntax(allowsSign = true, maximumDigits = 2),
        "BYYEARDAY" to IntegerTokenSyntax(allowsSign = true, maximumDigits = 3),
        "BYWEEKNO" to IntegerTokenSyntax(allowsSign = true, maximumDigits = 2),
        "BYMONTH" to IntegerTokenSyntax(allowsSign = false, maximumDigits = 2),
        "BYSETPOS" to IntegerTokenSyntax(allowsSign = true, maximumDigits = 3),
    )
    private val supportedProperties = setOf(
        "FREQ",
        "UNTIL",
        "COUNT",
        "INTERVAL",
        "BYSECOND",
        "BYMINUTE",
        "BYHOUR",
        "BYDAY",
        "BYMONTHDAY",
        "BYYEARDAY",
        "BYWEEKNO",
        "BYMONTH",
        "BYSETPOS",
        "WKST",
    )

    private fun fail(
        input: String,
        property: String? = null,
        invalidToken: String? = null,
        reason: RecurrenceErrorReason,
        position: Int? = null,
        detail: String,
    ): Nothing = throw RecurrenceParseException(
        inputValue = input,
        propertyName = property,
        invalidToken = invalidToken,
        reason = reason,
        position = position,
        detail = detail,
    )
}
