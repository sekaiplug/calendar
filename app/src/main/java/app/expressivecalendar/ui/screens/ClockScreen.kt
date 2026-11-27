package app.expressivecalendar.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.expressivecalendar.data.AlarmItem
import app.expressivecalendar.data.FocusSession
import app.expressivecalendar.data.SessionType
import app.expressivecalendar.ui.ClockSubTab
import app.expressivecalendar.ui.ClockViewModel
import app.expressivecalendar.ui.elasticClickable
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ClockScreen(
    vm: ClockViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val currentSubTab by vm.currentSubTab.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Expressive Top Switcher Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            ClockSubTab.entries.forEach { tab ->
                val selected = currentSubTab == tab
                val icon = when (tab) {
                    ClockSubTab.POMODORO -> Icons.Outlined.HourglassBottom
                    ClockSubTab.TIMER -> Icons.Outlined.Timer
                    ClockSubTab.ALARM -> Icons.Outlined.Alarm
                    ClockSubTab.ANALYTICS -> Icons.Outlined.Analytics
                }
                FilterChip(
                    selected = selected,
                    onClick = { vm.setSubTab(tab) },
                    label = { Text(tab.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = {
                        Icon(icon, contentDescription = tab.label, modifier = Modifier.size(18.dp))
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = currentSubTab,
            label = "clockSubTabContent",
            modifier = Modifier.fillMaxSize()
        ) { target ->
            when (target) {
                ClockSubTab.POMODORO -> PomodoroSection(vm)
                ClockSubTab.TIMER -> TimerSection(vm)
                ClockSubTab.ALARM -> AlarmSection(vm)
                ClockSubTab.ANALYTICS -> AnalyticsSection(vm)
            }
        }
    }
}

// -------------------------------------------------------------
// POMODORO SECTION
// -------------------------------------------------------------
@Composable
private fun PomodoroSection(vm: ClockViewModel) {
    val isRunning by vm.isPomoRunning.collectAsStateWithLifecycle()
    val remaining by vm.pomoSecondsRemaining.collectAsStateWithLifecycle()
    val elapsed by vm.pomoSecondsElapsed.collectAsStateWithLifecycle()
    val targetMinutes by vm.pomoTargetMinutes.collectAsStateWithLifecycle()

    val totalSeconds = targetMinutes * 60L
    val progress = if (totalSeconds > 0) (remaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Slider selection bar (1 to 180 minutes)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$targetMinutes min${if (targetMinutes > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = targetMinutes.toFloat(),
                onValueChange = { vm.setPomoTarget(kotlin.math.round(it).toInt()) },
                valueRange = 1f..180f,
                steps = 178,
                enabled = !isRunning,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "1m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "60m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "180m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Circular Display
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(250.dp)) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = 14.dp,
                trackColor = Color.Transparent,
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 14.dp,
                strokeCap = StrokeCap.Round,
                trackColor = Color.Transparent,
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val minutes = remaining / 60
                val seconds = remaining % 60
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 50.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isRunning) "Focusing" else "Ready to Focus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Expressive Control Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            if (isRunning) {
                IconButton(
                    onClick = { vm.pausePomodoro() },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Icon(Icons.Rounded.Pause, "Pause", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
                }
                IconButton(
                    onClick = { vm.stopPomodoro() },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(Icons.Rounded.Stop, "Stop & Save", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(32.dp))
                }
            } else {
                IconButton(
                    onClick = { vm.startPomodoro() },
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Start", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                }
                if (elapsed > 0 || remaining < totalSeconds) {
                    IconButton(
                        onClick = { vm.stopPomodoro() },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Icon(Icons.Rounded.Refresh, "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TIMER SECTION
// -------------------------------------------------------------
@Composable
private fun TimerSection(vm: ClockViewModel) {
    val isRunning by vm.isTimerRunning.collectAsStateWithLifecycle()
    val targetSeconds by vm.timerTargetSeconds.collectAsStateWithLifecycle()
    val remainingSeconds by vm.timerRemainingSeconds.collectAsStateWithLifecycle()

    val progress = if (targetSeconds > 0) (remainingSeconds.toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        // Quick Presets
        if (!isRunning) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60L to "1m", 300L to "5m", 600L to "10m", 900L to "15m", 1800L to "30m").forEach { (sec, label) ->
                    val isSel = targetSeconds == sec
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { vm.setTimerSeconds(sec) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(36.dp))
        }

        // Circular Timer Indicator
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 14.dp,
                trackColor = Color.Transparent,
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.tertiary,
                strokeWidth = 14.dp,
                strokeCap = StrokeCap.Round,
                trackColor = Color.Transparent,
            )

            val hours = remainingSeconds / 3600
            val minutes = (remainingSeconds % 3600) / 60
            val seconds = remainingSeconds % 60

            val formatted = if (hours > 0) {
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (hours > 0) 42.sp else 52.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("Countdown", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRunning) {
                IconButton(
                    onClick = { vm.pauseTimer() },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Icon(Icons.Rounded.Pause, "Pause", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
                }
            } else {
                IconButton(
                    onClick = { vm.startTimer() },
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Start", tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(40.dp))
                }
            }

            IconButton(
                onClick = { vm.resetTimer() },
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Icon(Icons.Rounded.Refresh, "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// -------------------------------------------------------------
// ALARM SECTION
// -------------------------------------------------------------
@Composable
private fun AlarmSection(vm: ClockViewModel) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Alarms",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(Icons.Rounded.Add, "Add Alarm", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (alarms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No alarms set", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmRowItem(
                        alarm = alarm,
                        onToggle = { vm.toggleAlarm(alarm.id) },
                        onDelete = { vm.deleteAlarm(alarm.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddAlarmDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { h, m, label ->
                vm.addAlarm(h, m, label)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AlarmRowItem(
    alarm: AlarmItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
            Text(
                text = timeStr,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = alarm.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = { onToggle() }
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddAlarmDialog(
    onDismiss: () -> Unit,
    onAdd: (hour: Int, minute: Int, label: String) -> Unit
) {
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var label by remember { mutableStateOf("Wake up") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Set Alarm", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))

            // Time Selector (Expressive counter)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { hour = (hour + 1) % 24 }) { Text("▲") }
                    Text(String.format(Locale.getDefault(), "%02d", hour), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { hour = (hour + 23) % 24 }) { Text("▼") }
                }
                Text(":", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { minute = (minute + 5) % 60 }) { Text("▲") }
                    Text(String.format(Locale.getDefault(), "%02d", minute), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { minute = (minute + 55) % 60 }) { Text("▼") }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(onClick = { onAdd(hour, minute, label) }) {
                    Text("Save")
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ANALYTICS & CALENDAR DAY VIEW SECTION
// -------------------------------------------------------------
@Composable
private fun AnalyticsSection(vm: ClockViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val selectedDate by vm.analyticsSelectedDate.collectAsStateWithLifecycle()
    var analyticsPeriod by remember { mutableStateOf("Day") } // "Day", "Week", "Month"

    val today = LocalDate.now()
    val totalSecondsDay = sessions.filter { it.date == today.toString() }.sumOf { it.durationSeconds }
    val totalSecondsWeek = sessions.filter {
        val sDate = try { LocalDate.parse(it.date) } catch (_: Exception) { null }
        sDate != null && !sDate.isBefore(today.minusDays(7))
    }.sumOf { it.durationSeconds }
    val totalSecondsMonth = sessions.filter {
        val sDate = try { LocalDate.parse(it.date) } catch (_: Exception) { null }
        sDate != null && !sDate.isBefore(today.minusDays(30))
    }.sumOf { it.durationSeconds }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnalyticsCard(title = "Today", minutes = totalSecondsDay / 60, active = analyticsPeriod == "Day", modifier = Modifier.weight(1f)) {
                    analyticsPeriod = "Day"
                }
                AnalyticsCard(title = "7 Days", minutes = totalSecondsWeek / 60, active = analyticsPeriod == "Week", modifier = Modifier.weight(1f)) {
                    analyticsPeriod = "Week"
                }
                AnalyticsCard(title = "30 Days", minutes = totalSecondsMonth / 60, active = analyticsPeriod == "Month", modifier = Modifier.weight(1f)) {
                    analyticsPeriod = "Month"
                }
            }
        }

        // Embedded Calendar for Date Inspection
        item {
            Text(
                text = "Select a date on Calendar to inspect",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            AnalyticsCalendarGrid(
                selectedDate = selectedDate,
                sessions = sessions,
                onSelectDate = { vm.selectAnalyticsDate(it) }
            )
        }

        // Day Specific Breakdown for Selected Date
        item {
            val dateStr = selectedDate.toString()
            val daySessions = sessions.filter { it.date == dateStr }
            val dayTotalMins = daySessions.sumOf { it.durationSeconds } / 60

            val formattedHeader = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(formattedHeader, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("$dayTotalMins mins total focus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("${daySessions.size} sessions", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (daySessions.isEmpty()) {
                    Text("No focus sessions recorded on this day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    daySessions.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("• ${s.type.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("${s.durationSeconds / 60}m ${s.durationSeconds % 60}s", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AnalyticsCard(
    title: String,
    minutes: Long,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, label = "cardBg")
    val textCol = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = textCol.copy(alpha = 0.8f))
        Spacer(Modifier.height(4.dp))
        Text("${minutes}m", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = textCol)
        Text("focus", style = MaterialTheme.typography.labelSmall, color = textCol.copy(alpha = 0.6f))
    }
}

@Composable
private fun AnalyticsCalendarGrid(
    selectedDate: LocalDate,
    sessions: List<FocusSession>,
    onSelectDate: (LocalDate) -> Unit
) {
    val ym = YearMonth.from(selectedDate)
    val daysInMonth = ym.lengthOfMonth()
    val firstDayOfWeek = ym.atDay(1).dayOfWeek.value // 1 = Monday .. 7 = Sunday
    val monthTitle = ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            text = monthTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(10.dp))

        // Weekday headers
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Days Grid (6 rows max)
        var currentDay = 1
        val sessionDates = sessions.map { it.date }.toSet()

        for (row in 0..5) {
            if (currentDay > daysInMonth) break
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                for (col in 1..7) {
                    if ((row == 0 && col < firstDayOfWeek) || currentDay > daysInMonth) {
                        Spacer(Modifier.size(36.dp))
                    } else {
                        val dayVal = currentDay
                        val thisDate = ym.atDay(dayVal)
                        val isSelected = thisDate == selectedDate
                        val hasSession = sessionDates.contains(thisDate.toString())

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(if (isSelected) MaterialShapes.Sunny.toShape() else CircleShape)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        hasSession -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onSelectDate(thisDate) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayVal.toString(),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected || hasSession) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    hasSession -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        currentDay++
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
