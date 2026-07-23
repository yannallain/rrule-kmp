package io.github.yallain.rrule

/**
 * Applies RFC 5545's fallback for runtimes that cannot represent a leap second.
 *
 * Kotlin temporal types have no `:60` representation. RFC 5545 therefore recommends interpreting
 * second 60 as second 59. Normalizing selectors as a set also ensures that `59,60` represents one
 * candidate before `BYSETPOS` is evaluated.
 */
internal fun normalizeRfcSecond(second: Int): Int = if (second == 60) 59 else second

internal fun Set<Int>.normalizeRfcSeconds(): Set<Int> {
    if (60 !in this) return this
    return mapTo(linkedSetOf(), ::normalizeRfcSecond)
}
