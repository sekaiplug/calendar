package app.expressivecalendar.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.expressivecalendar.data.EventDraft
import app.expressivecalendar.data.EventInstance
import app.expressivecalendar.data.Holiday
import app.expressivecalendar.ui.screens.AgendaScreen
import app.expressivecalendar.ui.screens.ClockScreen
import app.expressivecalendar.ui.screens.DayScreen
import app.expressivecalendar.ui.screens.MonthScreen
import app.expressivecalendar.ui.screens.PermissionScreen
import app.expressivecalendar.ui.screens.SettingsScreen
import app.expressivecalendar.ui.sheets.EventDetailSheet
import app.expressivecalendar.ui.sheets.EventEditorSheet
import app.expressivecalendar.ui.sheets.HolidayDetailSheet
import app.expressivecalendar.ui.sheets.newDraftFor
import app.expressivecalendar.ui.theme.CalendarTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

private val DOCK_CLEARANCE = 20.dp
private val DOCK_RESERVE = DOCK_HEIGHT + DOCK_CLEARANCE + 12.dp

@Composable
fun CalendarApp(vm: CalendarViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loaded by vm.settingsLoaded.collectAsStateWithLifecycle()
    val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()

    CalendarTheme(settings.theme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (!loaded) return@Box

            val context = LocalContext.current
            var askedOnce by rememberSaveable { mutableStateOf(false) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                askedOnce = true
                vm.onPermissionResult()
            }
            val request = {
                launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            }

            AnimatedContent(
                targetState = hasPermission,
                transitionSpec = {
                    slideInHorizontally(spring(stiffness = 380f, dampingRatio = .82f)) { it } + fadeIn(tween(220)) togetherWith
                        slideOutHorizontally(spring(stiffness = 380f, dampingRatio = .82f)) { -it } + fadeOut(tween(200))
                },
                label = "permission",
            ) { granted ->
                if (!granted) {
                    val activity = context as? Activity
                    val permanentlyDenied = askedOnce && activity != null &&
                        !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
                    PermissionScreen(
                        permanentlyDenied = permanentlyDenied,
                        onRequest = request,
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                    )
                } else {
                    MainShell(vm)
                }
            }
        }
    }
}

