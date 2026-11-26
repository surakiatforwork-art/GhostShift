package com.phantom.ghostshift.domain

/** A sliver of a photo is not enough to identify the target safely. */
object NextPhotoVisibility {
    fun isVisible(top: Float, bottom: Float, viewportTop: Float, viewportBottom: Float): Boolean {
        val height = bottom - top
        val viewportHeight = viewportBottom - viewportTop
        if (height <= 0f || viewportHeight <= 0f || !height.isFinite() || !viewportHeight.isFinite()) return false
        val visible = (minOf(bottom, viewportBottom) - maxOf(top, viewportTop)).coerceAtLeast(0f)
        return visible >= minOf(height, viewportHeight) * .5f
    }
}
