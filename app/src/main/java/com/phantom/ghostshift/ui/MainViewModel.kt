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
import com.phantom.ghostshift.domain.ScheduleSettings
import com.phantom.ghostshift.domain.SlotManager
import com.phantom.ghostshift.domain.TimerState
import com.phantom.ghostshift.system.AlarmScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    // settings
    val soundUri: String? = null,
    val scheduleSettings: ScheduleSettings = ScheduleSettings(),

    // clock (UI countdown only)
    val currentTime: Long = System.currentTimeMillis(),

    // Keeps the Next action single-flight so rapid taps cannot export two photos.
    val isExportingNext: Boolean = false
)

data class ExportHeadsUpEvent(
    val tag: String,
    val remark: String?
)

data class SharedPhotoInput(
    val uri: Uri,
    val remark: String?
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
    private val isExportingNext = MutableStateFlow(false)
    private val _exportHeadsUpEvents = MutableSharedFlow<ExportHeadsUpEvent>(extraBufferCapacity = 1)
    val exportHeadsUpEvents = _exportHeadsUpEvents.asSharedFlow()

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
        prefs.soundPref,
        prefs.scheduleSettings
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val pending = args[0] as List<PhotoEntity>
        @Suppress("UNCHECKED_CAST")
        val downloaded = args[1] as List<PhotoEntity>
        val timer = args[2] as TimerState
        @Suppress("UNCHECKED_CAST")
        val alarmTriple = args[3] as Triple<Long?, String?, String?>
        val sound = args[4] as String?
        val settings = args[5] as ScheduleSettings

        val all = (pending + downloaded).sortedBySlot()

        // Gate: at least one first-time export exists
        val gateOpen = all.any { it.downloadedAt != null }

        // Persisted alarm info
        val (storedDueAt, storedTag, storedChannelId) = alarmTriple
        val alarmActive = storedDueAt != null

        // Build domain photo list once
        val domainPhotos = all.map { it.toSchedulePhoto() }

        // Next slot (IN-1, OUT-1, IN-2...)
        val nextSlot = SlotManager.computeNextSlot(domainPhotos)

        // Schedule: only meaningful when timer.running and gateOpen
        // Schedule: only meaningful when timer.running
        val schedule = when {
            !timer.running -> ScheduleResult(ok = false, warn = "ยังไม่เริ่มจับเวลา")
            else -> ScheduleCalculator.computeScheduleExactFit(domainPhotos, timer, settings)
        }

        MainUiState(
            pendingPhotos = pending,
            downloadedPhotos = downloaded,
            timer = timer,
            schedule = schedule,
            nextSlotTag = nextSlot,
            canStartTimer = !timer.running,
            isTimerLocked = false,
            alarmDueAt = storedDueAt,
            alarmTag = storedTag,
            alarmChannelId = storedChannelId,
            alarmActive = alarmActive,
            gateOpen = gateOpen,
            soundUri = sound,
            scheduleSettings = settings,
            currentTime = System.currentTimeMillis() // will be overridden by uiState combine with _now
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    /**
     * UI state: depends on clock ticker for countdown only.
     * IMPORTANT: schedule is not recomputed each second.
     */
    val uiState: StateFlow<MainUiState> = combine(coreState, _now, isExportingNext) { core, nowMillis, exportingNext ->
        core.copy(currentTime = nowMillis, isExportingNext = exportingNext)
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
                        state.timer.running &&
                            state.gateOpen &&
                            state.schedule.nextAt != null &&
                            state.schedule.nextTag != null,
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
            gateOpen &&  // Restored: Only schedule alarm if at least 1 photo is exported (Gate Open)
            schedule.nextAt != null &&
            schedule.nextTag != null

        if (!shouldHaveAlarm) {
            if (state.alarmActive) {
                alarmScheduler.cancel()
                prefs.saveAlarmState(null, null)
                // Also cancel ongoing - handled by alarmScheduler.cancel()
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
            
            // Trigger ongoing notification (needs Context).
            // AlarmScheduler holds context. Let's add method there or expose it?
            // Cleanest: add updateOngoing(dueAt, tag) to AlarmScheduler.
            alarmScheduler.updateOngoing(desired.dueAt, desired.tag)
            
            lastAlarmKey = desired
        }
    }

    fun startTimer910() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val savedTimer = prefs.timerState.first()
            val defaultTarget = now + (9 * 3600 * 1000L) + (10 * 60 * 1000L)
            val target = savedTimer.targetAt?.takeIf { it > now } ?: defaultTarget

            val newState = TimerState(
                running = true,
                locked = false,
                startAt = now,
                targetAt = target
            )
            prefs.saveTimerState(newState)
        }
    }

    fun setTargetTime(targetAt: Long) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (targetAt <= now) return@launch
            val current = prefs.timerState.first()
            prefs.saveTimerState(current.copy(targetAt = targetAt))
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

    fun addPhotoFromPicker(uri: Uri, mirrorHorizontally: Boolean = false) {
        addPhotosFromPicker(listOf(uri), mirrorHorizontally)
    }

    fun addPhotosFromPicker(uris: List<Uri>, mirrorHorizontally: Boolean = false) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            for (uri in uris) {
                val current = repo.allPhotosNowSorted()
                val mapped = current.map { it.toSchedulePhoto() }
                val nextTag = SlotManager.computeNextSlot(mapped)
                repo.addPhotoFromUri(uri, nextTag, mirrorHorizontally)
            }
        }
    }

    fun replacePhoto(id: Long, uri: Uri, mirrorHorizontally: Boolean = false) {
        viewModelScope.launch {
            repo.replacePhotoFromUri(id, uri, mirrorHorizontally)
        }
    }

    fun deletePendingPhoto(photoId: Long) {
        viewModelScope.launch {
            repo.deletePendingPhotoAndShift(photoId)
        }
    }

    fun reorderPending(photoIdsInOrder: List<Long>) {
        if (photoIdsInOrder.isEmpty()) return
        viewModelScope.launch {
            repo.reorderPendingByIds(photoIdsInOrder)
        }
    }

    fun reorderCompletePendingPairs(pairIdsInOrder: List<Long>) {
        if (pairIdsInOrder.isEmpty()) return
        viewModelScope.launch {
            repo.reorderCompletePendingPairs(pairIdsInOrder)
        }
    }

    /** Imports images shared by Time Keeper in the exact order chosen by the user. */
    fun addSharedPhotos(inputs: List<SharedPhotoInput>) {
        if (inputs.isEmpty()) return

        viewModelScope.launch {
            for (input in inputs) {
                val current = repo.allPhotosNowSorted()
                val nextTag = SlotManager.computeNextSlot(current.map { it.toSchedulePhoto() })
                repo.addPhotoFromUri(input.uri, nextTag, remark = input.remark)
            }
        }
    }

    fun swapPair(index: Int) {
        viewModelScope.launch { repo.swapPairContents(index) }
    }

    fun savePairRemark(index: Int, remark: String) {
        viewModelScope.launch { repo.savePairRemark(index, remark) }
    }

    fun exportPhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            repo.exportPhoto(photo)
        }
    }

    fun exportNextPhoto() {
        if (!isExportingNext.compareAndSet(expect = false, update = true)) return
        val next = coreState.value.pendingPhotos.firstOrNull()
        if (next == null) {
            isExportingNext.value = false
            return
        }
        viewModelScope.launch {
            try {
                val currentTimer = prefs.timerState.first()
                val settings = prefs.scheduleSettings.first()
                if (next.tag.equals("IN-1", ignoreCase = true) && settings.autoStartOnFirstDownload && !currentTimer.running) {
                    startTimerNow()
                }
                val exported = repo.exportPhoto(next)
                // Keep the action locked briefly so a double-tap cannot export the following photo.
                delay(700)
                if (exported) {
                    _exportHeadsUpEvents.emit(ExportHeadsUpEvent(next.tag, next.remark))
                }
            } finally {
                isExportingNext.value = false
            }
        }
    }

    fun setSound(uri: String?) {
        viewModelScope.launch {
            prefs.setSoundUri(uri)
            // coreState will emit; manageAlarmSideEffect will reschedule if needed because soundUri is part of AlarmKey
        }
    }

    fun saveScheduleSettings(settings: ScheduleSettings) {
        viewModelScope.launch { prefs.saveScheduleSettings(settings) }
    }

    private suspend fun startTimerNow() {
        val now = System.currentTimeMillis()
        val saved = prefs.timerState.first()
        val target = saved.targetAt?.takeIf { it > now } ?: now + (9 * 3600 * 1000L) + (10 * 60 * 1000L)
        prefs.saveTimerState(TimerState(true, false, now, target))
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
