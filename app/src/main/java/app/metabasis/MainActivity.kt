package app.metabasis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.min

private val chassis = Color(0xFFE2E4E8)
private val ink = Color(0xFF121214)
private val glass = Color(0xFF0A0A0A)
private val white = Color(0xFFF6F6F4)
private val themes = listOf(
    listOf(Color(0xFFFFE0B2), Color(0xFFFFC266), Color(0xFFFFA133), Color(0xFFE0661A), Color(0xFFA63D0A)),
    listOf(Color(0xFFD7EBB5), Color(0xFFA3D46A), Color(0xFF5FA83D), Color(0xFF2E7D32), Color(0xFF14401A)),
    listOf(Color(0xFFD4E8B0), Color(0xFFF2C94C), Color(0xFFE07A2E), Color(0xFFC0392B), Color(0xFF6B4226)),
    listOf(Color(0xFFFFD9E3), Color(0xFFFFB3C7), Color(0xFFF06292), Color(0xFFC2185B), Color(0xFF7B1E3F)),
    listOf(Color(0xFFDCE6F5), Color(0xFF7FA8E0), Color(0xFF4A5FC1), Color(0xFF6A3FA0), Color(0xFF2A1B4A))
)
private val themeNames = listOf("Amber", "Lush", "Shed", "Cherry", "Dusk")
private val stageNames = listOf("MARIGOLD", "CITRUS", "EMBER", "GARNET", "DUSK")
private val SpaceGrotesk = FontFamily(Font(R.font.space_grotesk))
private val Inter = FontFamily(Font(R.font.inter))

private data class StateData(val goal: Int = 5000, val theme: Int = 0, val dark: Boolean = false,
    val cycle: Int = 1, val pass: Int = 0, val index: Int = 0, val fragments: Int = 0,
    val remainder: Int = 0, val lastSteps: Long = 0, val lastStepsDate: String = LocalDate.now().toString(), val lastCompletion: String = LocalDate.now().minusDays(2).toString(),
    val history: Map<String, Int> = emptyMap()) {
    val stageCost get() = (goal / 5).coerceAtLeast(1)
    private val cyclePattern: List<Int> get() = when (cycle % 3) { 1 -> listOf(0,1,2,3,4); 2 -> listOf(0,2,4,1,3); else -> listOf(1,3,0,2,4) }
    val sequence: List<Int> get() = if (pass == 0) cyclePattern else cyclePattern.reversed()
    val stage get() = sequence[index.coerceIn(0,4)]
    val progress get() = (fragments.toFloat() / stageCost).coerceIn(0f, 1f)
}

class MainActivity : ComponentActivity() {
    private lateinit var hc: HealthConnectClient
    private val perms = setOf(HealthPermission.getReadPermission(StepsRecord::class))
    private val permissionLauncher = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        hc = HealthConnectClient.getOrCreate(this)
        setContent { Metabasis(hc, perms) { permissionLauncher.launch(perms) } }
    }
}

