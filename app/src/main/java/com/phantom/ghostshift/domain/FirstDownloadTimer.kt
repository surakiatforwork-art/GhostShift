package com.phantom.ghostshift.domain

/** Preserve a valid intentionally started timer; never carry an expired shift into IN-1. */
object FirstDownloadTimer {
    data class Decision(val timer: TimerState, val replaceTimer: Boolean, val clearExpiredOverride: Boolean)
    fun resolve(now: Long, running: Boolean, start: Long?, target: Long?, finishOverride: Long?, minutes: Int, autoStart: Boolean): Decision {
        val valid = running && start != null && start <= now && target != null && target > now && target > start
        val expiredOverride = finishOverride != null && finishOverride <= now
        if (valid) return Decision(TimerState(true, false, start, target), false, expiredOverride)
        val fresh = if (autoStart) TimerState(true, false, now, finishOverride?.takeIf { it > now } ?: now + minutes.coerceAtLeast(1) * 60_000L)
            else TimerState(false, false, null, null)
        return Decision(fresh, true, expiredOverride)
    }
}
