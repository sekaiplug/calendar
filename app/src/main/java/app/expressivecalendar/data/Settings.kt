package app.expressivecalendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.expressivecalendar.ui.theme.Palette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/** Every preference the app has, in one tree. */
data class CalendarSettings(
    val theme: ThemeSettings = ThemeSettings(),
    val weekStart: WeekStart = WeekStart.SYSTEM,
    val defaultView: CalendarView = CalendarView.MONTH,
    /** Minutes before start; -1 means no reminder. */
    val defaultReminderMinutes: Int = 10,
    val showHolidays: Boolean = true,
    /** ISO 3166-1 alpha-2 codes; empty to follow the device locale. */
    val holidayCountries: Set<String> = emptySet(),
    val hiddenCalendarIds: Set<Long> = emptySet(),
    val showWeekNumbers: Boolean = false,
    /** Show reminder notifications ourselves; null = automatic (on unless another calendar app does). */
    val eventNotifications: Boolean? = null,
) {
    /** The countries to show holidays for, alphabetical by display name. */
    val effectiveCountries: List<String>
        get() = holidayCountries.sortedBy { countryName(it) }.ifEmpty { listOf(Locale.getDefault().country.ifBlank { "US" }) }
}

data class ThemeSettings(
    val dynamicColor: Boolean = true,
    val palette: Palette = Palette.CONCEPT,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val amoledBlack: Boolean = false,
)

enum class DarkMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class CalendarView(val label: String) { MONTH("Month"), AGENDA("Agenda"), DAY("Day"), CLOCK("Clock") }

enum class WeekStart(val label: String, val day: DayOfWeek?) {
    SYSTEM("System default", null),
    SATURDAY("Saturday", DayOfWeek.SATURDAY),
    SUNDAY("Sunday", DayOfWeek.SUNDAY),
    MONDAY("Monday", DayOfWeek.MONDAY);

    fun firstDay(): DayOfWeek = day ?: WeekFields.of(Locale.getDefault()).firstDayOfWeek

    /** The seven weekdays, rotated to start on the configured first day. */
    fun order(): List<DayOfWeek> {
        val first = firstDay()
        return (0..6).map { first.plus(it.toLong()) }
    }
}

val REMINDER_CHOICES: List<Int> = listOf(-1, 0, 5, 10, 15, 30, 60, 120, 1440, 10080)

fun reminderLabel(minutes: Int): String = when {
    minutes < 0 -> "No reminder"
    minutes == 0 -> "At start time"
    minutes < 60 -> "$minutes minutes before"
    minutes == 60 -> "1 hour before"
    minutes < 1440 -> "${minutes / 60} hours before"
    minutes == 1440 -> "1 day before"
    minutes == 10080 -> "1 week before"
    else -> "${minutes / 1440} days before"
}

private val Context.store: DataStore<Preferences> by preferencesDataStore("calendar_settings")

class SettingsStore(private val context: Context) {
    private object K {
        val dynamic = booleanPreferencesKey("dynamic_color")
        val palette = stringPreferencesKey("palette")
        val dark = stringPreferencesKey("dark_mode")
        val amoled = booleanPreferencesKey("amoled")
        val weekStart = stringPreferencesKey("week_start")
        val view = stringPreferencesKey("default_view")
        val reminder = intPreferencesKey("default_reminder")
        val showHolidays = booleanPreferencesKey("show_holidays")
        /** Pre-0.2.0 single-country setting; read once as a fallback, then replaced. */
        val legacyCountry = stringPreferencesKey("holiday_country")
        val countries = stringSetPreferencesKey("holiday_countries")
        val hidden = stringSetPreferencesKey("hidden_calendars")
        val weekNumbers = booleanPreferencesKey("week_numbers")
        val notifications = booleanPreferencesKey("event_notifications")
    }

    val settings: Flow<CalendarSettings> = context.store.data.map { p ->
        CalendarSettings(
            theme = ThemeSettings(
                dynamicColor = p[K.dynamic] ?: true,
                palette = p[K.palette].enumOr(Palette.CONCEPT),
                darkMode = p[K.dark].enumOr(DarkMode.SYSTEM),
                amoledBlack = p[K.amoled] ?: false,
            ),
            weekStart = p[K.weekStart].enumOr(WeekStart.SYSTEM),
            defaultView = p[K.view].enumOr(CalendarView.MONTH),
            defaultReminderMinutes = p[K.reminder] ?: 10,
            showHolidays = p[K.showHolidays] ?: true,
            holidayCountries = p[K.countries] ?: p[K.legacyCountry]?.let { setOf(it) } ?: emptySet(),
            hiddenCalendarIds = p[K.hidden].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            showWeekNumbers = p[K.weekNumbers] ?: false,
            eventNotifications = p[K.notifications],
        )
    }

    suspend fun save(s: CalendarSettings) {
        context.store.edit { p ->
            p[K.dynamic] = s.theme.dynamicColor
            p[K.palette] = s.theme.palette.name
            p[K.dark] = s.theme.darkMode.name
            p[K.amoled] = s.theme.amoledBlack
            p[K.weekStart] = s.weekStart.name
            p[K.view] = s.defaultView.name
            p[K.reminder] = s.defaultReminderMinutes
            p[K.showHolidays] = s.showHolidays
            p[K.countries] = s.holidayCountries
            p.remove(K.legacyCountry)
            p[K.hidden] = s.hiddenCalendarIds.map { it.toString() }.toSet()
            p[K.weekNumbers] = s.showWeekNumbers
            if (s.eventNotifications != null) p[K.notifications] = s.eventNotifications else p.remove(K.notifications)
        }
    }
}

private inline fun <reified T : Enum<T>> String?.enumOr(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
