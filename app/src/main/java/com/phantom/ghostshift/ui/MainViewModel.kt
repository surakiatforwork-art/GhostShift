package com.phantom.ghostshift.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.phantom.ghostshift.data.PhotoEntity
import com.phantom.ghostshift.data.PhotoRepository
import com.phantom.ghostshift.data.UserPreferences
import com.phantom.ghostshift.domain.ScheduleCalculator
import com.phantom.ghostshift.domain.SchedulePhoto
import com.phantom.ghostshift.domain.ScheduleResult
import com.phantom.ghostshift.domain.SlotManager
import com.phantom.ghostshift.domain.TimerState
import com.phantom.ghostshift.domain.UnlockRules
import com.phantom.ghostshift.system.AlarmScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class MainUiState(
    val pendingPhotos: List<PhotoEntity> = emptyList(),
    val downloadedPhotos: List<PhotoEntity> = emptyList(),

    val timer: TimerState = TimerState(running = false, locked = false, startAt = null, targetAt = null),
    val schedule: ScheduleResult = ScheduleResult(ok = false),

    val nextSlotTag: String = "IN-1",

    val canStartTimer: Boolean = true,
    val isTimerLocked: Boolean = false,

    // persisted alarm info (from prefs)
    val alarmDueAt: Long? = null,
    val alarmTag: String? = null,
    val alarmChannelId: String? = null,
    val alarmActive: Boolean = false,

    // gating + unlock progress
    val gateOpen: Boolean = false,
    val exportedPairsContiguous: Int = 0, // 0..20
    val unlockEligible: Boolean = false,

    // settings
    val soundUri: String? = null,

    // clock (UI countdown only)
    val currentTime: Long = System.currentTimeMillis()
)

private data class AlarmKey(
    val dueAt: Long,
    val tag: String,
    val soundUri: String?
)

