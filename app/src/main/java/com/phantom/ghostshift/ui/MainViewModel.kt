package com.phantom.ghostshift.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.phantom.ghostshift.data.PhotoEntity
import com.phantom.ghostshift.data.PhotoRepository
import com.phantom.ghostshift.data.UserPreferences
import com.phantom.ghostshift.domain.ScheduleCalculator
import com.phantom.ghostshift.domain.ScheduleResult
import com.phantom.ghostshift.domain.SlotManager
import com.phantom.ghostshift.domain.TimerState
import com.phantom.ghostshift.domain.UnlockRules
import com.phantom.ghostshift.system.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val pendingPhotos: List<PhotoEntity> = emptyList(),
    val downloadedPhotos: List<PhotoEntity> = emptyList(),
    val timer: TimerState = TimerState(running = false, locked = false, startAt = null, targetAt = null),
    val schedule: ScheduleResult = ScheduleResult(ok = false),
    val nextSlotTag: String = "IN-1",
    val canStartTimer: Boolean = true,
    val isTimerLocked: Boolean = false,
    val alarmActive: Boolean = false,
    val gateOpen: Boolean = false,
    val soundUri: String? = null,
    val currentTime: Long = System.currentTimeMillis()
)

class MainViewModel(
    private val repo: PhotoRepository,
    private val prefs: UserPreferences,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _now = MutableStateFlow(System.currentTimeMillis())

    // Actions to UI (One-off events) - could use Channel
    // simplified: just state for now.

    val uiState: StateFlow<MainUiState> = combine(
        repo.pendingPhotos,
        repo.downloadedPhotos,
        prefs.timerState,
        prefs.alarmState,
        prefs.soundPref,
        _now // Trigger updates for countdown
    ) { pending, downloaded, timer, alarm, sound, nowMillis ->
        val all = (pending + downloaded).sortedBy { it.idx } // strict sort by idx/kind? SlotManager uses idx.
        // Actually repo returns sorted flow.
        
        // Strict Gate Check: At least one "first-time export" (downloadedAt != null)
        val hasFirstDownload = downloaded.any { it.downloadedAt != null }
        val gateOpen = hasFirstDownload
        
        // Timer Logic
        val schedule = if (timer.running) {
             if (!gateOpen) {
                 ScheduleResult(ok = false, warn = "ต้องดาวน์โหลดรูปอย่างน้อย 1 รูปเพื่อเริ่มตารางแจ้งเตือน")
             } else {
                 val sortedAll = (pending + downloaded).sortedWith(compareBy<PhotoEntity> { it.idx }.thenBy { it.kind })
                 // Mapper to SchedulePhoto
                 val params = sortedAll.map { 
                     com.phantom.ghostshift.domain.SchedulePhoto(it.id, it.tag, it.kind, it.idx, it.downloaded, it.downloadedAt)
                 }
                 ScheduleCalculator.computeScheduleExactFit(params, timer)
             }
        } else {
             ScheduleResult(ok = false, warn = "ยังไม่เริ่มจับเวลา")
        }

        // Unlock Logic
        val unlockMaxPair = if (gateOpen) {
            val params = (pending + downloaded).sortedWith(compareBy<PhotoEntity> { it.idx }.thenBy { it.kind }).map { 
                 com.phantom.ghostshift.domain.SchedulePhoto(it.id, it.tag, it.kind, it.idx, it.downloaded, it.downloadedAt)
            }
            UnlockRules.getUnlockMaxPairIfEligible(params)
        } else 0
        
        // Check if unlocked (timer.locked = false) OR eligible to unlock?
        // Actually logic says "Unlock triggers ONLY when OUT-20 is exported".
        // State just reflects current locked status.
        
        // Alarm Active Check
        val isAlarmSet = (alarm.first != null) // stored dueAt

        MainUiState(
            pendingPhotos = pending,
            downloadedPhotos = downloaded,
            timer = timer,
            schedule = schedule,
            nextSlotTag = SlotManager.computeNextSlot(all.map { com.phantom.ghostshift.domain.SchedulePhoto(it.id, it.tag, it.kind, it.idx, it.downloaded, it.downloadedAt) }),
            canStartTimer = !timer.running,
            isTimerLocked = timer.locked,
            alarmActive = isAlarmSet,
            gateOpen = gateOpen,
            soundUri = sound,
            currentTime = nowMillis
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    init {
        // Ticker for UI countdown
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _now.value = System.currentTimeMillis()
            }
        }
        
        // Alarm Side Effect Manager
        viewModelScope.launch {
            uiState.collect { state ->
                manageAlarmSideEffect(state)
            }
        }
    }
    
    private suspend fun manageAlarmSideEffect(state: MainUiState) {
        val timer = state.timer
        val schedule = state.schedule
        val gateOpen = state.gateOpen
        
        // Strict Rule: No alarm if not running OR gated
        if (!timer.running || !gateOpen) {
             if (state.alarmActive) {
                 alarmScheduler.cancel()
                 prefs.saveAlarmState(null, null)
             }
             return
        }
        
        // Running & Open
        if (schedule.ok && schedule.nextAt != null && schedule.nextTag != null) {
             val currentAlarm = prefs.alarmState.first()
             val (storedDue, storedTag, _) = currentAlarm
             
             // Threshold to avoid thrashing? check != 
             if (storedDue != schedule.nextAt || storedTag != schedule.nextTag) {
                 // Schedule It
                 alarmScheduler.scheduleExact(schedule.nextAt!!, schedule.nextTag!!, state.soundUri)
                 prefs.saveAlarmState(schedule.nextAt, schedule.nextTag)
             }
        } else {
             // Schedule warning/error -> Cancel alarm
             if (state.alarmActive) {
                 alarmScheduler.cancel()
                 prefs.saveAlarmState(null, null)
             }
        }
    }

    fun startTimer910() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // 9h 10m
            val target = now + (9 * 3600 * 1000) + (10 * 60 * 1000)
            val newState = TimerState(
                running = true,
                locked = true, // Start always locks
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
        }
    }
    
    fun deleteAll() {
        viewModelScope.launch {
            repo.deleteAll() // Clears DB (+ logic to clear files if impl)
            resetTimer()
        }
    }

    fun addPhotoFromPicker(uri: android.net.Uri) {
        viewModelScope.launch {
            // Determine next slot from current list
            // We need the latest list from repo or state
            // State is safe enough
            val current = uiState.value.pendingPhotos + uiState.value.downloadedPhotos
            val mapped = current.map { com.phantom.ghostshift.domain.SchedulePhoto(it.id, it.tag, it.kind, it.idx, it.downloaded, it.downloadedAt) }
            val nextTag = SlotManager.computeNextSlot(mapped)
            
            repo.addPhotoFromUri(uri, nextTag)
        }
    }
    
    fun replacePhoto(id: Long, uri: android.net.Uri) {
        viewModelScope.launch {
            repo.replacePhotoFromUri(id, uri)
        }
    }

    fun exportPhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            val wasFirstTime = !photo.downloaded
            val ok = repo.exportPhoto(photo)
            if (ok && wasFirstTime) {
                // Check Unlock triggers
                val allPhotos = repo.allPhotos.first() // Get fresh
                // Map to domain
                val params = allPhotos.map { 
                    com.phantom.ghostshift.domain.SchedulePhoto(it.id, it.tag, it.kind, it.idx, it.downloaded, it.downloadedAt)
                }
                val maxPair = UnlockRules.getUnlockMaxPairIfEligible(params)
                
                // Rule: Unlock triggers ONLY when OUT-20 (or higher contiguous) is exported first time
                // And photos containing this exported one must fulfill the rule
                
                if (maxPair >= 20) {
                     val currentTimer = prefs.timerState.first()
                     if (currentTimer.running && currentTimer.locked && photo.tag == "OUT-$maxPair") {
                         prefs.saveTimerState(currentTimer.copy(locked = false))
                     }
                }
            }
        }
    }
    
    fun setSound(uri: String?) {
        viewModelScope.launch {
            prefs.setSoundUri(uri)
            // Side effect manager will pick up change in next combine emission? yes
        }
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
