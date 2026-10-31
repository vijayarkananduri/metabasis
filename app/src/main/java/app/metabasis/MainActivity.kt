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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val chassis = Color(0xFFE2E4E8)
private val titanium = Color(0xFF121214)
private val glass = Color(0xFF08090B)
private val milk = Color(0xFFF5F5F2)
private val muted = Color(0xFF9B9EA3)
private val stageNames = listOf("MARIGOLD", "CITRUS", "EMBER", "GARNET", "DUSK")
private val themeNames = listOf("Amber", "Lush", "Shed", "Cherry", "Dusk")
private val themeIds = listOf("amber", "lush", "shed", "cherry", "dusk")
private val ledThemes = listOf(
    listOf(Color(0xFFFFE0B2), Color(0xFFFFC266), Color(0xFFFFA133), Color(0xFFE0661A), Color(0xFFA63D0A)),
    listOf(Color(0xFFD7EBB5), Color(0xFFA3D46A), Color(0xFF5FA83D), Color(0xFF2E7D32), Color(0xFF14401A)),
    listOf(Color(0xFFD4E8B0), Color(0xFFF2C94C), Color(0xFFE07A2E), Color(0xFFC0392B), Color(0xFF6B4226)),
    listOf(Color(0xFFFFD9E3), Color(0xFFFFB3C7), Color(0xFFF06292), Color(0xFFC2185B), Color(0xFF7B1E3F)),
    listOf(Color(0xFFDCE6F5), Color(0xFF7FA8E0), Color(0xFF4A5FC1), Color(0xFF6A3FA0), Color(0xFF2A1B4A))
)
private val SpaceGrotesk = FontFamily(Font(R.font.space_grotesk))
private val Inter = FontFamily(Font(R.font.inter))
private val MetabasisTypography = Typography().let { t -> t.copy(
    displayLarge=t.displayLarge.copy(fontFamily=SpaceGrotesk), displayMedium=t.displayMedium.copy(fontFamily=SpaceGrotesk), displaySmall=t.displaySmall.copy(fontFamily=SpaceGrotesk),
    headlineLarge=t.headlineLarge.copy(fontFamily=SpaceGrotesk), headlineMedium=t.headlineMedium.copy(fontFamily=SpaceGrotesk), headlineSmall=t.headlineSmall.copy(fontFamily=SpaceGrotesk),
    titleLarge=t.titleLarge.copy(fontFamily=SpaceGrotesk), titleMedium=t.titleMedium.copy(fontFamily=SpaceGrotesk), titleSmall=t.titleSmall.copy(fontFamily=SpaceGrotesk),
    labelLarge=t.labelLarge.copy(fontFamily=SpaceGrotesk), labelMedium=t.labelMedium.copy(fontFamily=SpaceGrotesk), labelSmall=t.labelSmall.copy(fontFamily=SpaceGrotesk),
    bodyLarge=t.bodyLarge.copy(fontFamily=Inter), bodyMedium=t.bodyMedium.copy(fontFamily=Inter), bodySmall=t.bodySmall.copy(fontFamily=Inter)
) }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MetabasisApp() }
    }
}

