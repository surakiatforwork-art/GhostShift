package com.phantom.ghostshift

import org.junit.Test
import org.junit.Assert.*
import com.phantom.ghostshift.data.PhotoEntity

class UnlockLogicTest {

    // Mock minimal entity for testing logic
    private fun mk(tag: String, downloaded: Boolean): PhotoEntity {
        return PhotoEntity(
            id = 0, tag = tag, kind = if(tag.startsWith("IN")) "IN" else "OUT", 
            idx = tag.split("-").last().toIntOrNull() ?: 0,
            filePath = "", width = 0, height = 0, dateAdded = 0, 
            downloaded = downloaded, 
            downloadedAt = if (downloaded) System.currentTimeMillis() else null
        )
    }

    private fun computeContiguousExportedPairs(all: List<PhotoEntity>, maxPairs: Int): Int {
        val byTag = all.associateBy { it.tag }
        var count = 0
        for (i in 1..maxPairs) {
            val inE = byTag["IN-$i"]
            val outE = byTag["OUT-$i"]
            val ok = (inE?.downloadedAt != null) && (outE?.downloadedAt != null)
            if (ok) count++ else break
        }
        return count
    }

    @Test
    fun `test exact 20 pairs unlocks`() {
        val all = mutableListOf<PhotoEntity>()
        for (i in 1..20) {
            all.add(mk("IN-$i", true))
            all.add(mk("OUT-$i", true))
        }
        
        val pairs = computeContiguousExportedPairs(all, 20)
        assertEquals(20, pairs)
        assertTrue("Should account for all 20 pairs", pairs >= 20)
    }

    @Test
    fun `test missing pair breaks contiguous count`() {
        val all = mutableListOf<PhotoEntity>()
        // 1..5 present
        for (i in 1..5) {
            all.add(mk("IN-$i", true))
            all.add(mk("OUT-$i", true))
        }
        // 6 missing OUT
        all.add(mk("IN-6", true))
        all.add(mk("OUT-6", false)) 
        
        // 7..20 present
        for (i in 7..20) {
            all.add(mk("IN-$i", true))
            all.add(mk("OUT-$i", true))
        }

        val pairs = computeContiguousExportedPairs(all, 20)
        assertEquals(5, pairs) // Should stop at 5
    }

    @Test
    fun `test order does not matter for map lookup`() {
        val all = mutableListOf<PhotoEntity>()
        all.add(mk("OUT-1", true))
        all.add(mk("IN-1", true))
        
        val pairs = computeContiguousExportedPairs(all, 20)
        assertEquals(1, pairs)
    }
}
