package app.metabasis

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

internal data class Evolution(val date: String, val from: String, val to: String, val cycle: Int, val pass: String, val spent: Int)

internal data class AppState(
    val goalSteps: Int = 5000,
    val themeId: String = "amber",
    val logoMode: String = "monochrome",
    val appearance: String = "system",
    val userName: String = "",
    val introSeen: Boolean = false,
    val themeLocked: Boolean = false,
    val cycle: Int = 1,
    val pass: String = "forward",
    val stageIndex: Int = 0,
    val fragments: Int = 0,
    val stepRemainder: Int = 0,
    val lastCompletionDate: String = LocalDate.now().toString(),
    val today: String = LocalDate.now().toString(),
    val todaySteps: Int = 0,
    val history: Map<String, Int> = emptyMap(),
    val evolutions: List<Evolution> = emptyList(),
    val sensorMode: String = "Starting motion sensor…",
    val lastEvolutionAt: Long = 0L,
    val lastEvolutionFrom: String = "",
    val lastEvolutionTo: String = "",
    val liveLogs: List<String> = emptyList(),
    val liveLogTimes: List<Long> = emptyList(),
    val showLiveLogs: Boolean = true,
    val hapticsEnabled: Boolean = true
) {
    val stageCost: Int get() = 250
    private val pattern: List<Int> get() = when (cycle) {
        1 -> listOf(0, 1, 2, 3, 4)
        2 -> listOf(0, 2, 4, 1, 3)
        else -> if (cycle % 2 == 1) listOf(1, 3, 0, 2, 4) else listOf(0, 2, 4, 1, 3)
    }
    val sequence: List<Int> get() = if (pass == "forward") pattern else pattern.reversed()
    val stage: Int get() = sequence[stageIndex.coerceIn(0, 4)]
    val progress: Float get() = (fragments.toFloat() / stageCost).coerceIn(0f, 1f)
}

internal object StepLedger {
    private const val PREFS = "metabasis-ledger"
    private const val KEY_STATE = "state_json"
    private const val KEY_SENSOR = "last_sensor_value"
    private const val KEY_SENSOR_INITIALIZED = "sensor_initialized"
    private const val KEY_LIFETIME = "lifetime_steps"

    fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun load(context: Context): AppState = decode(preferences(context).getString(KEY_STATE, null)) ?: AppState()

