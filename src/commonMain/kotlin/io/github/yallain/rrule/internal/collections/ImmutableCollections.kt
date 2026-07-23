package io.github.yallain.rrule.internal.collections

/**
 * Returns a detached list whose runtime type does not implement [MutableList].
 *
 * Kotlin's standard `toList()` is a defensive copy, but multi-element copies are commonly backed
 * by an `ArrayList`. That backing list can be recovered with an unchecked Kotlin cast or through
 * Java's collection interfaces. Keeping the copied storage behind [AbstractList] makes mutation
 * fail at the collection boundary on every supported target.
 */
internal fun <Element> immutableListCopyOf(elements: Iterable<Element>): List<Element> =
    ImmutableList(elements)

/**
 * Returns a detached set whose runtime type and iterator expose no mutable collection interface.
 */
internal fun <Element> immutableSetCopyOf(elements: Iterable<Element>): Set<Element> =
    ImmutableSet(elements)

private class ImmutableList<Element>(elements: Iterable<Element>) : AbstractList<Element>() {
    private val elements: List<Element> = elements.toList()

    override val size: Int
        get() = elements.size

    override fun get(index: Int): Element = elements[index]
}

private class ImmutableSet<Element>(elements: Iterable<Element>) : AbstractSet<Element>() {
    private val elements: Set<Element> = elements.toSet()

    override val size: Int
        get() = elements.size

    override fun contains(element: Element): Boolean = element in elements

    override fun iterator(): Iterator<Element> = ImmutableIterator(elements.iterator())
}

/** Hides a mutable backing iterator's optional mutation operation from callers. */
private class ImmutableIterator<out Element>(
    private val delegate: Iterator<Element>,
) : Iterator<Element> {
    override fun hasNext(): Boolean = delegate.hasNext()

    override fun next(): Element = delegate.next()
}
