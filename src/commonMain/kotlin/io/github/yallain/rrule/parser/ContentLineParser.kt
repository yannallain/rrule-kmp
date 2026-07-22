package io.github.yallain.rrule

/** Lexes, unfolds, and validates the property/parameter structure of iCalendar content lines. */
internal object ContentLineParser {
    fun parse(input: String): List<ParsedContentLine> = unfold(input).map { line ->
        parseLine(input, line)
    }

    private fun parseLine(input: String, line: LogicalContentLine): ParsedContentLine {
        val colon = delimiterIndexesOutsideQuotes(line.text, ':').firstOrNull()
        if (colon == null || colon <= 0) {
            contentParseFailure(
                input = input,
                invalidToken = line.text,
                reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                position = line.sourcePosition(0),
                detail = "A content line must contain an unquoted ':' separator",
            )
        }
        val header = line.text.substring(0, colon)
        val headerParts = splitOutsideQuotes(header, ';')
        val rawName = headerParts.first().text
        if (!contentNamePattern.matches(rawName)) {
            contentParseFailure(
                input = input,
                invalidToken = rawName,
                reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                position = line.sourcePosition(0),
                detail = "A content line property name must be an RFC 5545 name token",
            )
        }
        val parameters = parseParameters(input, line, headerParts.drop(1))
        val rawValue = line.text.substring(colon + 1)
        if (rawValue.isEmpty()) {
            contentParseFailure(
                input = input,
                property = rawName.uppercase(),
                invalidToken = rawValue,
                reason = RecurrenceErrorReason.EMPTY_VALUE,
                position = line.sourcePosition(colon + 1),
                detail = "${rawName.uppercase()} must not be empty",
            )
        }
        return ParsedContentLine(
            name = rawName.uppercase(),
            rawName = rawName,
            parameters = parameters,
            value = rawValue,
            start = line.sourcePosition(0),
            valueSourcePositions = (0..rawValue.length).map { relativeIndex ->
                line.sourcePosition(colon + 1 + relativeIndex)
            },
        )
    }

    private fun parseParameters(
        input: String,
        line: LogicalContentLine,
        parts: List<HeaderToken>,
    ): List<ContentParameter> {
        val result = mutableListOf<ContentParameter>()
        for (part in parts) {
            val equals = delimiterIndexesOutsideQuotes(part.text, '=').firstOrNull()
            if (equals == null || equals <= 0) {
                contentParseFailure(
                    input = input,
                    invalidToken = part.text,
                    reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                    position = line.sourcePosition(part.start),
                    detail = "A content-line parameter must contain an unquoted '=' separator",
                )
            }
            val rawName = part.text.substring(0, equals)
            if (!contentNamePattern.matches(rawName)) {
                contentParseFailure(
                    input = input,
                    invalidToken = rawName,
                    reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                    position = line.sourcePosition(part.start),
                    detail = "A content-line parameter name must be an RFC 5545 name token",
                )
            }
            val name = rawName.uppercase()
            val rawValue = part.text.substring(equals + 1)
            val valueTokens = splitOutsideQuotes(rawValue, ',')
            val values = mutableListOf<String>()
            val valueStarts = mutableListOf<Int>()
            for (token in valueTokens) {
                val valueStart = line.sourcePosition(part.start + equals + 1 + token.start)
                if (token.text.isEmpty()) {
                    contentParseFailure(
                        input = input,
                        property = name,
                        invalidToken = token.text,
                        reason = RecurrenceErrorReason.EMPTY_VALUE,
                        position = valueStart,
                        detail = "$name must not contain an empty parameter value",
                    )
                }
                values += parseParameterValue(
                    input = input,
                    line = line,
                    value = token.text,
                    logicalStart = part.start + equals + 1 + token.start,
                )
                valueStarts += valueStart
            }
            result += ContentParameter(
                name = name,
                rawName = rawName,
                values = values,
                start = line.sourcePosition(part.start),
                valueStarts = valueStarts,
            )
        }
        return result
    }

    private fun parseParameterValue(
        input: String,
        line: LogicalContentLine,
        value: String,
        logicalStart: Int,
    ): String {
        val position = line.sourcePosition(logicalStart)
        val quoted = value.startsWith('"')
        if ('"' in value &&
            (!quoted || value.length < 2 || !value.endsWith('"') || '"' in value.drop(1).dropLast(1))
        ) {
            contentParseFailure(
                input = input,
                invalidToken = value,
                reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                position = position,
                detail = "A quoted parameter value must have exactly one matching pair of double quotes",
            )
        }
        val parsed = if (quoted) value.substring(1, value.lastIndex) else value
        val invalidIndex = parsed.indexOfFirst { character ->
            if (quoted) !character.isQuotedSafe() else !character.isParameterSafe()
        }
        if (invalidIndex >= 0) {
            contentParseFailure(
                input = input,
                invalidToken = parsed[invalidIndex].toString(),
                reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                position = line.sourcePosition(logicalStart + invalidIndex + if (quoted) 1 else 0),
                detail = "A content-line parameter contains a character forbidden by RFC 5545",
            )
        }
        return parsed
    }