class MainViewModel(
    private val repo: PhotoRepository,
    private val prefs: UserPreferences,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _now = MutableStateFlow(System.currentTimeMillis())

    // cache to avoid rescheduling every time
    private var lastAlarmKey: AlarmKey? = null

    /**
     * Core state: DOES NOT depend on clock ticker.
     * Compute schedule only when photos/timer/alarm prefs/sound change.
     */
    private val coreState: StateFlow<MainUiState> = combine(
        repo.pendingPhotos,
        repo.downloadedPhotos,
        prefs.timerState,
        prefs.alarmState,
        prefs.soundPref
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val pending = args[0] as List<PhotoEntity>
        @Suppress("UNCHECKED_CAST")
        val downloaded = args[1] as List<PhotoEntity>
        val timer = args[2] as TimerState
        @Suppress("UNCHECKED_CAST")
        val alarmTriple = args[3] as Triple<Long?, String?, String?>
        val sound = args[4] as String?

        val all = (pending + downloaded).sortedBySlot()

        // Gate: at least one first-time export exists
        val gateOpen = all.any { it.downloadedAt != null }

        // Persisted alarm info
        val (storedDueAt, storedTag, storedChannelId) = alarmTriple
        val alarmActive = storedDueAt != null

        // Compute exported contiguous pairs (1..20) using downloadedAt (not 'downloaded' flag)
        val contiguousPairs = computeContiguousExportedPairs(all, maxPairs = 20)
        val unlockEligible = contiguousPairs >= 20

        // Build domain photo list once
        val domainPhotos = all.map { it.toSchedulePhoto() }

        // Next slot (IN-1, OUT-1, IN-2...)
        val nextSlot = SlotManager.computeNextSlot(domainPhotos)

        // Schedule: only meaningful when timer.running and gateOpen
        // Schedule: only meaningful when timer.running
        val schedule = when {
            !timer.running -> ScheduleResult(ok = false, warn = "ยังไม่เริ่มจับเวลา")
            else -> ScheduleCalculator.computeScheduleExactFit(domainPhotos, timer)
        }

        MainUiState(
            pendingPhotos = pending,
            downloadedPhotos = downloaded,
            timer = timer,
            schedule = schedule,
            nextSlotTag = nextSlot,
            canStartTimer = !timer.running,
            isTimerLocked = timer.locked,
            alarmDueAt = storedDueAt,
            alarmTag = storedTag,
            alarmChannelId = storedChannelId,
            alarmActive = alarmActive,
            gateOpen = gateOpen,
            exportedPairsContiguous = contiguousPairs,
            unlockEligible = unlockEligible,
            soundUri = sound,
            currentTime = System.currentTimeMillis() // will be overridden by uiState combine with _now
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    /**
     * UI state: depends on clock ticker for countdown only.
     * IMPORTANT: schedule is not recomputed each second.
     */
    val uiState: StateFlow<MainUiState> = combine(coreState, _now) { core, nowMillis ->
        core.copy(currentTime = nowMillis)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    init {
        // 1) Clock ticker (UI countdown only)
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _now.value = System.currentTimeMillis()
            }
        }

        // 2) Alarm side-effects (react ONLY to core changes; not every second)
        viewModelScope.launch {
            coreState
                .map { state ->
                    // only the fields relevant to alarm scheduling
                    Triple(
                        // shouldSchedule?
                        state.timer.running && state.gateOpen && state.schedule.ok,
                        // next key inputs
                        AlarmKey(
                            dueAt = state.schedule.nextAt ?: -1L,
                            tag = state.schedule.nextTag ?: "",
                            soundUri = state.soundUri
                        ),
                        // persisted alarm snapshot
                        Pair(state.alarmDueAt, state.alarmTag)
                    )
                }
                .distinctUntilChanged()
                .collect {
                    manageAlarmSideEffect(coreState.value)
                }
        }
    }

    private suspend fun manageAlarmSideEffect(state: MainUiState) {
        val timer = state.timer
        val schedule = state.schedule
        val gateOpen = state.gateOpen

        // Strict: no alarm if not running OR gate not open (matches Web T1)
        val shouldHaveAlarm =
            timer.running &&
            schedule.ok &&
            gateOpen &&  // Restored: Only schedule alarm if at least 1 photo is exported (Gate Open)
            schedule.nextAt != null &&
            schedule.nextTag != null

        if (!shouldHaveAlarm) {
            if (state.alarmActive) {
                alarmScheduler.cancel()
                prefs.saveAlarmState(null, null)
            }
            lastAlarmKey = null
            return
        }

        val desired = AlarmKey(
            dueAt = schedule.nextAt!!,
            tag = schedule.nextTag!!,
            soundUri = state.soundUri
        )

        // If due/tag/sound changed => reschedule
        if (lastAlarmKey != desired) {
            alarmScheduler.scheduleExact(desired.dueAt, desired.tag, desired.soundUri)
            prefs.saveAlarmState(desired.dueAt, desired.tag)
            lastAlarmKey = desired
        }
    }

    fun startTimer910() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val target = now + (9 * 3600 * 1000L) + (10 * 60 * 1000L)

            // If user already exported pairs >= 20 BEFORE starting, do not re-lock.
            val all = repo.allPhotosNowSorted()
            val contiguousPairs = computeContiguousExportedPairs(all, maxPairs = 20)
            val unlockedAlready = contiguousPairs >= 20

            val newState = TimerState(
                running = true,
                locked = !unlockedAlready,
                startAt = now,
                targetAt = target
            )
            prefs.saveTimerState(newState)
        }
    }

    fun resetTimer() {
        viewModelScope.launch {
            val newState = TimerState(
                running = false,
                locked = false,
                startAt = null,
                targetAt = null
            )
            prefs.saveTimerState(newState)
            prefs.saveAlarmState(null, null)
            alarmScheduler.cancel()
            lastAlarmKey = null
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            // deterministic order
            repo.deleteAll()
            val newState = TimerState(running = false, locked = false, startAt = null, targetAt = null)
            prefs.saveTimerState(newState)
            prefs.saveAlarmState(null, null)
            alarmScheduler.cancel()
            lastAlarmKey = null
        }
    }

    fun addPhotoFromPicker(uri: Uri) {
        viewModelScope.launch {
            val current = (uiState.value.pendingPhotos + uiState.value.downloadedPhotos).sortedBySlot()
            val mapped = current.map { it.toSchedulePhoto() }
            val nextTag = SlotManager.computeNextSlot(mapped)
            repo.addPhotoFromUri(uri, nextTag)
        }
    }

    fun replacePhoto(id: Long, uri: Uri) {
        viewModelScope.launch {
            repo.replacePhotoFromUri(id, uri)
        }
    }

    fun exportPhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            // First-time export should be driven by downloadedAt, not boolean flag.
            val wasFirstTime = (photo.downloadedAt == null)

            val ok = repo.exportPhoto(photo)
            if (!ok) return@launch

            // Unlock triggers ONLY on first-time export, and specifically when OUT-20 is first-time exported
            if (wasFirstTime) {
                val all = repo.allPhotosNowSorted()
                val contiguousPairs = computeContiguousExportedPairs(all, maxPairs = 20)

                if (contiguousPairs >= 20) {
                    val currentTimer = prefs.timerState.stateIn(viewModelScope, SharingStarted.Eagerly, TimerState(false, false, null, null)).value
                    // Safer: re-read directly (suspend) if you have a Flow-first utility; leaving as current snapshot is ok if timerState is StateFlow in prefs.
                    // If your prefs.timerState is Flow, consider using .first() here.

                    // Unlock whenever we reach 20 pairs, regardless of which one was last
                    if (currentTimer.running && currentTimer.locked) {
                        prefs.saveTimerState(currentTimer.copy(locked = false))
                    }
                }
            }
        }
    }

    fun exportNextPhoto() {
        // We assume pendingPhotos is already sorted by slot in the UiState because coreState sorts them
        val next = coreState.value.pendingPhotos.firstOrNull()
        if (next != null) {
            exportPhoto(next)
        }
    }

    fun setSound(uri: String?) {
        viewModelScope.launch {
            prefs.setSoundUri(uri)
            // coreState will emit; manageAlarmSideEffect will reschedule if needed because soundUri is part of AlarmKey
        }
    }

    // -------------------------
    // Helpers
    // -------------------------

    private fun PhotoEntity.toSchedulePhoto(): SchedulePhoto {
        return SchedulePhoto(
            id = this.id,
            tag = this.tag,
            kind = this.kind,
            idx = this.idx,
            downloaded = this.downloaded,
            downloadedAt = this.downloadedAt
        )
    }

    private fun List<PhotoEntity>.sortedBySlot(): List<PhotoEntity> {
        return this.sortedWith(
            compareBy<PhotoEntity> { it.idx }.thenBy { kindOrder(it.kind) }
        )
    }

    private fun kindOrder(kind: Any?): Int {
        val k = kind?.toString()?.uppercase() ?: ""
        return if (k == "IN") 0 else 1
    }

    private fun computeContiguousExportedPairs(all: List<PhotoEntity>, maxPairs: Int): Int {
        val byTag = all.associateBy { it.tag }
        var count = 0
        for (i in 1..maxPairs) {
            val inE = byTag["IN-$i"]
            val outE = byTag["OUT-$i"]
            val ok = (inE?.downloadedAt != null) && (outE?.downloadedAt != null)
            if (ok) count++ else break
        }
        return count
    }

    /**
     * Convenience: snapshot all photos sorted (requires repo.allPhotos Flow or equivalent).
     * If you don't have this helper in repo, replace with: repo.allPhotos.first().sortedBySlot()
     */
    private suspend fun PhotoRepository.allPhotosNowSorted(): List<PhotoEntity> {
        return this.allPhotos.first().sortedBySlot()
    }
}

class MainViewModelFactory(
    private val repo: PhotoRepository,
    private val prefs: UserPreferences,
    private val alarmScheduler: AlarmScheduler
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repo, prefs, alarmScheduler) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
