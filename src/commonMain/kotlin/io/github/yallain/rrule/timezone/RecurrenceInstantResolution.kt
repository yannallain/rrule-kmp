package io.github.yallain.rrule

import kotlin.time.Instant

/**
 * Resolves this recurrence value to an instant when it belongs to an absolute temporal domain.
 *
 * UTC values are returned directly. Zoned values use [timeZoneResolver] and
 * [ambiguousTimePolicy], matching recurrence evaluation. An explicitly supplied value in a local
 * time gap uses [RecurrenceTimeZoneResolver.nonexistentInstant], which represents RFC 5545's
 * offset-before-the-gap rule. A resolver that cannot provide that fallback causes a
 * [RecurrenceValidationException]. Date-only and floating values return `null` because they do not
 * identify an instant without application-owned context.
 */
public fun RecurrenceDateTime.toInstantOrNull(
    timeZoneResolver: RecurrenceTimeZoneResolver = KotlinxRecurrenceTimeZoneResolver,
    ambiguousTimePolicy: AmbiguousTimePolicy = AmbiguousTimePolicy.EARLIER,
): Instant? = resolveRecurrenceInstant(
    value = this,
    resolver = timeZoneResolver,
    ambiguityPolicy = ambiguousTimePolicy,
    propertyName = "VALUE",
)

internal fun resolveRecurrenceInstant(
    value: RecurrenceDateTime,
    resolver: RecurrenceTimeZoneResolver,
    ambiguityPolicy: AmbiguousTimePolicy,
    propertyName: String,
): Instant? = when (value) {
    is RecurrenceDateTime.DateOnly, is RecurrenceDateTime.Floating -> null
    is RecurrenceDateTime.Utc -> value.instant
    is RecurrenceDateTime.Zoned -> try {
        when (val resolution = resolver.resolve(value.dateTime, value.timeZoneId)) {
            LocalTimeResolution.Nonexistent -> resolver.nonexistentInstant(value.dateTime, value.timeZoneId)
                ?: throw RecurrenceValidationException(
                    propertyName = propertyName,
                    invalidToken = value.toString(),
                    reason = RecurrenceErrorReason.INVALID_COMBINATION,
                    detail = "The timezone resolver cannot apply RFC gap semantics to $propertyName",
                )
            is LocalTimeResolution.Valid -> resolution.instant
            is LocalTimeResolution.Ambiguous -> when (ambiguityPolicy) {
                AmbiguousTimePolicy.EARLIER -> resolution.earlier
                AmbiguousTimePolicy.LATER -> resolution.later
            }
        }
    } catch (error: RecurrenceValidationException) {
        throw error
    } catch (error: IllegalArgumentException) {
        throw RecurrenceValidationException(
            propertyName = "TZID",
            invalidToken = value.timeZoneId,
            reason = RecurrenceErrorReason.MALFORMED_TOKEN,
            detail = "TZID could not be resolved: ${error.message.orEmpty()}",
        )
    }
}
