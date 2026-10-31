package app.metabasis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class Evolution(val date: String, val from: String, val to: String, val cycle: Int, val pass: String, val spent: Int)

internal data class AppState(
    val goalSteps: Int = 5000,
    val themeId: String = "amber",
    val appearance: String = "system",
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
    val lastEvolutionTo: String = ""
) {
    val stageCost: Int get() = (goalSteps / 5).coerceAtLeast(1)
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
    private const val KEY_ACCEL_LAST = "accel_last_step_ms"

    fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): AppState = decode(preferences(context).getString(KEY_STATE, null)) ?: AppState()

    fun save(context: Context, state: AppState) {
        preferences(context).edit().putString(KEY_STATE, encode(state)).apply()
    }

    fun setSensorMode(context: Context, mode: String) {
        val prefs = preferences(context)
        val current = decode(prefs.getString(KEY_STATE, null)) ?: AppState()
        save(context, current.copy(sensorMode = mode))
    }

    fun recordHardwareTotal(context: Context, rawValue: Long) {
        val prefs = preferences(context)
        val initialized = prefs.getBoolean(KEY_SENSOR_INITIALIZED, false)
        val previous = prefs.getLong(KEY_SENSOR, 0L)
        if (!initialized) {
            // TYPE_STEP_COUNTER is cumulative since boot. Establish a clean, honest baseline on first use.
            prefs.edit().putBoolean(KEY_SENSOR_INITIALIZED, true).putLong(KEY_SENSOR, rawValue).apply()
            setSensorMode(context, "Phone step sensor · counting")
            return
        }
        val delta = if (rawValue >= previous) rawValue - previous else rawValue // Sensor restarted after device reboot.
        prefs.edit().putLong(KEY_SENSOR, rawValue).apply()
        if (delta > 0) addSteps(context, delta.toInt().coerceAtMost(100_000))
    }

    fun recordDetectedStep(context: Context, count: Int = 1) {
        if (count > 0) addSteps(context, count.coerceAtMost(1000))
    }

    private fun addSteps(context: Context, delta: Int) {
        val prefs = preferences(context)
        val current = decode(prefs.getString(KEY_STATE, null)) ?: AppState()
        val date = LocalDate.now(ZoneId.systemDefault()).toString()
        if (date < current.today) return // Clock rollback: discard this delta until local time catches up.
        val base = if (current.today == date) current else current.copy(today = date, todaySteps = 0)
        val fullSteps = base.stepRemainder + delta
        val earned = fullSteps / 1000
        val updatedHistory = base.history.toMutableMap().apply { put(date, (get(date) ?: 0) + delta) }
        var next = base.copy(
            todaySteps = base.todaySteps + delta,
            stepRemainder = fullSteps % 1000,
            fragments = base.fragments + earned,
            history = updatedHistory
        )
        next = evolveIfReady(next)
        val lifetime = prefs.getLong(KEY_LIFETIME, 0L) + delta
        prefs.edit().putLong(KEY_LIFETIME, lifetime).apply()
        save(context, next)
    }

    fun checkEvolution(context: Context) {
        val current = load(context)
        val date = LocalDate.now(ZoneId.systemDefault()).toString()
        val dayState = if (date > current.today) current.copy(today=date, todaySteps=0, history=current.history + (date to 0)) else current
        val next = evolveIfReady(dayState)
        if (next != current) save(context, next)
    }

    private fun evolveIfReady(input: AppState): AppState {
        var state = input
        val today = LocalDate.now(ZoneId.systemDefault())
        val elapsed = try { ChronoUnit.DAYS.between(LocalDate.parse(state.lastCompletionDate), today) } catch (_: Exception) { 0L }
        if (state.fragments < state.stageCost || elapsed < 2L) return state
        val from = stageName(state.stage)
        val spent = state.stageCost
        val oldPass = state.pass
        val nextIndex = state.stageIndex + 1
        state = if (nextIndex < 5) {
            state.copy(fragments = state.fragments - spent, stageIndex = nextIndex, lastCompletionDate = today.toString())
        } else if (oldPass == "forward") {
            state.copy(fragments = state.fragments - spent, pass = "reverse", stageIndex = 0, lastCompletionDate = today.toString())
        } else {
            state.copy(fragments = state.fragments - spent, pass = "forward", stageIndex = 0, cycle = state.cycle + 1, lastCompletionDate = today.toString())
        }
        val to = stageName(state.stage)
        val event = Evolution(today.toString(), from, to, state.cycle, oldPass, spent)
        val historyWithEvolutionDay = state.history.toMutableMap().apply { putIfAbsent(today.toString(), 0) }
        return state.copy(
            evolutions = state.evolutions + event,
            history = historyWithEvolutionDay,
            lastEvolutionAt = System.currentTimeMillis(),
            lastEvolutionFrom = from,
            lastEvolutionTo = to
        )
    }

    fun updateSettings(context: Context, goal: Int? = null, theme: String? = null, appearance: String? = null) {
        val old = load(context)
        save(context, old.copy(goalSteps = goal?.coerceIn(2500, 15000) ?: old.goalSteps, themeId = theme ?: old.themeId, appearance = appearance ?: old.appearance))
        checkEvolution(context)
    }

    fun replace(context: Context, state: AppState) {
        save(context, state)
    }

    fun exportJson(context: Context): String {
        val state = load(context)
        return JSONObject().apply {
            put("schema_version", 1)
            put("user_config", JSONObject().put("goal_steps", state.goalSteps).put("stages_per_pass", 5).put("theme_id", state.themeId).put("appearance", state.appearance))
            put("progress", JSONObject().put("current_cycle", state.cycle).put("current_pass", state.pass).put("current_stage_index", state.stageIndex)
                .put("current_fragments", state.fragments).put("step_remainder", state.stepRemainder).put("last_completion_date", state.lastCompletionDate)
                .put("last_recorded_steps_today", state.todaySteps).put("last_recorded_steps_date", state.today))
            put("history", JSONArray().also { array ->
                state.history.toSortedMap().forEach { (date, steps) ->
                    val logs = state.evolutions.filter { it.date == date }
                    array.put(JSONObject().put("date", date).put("steps", steps).put("evolutions", JSONArray().also { ev -> logs.forEach { item ->
                        ev.put(JSONObject().put("from_stage", item.from.lowercase()).put("to_stage", item.to.lowercase()).put("fragments_spent", item.spent).put("pass", item.pass).put("cycle", item.cycle))
                    } }))
                }
            })
        }.toString(2)
    }

    fun importJson(json: String, sensorMode: String = "Phone step sensor · counting"): AppState {
        val root = JSONObject(json)
        require(root.optInt("schema_version") == 1) { "Unsupported Metabasis export version" }
        val config = root.getJSONObject("user_config")
        val progress = root.getJSONObject("progress")
        val history = mutableMapOf<String, Int>()
        val events = mutableListOf<Evolution>()
        val rows = root.optJSONArray("history") ?: JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val date = LocalDate.parse(row.getString("date")).toString()
            history[date] = row.optInt("steps").coerceAtLeast(0)
            val evolutionRows = row.optJSONArray("evolutions") ?: JSONArray()
            for (j in 0 until evolutionRows.length()) {
                val event = evolutionRows.getJSONObject(j)
                events += Evolution(date, event.optString("from_stage").uppercase(), event.optString("to_stage").uppercase(), event.optInt("cycle", 1), event.optString("pass", "forward"), event.optInt("fragments_spent", 0))
            }
        }
        return AppState(
            goalSteps = config.optInt("goal_steps", 5000).coerceIn(2500, 15000), themeId = config.optString("theme_id", "amber"),
            appearance = config.optString("appearance", "system"), cycle = progress.optInt("current_cycle", 1).coerceAtLeast(1),
            pass = if (progress.optString("current_pass") == "reverse") "reverse" else "forward", stageIndex = progress.optInt("current_stage_index").coerceIn(0, 4),
            fragments = progress.optInt("current_fragments").coerceAtLeast(0), stepRemainder = progress.optInt("step_remainder").coerceIn(0, 999),
            lastCompletionDate = progress.optString("last_completion_date", LocalDate.now().toString()),
            today = progress.optString("last_recorded_steps_date", LocalDate.now().toString()), todaySteps = progress.optInt("last_recorded_steps_today").coerceAtLeast(0),
            history = history, evolutions = events, sensorMode = sensorMode
        )
    }

    private fun stageName(index: Int): String = when (index) { 0 -> "MARIGOLD"; 1 -> "CITRUS"; 2 -> "EMBER"; 3 -> "GARNET"; else -> "DUSK" }

    private fun encode(state: AppState): String = JSONObject().apply {
        put("schema_version", 1)
        put("user_config", JSONObject().put("goal_steps", state.goalSteps).put("theme_id", state.themeId).put("appearance", state.appearance))
        put("progress", JSONObject().put("current_cycle", state.cycle).put("current_pass", state.pass).put("current_stage_index", state.stageIndex).put("current_fragments", state.fragments)
            .put("step_remainder", state.stepRemainder).put("last_completion_date", state.lastCompletionDate).put("last_recorded_steps_today", state.todaySteps).put("last_recorded_steps_date", state.today))
        put("history", JSONArray().also { array -> state.history.forEach { (date, steps) -> array.put(JSONObject().put("date", date).put("steps", steps)) } })
        put("evolutions", JSONArray().also { array -> state.evolutions.forEach { e -> array.put(JSONObject().put("date", e.date).put("from", e.from).put("to", e.to).put("cycle", e.cycle).put("pass", e.pass).put("spent", e.spent)) } })
        put("sensor_mode", state.sensorMode).put("last_evolution_at", state.lastEvolutionAt).put("last_evolution_from", state.lastEvolutionFrom).put("last_evolution_to", state.lastEvolutionTo)
    }.toString()

    private fun decode(json: String?): AppState? {
        if (json.isNullOrBlank()) return null
        return try {
        val root = JSONObject(json); val config = root.getJSONObject("user_config"); val progress = root.getJSONObject("progress")
        val history = mutableMapOf<String, Int>(); val rows = root.optJSONArray("history") ?: JSONArray()
        for (i in 0 until rows.length()) { val row=rows.getJSONObject(i); history[row.getString("date")]=row.optInt("steps").coerceAtLeast(0) }
        val events=mutableListOf<Evolution>(); val eventRows=root.optJSONArray("evolutions") ?: JSONArray()
        for(i in 0 until eventRows.length()) { val e=eventRows.getJSONObject(i); events += Evolution(e.optString("date"),e.optString("from"),e.optString("to"),e.optInt("cycle",1),e.optString("pass","forward"),e.optInt("spent")) }
        AppState(config.optInt("goal_steps",5000).coerceIn(2500,15000), config.optString("theme_id","amber"), config.optString("appearance","system"),
            progress.optInt("current_cycle",1).coerceAtLeast(1), if(progress.optString("current_pass")=="reverse") "reverse" else "forward", progress.optInt("current_stage_index").coerceIn(0,4),
            progress.optInt("current_fragments").coerceAtLeast(0), progress.optInt("step_remainder").coerceIn(0,999), progress.optString("last_completion_date",LocalDate.now().toString()),
            progress.optString("last_recorded_steps_date",LocalDate.now().toString()), progress.optInt("last_recorded_steps_today").coerceAtLeast(0), history, events,
            root.optString("sensor_mode","Starting motion sensor…"), root.optLong("last_evolution_at"), root.optString("last_evolution_from"), root.optString("last_evolution_to"))
        } catch (_: Exception) { null }
    }
}