@Composable
private fun MetabasisApp() {
    val context = LocalContext.current
    val activity = context as Activity
    var state by remember { mutableStateOf(StepLedger.load(context)) }
    var selectedTab by remember { mutableStateOf(0) }
    var permissionDenied by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    fun refresh() { state = StepLedger.load(context) }
    fun startCounter() {
        val intent = Intent(context, StepCounterService::class.java)
        ContextCompat.startForegroundService(context, intent)
        refresh()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startCounter() }
    val motionPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startCounter()
        } else refresh()
    }
    fun ensureCounter() {
        if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            motionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startCounter()
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(StepLedger.exportJson(context)) } }
            .onFailure { importError = "Could not write export: ${it.message ?: "storage error"}" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val json = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            StepLedger.replace(context, StepLedger.importJson(json, StepLedger.load(context).sensorMode))
            refresh()
        }.onFailure { importError = "That file is not a valid Metabasis JSON export." }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) startCounter()
        else motionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        while (true) { delay(900); refresh() }
    }
    val dark = state.appearance == "dark" || (state.appearance == "system" && (context.resources.configuration.uiMode and 0x30) == 0x20)
    val background = if (dark) titanium else chassis
    val stageColor = cycleColor(ledThemes.getOrElse(themeIds.indexOf(state.themeId).coerceAtLeast(0)) { ledThemes[0] }[state.stage], state.cycle)
    val currentLed by animateColorAsState(stageColor.copy(alpha=.2f + state.progress*.8f), tween(3000), label="stage-led-crossfade")

    MaterialTheme(colorScheme = if (dark) darkColorScheme(background=titanium, surface=glass, onSurface=milk, primary=currentLed) else lightColorScheme(background=chassis, surface=glass, onSurface=milk, primary=currentLed), typography=MetabasisTypography) {
        Box(Modifier.fillMaxSize().background(background)) {
            if (!state.introSeen) {
                OnboardingScreen(dark, currentLed, onFinish = { name, goal, theme, appearance -> StepLedger.finishIntro(context, name, goal, theme, appearance); refresh() })
            } else Column(Modifier.fillMaxSize()) {
                Header(state, dark, currentLed, state.sensorMode)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> BaseScreen(state, currentLed, dark, ::ensureCounter, permissionDenied)
                        1 -> TracesScreen(state, currentLed, dark)
                        else -> TuneScreen(state, currentLed, dark, onTheme={ theme -> StepLedger.updateSettings(context, theme=theme); refresh() }, onGoal={ goal -> StepLedger.updateSettings(context, goal=goal); refresh() }, onAppearance={ appearance -> StepLedger.updateSettings(context, appearance=appearance); refresh() }, onLiveLogs={ enabled -> StepLedger.updateLiveLogs(context, enabled); refresh() }, onExport={ exportLauncher.launch("metabasis-${LocalDate.now()}.json") }, onImport={ importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }, sensorMode=state.sensorMode)
                    }
                }
                FloatingNavigation(selectedTab, currentLed, dark) { selectedTab = it }
            }
            if (state.lastEvolutionAt > 0L && System.currentTimeMillis() - state.lastEvolutionAt < 10_000L) {
                EvolutionToast(state, currentLed, dark, Modifier.align(Alignment.BottomCenter).padding(bottom=104.dp))
            }
        }
    }
    importError?.let { message -> AlertDialog(onDismissRequest={importError=null}, title={Text("Data file")}, text={Text(message)}, confirmButton={TextButton(onClick={importError=null}) {Text("OK")}}) }
}

