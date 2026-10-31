package app.metabasis

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

private val Titanium = Color(0xFF111214)
private val Silver = Color(0xFFE3E6E8)
private val Glass = Color(0xFF090A0C)
private val Milk = Color(0xFFF4F4F0)
private val Muted = Color(0xFF9CA0A6)
private val SpaceGrotesk = FontFamily(Font(R.font.space_grotesk))
private val Inter = FontFamily(Font(R.font.inter))
private val themeNames = listOf("Amber", "Lush", "Shed", "Cherry", "Dusk")
private val themeIds = listOf("amber", "lush", "shed", "cherry", "dusk")
private val ledThemes = listOf(
    listOf(Color(0xFFFFE0B2), Color(0xFFFFC266), Color(0xFFFFA133), Color(0xFFE0661A), Color(0xFFA63D0A)),
    listOf(Color(0xFFD7EBB5), Color(0xFFA3D46A), Color(0xFF5FA83D), Color(0xFF2E7D32), Color(0xFF14401A)),
    listOf(Color(0xFFD4E8B0), Color(0xFFF2C94C), Color(0xFFE07A2E), Color(0xFFC0392B), Color(0xFF6B4226)),
    listOf(Color(0xFFFFD9E3), Color(0xFFFFB3C7), Color(0xFFF06292), Color(0xFFC2185B), Color(0xFF7B1E3F)),
    listOf(Color(0xFFDCE6F5), Color(0xFF7FA8E0), Color(0xFF4A5FC1), Color(0xFF6A3FA0), Color(0xFF2A1B4A))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MetabasisApp() } }
}

