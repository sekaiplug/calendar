package app.expressivecalendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class SessionType(val label: String) {
    POMODORO("Pomodoro"),
    STOPWATCH("Stopwatch Focus"),
    TIMER("Timer")
}

data class FocusSession(
    val id: String = UUID.randomUUID().toString(),
    val type: SessionType,
    val durationSeconds: Long,
    val date: String, // YYYY-MM-DD
    val timestampMillis: Long = System.currentTimeMillis(),
    val tag: String = "Focus"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("durationSeconds", durationSeconds)
        put("date", date)
        put("timestampMillis", timestampMillis)
        put("tag", tag)
    }

    companion object {
        fun fromJson(json: JSONObject): FocusSession = FocusSession(
            id = json.optString("id", UUID.randomUUID().toString()),
            type = try { SessionType.valueOf(json.optString("type", SessionType.POMODORO.name)) } catch (_: Exception) { SessionType.POMODORO },
            durationSeconds = json.optLong("durationSeconds", 0L),
            date = json.optString("date", LocalDate.now().toString()),
            timestampMillis = json.optLong("timestampMillis", System.currentTimeMillis()),
            tag = json.optString("tag", "Focus")
        )
    }
}

data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val label: String = "Alarm",
    val isEnabled: Boolean = true,
    val daysOfWeek: Set<Int> = emptySet() // 1 = Monday .. 7 = Sunday
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("isEnabled", isEnabled)
        put("daysOfWeek", JSONArray(daysOfWeek))
    }

    companion object {
        fun fromJson(json: JSONObject): AlarmItem {
            val daysJson = json.optJSONArray("daysOfWeek")
            val days = mutableSetOf<Int>()
            if (daysJson != null) {
                for (i in 0 until daysJson.length()) {
                    days.add(daysJson.optInt(i))
                }
            }
            return AlarmItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                hour = json.optInt("hour", 7),
                minute = json.optInt("minute", 0),
                label = json.optString("label", "Alarm"),
                isEnabled = json.optBoolean("isEnabled", true),
                daysOfWeek = days
            )
        }
    }
}

private val Context.clockDataStore: DataStore<Preferences> by preferencesDataStore("expressive_clock_storage")

class ClockRepository(private val context: Context) {
    private val sessionsKey = stringPreferencesKey("focus_sessions_history")
    private val alarmsKey = stringPreferencesKey("active_alarms")

    val sessionsFlow: Flow<List<FocusSession>> = context.clockDataStore.data.map { prefs ->
        val raw = prefs[sessionsKey] ?: return@map emptyList()
        try {
            val array = JSONArray(raw)
            val list = mutableListOf<FocusSession>()
            for (i in 0 until array.length()) {
                list.add(FocusSession.fromJson(array.getJSONObject(i)))
            }
            list.sortedByDescending { it.timestampMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    val alarmsFlow: Flow<List<AlarmItem>> = context.clockDataStore.data.map { prefs ->
        val raw = prefs[alarmsKey] ?: return@map defaultAlarms()
        try {
            val array = JSONArray(raw)
            val list = mutableListOf<AlarmItem>()
            for (i in 0 until array.length()) {
                list.add(AlarmItem.fromJson(array.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            defaultAlarms()
        }
    }

    suspend fun recordSession(session: FocusSession) {
        context.clockDataStore.edit { prefs ->
            val raw = prefs[sessionsKey]
            val array = if (raw != null) {
                try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
            } else JSONArray()
            array.put(session.toJson())
            prefs[sessionsKey] = array.toString()
        }
    }

    suspend fun saveAlarms(alarms: List<AlarmItem>) {
        context.clockDataStore.edit { prefs ->
            val array = JSONArray()
            alarms.forEach { array.put(it.toJson()) }
            prefs[alarmsKey] = array.toString()
        }
    }

    suspend fun toggleAlarm(alarmId: String) {
        context.clockDataStore.edit { prefs ->
            val raw = prefs[alarmsKey] ?: return@edit
            val array = JSONArray(raw)
            val updated = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") == alarmId) {
                    obj.put("isEnabled", !obj.optBoolean("isEnabled", true))
                }
                updated.put(obj)
            }
            prefs[alarmsKey] = updated.toString()
        }
    }

    private fun defaultAlarms(): List<AlarmItem> = listOf(
        AlarmItem(hour = 7, minute = 0, label = "Morning Wake up", isEnabled = true),
        AlarmItem(hour = 22, minute = 30, label = "Wind Down", isEnabled = false)
    )
}
