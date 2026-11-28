package com.phantom.ghostshift.domain

/** Only for explicit additions/replacements, never Download Next or alarms. */
internal object AddedCardScrollTarget {
    fun distance(top: Int, size: Int, start: Int, end: Int, centerRequested: Boolean = false): Float {
        val height = (end - start).toFloat()
        if (height <= 0 || size <= 0) return 0f
        val center = top + size / 2f
        val zoneStart = start + height * .20f
        val zoneEnd = start + height * .60f
        val fits = size <= height
        val fullyVisible = top >= start && top + size <= end
        if (!centerRequested && center in zoneStart..zoneEnd && (fullyVisible || !fits)) return 0f
        // Prefer 40%, but keep an entire card visible if it fits in the viewport.
        val desired = start + height * .40f
        val target = if (fits) desired.coerceIn(start + size / 2f, end - size / 2f) else desired
        return center - target
    }

    fun tailPadding(viewport: Int, original: Int): Int =
        maxOf(original, (viewport.coerceAtLeast(0) * .60f).toInt())
}
