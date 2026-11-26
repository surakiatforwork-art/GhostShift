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
        val TIMER_ENABLED = booleanPreferencesKey("timer_enabled")
        val TARGET_MINUTES = intPreferencesKey("target_minutes")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FINISH_OVERRIDE_AT = longPreferencesKey("finish_override_at")
        val LAST_NEXT_AT = longPreferencesKey("last_next_at")
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
        val REPEAT_ALARM_SOUND = booleanPreferencesKey("repeat_alarm_sound")
    }

    val themeMode: Flow<String> = dataStore.data.map { it[Keys.THEME_MODE]?.takeIf { mode -> mode in setOf("system", "dark", "light") } ?: "system" }

    suspend fun setThemeMode(mode: String) {
        require(mode in setOf("system", "dark", "light"))
        dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    val lastNextAt: Flow<Long?> = dataStore.data.map { it[Keys.LAST_NEXT_AT] }

    suspend fun recordNext(at: Long) { dataStore.edit { it[Keys.LAST_NEXT_AT] = at } }
    suspend fun recordSuccessfulNext(at: Long, firstIn: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_NEXT_AT] = at
            if (!firstIn || prefs[Keys.TIMER_ENABLED] != true) return@edit
            val running = prefs[Keys.TIMER_RUNNING] == true
            val start = prefs[Keys.TIMER_START_AT]
            val target = prefs[Keys.TIMER_TARGET_AT]
            val override = prefs[Keys.FINISH_OVERRIDE_AT]
            val decision = com.phantom.ghostshift.domain.FirstDownloadTimer.resolve(
                at, running, start, target, override, prefs[Keys.TARGET_MINUTES] ?: 550,
                prefs[Keys.AUTO_START_FIRST_DOWNLOAD] ?: true
            )
            if (decision.clearExpiredOverride) prefs.remove(Keys.FINISH_OVERRIDE_AT)
            if (decision.replaceTimer) {
                prefs[Keys.TIMER_RUNNING] = decision.timer.running
                prefs[Keys.TIMER_LOCKED] = false
                if (decision.timer.startAt != null) prefs[Keys.TIMER_START_AT] = decision.timer.startAt else prefs.remove(Keys.TIMER_START_AT)
                if (decision.timer.targetAt != null) prefs[Keys.TIMER_TARGET_AT] = decision.timer.targetAt else prefs.remove(Keys.TIMER_TARGET_AT)
                prefs.remove(Keys.ALARM_DUE_AT)
                prefs.remove(Keys.ALARM_NEXT_TAG)
            }
        }
    }

    suspend fun clearLastNext() { dataStore.edit { it.remove(Keys.LAST_NEXT_AT) } }

    // The fixed finish is for the current shift only; it never changes the default duration.
    suspend fun setFinishOverride(at: Long?) {
        dataStore.edit { prefs ->
            if (at != null && at <= System.currentTimeMillis()) return@edit
            if (at == null) prefs.remove(Keys.FINISH_OVERRIDE_AT) else prefs[Keys.FINISH_OVERRIDE_AT] = at
            val start = prefs[Keys.TIMER_START_AT]
            if (prefs[Keys.TIMER_ENABLED] == true && prefs[Keys.TIMER_RUNNING] == true && start != null) {
                prefs[Keys.TIMER_TARGET_AT] = at ?: (start + (prefs[Keys.TARGET_MINUTES] ?: 550) * 60_000L)
            } else if (at == null) {
                prefs.remove(Keys.TIMER_TARGET_AT)
            } else {
                prefs[Keys.TIMER_TARGET_AT] = at
            }
        }
    }

    suspend fun resetShift() {
        dataStore.edit { prefs ->
            prefs[Keys.TIMER_RUNNING] = false
            prefs[Keys.TIMER_LOCKED] = false
            prefs.remove(Keys.TIMER_START_AT)
            prefs.remove(Keys.TIMER_TARGET_AT)
            prefs.remove(Keys.FINISH_OVERRIDE_AT)
            prefs.remove(Keys.ALARM_DUE_AT)
            prefs.remove(Keys.ALARM_NEXT_TAG)
            prefs.remove(Keys.LAST_NEXT_AT)
        }
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
            autoStartOnFirstDownload = prefs[Keys.AUTO_START_FIRST_DOWNLOAD] ?: true,
            // Existing installations default to one play, preventing an unexpected endless alarm.
            repeatAlarmSound = prefs[Keys.REPEAT_ALARM_SOUND] ?: false,
            timerEnabled = prefs[Keys.TIMER_ENABLED] ?: false,
            targetMinutes = prefs[Keys.TARGET_MINUTES] ?: 550,
            finishOverrideAt = prefs[Keys.FINISH_OVERRIDE_AT]
        ).normalized()
    }

    suspend fun setTimerEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val wasEnabled = prefs[Keys.TIMER_ENABLED] == true
            prefs[Keys.TIMER_ENABLED] = enabled
            if (!enabled || !wasEnabled) {
                prefs[Keys.TIMER_RUNNING] = false
                prefs[Keys.TIMER_LOCKED] = false
                prefs.remove(Keys.TIMER_START_AT)
                prefs.remove(Keys.TIMER_TARGET_AT)
                prefs.remove(Keys.ALARM_DUE_AT)
                prefs.remove(Keys.ALARM_NEXT_TAG)
            }
        }
    }

    suspend fun setTargetMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.TARGET_MINUTES] = minutes
            val start = prefs[Keys.TIMER_START_AT]
            if (prefs[Keys.TIMER_ENABLED] == true && prefs[Keys.TIMER_RUNNING] == true && start != null) {
                if (prefs[Keys.FINISH_OVERRIDE_AT] == null) {
                    prefs[Keys.TIMER_TARGET_AT] = start + minutes * 60_000L
                }
            }
        }
    }

    suspend fun saveTimerState(state: TimerState) {
        dataStore.edit { prefs ->
            if (state.running && prefs[Keys.TIMER_ENABLED] != true) return@edit
            prefs[Keys.TIMER_RUNNING] = state.running
            prefs[Keys.TIMER_LOCKED] = state.locked
            if (state.startAt != null) prefs[Keys.TIMER_START_AT] = state.startAt else prefs.remove(Keys.TIMER_START_AT)
            if (state.targetAt != null) prefs[Keys.TIMER_TARGET_AT] = state.targetAt else prefs.remove(Keys.TIMER_TARGET_AT)
        }
    }

    suspend fun saveAlarmState(dueAt: Long?, nextTag: String?) {
        dataStore.edit { prefs ->
            if (dueAt != null && prefs[Keys.TIMER_ENABLED] != true) return@edit
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
            prefs[Keys.REPEAT_ALARM_SOUND] = normalized.repeatAlarmSound
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
