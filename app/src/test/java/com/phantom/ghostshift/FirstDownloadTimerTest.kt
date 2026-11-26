package com.phantom.ghostshift

import com.phantom.ghostshift.domain.FirstDownloadTimer
import com.phantom.ghostshift.domain.TimerState
import org.junit.Assert.*
import org.junit.Test

class FirstDownloadTimerTest {
    private val hour = 3_600_000L
    private val now = 32 * hour + 30 * 60_000L // 08:30 next day

    @Test fun expiredNightShiftStartsAtFirstDownload() {
        val oldStart = 20 * hour
        val oldTarget = 29 * hour // 05:00 next day
        val d = FirstDownloadTimer.resolve(now, true, oldStart, oldTarget, oldTarget, 550, true)
        assertTrue(d.replaceTimer)
        assertTrue(d.clearExpiredOverride)
        assertEquals(TimerState(true, false, now, now + 550 * 60_000L), d.timer)
    }

    @Test fun freshShiftStartsFromSuccessfulDownload() {
        val d = FirstDownloadTimer.resolve(now, false, null, null, null, 550, true)
        assertEquals(now, d.timer.startAt)
        assertEquals(now + 550 * 60_000L, d.timer.targetAt)
    }

    @Test fun preserveManualStartAndFutureFinish() {
        val d = FirstDownloadTimer.resolve(now, true, now - 15 * 60_000L, now + 8 * hour, null, 550, true)
        assertFalse(d.replaceTimer)
        val fresh = FirstDownloadTimer.resolve(now, false, null, null, now + 7 * hour, 550, true)
        assertEquals(now + 7 * hour, fresh.timer.targetAt)
    }

    @Test fun autoStartDisabledClearsExpiredTimerWithoutStarting() {
        val d = FirstDownloadTimer.resolve(now, true, now - 10 * hour, now - hour, now - hour, 550, false)
        assertTrue(d.replaceTimer)
        assertTrue(d.clearExpiredOverride)
        assertEquals(TimerState(false, false, null, null), d.timer)
    }

    @Test fun invalidFutureStartDoesNotCarryForward() {
        val d = FirstDownloadTimer.resolve(now, true, now + hour, now + 8 * hour, null, 550, true)
        assertEquals(now, d.timer.startAt)
        assertTrue(d.replaceTimer)
    }
}
