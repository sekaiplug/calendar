package app.expressivecalendar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.expressivecalendar.data.AlarmItem
import app.expressivecalendar.data.ClockRepository
import app.expressivecalendar.data.FocusSession
import app.expressivecalendar.data.SessionType
import app.expressivecalendar.notify.ClockForegroundService
import app.expressivecalendar.notify.ClockReceiver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ClockSubTab(val label: String) {
    POMODORO("Pomodoro"),
    TIMER("Timer"),
    ALARM("Alarm"),
    ANALYTICS("Analytics")
}

class ClockViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = ClockRepository(app)

    init {
        ClockReceiver.listener = object : ClockReceiver.ClockActionListener {
            override fun onPause() {
                if (_isPomoRunning.value) pausePomodoro()
                else if (_isTimerRunning.value) pauseTimer()
            }

            override fun onResume() {
                if (!_isPomoRunning.value && _currentSubTab.value == ClockSubTab.POMODORO) startPomodoro()
                else if (!_isTimerRunning.value && _currentSubTab.value == ClockSubTab.TIMER) startTimer()
            }

            override fun onStop() {
                if (_isPomoRunning.value) stopPomodoro()
                else if (_isTimerRunning.value) resetTimer()
            }
        }
    }

    val sessions: StateFlow<List<FocusSession>> = repo.sessionsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val alarms: StateFlow<List<AlarmItem>> = repo.alarmsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Current Clock sub tab
    private val _currentSubTab = MutableStateFlow(ClockSubTab.POMODORO)
    val currentSubTab = _currentSubTab.asStateFlow()

    fun setSubTab(tab: ClockSubTab) {
        _currentSubTab.value = tab
    }

    // --- POMODORO STATE ---
    private val _pomoTargetMinutes = MutableStateFlow(25)
    val pomoTargetMinutes = _pomoTargetMinutes.asStateFlow()

    private val _pomoSecondsRemaining = MutableStateFlow(25 * 60L)
    val pomoSecondsRemaining = _pomoSecondsRemaining.asStateFlow()

    private val _pomoSecondsElapsed = MutableStateFlow(0L)
    val pomoSecondsElapsed = _pomoSecondsElapsed.asStateFlow()

    private val _isPomoRunning = MutableStateFlow(false)
    val isPomoRunning = _isPomoRunning.asStateFlow()

    private var pomoJob: Job? = null

    fun setPomoTarget(minutes: Int) {
        if (_isPomoRunning.value) return
        _pomoTargetMinutes.value = minutes.coerceIn(1, 180)
        _pomoSecondsRemaining.value = _pomoTargetMinutes.value * 60L
    }

    fun startPomodoro() {
        if (_isPomoRunning.value) return
        _isPomoRunning.value = true
        pomoJob?.cancel()
        val endTime = System.currentTimeMillis() + (_pomoSecondsRemaining.value * 1000L)
        ClockForegroundService.start(app, "Pomodoro Focus", _pomoSecondsRemaining.value)
        pomoJob = viewModelScope.launch {
            while (_isPomoRunning.value) {
                delay(1000)
                if (_pomoSecondsRemaining.value > 0) {
                    _pomoSecondsRemaining.value -= 1
                    _pomoSecondsElapsed.value += 1
                    ClockForegroundService.updateTick(app, _pomoSecondsRemaining.value, endTime)
                } else {
                    // Completed!
                    _isPomoRunning.value = false
                    ClockForegroundService.stop(app)
                    recordFocusSession(SessionType.POMODORO, _pomoSecondsElapsed.value)
                    resetPomodoro()
                    break
                }
            }
        }
    }

    fun pausePomodoro() {
        _isPomoRunning.value = false
        pomoJob?.cancel()
        ClockForegroundService.pause(app, "Pomodoro Focus", _pomoSecondsRemaining.value)
    }

    fun stopPomodoro() {
        val elapsed = _pomoSecondsElapsed.value
        _isPomoRunning.value = false
        pomoJob?.cancel()
        ClockForegroundService.stop(app)
        if (elapsed >= 10) { // save if lasted more than 10s
            recordFocusSession(SessionType.POMODORO, elapsed)
        }
        resetPomodoro()
    }

    private fun resetPomodoro() {
        _pomoSecondsRemaining.value = _pomoTargetMinutes.value * 60L
        _pomoSecondsElapsed.value = 0L
    }

    // --- COUNTDOWN TIMER STATE ---
    private val _timerTargetSeconds = MutableStateFlow(5 * 60L) // Default 5 mins
    val timerTargetSeconds = _timerTargetSeconds.asStateFlow()

    private val _timerRemainingSeconds = MutableStateFlow(5 * 60L)
    val timerRemainingSeconds = _timerRemainingSeconds.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning = _isTimerRunning.asStateFlow()

    private var timerJob: Job? = null

    fun setTimerSeconds(totalSeconds: Long) {
        if (_isTimerRunning.value) return
        val clamped = totalSeconds.coerceIn(5, 24 * 3600)
        _timerTargetSeconds.value = clamped
        _timerRemainingSeconds.value = clamped
    }

    fun startTimer() {
        if (_isTimerRunning.value) return
        _isTimerRunning.value = true
        timerJob?.cancel()
        val endTime = System.currentTimeMillis() + (_timerRemainingSeconds.value * 1000L)
        ClockForegroundService.start(app, "Timer Countdown", _timerRemainingSeconds.value)
        timerJob = viewModelScope.launch {
            while (_isTimerRunning.value && _timerRemainingSeconds.value > 0) {
                delay(1000)
                _timerRemainingSeconds.value -= 1
                if (_timerRemainingSeconds.value <= 0) {
                    _isTimerRunning.value = false
                    ClockForegroundService.stop(app)
                    recordFocusSession(SessionType.TIMER, _timerTargetSeconds.value)
                    _timerRemainingSeconds.value = _timerTargetSeconds.value
                    break
                } else {
                    ClockForegroundService.updateTick(app, _timerRemainingSeconds.value, endTime)
                }
            }
        }
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
        ClockForegroundService.pause(app, "Timer Countdown", _timerRemainingSeconds.value)
    }

    fun resetTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
        ClockForegroundService.stop(app)
        _timerRemainingSeconds.value = _timerTargetSeconds.value
    }

    // --- ALARM STATE ---
    fun toggleAlarm(alarmId: String) {
        viewModelScope.launch {
            repo.toggleAlarm(alarmId)
        }
    }

    fun addAlarm(hour: Int, minute: Int, label: String) {
        viewModelScope.launch {
            val current = alarms.value.toMutableList()
            current.add(AlarmItem(hour = hour, minute = minute, label = label.ifBlank { "Alarm" }, isEnabled = true))
            repo.saveAlarms(current)
        }
    }

    fun deleteAlarm(alarmId: String) {
        viewModelScope.launch {
            val updated = alarms.value.filter { it.id != alarmId }
            repo.saveAlarms(updated)
        }
    }

    // --- RECORDING & ANALYTICS ---
    private fun recordFocusSession(type: SessionType, seconds: Long) {
        if (seconds <= 0) return
        viewModelScope.launch {
            repo.recordSession(
                FocusSession(
                    type = type,
                    durationSeconds = seconds,
                    date = LocalDate.now().toString(),
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
    }

    // Selected analytics calendar inspection date
    private val _analyticsSelectedDate = MutableStateFlow(LocalDate.now())
    val analyticsSelectedDate = _analyticsSelectedDate.asStateFlow()

    fun selectAnalyticsDate(date: LocalDate) {
        _analyticsSelectedDate.value = date
    }
}