    fun save(context: Context, state: AppState) {
        preferences(context).edit().putString(KEY_STATE, encode(state)).apply()
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DashboardWidget::class.java))
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, android.R.id.content)
            context.sendBroadcast(Intent(context, DashboardWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }

    fun setSensorMode(context: Context, mode: String) = save(context, load(context).copy(sensorMode = mode))

    fun recordHardwareTotal(context: Context, rawValue: Long) {
        val prefs = preferences(context)
        val initialized = prefs.getBoolean(KEY_SENSOR_INITIALIZED, false)
        val previous = prefs.getLong(KEY_SENSOR, 0L)
        if (!initialized) {
            prefs.edit().putBoolean(KEY_SENSOR_INITIALIZED, true).putLong(KEY_SENSOR, rawValue).apply()
            setSensorMode(context, "Phone step sensor · counting")
            return
        }
        val delta = if (rawValue >= previous) rawValue - previous else rawValue
        prefs.edit().putLong(KEY_SENSOR, rawValue).apply()
        if (delta > 0) addSteps(context, delta.toInt().coerceAtMost(100_000))
    }

    fun recordDetectedStep(context: Context, count: Int = 1) { if (count > 0) addSteps(context, count.coerceAtMost(1000)) }

    private fun addSteps(context: Context, delta: Int) {
        val prefs = preferences(context)
        val current = decode(prefs.getString(KEY_STATE, null)) ?: AppState()
        val date = LocalDate.now(ZoneId.systemDefault()).toString()
        if (date < current.today) return
        val base = if (current.today == date) current else current.copy(today = date, todaySteps = 0)
        val fullSteps = base.stepRemainder + delta
        val earned = fullSteps / 100
        val updatedHistory = base.history.toMutableMap().apply { put(date, (get(date) ?: 0) + delta) }
        val stamp = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))
        var next = base.copy(todaySteps = base.todaySteps + delta, stepRemainder = fullSteps % 100, fragments = base.fragments + earned, history = updatedHistory, liveLogs = (base.liveLogs + "$stamp · $delta steps").takeLast(5), liveLogTimes = (base.liveLogTimes + System.currentTimeMillis()).takeLast(5))
        next = evolveIfReady(next)
        prefs.edit().putLong(KEY_LIFETIME, prefs.getLong(KEY_LIFETIME, 0L) + delta).apply()
        save(context, next)
    }

    fun checkEvolution(context: Context) {
        val current = load(context)
        val date = LocalDate.now(ZoneId.systemDefault()).toString()
        val dayState = if (date > current.today) current.copy(today = date, todaySteps = 0, history = current.history + (date to 0)) else current
        val next = evolveIfReady(dayState)
        if (next != current) save(context, next)
    }

    private fun evolveIfReady(input: AppState): AppState {
        if (input.fragments < input.stageCost) return input
        val today = LocalDate.now(ZoneId.systemDefault())
        val last = runCatching { LocalDate.parse(input.lastCompletionDate) }.getOrNull()
        if (input.evolutions.isNotEmpty() && last != null && ChronoUnit.DAYS.between(last, today) < 2) return input
        val from = stageName(input.stage)
        val spent = input.stageCost
        val oldPass = input.pass
        val nextIndex = input.stageIndex + 1
        val state = if (nextIndex < 5) input.copy(fragments = input.fragments - spent, stageIndex = nextIndex, lastCompletionDate = today.toString(), themeLocked = true)
        else if (oldPass == "forward") input.copy(fragments = input.fragments - spent, pass = "reverse", stageIndex = 0, lastCompletionDate = today.toString(), themeLocked = true)
        else input.copy(fragments = input.fragments - spent, pass = "forward", stageIndex = 0, cycle = input.cycle + 1, lastCompletionDate = today.toString(), themeLocked = true)
        val event = Evolution(today.toString(), from, stageName(state.stage), state.cycle, oldPass, spent)
        return state.copy(evolutions = state.evolutions + event, history = state.history + (today.toString() to (state.history[today.toString()] ?: 0)), lastEvolutionAt = System.currentTimeMillis(), lastEvolutionFrom = from, lastEvolutionTo = stageName(state.stage))
    }

    fun updateSettings(context: Context, goal: Int? = null, theme: String? = null, logoMode: String? = null, appearance: String? = null, userName: String? = null) {
        val old = load(context)
        val chosenTheme = if (old.themeLocked) old.themeId else theme ?: old.themeId
        val next = old.copy(goalSteps = goal?.coerceIn(2500, 15000) ?: old.goalSteps, themeId = chosenTheme, logoMode = logoMode ?: old.logoMode, appearance = appearance ?: old.appearance, userName = userName ?: old.userName)
        save(context, if (goal != null) evolveIfReady(next) else next)
    }

    fun updateLiveLogs(context: Context, enabled: Boolean) = save(context, load(context).copy(showLiveLogs = enabled))
    fun updateHaptics(context: Context, enabled: Boolean) = save(context, load(context).copy(hapticsEnabled = enabled))

    fun finishIntro(context: Context, name: String, goal: Int, theme: String, appearance: String) {
        save(context, load(context).copy(userName = name.trim().ifBlank { "Walker" }, goalSteps = goal.coerceIn(2500, 15000), themeId = theme, appearance = appearance, introSeen = true))
    }

    fun replace(context: Context, state: AppState) = save(context, state)

    fun exportJson(context: Context): String {
        val state = load(context)
        return JSONObject().apply {
            put("schema_version", 1)
            put("user_config", JSONObject().put("goal_steps", state.goalSteps).put("stages_per_pass", 5).put("theme_id", state.themeId).put("logo_mode", state.logoMode).put("appearance", state.appearance).put("user_name", state.userName).put("intro_seen", state.introSeen).put("theme_locked", state.themeLocked).put("haptics_enabled", state.hapticsEnabled))
            put("progress", JSONObject().put("current_cycle", state.cycle).put("current_pass", state.pass).put("current_stage_index", state.stageIndex).put("current_fragments", state.fragments).put("step_remainder", state.stepRemainder).put("last_completion_date", state.lastCompletionDate).put("last_recorded_steps_today", state.todaySteps).put("last_recorded_steps_date", state.today))
            put("history", JSONArray().also { a -> state.history.toSortedMap().forEach { (date, steps) -> a.put(JSONObject().put("date", date).put("steps", steps)) } })
        }.toString(2)
    }

    fun importJson(json: String, sensorMode: String = "Phone step sensor · counting"): AppState {
        val root = JSONObject(json); require(root.optInt("schema_version") == 1)
        val config = root.getJSONObject("user_config"); val progress = root.getJSONObject("progress")
        val history = mutableMapOf<String, Int>(); val rows = root.optJSONArray("history") ?: JSONArray()
        for (i in 0 until rows.length()) { val row = rows.getJSONObject(i); history[LocalDate.parse(row.getString("date")).toString()] = row.optInt("steps").coerceAtLeast(0) }
        val events = mutableListOf<Evolution>(); val eventRows = root.optJSONArray("evolutions") ?: JSONArray()
        for (i in 0 until eventRows.length()) { val e = eventRows.getJSONObject(i); events += Evolution(e.optString("date"), e.optString("from"), e.optString("to"), e.optInt("cycle", 1), e.optString("pass", "forward"), e.optInt("spent")) }
        val logs = mutableListOf<String>(); val logRows = root.optJSONArray("live_logs") ?: JSONArray(); for (i in 0 until logRows.length()) logs += logRows.optString(i)
        val logTimes = mutableListOf<Long>(); val timeRows = root.optJSONArray("live_log_times") ?: JSONArray(); for (i in 0 until timeRows.length()) logTimes += timeRows.optLong(i)
        return AppState(goalSteps = config.optInt("goal_steps", 5000).coerceIn(2500, 15000), themeId = config.optString("theme_id", "amber"), logoMode = config.optString("logo_mode", "monochrome"), appearance = config.optString("appearance", "system"), userName = config.optString("user_name", ""), introSeen = config.optBoolean("intro_seen", false), themeLocked = config.optBoolean("theme_locked", false), cycle = progress.optInt("current_cycle", 1).coerceAtLeast(1), pass = if (progress.optString("current_pass") == "reverse") "reverse" else "forward", stageIndex = progress.optInt("current_stage_index").coerceIn(0, 4), fragments = progress.optInt("current_fragments").coerceAtLeast(0), stepRemainder = progress.optInt("step_remainder", 0).coerceIn(0, 99), lastCompletionDate = progress.optString("last_completion_date", LocalDate.now().toString()), today = progress.optString("last_recorded_steps_date", LocalDate.now().toString()), todaySteps = progress.optInt("last_recorded_steps_today").coerceAtLeast(0), history = history, evolutions = events, sensorMode = sensorMode, lastEvolutionAt = root.optLong("last_evolution_at"), lastEvolutionFrom = root.optString("last_evolution_from"), lastEvolutionTo = root.optString("last_evolution_to"), liveLogs = logs.takeLast(5), liveLogTimes = logTimes.takeLast(5), showLiveLogs = root.optBoolean("show_live_logs", true), hapticsEnabled = config.optBoolean("haptics_enabled", true))
    }

    private fun stageName(index: Int): String = when (index) { 0 -> "MARIGOLD"; 1 -> "CITRUS"; 2 -> "EMBER"; 3 -> "GARNET"; else -> "DUSK" }

    private fun encode(state: AppState): String = JSONObject().apply {
        put("schema_version", 1)
        put("user_config", JSONObject().put("goal_steps", state.goalSteps).put("theme_id", state.themeId).put("logo_mode", state.logoMode).put("appearance", state.appearance).put("user_name", state.userName).put("intro_seen", state.introSeen).put("theme_locked", state.themeLocked).put("haptics_enabled", state.hapticsEnabled))
        put("progress", JSONObject().put("current_cycle", state.cycle).put("current_pass", state.pass).put("current_stage_index", state.stageIndex).put("current_fragments", state.fragments).put("step_remainder", state.stepRemainder).put("last_completion_date", state.lastCompletionDate).put("last_recorded_steps_today", state.todaySteps).put("last_recorded_steps_date", state.today))
        put("history", JSONArray().also { a -> state.history.forEach { (date, steps) -> a.put(JSONObject().put("date", date).put("steps", steps)) } })
        put("evolutions", JSONArray().also { a -> state.evolutions.forEach { e -> a.put(JSONObject().put("date", e.date).put("from", e.from).put("to", e.to).put("cycle", e.cycle).put("pass", e.pass).put("spent", e.spent)) } })
        put("sensor_mode", state.sensorMode).put("last_evolution_at", state.lastEvolutionAt).put("last_evolution_from", state.lastEvolutionFrom).put("last_evolution_to", state.lastEvolutionTo).put("live_logs", JSONArray(state.liveLogs)).put("live_log_times", JSONArray(state.liveLogTimes)).put("show_live_logs", state.showLiveLogs)
    }.toString()

    private fun decode(json: String?): AppState? {
        return try {
        if (json.isNullOrBlank()) return null
        val root = JSONObject(json); val config = root.getJSONObject("user_config"); val progress = root.getJSONObject("progress")
        val history = mutableMapOf<String, Int>(); val rows = root.optJSONArray("history") ?: JSONArray()
        for (i in 0 until rows.length()) { val row = rows.getJSONObject(i); history[row.getString("date")] = row.optInt("steps").coerceAtLeast(0) }
        val events = mutableListOf<Evolution>(); val eventRows = root.optJSONArray("evolutions") ?: JSONArray()
        for (i in 0 until eventRows.length()) { val e = eventRows.getJSONObject(i); events += Evolution(e.optString("date"), e.optString("from"), e.optString("to"), e.optInt("cycle", 1), e.optString("pass", "forward"), e.optInt("spent")) }
        val logs = mutableListOf<String>(); val logRows = root.optJSONArray("live_logs") ?: JSONArray(); for (i in 0 until logRows.length()) logs += logRows.optString(i)
        val logTimes = mutableListOf<Long>(); val timeRows = root.optJSONArray("live_log_times") ?: JSONArray(); for (i in 0 until timeRows.length()) logTimes += timeRows.optLong(i)
        AppState(config.optInt("goal_steps", 5000).coerceIn(2500, 15000), config.optString("theme_id", "amber"), config.optString("logo_mode", "monochrome"), config.optString("appearance", "system"), config.optString("user_name", ""), config.optBoolean("intro_seen", false), config.optBoolean("theme_locked", false), progress.optInt("current_cycle", 1).coerceAtLeast(1), if (progress.optString("current_pass") == "reverse") "reverse" else "forward", progress.optInt("current_stage_index").coerceIn(0, 4), progress.optInt("current_fragments").coerceAtLeast(0), progress.optInt("step_remainder", 0).coerceIn(0, 99), progress.optString("last_completion_date", LocalDate.now().toString()), progress.optString("last_recorded_steps_date", LocalDate.now().toString()), progress.optInt("last_recorded_steps_today").coerceAtLeast(0), history, events, root.optString("sensor_mode", "Starting motion sensor…"), root.optLong("last_evolution_at"), root.optString("last_evolution_from"), root.optString("last_evolution_to"), logs.takeLast(5), logTimes.takeLast(5), root.optBoolean("show_live_logs", true), config.optBoolean("haptics_enabled", true))
        } catch (_: Exception) { null }
    }
}
