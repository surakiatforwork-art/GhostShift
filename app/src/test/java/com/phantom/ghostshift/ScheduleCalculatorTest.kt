package com.phantom.ghostshift

import com.phantom.ghostshift.domain.Kind
import com.phantom.ghostshift.domain.ScheduleCalculator
import com.phantom.ghostshift.domain.SchedulePhoto
import com.phantom.ghostshift.domain.TimerState
import org.junit.Assert.*
import org.junit.Test

class ScheduleCalculatorTest {

    // Helper to create dummy photos
    private fun createPhoto(
        id: Long, 
        tag: String, 
        kind: Kind, 
        downloaded: Boolean, 
        downloadedAt: Long? = nullRun ./gradlew testDebugUnitTest
Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain
Caused by: java.lang.ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain
Error: Process completed with exit code 1.aded, downloadedAt)
    }

    @Test
    fun testGateClosed_withoutAnyExportedPhoto() {
        // Web Rule T1: Schedule calculation MUST run even if gate is closed (no exported photos).
        // Gate logic for Alarm is handled in MainViewModel (gateOpen check).
        
        // Arrange: No downloaded photos, but enough pending to form a chain?
        // Let's provide IN-1 (pending), OUT-1 (pending).
        val p1 = createPhoto(1, "IN-1", Kind.IN, false, null)
        val p2 = createPhoto(2, "OUT-1", Kind.OUT, false, null)
        val photos = listOf(p1, p2)

        val timer = TimerState(true, false, 1_700_000_000_000L, 1_700_000_000_000L + 20 * 60 * 1000L) // 20 min target

        // Act
        val result = ScheduleCalculator.computeScheduleExactFit(photos, timer)

        // Assert
        assertTrue("Schedule should SUCCEED in calculation even if gate is closed (no exported photos)", result.ok)
        assertEquals("IN-1", result.nextTag)
    }

    @Test
    fun testGateOpen_withDownloadedPhoto_InsufficientPhotos() {
        val baseTime = 1_700_000_000_000L
        val startAt = baseTime
        val downloadedAt = baseTime + 300_000L // 5 min
        val targetAt = startAt + 550 * 60 * 1000L // 9h 10m

        val p1 = createPhoto(1, "IN-1", Kind.IN, true, downloadedAt)
        val p2 = createPhoto(2, "OUT-1", Kind.OUT, false, null)
        
        val photos = listOf(p1, p2)
        val timer = TimerState(true, false, startAt, targetAt)

        val result = ScheduleCalculator.computeScheduleExactFit(photos, timer)

        // Assert: insufficient photos
        assertFalse("Should be false because photo chain is too short", result.ok)
        val err = result.error
        assertTrue("Error should be about missing photos or time. Got: $err", 
            err.contains("ขาดรูป") || err.contains("รูปไม่พอ") || err.contains("missing"))
    }

    @Test
    fun testGateOpen_withDownloadedPhoto_SufficientPhotos() {
        // Shorten the target duration so we don't need 100 photos
        // Start: 0
        // Downloaded (IN-1): 5m
        // Target: 20m from start (so 15m remaining).
        // Chain: OUT-1 (pending), IN-2 (pending).
        
        // IN-1 (downloaded).
        // Gap1 (for IN-1): 3..25m.
        // OUT-1 (pending). Gap2 (for OUT-1): 4..30m.
        // IN-2 (pending).
        
        // Remaining time = 15m.
        // Gap1 covers IN-1.
        // Gap2 covers OUT-1.
        // Time is distributed.
        // 15m is within range. Min = 3+4=7. Max = 25+30=55.
        // So 15m is solvable.

        val baseTime = 1_700_000_000_000L
        val startAt = baseTime
        val downloadedAt = baseTime + 5 * 60 * 1000L // +5m
        val targetAt = baseTime + 20 * 60 * 1000L // +20m
        
        // Total time to fill = 20 - 5 = 15m.
        
        val p1 = createPhoto(1, "IN-1", Kind.IN, true, downloadedAt)
        val p2 = createPhoto(2, "OUT-1", Kind.OUT, false, null)
        val p3 = createPhoto(3, "IN-2", Kind.IN, false, null)
        
        val photos = listOf(p1, p2, p3)
        val timer = TimerState(true, false, startAt, targetAt)

        val result = ScheduleCalculator.computeScheduleExactFit(photos, timer)

        assertTrue("Should be OK with sufficient photos. Error: ${result.error}", result.ok)
        assertEquals("Next tag should be OUT-1", "OUT-1", result.nextTag)
        assertNotNull(result.nextAt)
        assertTrue("Next at should be > downloadedAt", result.nextAt!! > downloadedAt)
    }
}