@Composable private fun OnboardingScreen(dark: Boolean, led: Color, onFinish: (String, Int, String, String) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var goal by remember { mutableFloatStateOf(5000f) }
    var theme by remember { mutableStateOf("amber") }
    var appearance by remember { mutableStateOf("system") }
    val titleColor = if (dark) milk else titanium
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp, vertical=22.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        FootprintMark(led, Modifier.size(62.dp))
        Spacer(Modifier.height(18.dp))
        Text(if (page == 0) "WELCOME TO METABASIS" else "MAKE IT YOURS", color=titleColor, fontFamily=SpaceGrotesk, fontWeight=FontWeight.Bold, fontSize=22.sp, letterSpacing=1.6.sp, textAlign=TextAlign.Center)
        Text(if (page == 0) "A calm, adaptive step dashboard that changes with your rhythm." else "A small setup now. A dashboard that feels like yours after.", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=12.sp, lineHeight=18.sp, textAlign=TextAlign.Center, modifier=Modifier.padding(top=10.dp))
        if (page == 0) {
            Text("Alive UI is software that feels alive: it adapts to your habits, context, and attention instead of holding you inside a rigid layout. Metabasis turns movement into a gentle visual journey.", color=if(dark) milk else titanium, fontFamily=Inter, fontSize=12.sp, lineHeight=19.sp, textAlign=TextAlign.Center, modifier=Modifier.padding(top=28.dp))
            Spacer(Modifier.height(26.dp))
            Button(onClick={page=1}, colors=ButtonDefaults.buttonColors(containerColor=led, contentColor=Color.Black), modifier=Modifier.fillMaxWidth().height(52.dp)) { Text("START SETUP", fontFamily=SpaceGrotesk, fontWeight=FontWeight.Bold, letterSpacing=1.sp) }
            TextButton(onClick={page=1}) { Text("SKIP INTRO", color=led, fontFamily=Inter) }
        } else {
            OutlinedTextField(value=name, onValueChange={name=it}, label={Text("Your name")}, placeholder={Text("What should Metabasis call you?")}, singleLine=true, modifier=Modifier.fillMaxWidth().padding(top=22.dp))
            Spacer(Modifier.height(18.dp)); SectionLabel("DAILY STEP GOAL · ${goal.toInt()}", dark)
            Slider(value=goal, onValueChange={goal=(it/500).toInt()*500f}, valueRange=2500f..15000f, steps=24, colors=SliderDefaults.colors(thumbColor=led, activeTrackColor=led))
            SectionLabel("CHOOSE A COLOR WORLD", dark)
            themeNames.forEachIndexed { index, label -> Row(Modifier.fillMaxWidth().padding(vertical=4.dp).background(glass, RoundedCornerShape(12.dp)).clickable { theme=themeIds[index] }.padding(12.dp), verticalAlignment=Alignment.CenterVertically) { Text(if(theme==themeIds[index]) "●" else "○", color=ledThemes[index][2]); Spacer(Modifier.width(10.dp)); Text(label, color=if(dark) milk else titanium, fontFamily=SpaceGrotesk, modifier=Modifier.weight(1f)); Box(Modifier.size(18.dp).background(ledThemes[index][2], CircleShape)) } }
            Spacer(Modifier.height(16.dp)); SectionLabel("CHASSIS", dark)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.padding(top=8.dp)) { listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key,label) -> Surface(onClick={appearance=key}, color=if(appearance==key) led.copy(alpha=.3f) else glass, shape=RoundedCornerShape(18.dp)) { Text(label, color=if(dark) milk else titanium, fontFamily=Inter, fontSize=11.sp, modifier=Modifier.padding(horizontal=14.dp, vertical=10.dp)) } } }
            Spacer(Modifier.height(20.dp)); Button(onClick={onFinish(name, goal.toInt(), theme, appearance)}, colors=ButtonDefaults.buttonColors(containerColor=led, contentColor=Color.Black), modifier=Modifier.fillMaxWidth().height(52.dp)) { Text("OPEN MY DASHBOARD", fontFamily=SpaceGrotesk, fontWeight=FontWeight.Bold, letterSpacing=1.sp) }
        }
        Text("You can revisit this introduction from Tune → Help.", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=9.sp, textAlign=TextAlign.Center, modifier=Modifier.padding(top=18.dp))
    }
}