@Composable private fun MetabasisApp() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(StepLedger.load(context)) }
    var tab by remember { mutableIntStateOf(0) }
    var permissionDenied by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun refresh() { state = StepLedger.load(context) }
    fun startCounter() { ContextCompat.startForegroundService(context, Intent(context, StepCounterService::class.java)); refresh() }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startCounter() }
    val motion = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notification.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (granted) startCounter() else refresh()
    }
    fun ensureCounter() {
        if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) motion.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notification.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startCounter()
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? -> if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(StepLedger.exportJson(context)) } }.onFailure { error = "Could not export data." } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) runCatching { val json = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }; StepLedger.replace(context, StepLedger.importJson(json, StepLedger.load(context).sensorMode)); refresh() }.onFailure { error = "That file is not a valid Metabasis export." } }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) startCounter() else motion.launch(Manifest.permission.ACTIVITY_RECOGNITION); while (true) { delay(900); refresh() } }
    val dark = state.appearance == "dark" || (state.appearance == "system" && (context.resources.configuration.uiMode and 0x30) == 0x20)
    val palette = ledThemes[themeIds.indexOf(state.themeId).coerceAtLeast(0)]
    val accent = palette[state.stage]
    val animatedAccent by animateColorAsState(accent.copy(alpha = .48f + state.progress * .52f), tween(1800), label = "led-crossfade")
    MaterialTheme(colorScheme = if (dark) darkColorScheme(background = Titanium, surface = Glass, onSurface = Milk, primary = animatedAccent) else lightColorScheme(background = Silver, surface = Color(0xFFF8F9F7), onSurface = Titanium, primary = animatedAccent)) {
        Box(Modifier.fillMaxSize().background(if (dark) Titanium else Silver)) {
            if (!state.introSeen) OnboardingScreen(dark, palette, onFinish = { name, goal, theme, appearance -> StepLedger.finishIntro(context, name, goal, theme, appearance); refresh() })
            else Column(Modifier.fillMaxSize()) {
                GreetingHeader(state, dark, state.logoMode, palette)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> BaseScreen(state, animatedAccent, dark, ::ensureCounter, permissionDenied)
                        1 -> TracesScreen(state, animatedAccent, dark)
                        else -> TuneScreen(state, animatedAccent, dark, palette, onTheme = { StepLedger.updateSettings(context, theme = it); refresh() }, onName = { StepLedger.updateSettings(context, userName = it); refresh() }, onGoal = { StepLedger.updateSettings(context, goal = it); refresh() }, onAppearance = { StepLedger.updateSettings(context, appearance = it); refresh() }, onLogs = { StepLedger.updateLiveLogs(context, it); refresh() }, onHaptics = { StepLedger.updateHaptics(context, it); refresh() }, onExport = { export.launch("metabasis-${LocalDate.now()}.json") }, onImport = { import.launch(arrayOf("application/json", "text/json", "*/*")) })
                    }
                }
                Navigation(tab, animatedAccent) { tab = it }
            }
        }
    }
    error?.let { AlertDialog(onDismissRequest = { error = null }, title = { Text("Data file") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

@Composable private fun OnboardingScreen(dark: Boolean, palette: List<Color>, onFinish: (String, Int, String, String) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var goal by remember { mutableFloatStateOf(5000f) }
    var theme by remember { mutableStateOf("amber") }
    var appearance by remember { mutableStateOf("system") }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val ink = if (dark) Milk else Titanium
    val secondary = if (dark) Muted else Color(0xFF454C53)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FootstepsMark(palette[2], Modifier.size(112.dp))
        Spacer(Modifier.height(18.dp))
        Text(if (page == 0) "WELCOME TO METABASIS" else "MAKE IT YOURS", color = ink, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = 1.4.sp, textAlign = TextAlign.Center)
        Text(if (page == 0) "A calm dashboard that changes with your rhythm." else "A small setup now. A dashboard that feels like yours after.", color = secondary, fontFamily = Inter, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        if (page == 0) {
            InstrumentPanel(dark, Modifier.padding(top = 26.dp)) { Text("Steps become fragments. Fragments move through stages. Your history stays on this device.", color = ink, fontFamily = Inter, fontSize = 12.sp, lineHeight = 19.sp) }
            Spacer(Modifier.height(26.dp)); PrimaryButton("SET UP METABASIS", palette[2], haptic) { page = 1 }
            TextButton(onClick = { openUrl(context, "https://vijayarkananduri.github.io/metabasis/metabasis-guide.html") }) { Text("VISIT THE GUIDE", color = palette[2], fontFamily = Inter) }
        } else {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Your name") }, placeholder = { Text("What should Metabasis call you?") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 22.dp))
            Spacer(Modifier.height(18.dp)); SectionLabel("DAILY STEP GOAL · ${goal.toInt()}", dark)
            Slider(value = goal, onValueChange = { goal = (it / 500).toInt() * 500f }, valueRange = 2500f..15000f, steps = 24, colors = SliderDefaults.colors(thumbColor = palette[2], activeTrackColor = palette[2]))
            SectionLabel("CHOOSE A COLOR WORLD", dark)
            themeNames.forEachIndexed { index, label -> ChoiceRow(label, theme == themeIds[index], ledThemes[index][2], dark) { theme = themeIds[index]; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
            Spacer(Modifier.height(16.dp)); SectionLabel("CHASSIS", dark)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) -> Surface(onClick = { appearance = key }, color = if (appearance == key) palette[2].copy(alpha = if (dark) .30f else .18f) else if (dark) Glass else Color(0xFFF8F9F7), shape = RoundedCornerShape(18.dp), border = if (appearance == key) androidx.compose.foundation.BorderStroke(1.dp, palette[2]) else null) { Text(label, color = ink, fontFamily = Inter, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) } } }
            Spacer(Modifier.height(20.dp)); PrimaryButton("OPEN MY DASHBOARD", palette[2], haptic) { onFinish(name, goal.toInt(), theme, appearance) }
        }
        Text("No account. No internet. Your movement stays on this device.", color = secondary, fontFamily = Inter, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable private fun BaseScreen(state: AppState, led: Color, dark: Boolean, ensureCounter: () -> Unit, denied: Boolean) {
    val ink = if (dark) Milk else Titanium
    val secondary = if (dark) Muted else Color(0xFF454C53)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(118.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(116.dp).shadow(12.dp, CircleShape).background(Glass, CircleShape).border(1.dp, led.copy(alpha = .42f), CircleShape), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("TODAY", fontFamily = SpaceGrotesk, color = Muted, fontSize = 9.sp, letterSpacing = 2.sp); Spacer(Modifier.height(4.dp)); Text("%,d".format(state.todaySteps), fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = Milk, fontSize = 23.sp, maxLines = 1); Text("STEPS", fontFamily = SpaceGrotesk, color = led, fontSize = 9.sp, letterSpacing = 2.sp) }
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) { drawCircle(led.copy(alpha = .35f), style = Stroke(1.dp.toPx())) }
                }
                Spacer(Modifier.height(13.dp))
                Box(Modifier.fillMaxWidth().height(52.dp).shadow(9.dp, RoundedCornerShape(27.dp)).background(Glass, RoundedCornerShape(27.dp)).border(1.dp, led.copy(alpha = .24f), RoundedCornerShape(27.dp)), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth(state.progress.coerceAtLeast(.035f)).fillMaxHeight().padding(5.dp).background(led, RoundedCornerShape(22.dp)))
                    Text("${state.fragments} / ${state.stageCost}", color = if (android.graphics.Color.luminance(led.toArgb()) > .55f) Color.Black else Color.White, fontFamily = SpaceGrotesk, fontSize = 9.sp, letterSpacing = .35.sp, maxLines = 1)
                }
                Text("STAGE FRAGMENTS", color = secondary, fontFamily = SpaceGrotesk, fontSize = 7.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Box(Modifier.weight(1f).aspectRatio(1f).shadow(18.dp, CircleShape).background(Glass, CircleShape).border(1.dp, led.copy(alpha = .30f), CircleShape), contentAlignment = Alignment.Center) {
                ModuleCircle("CYCLE ${state.cycle}", "${(state.progress * 100).toInt()}%", "${stageName(state.stage)} · ${state.pass.uppercase()}", led, Modifier.fillMaxSize().padding(4.dp), true, state.progress)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("${(state.progress * 100).toInt()}%", color = led, fontFamily = SpaceGrotesk, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Text("STAGE INTENSITY", color = secondary, fontFamily = Inter, fontSize = 9.sp, letterSpacing = 1.4.sp); Spacer(Modifier.weight(1f)); Text("1 fragment / 100 steps", color = secondary, fontFamily = Inter, fontSize = 9.sp) }
        Spacer(Modifier.height(13.dp))
        InstrumentPanel(dark, Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { FootstepsMark(led, Modifier.size(34.dp)); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(if (denied || state.sensorMode.contains("No compatible", true)) "STEP TRACKING NEEDS SETUP" else "STEP TRACKING ACTIVE", color = ink, fontFamily = SpaceGrotesk, fontSize = 9.sp, letterSpacing = .8.sp); Text("Today · ${state.today} · ${state.todaySteps} steps recorded", color = secondary, fontFamily = Inter, fontSize = 9.sp, maxLines = 1) }; if (denied || state.sensorMode.contains("No compatible", true)) Text("SETUP", color = led, fontFamily = SpaceGrotesk, fontSize = 9.sp, modifier = Modifier.clickable(onClick = ensureCounter).padding(8.dp)) } }
        if (state.showLiveLogs) { Spacer(Modifier.height(8.dp)); InstrumentPanel(dark, Modifier.fillMaxWidth()) { Text("LIVE TRACE", color = led, fontFamily = SpaceGrotesk, fontSize = 9.sp, letterSpacing = 1.4.sp); if (state.liveLogs.isEmpty()) Text("No new step events yet.", color = secondary, fontFamily = Inter, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp)) else state.liveLogs.asReversed().take(4).forEach { entry -> Text(entry, color = ink, fontFamily = Inter, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp)) } } }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable private fun TracesScreen(state: AppState, led: Color, dark: Boolean) {
    var month by remember { mutableStateOf(YearMonth.now()) }; var selected by remember { mutableStateOf(LocalDate.now().toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("TRACES", color = if (dark) Milk else Titanium, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp); Text("A calendar of accumulation, not a streak. Select a day for its details.", color = if (dark) Muted else Color(0xFF454C53), fontFamily = Inter, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹", fontSize = 24.sp, color = led) }; Text("${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}", color = if (dark) Milk else Titanium, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold); TextButton(onClick = { if (month < YearMonth.now()) month = month.plusMonths(1) }) { Text("›", fontSize = 24.sp, color = if (month < YearMonth.now()) led else Muted) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, color = if (dark) Muted else Color(0xFF555B61), fontSize = 9.sp, modifier = Modifier.width(35.dp), textAlign = TextAlign.Center) } }
        Spacer(Modifier.height(8.dp)); val offset = month.atDay(1).dayOfWeek.value - 1; val days: List<Int?> = List(offset) { null } + (1..month.lengthOfMonth()).map { it }
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth().height(305.dp), userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(days) { day ->
                if (day == null) Spacer(Modifier.aspectRatio(1f)) else {
                    val date = month.atDay(day).toString()
                    val steps = state.history[date] ?: if (date == state.today) state.todaySteps else 0
                    val ratio = (steps.toFloat() / state.goalSteps).coerceIn(0f, 1f)
                    val dayBackground = if (steps == 0) {
                        if (dark) Glass else Color(0xFFF1F2EF)
                    } else led.copy(alpha = .18f + ratio * .8f)
                    val dayText = if (android.graphics.Color.luminance(dayBackground.toArgb()) > .50f) Titanium else Milk
                    Box(
                        Modifier.aspectRatio(1f)
                            .background(dayBackground, RoundedCornerShape(7.dp))
                            .clickable { selected = date }
                            .then(if (date == selected) Modifier.border(1.dp, led, RoundedCornerShape(7.dp)) else Modifier),
                        contentAlignment = Alignment.Center
                    ) { Text("$day", color = dayText, fontSize = 9.sp) }
                }
            }
        }
        Spacer(Modifier.height(14.dp)); InstrumentPanel(dark, Modifier.fillMaxWidth()) { val chosen = if (selected == state.today) state.todaySteps else state.history[selected] ?: 0; val goalRatio = (chosen.toFloat() / state.goalSteps).coerceIn(0f, 1f); Text("FOOTPRINT · $selected", color = if (dark) Muted else Color(0xFF555B61), fontFamily = SpaceGrotesk, fontSize = 9.sp, letterSpacing = 1.5.sp); Text("%,d steps".format(chosen), color = if (dark) Milk else Titanium, fontFamily = SpaceGrotesk, fontSize = 26.sp, modifier = Modifier.padding(top = 5.dp)); Text("${(goalRatio * 100).toInt()}% of daily goal · ${chosen / 100} fragments earned", color = if (dark) led else Color(0xFF454B51), fontFamily = Inter, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp)) }
        TextButton(onClick = { month = YearMonth.now(); selected = state.today }) { Text("RETURN TO TODAY", color = led, fontFamily = SpaceGrotesk, fontSize = 10.sp) }
    }
}

@Composable private fun TuneScreen(state: AppState, led: Color, dark: Boolean, palette: List<Color>, onTheme: (String) -> Unit, onName: (String) -> Unit, onGoal: (Int) -> Unit, onAppearance: (String) -> Unit, onLogs: (Boolean) -> Unit, onHaptics: (Boolean) -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    var name by remember(state.userName) { mutableStateOf(state.userName) }; var editingName by remember { mutableStateOf(false) }; var goal by remember(state.goalSteps) { mutableFloatStateOf(state.goalSteps.toFloat()) }; var pendingGoal by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("TUNE", color = if (dark) Milk else Titanium, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp); Text("Adjust the instrument. The ledger stays local.", color = if (dark) Muted else Color.DarkGray, fontFamily = Inter, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        SectionLabel("YOUR NAME", dark); Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { if (editingName) { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.weight(1f)); TextButton(onClick = { onName(name.trim().ifBlank { "Walker" }); editingName = false }) { Text("SAVE", color = led) } } else { Text(name.ifBlank { "Walker" }, color = if (dark) Milk else Titanium, fontFamily = SpaceGrotesk, modifier = Modifier.weight(1f)); TextButton(onClick = { editingName = true }) { Text("EDIT", color = led) } } }
        Spacer(Modifier.height(16.dp)); SectionLabel("LED THEMES", dark); themeNames.forEachIndexed { i, label -> ChoiceRow(label + if (state.themeLocked && state.themeId == themeIds[i]) " · LOCKED" else "", state.themeId == themeIds[i], ledThemes[i][2], dark, enabled = !state.themeLocked) { onTheme(themeIds[i]) } }; Text(if (state.themeLocked) "Your light journey is locked after the first stage pass. The amber footsteps mark stays fixed." else "Choose carefully: this theme locks after your first completed stage.", color = if (dark) Muted else Color.DarkGray, fontFamily = Inter, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
        Spacer(Modifier.height(16.dp)); SectionLabel("DAILY GOAL · ${goal.toInt()} STEPS", dark); Slider(value = goal, onValueChange = { goal = (it / 500).toInt() * 500f }, onValueChangeFinished = { if (goal.toInt() != state.goalSteps) pendingGoal = goal.toInt() }, valueRange = 2500f..15000f, steps = 24, colors = SliderDefaults.colors(thumbColor = led, activeTrackColor = led)); Text("Stage cost: ${state.stageCost} fragments · medium five-stage pass", color = if (dark) Muted else Color.DarkGray, fontFamily = Inter, fontSize = 10.sp)
        Spacer(Modifier.height(16.dp)); SectionLabel("CHASSIS", dark); listOf("system" to "System", "light" to "Brushed silver", "dark" to "Titanium black").forEach { (k, v) -> ChoiceRow(v, state.appearance == k, led, dark) { onAppearance(k) } }
        Spacer(Modifier.height(16.dp)); SectionLabel("FEEL", dark); SettingSwitch("Soft haptic ticks", state.hapticsEnabled, led, dark, onHaptics); SettingSwitch("Show recent step times on Base", state.showLiveLogs, led, dark, onLogs)
        Spacer(Modifier.height(16.dp)); SectionLabel("GUIDE + UPDATES", dark); Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) { ActionPill("OPEN GUIDE", led, Modifier.weight(1f)) { openUrl(context, "https://vijayarkananduri.github.io/metabasis/metabasis-guide.html") }; ActionPill("CHECK UPDATES", led, Modifier.weight(1f)) { openUrl(context, "https://vijayarkananduri.github.io/metabasis/metabasis-updates.html") } }; Text("Metabasis v1 · Vijayarka Nanduri · Alive UI", color = if (dark) Muted else Color.DarkGray, fontFamily = Inter, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp)); SectionLabel("DATA · JSON", dark); Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) { ActionPill("EXPORT DATA", led, Modifier.weight(1f), onExport); ActionPill("IMPORT DATA", led, Modifier.weight(1f), onImport) }
        pendingGoal?.let { target -> AlertDialog(onDismissRequest = { pendingGoal = null }, title = { Text("Change your stage pace?") }, text = { Text("The stage uses 250 fragments. Your current fragments stay in place.") }, confirmButton = { TextButton(onClick = { onGoal(target); pendingGoal = null }) { Text("APPLY") } }, dismissButton = { TextButton(onClick = { pendingGoal = null }) { Text("CANCEL") } }) }
    }
}