@Composable private fun Metabasis(hc: HealthConnectClient, permissions: Set<String>, onInstall: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("metabasis", 0) }
    var data by remember { mutableStateOf(load(prefs)) }
    var tab by remember { mutableStateOf(0) }
    var permission by remember { mutableStateOf(false) }
    var syncNote by remember { mutableStateOf("Connect Health Connect to sync steps") }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now().toString()
    val led = themes[data.theme][data.stage]
    val bg = if (data.dark) ink else chassis

    fun save(updated: StateData) { data = updated; prefs.edit().putString("state", encode(updated)).apply() }
    fun sync() { scope.launch {
        try {
            if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) { syncNote = "Health Connect is not available on this device"; return@launch }
            val granted = hc.permissionController.getGrantedPermissions()
            if (!granted.containsAll(permissions)) { syncNote = "Grant step access in Health Connect"; return@launch }
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val end = Instant.now()
            val response = hc.readRecords(ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(start, end)))
            val total = response.records.sumOf { it.count }
            val current = load(prefs)
            val baseline = if (current.lastStepsDate == today) current.lastSteps else 0L
            if (total >= baseline) {
                val delta = total - baseline
                var fragments = current.fragments
                var rem = current.remainder
                val earned = (delta + rem) / 1000
                rem = ((delta + rem) % 1000).toInt()
                fragments += earned.toInt()
                var next = current.copy(lastSteps = total, lastStepsDate = today, fragments = fragments, remainder = rem,
                    history = current.history + (today to total.toInt()))
                var completed = 0
                while (next.fragments >= next.stageCost && ChronoUnit.DAYS.between(LocalDate.parse(next.lastCompletion), LocalDate.now()) >= 2 && completed < 30) {
                    val nextIndex = next.index + 1
                    next = if (nextIndex < 5) next.copy(fragments = next.fragments - next.stageCost, index = nextIndex, lastCompletion = today)
                    else next.copy(fragments = next.fragments - next.stageCost, index = 0, pass = 1-next.pass, cycle = if (next.pass == 1) next.cycle + 1 else next.cycle, lastCompletion = today)
                    completed++
                }
                save(next)
                syncNote = if (completed > 0) "Evolution complete · $completed stage${if (completed==1) "" else "s"}" else "Synced · ${"%,d".format(total)} steps"
            } else syncNote = "Step total moved backward; sync ignored"
        } catch (_: Exception) { syncNote = "Could not read steps. Check Health Connect permissions." }
    } }
    LaunchedEffect(Unit) { sync() }

    MaterialTheme(colorScheme = if (data.dark) darkColorScheme(background=ink, surface=glass, onSurface=white) else lightColorScheme(background=chassis, surface=glass, onSurface=white), typography = Typography(bodyLarge = TextStyle(fontFamily=Inter), bodyMedium = TextStyle(fontFamily=Inter), bodySmall = TextStyle(fontFamily=Inter), labelSmall = TextStyle(fontFamily=Inter))) {
        Column(Modifier.fillMaxSize().background(bg).padding(horizontal=18.dp).padding(top=24.dp, bottom=10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) { Text("METABASIS", color=if(data.dark) white else ink, fontSize=12.sp, fontWeight=FontWeight.Bold, letterSpacing=3.sp); Text(listOf("BASE", "TRACES", "TUNE")[tab], color=if(data.dark) Color.LightGray else Color.DarkGray, fontSize=10.sp, letterSpacing=2.sp) }
                Text("●", color=led, fontSize=12.sp); Spacer(Modifier.width(8.dp)); Text("OFFLINE", color=if(data.dark) Color.LightGray else Color.Gray, fontSize=9.sp, letterSpacing=1.sp)
            }
            Spacer(Modifier.height(18.dp))
            when(tab) {
                0 -> BaseScreen(data, led, syncNote, ::sync, onInstall)
                1 -> TracesScreen(data, led)
                else -> TuneScreen(data, ::save, prefs)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(30.dp)).background(Color(0xDD202124), RoundedCornerShape(30.dp)).padding(6.dp), horizontalArrangement=Arrangement.SpaceEvenly) {
                listOf("BASE", "TRACES", "TUNE").forEachIndexed { i, label ->
                    Column(Modifier.weight(1f).clickable { tab=i }.background(if(tab==i) Color(0xFF3B3B3D) else Color.Transparent, RoundedCornerShape(24.dp)).padding(vertical=10.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(listOf("◉", "▦", "⚙")[i], color=if(tab==i) led else Color.LightGray, fontSize=16.sp)
                        Text(label, color=if(tab==i) white else Color.LightGray, fontSize=9.sp, letterSpacing=1.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun BaseScreen(d: StateData, led: Color, note: String, sync: () -> Unit, connect: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.size(112.dp).shadow(10.dp, CircleShape).background(glass, CircleShape), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                Text("TODAY", color=Color.Gray, fontSize=9.sp, letterSpacing=2.sp); Spacer(Modifier.height(5.dp)); Text("%,d".format(d.history[LocalDate.now().toString()] ?: d.lastSteps), color=white, fontSize=19.sp, fontWeight=FontWeight.Bold); Text("STEPS", color=Color.Gray, fontSize=8.sp)
            }
            Box(Modifier.size(220.dp).shadow(14.dp, CircleShape).background(glass, CircleShape), contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                    val r = size.minDimension/2 - 5.dp.toPx(); val c = Offset(size.width/2, size.height/2)
                    drawCircle(Color(0xFF2B2B2D), r, c, style=Stroke(1.dp.toPx()))
                    for (i in 0 until 24) { val a = Math.toRadians((i*15-90).toDouble()); val p=Offset(c.x+kotlin.math.cos(a).toFloat()*r, c.y+kotlin.math.sin(a).toFloat()*r); drawCircle(if(i < floor(d.progress*24)) led else Color(0xFF373739), if(i < floor(d.progress*24)) 4.2.dp.toPx() else 2.5.dp.toPx(), p) }
                }
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(d.cycle.toString(), color=led, fontSize=66.sp, fontWeight=FontWeight.Light, lineHeight=68.sp)
                    Text(stageNames[d.stage], color=white, fontSize=10.sp, letterSpacing=3.sp)
                    Text(if(d.pass==0) "FORWARD PASS" else "REVERSE PASS", color=Color.Gray, fontSize=8.sp, letterSpacing=1.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().height(58.dp).shadow(8.dp, RoundedCornerShape(30.dp)).background(glass, RoundedCornerShape(30.dp)).padding(8.dp), verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(d.progress.coerceAtLeast(0.04f)).fillMaxHeight().background(led.copy(alpha=0.2f + d.progress*0.8f), RoundedCornerShape(24.dp)))
            Text("  ${d.fragments} / ${d.stageCost} FRAGMENTS", color=white, fontSize=11.sp, fontWeight=FontWeight.Medium, modifier=Modifier.padding(horizontal=8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("${(d.progress*100).toInt()}% · ${themeNames[d.theme].uppercase()} · 1 fragment / 1,000 steps", color=if(d.dark) Color.LightGray else Color.DarkGray, fontSize=10.sp, letterSpacing=0.5.sp)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(onClick=sync, colors=ButtonDefaults.buttonColors(containerColor=led, contentColor=ink), modifier=Modifier.weight(1f)) { Text("SYNC STEPS", fontSize=11.sp, fontWeight=FontWeight.Bold) }
            OutlinedButton(onClick=connect, modifier=Modifier.weight(1f), colors=ButtonDefaults.outlinedButtonColors(contentColor=if(d.dark) white else ink)) { Text("CONNECT", fontSize=11.sp) }
        }
        Spacer(Modifier.height(12.dp)); Text(note, color=if(d.dark) Color.LightGray else Color.DarkGray, fontSize=10.sp, textAlign=TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().background(glass, RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(led, CircleShape)); Spacer(Modifier.width(12.dp)); Column { Text("NEXT FOOTPRINT", color=Color.Gray, fontSize=9.sp, letterSpacing=2.sp); Text("Every walk leaves a trace.", color=white, fontSize=13.sp) }
        }
    }
}

@Composable private fun TracesScreen(d: StateData, led: Color) {
    Column {
        Text("12-WEEK TRACE", color=if(d.dark) white else ink, fontSize=13.sp, letterSpacing=2.sp, fontWeight=FontWeight.Bold)
        Text("Your steps, without streaks or scorekeeping.", color=if(d.dark) Color.LightGray else Color.DarkGray, fontSize=11.sp, modifier=Modifier.padding(top=5.dp, bottom=16.dp))
        var selected by remember { mutableStateOf<LocalDate?>(null) }
        val days = (83 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
        LazyVerticalGrid(columns=GridCells.Fixed(7), modifier=Modifier.height(280.dp), horizontalArrangement=Arrangement.spacedBy(7.dp), verticalArrangement=Arrangement.spacedBy(7.dp)) {
            items(days) { day ->
                val count = d.history[day.toString()] ?: 0
                val intensity = if(count==0) 0.12f else (0.25f + min(count.toFloat()/d.goal, 1f)*0.75f)
                Box(Modifier.aspectRatio(1f).background(if(count==0) glass else led.copy(alpha=intensity), RoundedCornerShape(5.dp)).clickable { selected=day })
            }
        }
        Spacer(Modifier.height(18.dp))
        val date = selected
        if(date!=null) {
            val steps=d.history[date.toString()] ?: 0
            Column(Modifier.fillMaxWidth().background(glass, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("FOOTPRINT · $date", color=Color.Gray, fontSize=10.sp, letterSpacing=2.sp)
                Text("%,d steps".format(steps), color=white, fontSize=22.sp, modifier=Modifier.padding(top=7.dp))
                Text("${steps/1000} fragments recorded", color=led, fontSize=11.sp)
            }
        } else Text("Select a cell to inspect its Footprint", color=if(d.dark) Color.LightGray else Color.DarkGray, fontSize=11.sp)
        Spacer(Modifier.height(18.dp)); Text("LESS ·  ▪  ▪  ▪  ▪  MORE", color=if(d.dark) Color.LightGray else Color.DarkGray, fontSize=9.sp, letterSpacing=1.sp)
    }
}

@Composable private fun TuneScreen(d: StateData, save: (StateData)->Unit, prefs: android.content.SharedPreferences) {
    Column {
        Text("TUNE THE INSTRUMENT", color=if(d.dark) white else ink, fontSize=13.sp, letterSpacing=2.sp, fontWeight=FontWeight.Bold)
        Text("Small adjustments. Your progress stays yours.", color=if(d.dark) Color.LightGray else Color.DarkGray, fontSize=11.sp, modifier=Modifier.padding(top=5.dp, bottom=18.dp))
        Text("LED PALETTE", color=Color.Gray, fontSize=9.sp, letterSpacing=2.sp)
        Spacer(Modifier.height(10.dp))
        themeNames.forEachIndexed { i, name ->
            Row(Modifier.fillMaxWidth().padding(vertical=5.dp).background(glass, RoundedCornerShape(12.dp)).clickable { save(d.copy(theme=i)) }.padding(13.dp), verticalAlignment=Alignment.CenterVertically) {
                Text(if(d.theme==i) "●" else "○", color=themes[i][2], fontSize=14.sp); Spacer(Modifier.width(12.dp)); Text(name, color=white, fontSize=13.sp, modifier=Modifier.weight(1f))
                Row { themes[i].forEach { Box(Modifier.padding(start=4.dp).size(14.dp).background(it, CircleShape)) } }
            }
        }
        Spacer(Modifier.height(12.dp)); Text("DAILY GOAL · ${d.goal} STEPS", color=Color.Gray, fontSize=9.sp, letterSpacing=1.sp)
        Slider(value=d.goal.toFloat(), onValueChange={ save(d.copy(goal=(it/500).toInt()*500)) }, valueRange=2500f..15000f, steps=24, colors=SliderDefaults.colors(thumbColor=themes[d.theme][2], activeTrackColor=themes[d.theme][2]))
        Text("${d.stageCost} fragments per stage · 5 stages per pass", color=if(d.dark) Color.LightGray else Color.DarkGray, fontSize=10.sp)
        Spacer(Modifier.height(14.dp)); Row(verticalAlignment=Alignment.CenterVertically) { Text("DARK TITANIUM", color=if(d.dark) white else ink, modifier=Modifier.weight(1f), fontSize=11.sp); Switch(checked=d.dark, onCheckedChange={ save(d.copy(dark=it)) }) }
        Spacer(Modifier.height(14.dp)); Text("DATA · JSON", color=Color.Gray, fontSize=9.sp, letterSpacing=2.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick={ val json=encode(d); prefs.edit().putString("last_export",json).apply() }) { Text("SAVE EXPORT", fontSize=10.sp) }
            OutlinedButton(onClick={ prefs.getString("last_export", null)?.let { decode(it)?.let(save) } }) { Text("RESTORE EXPORT", fontSize=10.sp) }
        }
        Text("Export snapshot is stored locally. No accounts, analytics, or network access.", color=if(d.dark) Color.LightGray else Color.DarkGray, fontSize=9.sp, modifier=Modifier.padding(top=6.dp))
    }
}

private fun load(p: android.content.SharedPreferences): StateData = p.getString("state", null)?.let { decode(it) } ?: StateData()
private fun encode(d: StateData): String = org.json.JSONObject().apply {
    put("schema_version",1); put("user_config",org.json.JSONObject().put("goal_steps",d.goal).put("stages_per_pass",5).put("theme_id",themeNames[d.theme].lowercase()).put("appearance",if(d.dark) "dark" else "light"))
    put("progress",org.json.JSONObject().put("current_cycle",d.cycle).put("current_pass",if(d.pass==0) "forward" else "reverse").put("current_stage_index",d.index).put("current_fragments",d.fragments).put("step_remainder",d.remainder).put("last_completion_date",d.lastCompletion).put("last_recorded_steps_today",d.lastSteps).put("last_recorded_steps_date",d.lastStepsDate))
    put("history",org.json.JSONArray().also { a -> d.history.forEach { (day,steps) -> a.put(org.json.JSONObject().put("date",day).put("steps",steps).put("evolutions",org.json.JSONArray())) } })
}.toString()
private fun decode(s: String): StateData? = try {
    val j=org.json.JSONObject(s); val cfg=j.getJSONObject("user_config"); val p=j.getJSONObject("progress")
    val hist=mutableMapOf<String,Int>(); val a=j.optJSONArray("history") ?: org.json.JSONArray(); for(i in 0 until a.length()) { val h=a.getJSONObject(i); hist[h.getString("date")]=h.optInt("steps") }
    StateData(goal=cfg.optInt("goal_steps",5000), theme=themeNames.indexOfFirst { it.equals(cfg.optString("theme_id","amber"),true) }.let { if(it<0) 0 else it }, dark=cfg.optString("appearance")=="dark", cycle=p.optInt("current_cycle",1), pass=if(p.optString("current_pass")=="reverse") 1 else 0, index=p.optInt("current_stage_index",0).coerceIn(0,4), fragments=p.optInt("current_fragments",0), remainder=p.optInt("step_remainder",0), lastSteps=p.optLong("last_recorded_steps_today",0), lastStepsDate=p.optString("last_recorded_steps_date",LocalDate.now().toString()), lastCompletion=p.optString("last_completion_date",LocalDate.now().minusDays(2).toString()), history=hist)
} catch (_: Exception) { null }
