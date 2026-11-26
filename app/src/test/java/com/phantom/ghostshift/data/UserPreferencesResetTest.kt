package com.phantom.ghostshift.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.phantom.ghostshift.domain.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

// Exercise the real preference transforms without Android filesystem semantics
// (DataStore 1.0's file rename cannot replace a file on Windows JVM).
private class MemoryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())
    private val mutex = Mutex()
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(data.value).also { data.value = it } }
}

class UserPreferencesResetTest {
    @Test fun resetClearsEveryShiftClockButKeepsDurationAndMode() = runBlocking {
        val prefs = UserPreferences(MemoryPreferences())
        val now = System.currentTimeMillis()
        prefs.setTimerEnabled(true)
        prefs.setTargetMinutes(540)
        prefs.setFinishOverride(now + 7_200_000L)
        prefs.saveTimerState(TimerState(true, false, now, now + 7_200_000L))
        prefs.saveAlarmState(now + 60_000L, "OUT-1")
        prefs.recordNext(now - 5000L)
        prefs.resetShift()
        assertNull(prefs.lastNextAt.first())
        assertEquals(TimerState(false, false, null, null), prefs.timerState.first())
        assertNull(prefs.alarmState.first().first)
        assertNull(prefs.alarmState.first().second)
        assertNull(prefs.scheduleSettings.first().finishOverrideAt)
        assertEquals(540, prefs.scheduleSettings.first().targetMinutes)
        assertTrue(prefs.scheduleSettings.first().timerEnabled)
        prefs.recordSuccessfulNext(now, firstIn = true)
        assertEquals(now, prefs.lastNextAt.first())
        assertEquals(now, prefs.timerState.first().startAt)
        assertEquals(now + 540 * 60_000L, prefs.timerState.first().targetAt)
    }

    @Test fun disabledTimerStillRecordsElapsedAndResets() = runBlocking {
        val prefs = UserPreferences(MemoryPreferences())
        val now = System.currentTimeMillis()
        prefs.recordSuccessfulNext(now, firstIn = true)
        assertEquals(now, prefs.lastNextAt.first())
        assertFalse(prefs.timerState.first().running)
        prefs.resetShift()
        assertNull(prefs.lastNextAt.first())
    }
}
