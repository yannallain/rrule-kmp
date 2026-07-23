package io.github.yallain.rrule

/** Machine-readable classification for strict parsing and validation failures. */
public enum class RecurrenceErrorReason {
    MISSING_REQUIRED_PROPERTY,
    DUPLICATE_PROPERTY,
    UNKNOWN_PROPERTY,
    EMPTY_VALUE,
    MALFORMED_TOKEN,
    OUT_OF_RANGE,
    MUTUALLY_EXCLUSIVE,
    INVALID_COMBINATION,
    INCOMPATIBLE_TEMPORAL_TYPE,
}

/** A failure while constructing or validating a recurrence model. */
public class RecurrenceValidationException(
    public val inputValue: String? = null,
    public val propertyName: String? = null,
    public val invalidToken: String? = null,
    public val reason: RecurrenceErrorReason,
    public val position: Int? = null,
    public val detail: String,
) : IllegalArgumentException(buildErrorMessage(propertyName, invalidToken, reason, position, detail))

/** A syntactic or semantic failure while parsing a rule value or recurrence content lines. */
public class RecurrenceParseException(
    public val inputValue: String,
    public val propertyName: String? = null,
    public val invalidToken: String? = null,
    public val reason: RecurrenceErrorReason,
    public val position: Int? = null,
    public val detail: String,
) : IllegalArgumentException(buildErrorMessage(propertyName, invalidToken, reason, position, detail))

private fun buildErrorMessage(
    propertyName: String?,
    invalidToken: String?,
    reason: RecurrenceErrorReason,
    position: Int?,
    detail: String,
): String = buildString {
    append(reason.name)
    propertyName?.let { append(" in ").append(it) }
    invalidToken?.let { append(": '").append(it).append('\'') }
    position?.let { append(" at index ").append(it) }
    append(". ").append(detail)
}
