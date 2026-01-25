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
        val p1 = SchedulePhoto(
            id = 1,
            tag = "IN-1",
            kind = Kind.IN,
            idx = 1,
            downloaded = true,
            downloadedAt = 1000000L
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

        // Timer is running, started at 1,000,000. Target is 9h 10m later.
        // 9h 10m = 550 minutes.
        val startAt = 1000000L
        val targetAt = startAt + 550 * 60 * 1000L
        val timer = TimerState(
            running = true,
            locked = false,
            startAt = startAt,
            targetAt = targetAt
        )

        // Act
        val result = ScheduleCalculator.computeScheduleExactFit(photos, timer)

        // Assert
        assertTrue("Schedule should be OK when gate is open (downloaded >= 1)", result.ok)
        assertEquals("Next expected tag should be OUT-1", "OUT-1", result.nextTag)
    }
}
