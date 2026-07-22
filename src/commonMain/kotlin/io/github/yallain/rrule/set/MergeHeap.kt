package io.github.yallain.rrule

internal class MergeHeap<T>(private val comparator: Comparator<T>) {
    private val values = mutableListOf<T>()

    val isNotEmpty: Boolean get() = values.isNotEmpty()

    fun add(value: T) {
        values += value
        var index = values.lastIndex
        while (index > 0) {
            val parent = (index - 1) / 2
            if (comparator.compare(values[parent], values[index]) <= 0) break
            values.swap(parent, index)
            index = parent
        }
    }

    fun removeFirst(): T {
        check(values.isNotEmpty())
        val first = values.first()
        val last = values.removeAt(values.lastIndex)
        if (values.isNotEmpty()) {
            values[0] = last
            var index = 0
            while (true) {
                val left = index * 2 + 1
                if (left >= values.size) break
                val right = left + 1
                val smallest = if (right < values.size && comparator.compare(values[right], values[left]) < 0) {
                    right
                } else {
                    left
                }
                if (comparator.compare(values[index], values[smallest]) <= 0) break
                values.swap(index, smallest)
                index = smallest
            }
        }
        return first
    }

    private fun MutableList<T>.swap(first: Int, second: Int) {
        val value = this[first]
        this[first] = this[second]
        this[second] = value
    }
}
