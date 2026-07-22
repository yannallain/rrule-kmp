package io.github.yallain.rrule.apple

import io.github.yallain.rrule.RecurrenceParseException
import io.github.yallain.rrule.RecurrenceValidationException

/** Stable error categories exposed by the native Apple facade. */
public enum class AppleRecurrenceErrorCode {
    INVALID_CONTENT,
    UNSUPPORTED_TEMPORAL_DOMAIN,
    INVALID_ARGUMENT,
    RESULT_LIMIT_EXCEEDED,
    EVALUATION_FAILED,
}

/**
 * A recurrence failure that is safe to cross the Objective-C/Swift boundary.
 *
 * Every throwing facade entry point declares this exception with [Throws], so Swift receives a
 * normal `Error` instead of terminating when Kotlin reports invalid input or evaluation failure.
 */
public class AppleRecurrenceException internal constructor(
    public val code: AppleRecurrenceErrorCode,
    public val inputValue: String?,
    public val reason: String?,
    public val propertyName: String?,
    public val invalidToken: String?,
    public val position: Int?,
    public val maximumCount: Int?,
    message: String,
) : Exception(message)

internal fun invalidAppleRecurrenceArgument(message: String): Nothing =
    throw AppleRecurrenceException(
        code = AppleRecurrenceErrorCode.INVALID_ARGUMENT,
        inputValue = null,
        reason = null,
        propertyName = null,
        invalidToken = null,
        position = null,
        maximumCount = null,
        message = message,
    )

internal fun appleRecurrenceResultLimitExceeded(maximumCount: Int): Nothing =
    throw AppleRecurrenceException(
        code = AppleRecurrenceErrorCode.RESULT_LIMIT_EXCEEDED,
        inputValue = null,
        reason = null,
        propertyName = null,
        invalidToken = null,
        position = null,
        maximumCount = maximumCount,
        message = "More than $maximumCount occurrences match the requested window",
    )

internal inline fun <T> appleRecurrenceBoundary(
    fallbackCode: AppleRecurrenceErrorCode,
    operation: () -> T,
): T = try {
    operation()
} catch (error: AppleRecurrenceException) {
    throw error
} catch (error: RecurrenceParseException) {
    throw AppleRecurrenceException(
        code = AppleRecurrenceErrorCode.INVALID_CONTENT,
        inputValue = error.inputValue,
        reason = error.reason.name,
        propertyName = error.propertyName,
        invalidToken = error.invalidToken,
        position = error.position,
        maximumCount = null,
        message = error.message.orEmpty(),
    )
} catch (error: RecurrenceValidationException) {
    throw AppleRecurrenceException(
        code = fallbackCode,
        inputValue = error.inputValue,
        reason = error.reason.name,
        propertyName = error.propertyName,
        invalidToken = error.invalidToken,
        position = error.position,
        maximumCount = null,
        message = error.message.orEmpty(),
    )
} catch (error: IllegalArgumentException) {
    throw AppleRecurrenceException(
        code = fallbackCode,
        inputValue = null,
        reason = null,
        propertyName = null,
        invalidToken = null,
        position = null,
        maximumCount = null,
        message = error.message.orEmpty(),
    )
} catch (error: Exception) {
    throw AppleRecurrenceException(
        code = fallbackCode,
        inputValue = null,
        reason = null,
        propertyName = null,
        invalidToken = null,
        position = null,
        maximumCount = null,
        message = error.message ?: "Recurrence evaluation failed",
    )
}
