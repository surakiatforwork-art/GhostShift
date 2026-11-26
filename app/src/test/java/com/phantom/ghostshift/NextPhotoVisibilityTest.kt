package com.phantom.ghostshift

import com.phantom.ghostshift.domain.NextPhotoVisibility
import org.junit.Assert.*
import org.junit.Test

class NextPhotoVisibilityTest {
    @Test fun fullyVisible() { assertTrue(NextPhotoVisibility.isVisible(200f, 400f, 100f, 600f)) }
    @Test fun aboveAndBelow() {
        assertFalse(NextPhotoVisibility.isVisible(-100f, 100f, 100f, 600f))
        assertFalse(NextPhotoVisibility.isVisible(600f, 800f, 100f, 600f))
    }
    @Test fun onlySliverOrMeaningfulPortion() {
        assertFalse(NextPhotoVisibility.isVisible(570f, 770f, 100f, 600f))
        assertTrue(NextPhotoVisibility.isVisible(480f, 680f, 100f, 600f))
        assertTrue(NextPhotoVisibility.isVisible(0f, 200f, 100f, 600f))
    }
    @Test fun smallViewportAndInvalidDimensions() {
        assertTrue(NextPhotoVisibility.isVisible(0f, 1000f, 200f, 300f))
        assertFalse(NextPhotoVisibility.isVisible(200f, 200f, 100f, 600f))
        assertFalse(NextPhotoVisibility.isVisible(200f, 400f, 100f, 100f))
    }
}