@Composable
private fun MainShell(vm: CalendarViewModel) {
    val clockVm: ClockViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val holidays by vm.holidays.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val month by vm.visibleMonth.collectAsStateWithLifecycle()
    val selected by vm.selectedDate.collectAsStateWithLifecycle()
    val holidayStatus by vm.holidayStatus.collectAsStateWithLifecycle()
    val notificationsAllowed by vm.notificationsAllowed.collectAsStateWithLifecycle()
    val pendingOpen by vm.pendingOpen.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.of(settings.defaultView)) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<EventInstance?>(null) }
    var holidayDetail by remember { mutableStateOf<Holiday?>(null) }
    var editing by remember { mutableStateOf<EventDraft?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Reminder notifications need POST_NOTIFICATIONS on Android 13+. Ask once at startup when
    // they're on; from the settings toggle, fall back to system settings if the prompt is blocked.
    val context = LocalContext.current
    var notifAskedFromSettings by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onPermissionResult()
        val activity = context as? Activity
        if (!granted && notifAskedFromSettings && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
        notifAskedFromSettings = false
    }
    fun requestNotifications(fromSettings: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && !vm.notificationsAllowed.value &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifAskedFromSettings = fromSettings
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (fromSettings && !vm.notificationsAllowed.value) {
            // Permission granted but notifications switched off for the app in system settings.
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    var notifAskedAtStart by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!notifAskedAtStart && vm.notificationsEnabled(settings)) {
            notifAskedAtStart = true
            requestNotifications(fromSettings = false)
        }
    }

    // Tapping a reminder notification opens that occurrence once it has loaded.
    LaunchedEffect(pendingOpen, events) {
        val (id, begin) = pendingOpen ?: return@LaunchedEffect
        val match = events.values.asSequence().flatten().firstOrNull { it.eventId == id && it.beginMillis == begin }
            ?: events.values.asSequence().flatten().firstOrNull { it.eventId == id }
        if (match != null) {
            showSettings = false
            holidayDetail = null
            editing = null
            detail = match
            vm.consumePendingOpen()
        }
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && tab != Tab.of(settings.defaultView)) { tab = Tab.of(settings.defaultView) }

    fun newEvent(draft: EventDraft) { editing = draft }

    AnimatedContent(
        targetState = showSettings,
        transitionSpec = {
            if (targetState) {
                slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { it } togetherWith
                    slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { -it / 8 } + fadeOut(tween(300))
            } else {
                slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 8 } + fadeIn(tween(200)) togetherWith
                    slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it }
            }
        },
        label = "settings",
    ) { inSettings ->
        if (inSettings) {
            SettingsScreen(
                settings = settings,
                calendars = calendars,
                countries = vm.countries,
                holidayStatus = holidayStatus,
                notificationsOn = vm.notificationsEnabled(settings) && notificationsAllowed,
                otherReminderApp = vm.otherReminderApp,
                onNotificationsChange = { on ->
                    vm.updateSettings { it.copy(eventNotifications = on) }
                    if (on) requestNotifications(fromSettings = true)
                },
                onChange = vm::updateSettings,
                onRefreshHolidays = vm::refreshHolidays,
                onExport = vm::exportIcs,
                onReadIcs = vm::readIcs,
                onImport = vm::importIcs,
                onCreateLocalCalendar = vm::createLocalCalendar,
                onBack = { showSettings = false },
            )
            return@AnimatedContent
        }

        Box(Modifier.fillMaxSize()) {
            // Top bar: Today chip on the left when away from today, settings gear on the right.
            val topInset = WindowInsets.statusBars
            val body = PaddingValues(start = 16.dp, end = 16.dp, top = 48.dp, bottom = DOCK_RESERVE + 24.dp)

            Box(Modifier.fillMaxSize().windowInsetsPadding(topInset)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        slideInHorizontally { w -> if (forward) w else -w } + fadeIn() togetherWith
                            slideOutHorizontally { w -> if (forward) -w else w } + fadeOut()
                    },
                    label = "tab",
                ) { current ->
                    val bottomInset = PaddingValues(
                        start = body.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        end = body.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        top = body.calculateTopPadding(),
                        bottom = body.calculateBottomPadding() + WindowInsets.navigationBars.asPaddingValuesBottom(),
                    )
                    when (current) {
                        Tab.MONTH -> MonthScreen(
                            visibleMonth = month,
                            selected = selected,
                            weekStart = settings.weekStart,
                            showWeekNumbers = settings.showWeekNumbers,
                            events = events,
                            holidays = holidays,
                            onShowMonth = vm::showMonth,
                            onSelect = vm::select,
                            onOpenDay = { vm.select(it); tab = Tab.DAY },
                            onNewEventOn = { newEvent(newDraftFor(it, settings.defaultReminderMinutes)) },
                            onEventClick = { detail = it },
                            onHolidayClick = { holidayDetail = it },
                            contentPadding = bottomInset,
                        )
                        Tab.AGENDA -> AgendaScreen(
                            events = events,
                            holidays = holidays,
                            onDayClick = { vm.select(it); tab = Tab.DAY },
                            onEventClick = { detail = it },
                            onHolidayClick = { holidayDetail = it },
                            onNewEvent = { newEvent(newDraftFor(LocalDate.now(), settings.defaultReminderMinutes)) },
                            contentPadding = bottomInset,
                        )
                        Tab.DAY -> DayScreen(
                            selected = selected,
                            weekStart = settings.weekStart,
                            events = events,
                            holidays = holidays,
                            onSelect = vm::select,
                            onEventClick = { detail = it },
                            onHolidayClick = { holidayDetail = it },
                            onNewEventAt = { newEvent(newDraftFor(it.toLocalDate(), settings.defaultReminderMinutes, it.toLocalTime())) },
                            contentPadding = PaddingValues(top = 48.dp, bottom = DOCK_RESERVE + WindowInsets.navigationBars.asPaddingValuesBottom()),
                        )
                        Tab.CLOCK -> ClockScreen(
                            vm = clockVm,
                            contentPadding = PaddingValues(top = 48.dp, bottom = DOCK_RESERVE + WindowInsets.navigationBars.asPaddingValuesBottom()),
                        )
                    }
                }

                TopActions(
                    showToday = selected != LocalDate.now(),
                    onToday = vm::goToToday,
                    onSettings = { showSettings = true },
                )
            }

            CalendarDock(
                selected = tab,
                onSelect = { tab = it },
                onAdd = {
                    val base = if (tab == Tab.AGENDA) LocalDate.now() else selected
                    newEvent(newDraftFor(base, settings.defaultReminderMinutes))
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = DOCK_CLEARANCE),
            )

            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = DOCK_RESERVE),
            )
        }
    }

    detail?.let { e ->
        EventDetailSheet(
            event = e,
            calendar = calendars.firstOrNull { it.id == e.calendarId },
            onDismiss = { detail = null },
            onEdit = {
                detail = null
                scope.launch { vm.loadDraft(e.eventId)?.let { editing = it } }
            },
            onDelete = {
                detail = null
                vm.deleteEvent(e.eventId)
                scope.launch { snackbar.showSnackbar("Event deleted") }
            },
        )
    }
    holidayDetail?.let { h -> HolidayDetailSheet(h) { holidayDetail = null } }
    editing?.let { draft ->
        EventEditorSheet(
            initial = draft,
            calendars = calendars,
            onDismiss = { editing = null },
            onSave = { d ->
                editing = null
                vm.saveEvent(d) { r ->
                    scope.launch {
                        snackbar.showSnackbar(
                            r.fold({ if (d.eventId == null) "Event added" else "Event updated" }, { "Couldn't save: ${it.message}" }),
                        )
                    }
                }
                vm.select(d.start.toLocalDate())
            },
            onCreateLocalCalendar = vm::createLocalCalendar,
        )
    }
}

@Composable
private fun TopActions(showToday: Boolean, onToday: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            showToday,
            enter = scaleIn(spring(dampingRatio = .5f, stiffness = 500f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Row(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .luminousElasticClickable(shape = RoundedCornerShape(22.dp), onClick = onToday)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Today, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("Today", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(48.dp).clip(CircleShape).elasticClickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun WindowInsets.asPaddingValuesBottom() =
    with(androidx.compose.ui.platform.LocalDensity.current) { getBottom(this).toDp() }
