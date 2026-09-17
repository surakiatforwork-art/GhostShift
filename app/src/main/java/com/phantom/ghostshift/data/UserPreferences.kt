package com.phantom.ghostshift.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.phantom.ghostshift.domain.TimerState
import com.phantom.ghostshift.domain.ScheduleSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val TIMER_RUNNING = booleanPreferencesKey("timer_running")
        val TIMER_LOCKED = booleanPreferencesKey("timer_locked")
        val TIMER_START_AT = longPreferencesKey("timer_start_at")
        val TIMER_TARGET_AT = longPreferencesKey("timer_target_at")
        
        val ALARM_DUE_AT = longPreferencesKey("alarm_due_at")
        val ALARM_NEXT_TAG = stringPreferencesKey("alarm_next_tag")
        
        val UI_MIRROR = booleanPreferencesKey("ui_mirror")
        val UI_AUTO_WAKE = booleanPreferencesKey("ui_auto_wake")
        
        val SOUND_URI = stringPreferencesKey("sound_uri")
        val SOUND_SOURCE = stringPreferencesKey("sound_source")
        val IN_OUT_MIN = intPreferencesKey("in_out_min_minutes")
        val IN_OUT_MAX = intPreferencesKey("in_out_max_minutes")
        val OUT_IN_MIN = intPreferencesKey("out_in_min_minutes")
        val OUT_IN_MAX = intPreferencesKey("out_in_max_minutes")
        val AUTO_START_FIRST_DOWNLOAD = booleanPreferencesKey("auto_start_first_download")
    }

    val timerState: Flow<TimerState> = dataStore.data.map { prefs ->
        TimerState(
            running = prefs[Keys.TIMER_RUNNING] ?: false,
            locked = prefs[Keys.TIMER_LOCKED] ?: false,
            startAt = prefs[Keys.TIMER_START_AT],
            targetAt = prefs[Keys.TIMER_TARGET_AT]
        )
    }

    val alarmState: Flow<Triple<Long?, String?, String?>> = dataStore.data.map { prefs ->
        Triple(
            prefs[Keys.ALARM_DUE_AT],
            prefs[Keys.ALARM_NEXT_TAG],
            prefs[Keys.SOUND_URI]
        )
    }

    val mirrorPref: Flow<Boolean> = dataStore.data.map { prefs -> prefs[Keys.UI_MIRROR] ?: true }
    val soundPref: Flow<String?> = dataStore.data.map { prefs -> prefs[Keys.SOUND_URI] }
    val scheduleSettings: Flow<ScheduleSettings> = dataStore.data.map { prefs ->
        ScheduleSettings(
            inOutMinMinutes = prefs[Keys.IN_OUT_MIN] ?: 3,
            inOutMaxMinutes = prefs[Keys.IN_OUT_MAX]?.takeIf { it > 0 } ?: if (prefs.contains(Keys.IN_OUT_MAX)) null else 25,
            outInMinMinutes = prefs[Keys.OUT_IN_MIN] ?: 4,
            outInMaxMinutes = prefs[Keys.OUT_IN_MAX]?.takeIf { it > 0 } ?: if (prefs.contains(Keys.OUT_IN_MAX)) null else 30,
            autoStartOnFirstDownload = prefs[Keys.AUTO_START_FIRST_DOWNLOAD] ?: false
        ).normalized()
    }

    suspend fun saveTimerState(state: TimerState) {
        dataStore.edit { prefs ->
            prefs[Keys.TIMER_RUNNING] = state.running
            prefs[Keys.TIMER_LOCKED] = state.locked
            if (state.startAt != null) prefs[Keys.TIMER_START_AT] = state.startAt else prefs.remove(Keys.TIMER_START_AT)
            if (state.targetAt != null) prefs[Keys.TIMER_TARGET_AT] = state.targetAt else prefs.remove(Keys.TIMER_TARGET_AT)
        }
    }

    suspend fun saveAlarmState(dueAt: Long?, nextTag: String?) {
        dataStore.edit { prefs ->
            if (dueAt != null) prefs[Keys.ALARM_DUE_AT] = dueAt else prefs.remove(Keys.ALARM_DUE_AT)
            if (nextTag != null) prefs[Keys.ALARM_NEXT_TAG] = nextTag else prefs.remove(Keys.ALARM_NEXT_TAG)
        }
    }
    
    suspend fun setMirrorPref(mirror: Boolean) {
        dataStore.edit { it[Keys.UI_MIRROR] = mirror }
    }

    suspend fun setSoundUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri != null) prefs[Keys.SOUND_URI] = uri else prefs.remove(Keys.SOUND_URI)
        }
    }

    suspend fun saveScheduleSettings(settings: ScheduleSettings) {
        val normalized = settings.normalized()
        dataStore.edit { prefs ->
            prefs[Keys.IN_OUT_MIN] = normalized.inOutMinMinutes
            prefs[Keys.IN_OUT_MAX] = normalized.inOutMaxMinutes ?: 0
            prefs[Keys.OUT_IN_MIN] = normalized.outInMinMinutes
            prefs[Keys.OUT_IN_MAX] = normalized.outInMaxMinutes ?: 0
            prefs[Keys.AUTO_START_FIRST_DOWNLOAD] = normalized.autoStartOnFirstDownload
        }
    }
}

private fun ScheduleSettings.normalized(): ScheduleSettings {
    val inMin = inOutMinMinutes.coerceAtLeast(0)
    val outMin = outInMinMinutes.coerceAtLeast(0)
    return copy(
        inOutMinMinutes = inMin,
        inOutMaxMinutes = inOutMaxMinutes?.coerceAtLeast(inMin),
        outInMinMinutes = outMin,
        outInMaxMinutes = outInMaxMinutes?.coerceAtLeast(outMin)
    )
}
