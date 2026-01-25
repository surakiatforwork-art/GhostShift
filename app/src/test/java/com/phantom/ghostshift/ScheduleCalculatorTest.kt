package com.phantom.ghostshift

import com.phantom.ghostshift.domain.Kind
import com.phantom.ghostshift.domain.ScheduleCalculator
import com.phantom.ghostshift.domain.SchedulePhoto
import com.phantom.ghostshift.domain.TimerState
import org.junit.Assert.*
import org.junit.Test

class ScheduleCalculatorTest {

    @Test
    fun testGateOpen_withDownloadedPhoto() {
        // Arrange: At least one photo downloaded (IN-1) to open the gate
        // And one pending photo (OUT-1) to be the next target
        
        // Use fixed time to avoid flakiness
        val baseTime = 1_700_000_000_000L // Approx year 2023
        val startAt = baseTime
        val downloadedAt = baseTime + 5 * 60 * 1000L // Downloaded 5 mins after start
        val targetAt = startAt + 550 * 60 * 1000L // Target 9h 10m (550m) from start

        val p1 = SchedulePhoto(
            id = 1,
            tag = "IN-1",
            kind = Kind.IN,
            idx = 1,
            downloaded = true,
            downloadedAt = downloadedAt
        )
        val p2 = SchedulePhoto(
            id = 2,
            tag = "OUT-1",
            kind = Kind.OUT,
            idx = 1,
            downloaded = false,
            downloadedAt = null
        )
        val photos = listOf(p1, p2)

        val timer = TimerState(
            running = true,
            locked = false,
            startAt = startAt,
            targetAt = targetAt
        )

        // Act
        val result = ScheduleCalculator.computeScheduleExactFit(photos, timer)

        // Assert
        // Logic: With only 1 pending photo (OUT-1) and ~9h remaining, it is IMPOSSIBLE to fill the time.
        // So result.ok MUST be false.
        // But we want to ensure it fails for the "Right Reason" (Time/Photos mismatch), not "Gate Closed".
        
        assertFalse("Should be false because photo chain is too short for 9h duration", result.ok)
        assertEquals("Next expected tag should be OUT-1", "OUT-1", result.nextTag)
        
        // Verify error message indicates "Not enough photos" rather than generic error
        val errorMsg = result.error
        assertTrue(
            "Error should indicate not enough photos or time mismatch. Actual: $errorMsg",
            errorMsg.contains("รูปไม่พอ") || errorMsg.contains("missing")
        )
        
        // Also verify nextAt is valid (should be > baseTime)
        assertNotNull(result.nextAt)
        assertTrue("nextAt should be after downloadedAt", result.nextAt!! >= downloadedAt)
    }
}
