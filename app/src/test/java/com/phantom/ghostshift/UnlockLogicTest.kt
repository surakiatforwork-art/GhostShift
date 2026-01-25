package com.phantom.ghostshift

import com.phantom.ghostshift.domain.Kind
import com.phantom.ghostshift.domain.SchedulePhoto
import com.phantom.ghostshift.domain.UnlockRules
import org.junit.Assert.*
import org.junit.Test

class UnlockLogicTest {

    @Test
    fun testUnlock_Contiguous20Pairs_ShouldReturn20() {
        // Arrange: Create contiguous pairs 1..20
        val photos = mutableListOf<SchedulePhoto>()
        for (i in 1..20) {
            photos.add(
                SchedulePhoto(
                    id = (i * 2 - 1).toLong(),
                    tag = "IN-$i",
                    kind = Kind.IN,
                    idx = i,
                    downloaded = true,
                    downloadedAt = 1000L + i
                )
            )
            photos.add(
                SchedulePhoto(
                    id = (i * 2).toLong(),
                    tag = "OUT-$i",
                    kind = Kind.OUT,
                    idx = i,
                    downloaded = true,
                    downloadedAt = 2000L + i
                )
            )
        }

        // Act
        val maxPair = UnlockRules.getUnlockMaxPairIfEligible(photos)

        // Assert
        assertEquals("Should unlock with maxPair 20 when 1..20 are complete", 20, maxPair)
    }

    @Test
    fun testUnlock_BrokenSequence_ShouldReturn0() {
        // Arrange: Create pairs 1..20 but SKIP pair 15 implicitly 
        // (or break continuity by missing 15)
        val photos = mutableListOf<SchedulePhoto>()
        for (i in 1..20) {
            if (i == 15) continue // Skip 15
            photos.add(
                SchedulePhoto(
                    id = (i * 2 - 1).toLong(),
                    tag = "IN-$i",
                    kind = Kind.IN,
                    idx = i,
                    downloaded = true,
                    downloadedAt = 1000L + i
                )
            )
            photos.add(
                SchedulePhoto(
                    id = (i * 2).toLong(),
                    tag = "OUT-$i",
                    kind = Kind.OUT,
                    idx = i,
                    downloaded = true,
                    downloadedAt = 2000L + i
                )
            )
        }

        // Act
        val maxPair = UnlockRules.getUnlockMaxPairIfEligible(photos)

        // Assert
        assertEquals("Should return 0 because sequence is not contiguous (missing 15)", 0, maxPair)
    }
}
