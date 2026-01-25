package com.phantom.ghostshift

import org.junit.Test
import org.junit.Assert.*
import com.phantom.ghostshift.domain.SlotManager

class SlotManagerTest {
    @Test
    fun `test tag sequence IN OUT`() {
        assertEquals("OUT-1", SlotManager.nextTagInSequence("IN-1"))
        assertEquals("IN-2", SlotManager.nextTagInSequence("OUT-1"))
        assertEquals("OUT-2", SlotManager.nextTagInSequence("IN-2"))
        assertEquals("IN-21", SlotManager.nextTagInSequence("OUT-20"))
    }
    
    @Test
    fun `test invalid tag returns null`() {
        assertNull(SlotManager.nextTagInSequence("INVALID"))
        assertNull(SlotManager.nextTagInSequence(""))
    }
}