    private fun Char.isParameterSafe(): Boolean =
        this == ' ' || this == '\t' || code == 0x21 || code in 0x23..0x2B ||
            code in 0x2D..0x39 || code in 0x3C..0x7E || code >= 0x80

    private fun Char.isQuotedSafe(): Boolean =
        this == ' ' || this == '\t' || code == 0x21 || code in 0x23..0x7E || code >= 0x80

    private fun unfold(input: String): List<LogicalContentLine> {
        val result = mutableListOf<LogicalContentLine>()
        var continuationAllowed = false
        for (line in physicalLines(input)) {
            if (line.text.startsWith(' ') || line.text.startsWith('\t')) {
                val previous = result.lastOrNull().takeIf { continuationAllowed } ?: contentParseFailure(
                    input = input,
                    invalidToken = line.text,
                    reason = RecurrenceErrorReason.MALFORMED_TOKEN,
                    position = line.start,
                    detail = "A folded continuation requires a preceding content line",
                )
                previous.appendContinuation(line)
            } else if (line.text.isNotEmpty()) {
                result += LogicalContentLine.from(line)
                continuationAllowed = true
            } else {
                continuationAllowed = false
            }
        }
        return result
    }

    private fun physicalLines(input: String): List<PhysicalLine> = buildList {
        var start = 0
        var cursor = 0
        while (cursor < input.length) {
            if (input[cursor] == '\r' || input[cursor] == '\n') {
                add(PhysicalLine(input.substring(start, cursor), start))
                if (input[cursor] == '\r' && cursor + 1 < input.length && input[cursor + 1] == '\n') cursor++
                cursor++
                start = cursor
            } else {
                cursor++
            }
        }
        if (start < input.length) add(PhysicalLine(input.substring(start), start))
    }

    private fun splitOutsideQuotes(value: String, delimiter: Char): List<HeaderToken> = buildList {
        var quoted = false
        var start = 0
        value.forEachIndexed { index, character ->
            if (character == '"') quoted = !quoted
            if (character == delimiter && !quoted) {
                add(HeaderToken(value.substring(start, index), start))
                start = index + 1
            }
        }
        add(HeaderToken(value.substring(start), start))
    }

    private fun delimiterIndexesOutsideQuotes(value: String, delimiter: Char): List<Int> = buildList {
        var quoted = false
        value.forEachIndexed { index, character ->
            if (character == '"') quoted = !quoted
            if (character == delimiter && !quoted) add(index)
        }
    }

    private data class PhysicalLine(val text: String, val start: Int)

    private data class LogicalContentLine(
        var text: String,
        val sourcePositions: MutableList<Int>,
    ) {
        fun appendContinuation(line: PhysicalLine) {
            text += line.text.drop(1)
            for (index in 1 until line.text.length) sourcePositions += line.start + index
        }

        fun sourcePosition(logicalIndex: Int): Int =
            sourcePositions.getOrNull(logicalIndex)
                ?: sourcePositions.lastOrNull()?.plus(1)
                ?: 0

        companion object {
            fun from(line: PhysicalLine): LogicalContentLine = LogicalContentLine(
                text = line.text,
                sourcePositions = line.text.indices.mapTo(mutableListOf()) { line.start + it },
            )
        }
    }

    private data class HeaderToken(val text: String, val start: Int)

    private val contentNamePattern = Regex("^[A-Za-z0-9-]+$")
}

internal data class ContentParameter(
    val name: String,
    val rawName: String,
    val values: List<String>,
    val start: Int,
    val valueStarts: List<Int>,
) {
    val value: String get() = values.single()
    val valueStart: Int get() = valueStarts.first()
}

internal fun ContentParameter.isExtensionParameter(): Boolean =
    name.startsWith("X-") || name !in standardParameterNames

private val standardParameterNames = setOf(
    "ALTREP",
    "CN",
    "CUTYPE",
    "DELEGATED-FROM",
    "DELEGATED-TO",
    "DIR",
    "ENCODING",
    "FMTTYPE",
    "FBTYPE",
    "LANGUAGE",
    "MEMBER",
    "PARTSTAT",
    "RANGE",
    "RELATED",
    "RELTYPE",
    "ROLE",
    "RSVP",
    "SENT-BY",
    "TZID",
    "VALUE",
)

internal data class ParsedContentLine(
    val name: String,
    val rawName: String,
    val parameters: List<ContentParameter>,
    val value: String,
    val start: Int,
    private val valueSourcePositions: List<Int>,
) {
    val valueStart: Int get() = valueSourcePositions.first()

    fun sourcePositionInValue(relativeIndex: Int): Int =
        valueSourcePositions.getOrElse(relativeIndex) { valueSourcePositions.last() }
}

internal fun contentParseFailure(
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