@Composable private fun GreetingHeader(state: AppState, dark: Boolean, logoMode: String, palette: List<Color>) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { FootstepsMark(Color(0xFFFFB453), Modifier.size(42.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(greeting(state.userName), color = if (dark) Milk else Titanium, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text("METABASIS · ALIVE IN MOTION", color = if (dark) Muted else Color(0xFF5F646A), fontFamily = Inter, fontSize = 8.sp, letterSpacing = 1.sp) }; Box(Modifier.size(8.dp).background(palette[state.stage], CircleShape).shadow(8.dp, CircleShape)) } }
private fun greeting(name: String): String { val hour = java.time.LocalTime.now().hour; val prefix = when { hour < 5 -> "Rest well"; hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; hour < 22 -> "Good evening"; else -> "Good night" }; return "$prefix, ${name.ifBlank { "Walker" }}" }
private fun stageName(stage: Int) = listOf("MARIGOLD", "CITRUS", "EMBER", "GARNET", "DUSK").getOrElse(stage) { "DUSK" }

@Composable private fun ModuleCircle(title: String, value: String, subtitle: String, led: Color, modifier: Modifier, ring: Boolean = false, progress: Float = 0f) { Box(modifier.shadow(12.dp, CircleShape).background(Glass, CircleShape), contentAlignment = Alignment.Center) { if (ring) Canvas(Modifier.fillMaxSize().padding(9.dp)) { drawCircle(led.copy(alpha = .2f), style = Stroke(1.dp.toPx())); repeat(24) { i -> val angle = Math.toRadians((i * 15 - 90).toDouble()); val active = i < progress * 24; val x = size.width / 2 + kotlin.math.cos(angle).toFloat() * (size.minDimension / 2 - 8.dp.toPx()); val y = size.height / 2 + kotlin.math.sin(angle).toFloat() * (size.minDimension / 2 - 8.dp.toPx()); drawCircle(if (active) led else Color(0xFF34363A), if (active) 4.dp.toPx() else 2.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y)) } }; Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(title, color = Muted, fontFamily = SpaceGrotesk, fontSize = 8.sp, letterSpacing = 1.2.sp); Text(value, color = Milk, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = if (ring) 28.sp else 20.sp, modifier = Modifier.padding(vertical = 4.dp)); Text(subtitle, color = led, fontFamily = Inter, fontSize = 7.sp, letterSpacing = .8.sp, textAlign = TextAlign.Center) } } }