@Composable private fun Header(state: AppState, dark: Boolean, led: Color, sensorMode: String) {
    Row(Modifier.fillMaxWidth().padding(start=22.dp, end=22.dp, top=11.dp, bottom=8.dp), verticalAlignment=Alignment.CenterVertically) {
        FootprintMark(led, Modifier.size(34.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(if (state.userName.isBlank()) "METABASIS" else state.userName.uppercase(), fontFamily=SpaceGrotesk, fontWeight=FontWeight.Bold, fontSize=14.sp, letterSpacing=2.1.sp, color=if(dark) milk else titanium)
            Text("METABASIS  ·  ALIVE DASHBOARD", fontFamily=Inter, fontSize=8.sp, letterSpacing=1.1.sp, color=if(dark) muted else Color(0xFF666A70), maxLines=1)
        }
        Box(Modifier.size(10.dp).background(led, CircleShape).shadow(7.dp, CircleShape))
    }
}

@Composable private fun BaseScreen(state: AppState, led: Color, dark: Boolean, ensureCounter: ()->Unit, denied: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top=4.dp), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.width(118.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.size(116.dp).shadow(12.dp, CircleShape).background(glass, CircleShape), contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("TODAY", fontFamily=SpaceGrotesk, color=muted, fontSize=9.sp, letterSpacing=2.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("%,d".format(state.todaySteps), fontFamily=SpaceGrotesk, fontWeight=FontWeight.Bold, color=milk, fontSize=23.sp, maxLines=1)
                        Text("STEPS", fontFamily=SpaceGrotesk, color=led, fontSize=9.sp, letterSpacing=2.sp)
                    }
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) { drawCircle(led.copy(alpha=.35f), style=Stroke(1.dp.toPx())) }
                }
                Spacer(Modifier.height(13.dp))
                Box(Modifier.fillMaxWidth().height(52.dp).shadow(9.dp,RoundedCornerShape(27.dp)).background(glass,RoundedCornerShape(27.dp)), contentAlignment=Alignment.Center) {
                    Box(Modifier.fillMaxWidth(state.progress.coerceAtLeast(.035f)).fillMaxHeight().padding(5.dp).background(led.copy(alpha=1f),RoundedCornerShape(22.dp)))
                    Text("${state.fragments} / ${state.stageCost}", color=milk, fontFamily=SpaceGrotesk, fontSize=9.sp, letterSpacing=.35.sp, maxLines=1)
                }
                Text("STAGE FRAGMENTS", color=if(dark) muted else Color.DarkGray, fontFamily=SpaceGrotesk, fontSize=7.sp, letterSpacing=1.2.sp, modifier=Modifier.padding(top=5.dp))
            }
            Box(Modifier.weight(1f).aspectRatio(1f).shadow(18.dp, CircleShape).background(glass, CircleShape), contentAlignment=Alignment.Center) {
                GlyphRing(progress=state.progress, led=led, modifier=Modifier.fillMaxSize().padding(12.dp))
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("${state.cycle}", color=led, fontFamily=SpaceGrotesk, fontWeight=FontWeight.Light, fontSize=64.sp, lineHeight=66.sp)
                    Text(stageNames[state.stage], color=milk, fontFamily=SpaceGrotesk, fontSize=10.sp, letterSpacing=2.2.sp)
                    Text("${state.pass.uppercase()} PASS", color=muted, fontFamily=Inter, fontSize=8.sp, letterSpacing=1.3.sp, modifier=Modifier.padding(top=4.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Text("${(state.progress*100).toInt()}%", color=led, fontFamily=SpaceGrotesk, fontSize=12.sp, fontWeight=FontWeight.Bold)
            Spacer(Modifier.width(8.dp)); Text("LED INTENSITY", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=9.sp, letterSpacing=1.4.sp)
            Spacer(Modifier.weight(1f)); Text("1 fragment / 1,000 steps", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=9.sp)
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth().background(glass, RoundedCornerShape(16.dp)).padding(horizontal=14.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically) {
            FootprintMark(led, Modifier.size(28.dp)); Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("LOCAL STEP LOG", color=milk, fontFamily=SpaceGrotesk, fontSize=9.sp, letterSpacing=.8.sp, maxLines=1)
                Text("Today · ${state.today} · ${state.todaySteps} steps recorded", color=muted, fontFamily=Inter, fontSize=9.sp, maxLines=1)
            }
            if (denied || state.sensorMode.contains("No compatible", true)) Text("SETUP", color=led, fontFamily=SpaceGrotesk, fontSize=9.sp, modifier=Modifier.clickable(onClick=ensureCounter).padding(8.dp))
        }
        if (state.showLiveLogs) {
            Column(Modifier.fillMaxWidth().background(Color(0x14000000), RoundedCornerShape(14.dp)).padding(horizontal=14.dp, vertical=10.dp)) {
                Text("LIVE LOG", color=led, fontFamily=SpaceGrotesk, fontSize=9.sp, letterSpacing=1.4.sp)
                if (state.liveLogs.isEmpty()) Text("No new step events yet.", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=10.sp, modifier=Modifier.padding(top=6.dp))
                else state.liveLogs.indices.reversed().filter { index -> state.liveLogTimes.getOrNull(index)?.let { System.currentTimeMillis() - it <= 45 * 60 * 1000L } == true }.take(4).forEach { index -> Text(state.liveLogs[index], color=if(dark) milk else titanium, fontFamily=Inter, fontSize=10.sp, modifier=Modifier.padding(top=5.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable private fun GlyphRing(progress: Float, led: Color, modifier: Modifier=Modifier) {
    Canvas(modifier) {
        val center=Offset(size.width/2,size.height/2); val radius=size.minDimension/2-8.dp.toPx(); val lit=floor(progress*24).toInt()
        drawCircle(Color(0xFF27292D), radius, center, style=Stroke(1.dp.toPx()))
        repeat(24) { i ->
            val angle=Math.toRadians((i*15-90).toDouble()); val point=Offset(center.x+cos(angle).toFloat()*radius,center.y+sin(angle).toFloat()*radius)
            val active=i<lit
            drawCircle(if(active) led else Color(0xFF35373B), if(active) 4.dp.toPx() else 2.1.dp.toPx(), point)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TracesScreen(state: AppState, led: Color, dark: Boolean) {
    var selected by remember { mutableStateOf(LocalDate.now().toString()) }
    var showDetail by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) {
        Text("TRACES", color=if(dark) milk else titanium, fontFamily=SpaceGrotesk, fontWeight=FontWeight.Bold, fontSize=22.sp, letterSpacing=2.sp)
        Text("CALENDAR + HEATMAP · tap a day for its result", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=11.sp, modifier=Modifier.padding(top=4.dp,bottom=14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { listOf("M","T","W","T","F","S","S").forEach { Text(it, color=muted, fontSize=9.sp, modifier=Modifier.width(35.dp), textAlign=TextAlign.Center) } }
        Spacer(Modifier.height(8.dp))
        val days=(83 downTo 0).map { LocalDate.now().minusDays(it.toLong()).toString() }
        LazyVerticalGrid(columns=GridCells.Fixed(7), modifier=Modifier.fillMaxWidth().height(265.dp), userScrollEnabled=false, horizontalArrangement=Arrangement.spacedBy(7.dp), verticalArrangement=Arrangement.spacedBy(7.dp)) {
            items(days) { day ->
                val steps=state.history[day] ?: 0
                val ratio=(steps.toFloat()/state.goalSteps).coerceIn(0f,1f)
                Box(Modifier.aspectRatio(1f).background(if(steps==0) glass else led.copy(alpha=.22f+ratio*.78f), RoundedCornerShape(5.dp)).clickable { selected=day; showDetail=true }, contentAlignment=Alignment.Center) {
                    Text(day.substring(8, 10), color=if(steps == 0) muted else if (ratio > .55f) Color.Black.copy(alpha=.72f) else milk, fontFamily=Inter, fontSize=8.sp)
                    if(day==selected) Canvas(Modifier.fillMaxSize()) { drawRoundRect(Color.White.copy(alpha=.8f), style=Stroke(1.2.dp.toPx()), cornerRadius=androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())) }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        val chosenSteps=if(selected==state.today) state.todaySteps else state.history[selected] ?: 0
        Column(Modifier.fillMaxWidth().shadow(9.dp,RoundedCornerShape(18.dp)).background(glass,RoundedCornerShape(18.dp)).padding(16.dp)) {
            Text("FOOTPRINT · $selected", color=muted, fontFamily=SpaceGrotesk, fontSize=9.sp, letterSpacing=2.sp)
            Text("%,d steps".format(chosenSteps), color=milk, fontFamily=SpaceGrotesk, fontSize=26.sp, modifier=Modifier.padding(top=5.dp))
            val entries=state.evolutions.filter { it.date==selected }
            Text(if(entries.isEmpty()) "${chosenSteps/1000} fragments earned · ${entries.size} evolutions" else entries.joinToString(" · ") { "${it.from} → ${it.to}" }, color=led, fontFamily=Inter, fontSize=10.sp, modifier=Modifier.padding(top=4.dp))
        }
        Spacer(Modifier.height(14.dp)); Text("LESS   ▪   ▪   ▪   ▪   MORE", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=9.sp, letterSpacing=1.sp)
    }
    if(showDetail) {
        val steps=if(selected==state.today) state.todaySteps else state.history[selected] ?: 0
        val entries=state.evolutions.filter { it.date==selected }
        ModalBottomSheet(onDismissRequest={showDetail=false},containerColor=glass,contentColor=milk) {
            Column(Modifier.fillMaxWidth().padding(start=22.dp,end=22.dp,bottom=34.dp)) {
                Text("FOOTPRINT · $selected",color=muted,fontFamily=SpaceGrotesk,fontSize=10.sp,letterSpacing=2.sp)
                Text("%,d steps".format(steps),color=milk,fontFamily=SpaceGrotesk,fontSize=30.sp,modifier=Modifier.padding(top=8.dp))
                Text("${steps/1000} fragments earned",color=led,fontFamily=Inter,fontSize=12.sp,modifier=Modifier.padding(top=5.dp))
                Spacer(Modifier.height(12.dp))
                Text(if(entries.isEmpty()) "No stage evolution recorded for this day." else entries.joinToString("\n") { "${it.from} → ${it.to} · ${it.spent} fragments · ${it.pass} pass · cycle ${it.cycle}" },color=milk,fontFamily=Inter,fontSize=11.sp,lineHeight=17.sp)
            }
        }
    }
}

@Composable private fun TuneScreen(state: AppState, led: Color, dark: Boolean, onTheme:(String)->Unit, onGoal:(Int)->Unit, onAppearance:(String)->Unit, onExport:()->Unit, onImport:()->Unit, onLiveLogs:(Boolean)->Unit = {}, sensorMode:String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) {
        Text("TUNE", color=if(dark) milk else titanium, fontFamily=SpaceGrotesk, fontWeight=FontWeight.Bold, fontSize=22.sp, letterSpacing=2.sp)
        Text("Adjust the instrument. Your ledger stays local.", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=11.sp, modifier=Modifier.padding(top=4.dp,bottom=18.dp))
        SectionLabel("LED THEMES", dark)
        themeNames.forEachIndexed { index, name ->
            val id=themeIds[index]
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp).background(glass,RoundedCornerShape(13.dp)).clickable(enabled=!state.themeLocked) { onTheme(id) }.padding(13.dp), verticalAlignment=Alignment.CenterVertically) {
                Text(if(state.themeId==id) "●" else "○", color=ledThemes[index][2], fontSize=14.sp); Spacer(Modifier.width(12.dp)); Text(name, color=milk, fontFamily=SpaceGrotesk, fontSize=12.sp, modifier=Modifier.weight(1f))
                ledThemes[index].forEach { Box(Modifier.padding(start=4.dp).size(13.dp).background(it,CircleShape)) }
            }
        }
        Text(if (state.themeLocked) "Theme locked after your first stage pass." else "Your chosen theme locks when you pass the first stage.", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=9.sp, modifier=Modifier.padding(top=5.dp))
        Spacer(Modifier.height(16.dp)); SectionLabel("GOAL · ${state.goalSteps} STEPS", dark)
        var slider by remember(state.goalSteps) { mutableFloatStateOf(state.goalSteps.toFloat()) }
        var pendingGoal by remember { mutableStateOf<Int?>(null) }
        Slider(value=slider,onValueChange={slider=(it/500).toInt()*500f},onValueChangeFinished={slider.toInt().coerceIn(2500,15000).takeIf { it!=state.goalSteps }?.let { pendingGoal=it }},valueRange=2500f..15000f,steps=24,colors=SliderDefaults.colors(thumbColor=led,activeTrackColor=led))
        Text("Stage cost: ${state.stageCost} fragments · five stages per pass", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=10.sp)
        Spacer(Modifier.height(16.dp)); SectionLabel("LIVE LOGS", dark)
        Row(Modifier.fillMaxWidth().padding(top=7.dp), verticalAlignment=Alignment.CenterVertically) {
            Text("Show recent step times on Base", color=if(dark) milk else titanium, fontFamily=Inter, fontSize=11.sp, modifier=Modifier.weight(1f))
            Switch(checked=state.showLiveLogs, onCheckedChange=onLiveLogs, colors=androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor=led, checkedTrackColor=led.copy(alpha=.45f)))
        }
        Spacer(Modifier.height(16.dp)); SectionLabel("CHASSIS", dark)
        listOf("system" to "System", "light" to "Brushed silver", "dark" to "Titanium black").forEach { (key,label) ->
            Row(Modifier.fillMaxWidth().clickable { onAppearance(key) }.padding(vertical=7.dp), verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).background(if(state.appearance==key) led else glass,CircleShape)); Spacer(Modifier.width(10.dp)); Text(label,color=if(dark) milk else titanium,fontFamily=Inter,fontSize=12.sp)
            }
        }
        Spacer(Modifier.height(16.dp)); SectionLabel("DATA · JSON", dark)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp), modifier=Modifier.padding(top=8.dp)) {
            ActionPill("EXPORT FILE", led, Modifier.weight(1f), onExport)
            ActionPill("IMPORT FILE", led, Modifier.weight(1f), onImport)
        }
        Text("Import and export the PRD schema through Android's file picker.", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=9.sp, modifier=Modifier.padding(top=7.dp))
        Spacer(Modifier.height(16.dp)); SectionLabel("STEP ENGINE", dark)
        Text(sensorMode, color=led, fontFamily=SpaceGrotesk, fontSize=11.sp, modifier=Modifier.padding(top=8.dp))
        Text("Local counting stays on this device. The dashboard only shows the step history you choose to keep.", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=10.sp, lineHeight=15.sp, modifier=Modifier.padding(top=5.dp,bottom=20.dp))
        SectionLabel("HELP · ALIVE UI", dark)
        Text("Metabasis adapts its dashboard to your rhythm: a calm, changing interface rather than a rigid control panel. Your chosen theme stays yours, and after the first stage passes it becomes part of your journey.", color=if(dark) muted else Color.DarkGray, fontFamily=Inter, fontSize=10.sp, lineHeight=15.sp, modifier=Modifier.padding(top=7.dp,bottom=20.dp))
        pendingGoal?.let { target -> AlertDialog(onDismissRequest={pendingGoal=null}, title={Text("Change your stage pace?")}, text={Text("The stage cost updates to ${target/5} fragments. Your current fragments stay in place.")}, confirmButton={TextButton(onClick={onGoal(target);pendingGoal=null}) {Text("Apply")}}, dismissButton={TextButton(onClick={pendingGoal=null}) {Text("Cancel")}}) }
    }
}

@Composable private fun SectionLabel(text:String,dark:Boolean) { Text(text,color=if(dark) muted else Color.DarkGray,fontFamily=SpaceGrotesk,fontSize=9.sp,letterSpacing=1.5.sp) }

@Composable private fun ActionPill(label:String, led:Color, modifier:Modifier=Modifier, onClick:()->Unit) {
    Surface(onClick=onClick, modifier=modifier.height(46.dp).shadow(7.dp,RoundedCornerShape(24.dp)), color=glass, shape=RoundedCornerShape(24.dp)) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text(label,color=led,fontFamily=SpaceGrotesk,fontWeight=FontWeight.Bold,fontSize=10.sp,letterSpacing=.8.sp) }
    }
}

@Composable private fun FloatingNavigation(selected:Int,led:Color,dark:Boolean,onSelect:(Int)->Unit) {
    val labels=listOf("BASE","TRACES","TUNE")
    Row(Modifier.fillMaxWidth().padding(start=14.dp,end=14.dp,bottom=12.dp,top=8.dp).height(68.dp).shadow(16.dp,RoundedCornerShape(32.dp)).background(Color(0xEF202124),RoundedCornerShape(32.dp)).padding(horizontal=8.dp,vertical=7.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
        labels.forEachIndexed { index,label ->
            val active=index==selected
            val width by animateDpAsState(if(active) 128.dp else 54.dp, spring(dampingRatio=.76f,stiffness=420f), label="nav-$label")
            Surface(onClick={onSelect(index)}, modifier=Modifier.width(width).fillMaxHeight(), color=if(active) led.copy(alpha=.18f) else Color.Transparent, shape=RoundedCornerShape(27.dp), contentColor=if(active) led else Color(0xFFB8BBC0)) {
                Row(Modifier.fillMaxSize().padding(horizontal=12.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.Center) {
                    NavGlyph(index,if(active) led else Color(0xFFB8BBC0),Modifier.size(22.dp))
                    AnimatedVisibility(active) { Text(label,color=if(active) milk else Color.LightGray,fontFamily=SpaceGrotesk,fontWeight=FontWeight.SemiBold,fontSize=10.sp,letterSpacing=.8.sp,maxLines=1,modifier=Modifier.padding(start=8.dp)) }
                }
            }
        }
    }
}

@Composable private fun NavGlyph(index:Int,color:Color,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val stroke=1.7.dp.toPx()
        when(index) {
            0 -> {
                val cx=size.width/2; val cy=size.height/2
                drawCircle(color.copy(alpha=.3f),size.minDimension*.45f,Offset(cx,cy),style=Stroke(stroke))
                drawCircle(color,size.minDimension*.22f,Offset(cx,cy),style=Stroke(stroke))
                drawCircle(color,size.minDimension*.06f,Offset(cx,cy))
            }
            1 -> {
                val cell=size.minDimension*.17f
                for(r in 0..2) for(c in 0..2) drawRoundRect(color,Offset(size.width*.12f+c*cell*1.45f,size.height*.12f+r*cell*1.45f),Size(cell,cell),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            }
            else -> {
                drawCircle(color,size.minDimension*.18f,Offset(size.width*.5f,size.height*.5f),style=Stroke(stroke))
                for(i in 0..7) { val a=i*Math.PI/4; val p1=Offset((size.width/2+cos(a).toFloat()*size.minDimension*.25f),(size.height/2+sin(a).toFloat()*size.minDimension*.25f)); val p2=Offset((size.width/2+cos(a).toFloat()*size.minDimension*.42f),(size.height/2+sin(a).toFloat()*size.minDimension*.42f)); drawLine(color,p1,p2,stroke,StrokeCap.Round) }
            }
        }
    }
}

@Composable private fun FootprintMark(color:Color,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        fun foot(x:Float,y:Float,angle:Float,scale:Float) {
            val center=Offset(x,y)
            drawOval(color,Offset(center.x-w*.095f*scale,center.y-h*.025f*scale),Size(w*.19f*scale,h*.31f*scale))
            val toe=Size(w*.07f*scale,h*.07f*scale)
            val toes=listOf(Offset(x-w*.12f*scale,y-h*.18f*scale),Offset(x-w*.04f*scale,y-h*.23f*scale),Offset(x+w*.045f*scale,y-h*.22f*scale),Offset(x+w*.12f*scale,y-h*.16f*scale))
            toes.forEachIndexed { i,p -> drawCircle(color.copy(alpha=1f-i*.08f),if(i==0) w*.038f*scale else w*.031f*scale,p) }
        }
        foot(w*.36f,h*.64f,-14f,.9f)
        foot(w*.68f,h*.39f,14f,.9f)
    }
}

@Composable private fun EvolutionToast(state:AppState,led:Color,dark:Boolean,modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal=22.dp).shadow(14.dp,RoundedCornerShape(18.dp)).background(Color(0xF2222326),RoundedCornerShape(18.dp)).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
        FootprintMark(led,Modifier.size(28.dp)); Spacer(Modifier.width(10.dp)); Column {
            Text("ITEM RECEIVED",color=led,fontFamily=SpaceGrotesk,fontWeight=FontWeight.Bold,fontSize=9.sp,letterSpacing=1.6.sp)
            Text("${state.lastEvolutionFrom} → ${state.lastEvolutionTo}",color=milk,fontFamily=Inter,fontSize=11.sp)
        }
    }
}

private fun cycleColor(base: Color, cycle: Int): Color {
    if (cycle < 4) return base
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    when ((cycle - 4) % 3) {
        0 -> hsv[1] = (hsv[1] * .85f).coerceIn(0f, 1f)
        1 -> hsv[0] = (hsv[0] + 6f) % 360f
        2 -> hsv[2] = (hsv[2] * .96f).coerceIn(0f, 1f)
    }
    return Color(android.graphics.Color.HSVToColor(hsv))
}
