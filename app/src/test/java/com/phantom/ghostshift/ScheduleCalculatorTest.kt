package com.phantom.ghostshift

import org.junit.Test
import org.junit.Assert.*
import com.phantom.ghostshift.domain.*
import com.phantom.ghostshift.domain.ScheduleCalculator.SolveResult

class ScheduleCalculatorTest {

    // Helper fake photo
    private fun p(tag: String, kind: Kind, downloaded: Boolean): SchedulePhoto {
        return SchedulePhoto(0, tag, kind.name, 0, downloaded, if (downloaded) 1000L else null)
    }

    @Test
    fun `test solveExactAB finds valid ranges`() {
        // total 100 min, 5 IN, 5 OUT -> mean 10 min
        val res = ScheduleCalculator.solveExactAB(100.0, 5, 5)
        assertTrue(res.ok)
        assertEquals(10.0, res.a!!, 0.1)
        assertEquals(10.0, res.b!!, 0.1)
    }

    @Test
    fun `test exact fit totalMs distribution`() {
        val now = 1000000L
        val target = now + (60 * 60 * 1000L) // 1 hour later
        
        // 4 gaps: 2 IN (3-25m), 2 OUT (4-30m)
        // photos: IN-1 (down), OUT-1 (ping), IN-2 (ping), OUT-2 (ping)
        // chain: OUT-1, IN-2, OUT-2
        val photos = listOf(
             p("IN-1", Kind.IN, true).copy(downloadedAt = now), // last
             p("OUT-1", Kind.OUT, false),
             p("IN-2", Kind.IN, false),
             p("OUT-2", Kind.OUT, false)
        )
        
        val timer = TimerState(true, false, now, target)
        val result = ScheduleCalculator.computeScheduleExactFit(photos, timer)
        
        assertTrue("Result should be ok", result.ok)
        assertNotNull(result.nextAt)
        
        // Check final time matches target
        val lastPlanTime = result.planAtByTag?.values?.maxOrNull()
        assertEquals("Final plan time must match target", target, lastPlanTime)
    }
    
    @Test
    fun `test base time logic uses max of lastDownload and startAt`() {
        val startAt = 2000000L
        val lastDownload = 1000000L // older than start
        val target = startAt + 100000L
        
        val photos = listOf(
             p("IN-1", Kind.IN, true).copy(downloadedAt = lastDownload),
             p("OUT-1", Kind.OUT, false)
        )
        
        val timer = TimerState(true, false, startAt, target)
        val result = ScheduleCalculator.computeScheduleExactFit(photos, timer)
        
        // The schedule should build upon startAt, not lastDownload
        val planTime = result.planAtByTag?.get("OUT-1")
        assertNotNull(planTime)
        assertTrue("Plan time should be > startAt", planTime!! > startAt)
    }
}