@Composable private fun FootstepsMark(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val amber = color
        fun footprint(cx: Float, cy: Float, scale: Float) {
            // A compact sole with toe dots reads as a small footprint, not a boot.
            drawOval(amber, androidx.compose.ui.geometry.Offset(cx - w * .075f * scale, cy - h * .06f * scale), androidx.compose.ui.geometry.Size(w * .15f * scale, h * .25f * scale))
            drawCircle(amber, w * .037f * scale, androidx.compose.ui.geometry.Offset(cx - w * .07f * scale, cy - h * .16f * scale))
            drawCircle(amber, w * .039f * scale, androidx.compose.ui.geometry.Offset(cx, cy - h * .19f * scale))
            drawCircle(amber, w * .037f * scale, androidx.compose.ui.geometry.Offset(cx + w * .07f * scale, cy - h * .15f * scale))
        }
        footprint(w * .38f, h * .35f, .72f)
        footprint(w * .65f, h * .63f, .72f)
    }
}

@Composable private fun InstrumentPanel(dark: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Column(modifier.shadow(10.dp, RoundedCornerShape(20.dp)).background(if (dark) Glass else Color(0xFFF8F9F7), RoundedCornerShape(20.dp)).border(1.dp, if (dark) Color(0xFF3A3D42) else Color(0xFFD0D5D2), RoundedCornerShape(20.dp)).padding(16.dp), content = content) }
@Composable private fun SectionLabel(text: String, dark: Boolean) { Text(text, color = if (dark) Muted else Color(0xFF3F454B), fontFamily = SpaceGrotesk, fontSize = 9.sp, letterSpacing = 1.5.sp) }
@Composable private fun ChoiceRow(text: String, selected: Boolean, color: Color, dark: Boolean, enabled: Boolean = true, onClick: () -> Unit) { val surface = if (dark) Glass else Color(0xFFF8F9F7); val ink = if (dark) Milk else Titanium; Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(if (selected) color.copy(alpha = if (dark) .22f else .16f) else surface, RoundedCornerShape(12.dp)).border(if (selected) 1.dp else 0.dp, if (selected) color.copy(alpha = .75f) else Color.Transparent, RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (selected) "●" else "○", color = if (enabled) color else Muted, fontSize = 14.sp); Spacer(Modifier.width(10.dp)); Text(text, color = if (enabled) ink else if (dark) Muted else Color(0xFF62676D), fontFamily = SpaceGrotesk, fontSize = 12.sp, modifier = Modifier.weight(1f)); if (selected) Text("SELECTED", color = if (dark) color else Titanium, fontFamily = Inter, fontSize = 8.sp) } }
@Composable private fun ChoicePill(text: String, selected: Boolean, color: Color, onClick: () -> Unit) { Surface(onClick = onClick, color = if (selected) color.copy(alpha = .32f) else Glass, shape = RoundedCornerShape(20.dp)) { Text(text, color = if (selected) Milk else Muted, fontFamily = SpaceGrotesk, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) } }
@Composable private fun SettingSwitch(label: String, checked: Boolean, led: Color, dark: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, color = if (dark) Milk else Titanium, fontFamily = Inter, fontSize = 11.sp, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = led, checkedTrackColor = led.copy(alpha = .42f))) } }
@Composable private fun PrimaryButton(label: String, color: Color, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, modifier: Modifier = Modifier, onClick: () -> Unit) { Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }, modifier = modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = if (android.graphics.Color.luminance(color.toArgb()) > .55f) Color.Black else Color.White), shape = RoundedCornerShape(25.dp)) { Text(label, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) } }
@Composable private fun ActionPill(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) { Surface(onClick = onClick, color = Glass, shape = RoundedCornerShape(23.dp), modifier = modifier.height(44.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, color = color, fontFamily = SpaceGrotesk, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp) } } }
@Composable private fun Navigation(selected: Int, led: Color, onSelect: (Int) -> Unit) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).height(58.dp).background(Color(0xEF202124), RoundedCornerShape(29.dp)).padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { listOf("BASE", "TRACES", "TUNE").forEachIndexed { i, label -> Surface(onClick = { onSelect(i) }, color = if (selected == i) led.copy(alpha = .22f) else Color.Transparent, shape = RoundedCornerShape(23.dp), modifier = Modifier.weight(1f).fillMaxHeight()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, color = if (selected == i) Milk else Color(0xFFB8BBC0), fontFamily = SpaceGrotesk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) } } } } }
private fun openUrl(context: Context, url: String) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
