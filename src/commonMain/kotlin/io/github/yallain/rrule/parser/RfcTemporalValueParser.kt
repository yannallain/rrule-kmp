package io.github.yallain.rrule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

/** Strict parsing primitives shared by RRULE `UNTIL` and recurrence content properties. */
internal object RfcTemporalValueParser {
    fun isDate(value: String): Boolean = datePattern.matches(value)

    fun isLocalDateTime(value: String): Boolean = localDateTimePattern.matches(value)

    fun isUtcDateTime(value: String): Boolean = utcDateTimePattern.matches(value)

    fun parseDate(value: String): LocalDate {
        require(isDate(value)) { "Expected an RFC 5545 DATE value" }
        return LocalDate(
            year = value.substring(0, 4).toInt(),
            month = value.substring(4, 6).toInt(),
            day = value.substring(6, 8).toInt(),
        )
    }

    fun parseLocalDateTime(value: String): LocalDateTime {
        require(isLocalDateTime(value)) { "Expected an RFC 5545 local DATE-TIME value" }
        val date = parseDate(value.substring(0, 8))
        return LocalDateTime(
            year = date.year,
            month = date.month.ordinal + 1,
            day = date.day,
            hour = value.substring(9, 11).toInt(),
            minute = value.substring(11, 13).toInt(),
            second = normalizeRfcSecond(value.substring(13, 15).toInt()),
        )
    }

    fun parseUtcDateTime(value: String): Instant {
        require(isUtcDateTime(value)) { "Expected an RFC 5545 UTC DATE-TIME value" }
        val local = parseLocalDateTime(value.dropLast(1))
        return Instant.parse(
            "${extendedDate(local)}T${two(local.hour)}:${two(local.minute)}:${two(local.second)}Z",
        )
    }

    private fun extendedDate(value: LocalDateTime): String =
        "${value.year.toString().padStart(4, '0')}-${two(value.month.ordinal + 1)}-${two(value.day)}"

    private fun two(value: Int): String = value.toString().padStart(2, '0')

    private val datePattern = Regex("^[0-9]{8}$")
    private val localDateTimePattern = Regex("^[0-9]{8}T[0-9]{6}$")
    private val utcDateTimePattern = Regex("^[0-9]{8}T[0-9]{6}Z$")
}
