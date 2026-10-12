package com.hax.focusdeck

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

private val Background = Color.Black
private val White get() = if (DeckColors.palette.id == "monochrome") Color(0xFFF4F4F4) else Color(0xFFF1F4F6)
private val Grey get() = DeckColors.muted
private val Cyan get() = DeckColors.accent
private val Yellow get() = DeckColors.secondary
private val Green get() = DeckColors.success
private val Red get() = DeckColors.error

class MainActivity : ComponentActivity() {
    private val repo get() = (application as DeckApplication).repository
    private val sounds get() = (application as DeckApplication).sounds
    private var touchStartX = 0f
    private var touchStartY = 0f
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if(event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {touchStartX=event.x;touchStartY=event.y}
        if(event.actionMasked == android.view.MotionEvent.ACTION_UP && kotlin.math.abs(event.x-touchStartX)<12*resources.displayMetrics.density && kotlin.math.abs(event.y-touchStartY)<12*resources.displayMetrics.density) { if(!repo.display.value.developerMode) sounds.play("ui") }
        return super.dispatchTouchEvent(event)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onStart() { super.onStart(); repo.setUiVisible(true) }
    override fun onStop() { repo.setUiVisible(false); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val stored by repo.display.collectAsStateWithLifecycle()
            DeckTheme(stored.theme, stored.motion, stored.immersive) {
                DisposableEffect(stored.immersive) {
                    WindowCompat.getInsetsController(window,window.decorView).apply {
                        isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false
                        systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        if(stored.immersive) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
                    }
                    onDispose {}
                }
                if(stored.developerMode) {
                    val previewScope=rememberCoroutineScope()
                    ProductionPreviewHost(repo,stored,disable={previewScope.launch { repo.developerMode(false) }})
                    return@DeckTheme
                }
                val connection by repo.connection.collectAsStateWithLifecycle()
                val tabletSnapshot by repo.tablet.snapshot.collectAsStateWithLifecycle()
                val storageError by repo.error.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                var devicesDialog by rememberSaveable { mutableStateOf(false) }
                var pairDialog by rememberSaveable { mutableStateOf(false) }
                var settingsDialog by rememberSaveable { mutableStateOf(false) }
                var soundDialog by rememberSaveable { mutableStateOf(false) }
                var developerDialog by rememberSaveable { mutableStateOf(false) }
                var activityHistory by rememberSaveable { mutableStateOf(false) }
                var dailyTimeline by rememberSaveable { mutableStateOf(false) }
                val timelineNavigation=remember { TimelineNavigationState() }
                val observedDate by remember(repo) {
                    combine(repo.now,repo.displayZone) { stamp,zone -> Instant.ofEpochMilli(stamp.wall).atZone(ZoneId.of(zone)).toLocalDate() }.distinctUntilChanged()
                }.collectAsStateWithLifecycle(initialValue=remember(repo) { Instant.ofEpochMilli(repo.now.value.wall).atZone(ZoneId.of(repo.displayZone.value)).toLocalDate() })
                val archivedReports by repo.history.collectAsStateWithLifecycle()
                var historyDialog by rememberSaveable { mutableStateOf(false) }
                var historyInitialPeriod by rememberSaveable { mutableStateOf(HistoryPeriod.WEEK) }
                var historyExpanded by rememberSaveable { mutableStateOf(false) }
                val homeLayout=rememberDashboardLayout("overview")
                var reportKey by rememberSaveable { mutableStateOf<String?>(null) }
                val reportDialog=archivedReports.find { it.key==reportKey }
                var menu by remember { mutableStateOf(false) }
                var menuRevision by remember { mutableStateOf(0L) }
                var endRequest by remember { mutableStateOf<Triple<String, Long, String>?>(null) }
                var achievementsPage by rememberSaveable { mutableStateOf(false) }
                var homeSelected by rememberSaveable { mutableStateOf(false) }
                var localError by remember { mutableStateOf("") }
                var pending by remember { mutableStateOf(false) }
                fun submitAction(name: String, expectedRevision: Long? = null) {
                    if (pending) return
                    pending = true
                    scope.launch {
                        try {
                            val ack = repo.command(UUID.randomUUID().toString(), name, expectedRevision = expectedRevision)
                            localError = ack.error.orEmpty()
                        } catch (e: Exception) { localError = e.message.orEmpty() }
                        finally { pending = false }
                    }
                }
                fun action(name: String) = submitAction(name)
                LaunchedEffect(stored.deviceId) {
                    if (stored.deviceId.isNotEmpty()) {
                        try { startForegroundService(Intent(this@MainActivity, FocusService::class.java)) }
                        catch (e: Exception) { localError = "Cannot keep LAN connection running: ${e.message}" }
                    }
                }
                val viewer = stored.pairing?.viewer == true
                val active = !viewer && stored.state.phase in listOf("active", "paused")
                val timerVisible = active && !homeSelected && !achievementsPage && !historyDialog && !activityHistory && !dailyTimeline && !settingsDialog && !soundDialog && !developerDialog && !pairDialog && reportDialog == null
                val clockPage = !viewer && stored.state.session != null && stored.state.phase in listOf("active", "paused", "ready", "between") &&
                    !homeSelected && !achievementsPage && !historyDialog && !activityHistory && !dailyTimeline && !settingsDialog && !soundDialog && !developerDialog && !pairDialog && reportDialog == null
                val skipColor = Color(0xFFFFC857)
                val endColor = Color(0xFFFF6B6B)
                val panelMax = remember { display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f }
                DisposableEffect(stored.dimDesk, timerVisible, stored.timerRefreshRate) {
                    window.attributes = window.attributes.apply {
                        screenBrightness = if (stored.dimDesk && timerVisible) 0.08f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        // A preference, not a guarantee of the panel's actual refresh rate.
                        preferredRefreshRate = NavigationRefresh.requested(timerVisible, stored.dimDesk, stored.timerRefreshRate, panelMax)
                    }
                    onDispose {
                        window.attributes = window.attributes.apply {
                            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            preferredRefreshRate = 0f
                        }
                    }
                }
                DisposableEffect(stored.keepAwake, active) {
                    if (stored.keepAwake && active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                val page = when {
                    achievementsPage -> "achievements"
                    homeSelected -> "home"
                    viewer && (stored.state.session == null && tabletSnapshot.state == null || stored.state.phase in listOf("active", "paused", "ready", "between", "quality", "reward", "session_reward")) -> "viewer"
                    stored.state.phase in listOf("quality", "reward", "session_reward") -> "reward"
                    stored.state.session != null && stored.state.phase in listOf("active", "paused", "ready", "between") -> "focus"
                    else -> "home"
                }
                val toolbarMenu: @Composable ()->Unit = {
                            Box {
                                TextButton(onClick = { menuRevision = stored.state.revision; menu = true }) { Text("⋮", color = Grey, fontSize = 22.sp) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Home") }, onClick = { menu=false;achievementsPage=false;homeSelected=true })
                                    DropdownMenuItem(text = { Text("Achievements") }, onClick = { menu=false;achievementsPage=true;homeSelected=false })
                                    if(stored.state.session!=null) DropdownMenuItem(text = { Text("Focus session") }, onClick = { menu=false;achievementsPage=false;homeSelected=false })
                                    DropdownMenuItem(text = { Text("Pair desktop") }, onClick = { menu = false; pairDialog = true })
                                    DropdownMenuItem(text = { Text("Devices") }, onClick = { menu=false;devicesDialog=true })
                                    DropdownMenuItem(text = { Text("Sounds") }, onClick = { menu = false; soundDialog = true })
                                    if(!viewer) DropdownMenuItem(text={Text("Developer Mode settings")},onClick={menu=false;settingsDialog=true})
                                    if(stored.developerMode && !viewer) DropdownMenuItem(text = { Text("Developer preview") }, onClick = { menu = false; developerDialog = true })
                                    DropdownMenuItem(text = { Text("Display settings") }, onClick = { menu = false; settingsDialog = true })
                                    if(!achievementsPage && (homeSelected || stored.state.session==null || stored.state.phase in listOf("idle","complete","stopped"))) DropdownMenuItem(text={Text("Edit Home layout")},onClick={menu=false;homeLayout.editing=true})
                                    DropdownMenuItem(text = { Text("Analytics & logs") }, onClick = { menu = false; historyDialog = true })
                                    if (clockPage) {
                                        HorizontalDivider()
                                        DropdownMenuItem(text = { Text("Skip block", color = skipColor) }, enabled = active && !pending,
                                            onClick = { menu = false; submitAction("skip_block", menuRevision) })
                                        DropdownMenuItem(text = { Text("End block", color = endColor) }, enabled = active && !pending,
                                            onClick = { menu = false; endRequest = Triple("end_block", menuRevision,
                                                stored.state.session?.blocks?.getOrNull(stored.state.index)?.title.orEmpty()) })
                                        DropdownMenuItem(text = { Text("End session", color = endColor) }, enabled = !pending,
                                            onClick = { menu = false; endRequest = Triple("stop", menuRevision, stored.state.session?.title.orEmpty()) })
                                    }
                                }
                            }
                }
                val navigation=PrimaryNavigation(if(page in listOf("viewer","reward")) "focus" else page,
                    home={achievementsPage=false;homeSelected=true},achievements={achievementsPage=true;homeSelected=false},
                    analytics={historyDialog=true},focus=if(stored.state.session!=null || viewer) ({achievementsPage=false;homeSelected=false}) else null)
                DestinationBackHandler(page=="home") { achievementsPage=false;homeSelected=true }
                CompositionLocalProvider(LocalPrimaryNavigation provides navigation,LocalTimelineNavigation provides timelineNavigation,
                    LocalDashboardDate provides observedDate) {
                Surface(Modifier.fillMaxSize(), color = if(page in listOf("home","achievements")) DashboardBackground else Background) {
                    Column(Modifier.windowInsetsPadding(dashboardViewportInsets()).padding(horizontal = if(page in listOf("home","achievements")) 16.dp else 24.dp, vertical = if(stored.immersive) 4.dp else 12.dp)) {
                        if(page=="home") DashboardHomeHeader(connection,toolbarMenu)
                        else if(page!="achievements") Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            if(!stored.immersive) {
                                IconButton({historyDialog=true}) { UiGlyph("chart",Modifier.size(24.dp)) }
                                IconButton({achievementsPage=!achievementsPage}) { UiGlyph("trophy",Modifier.size(24.dp)) }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(if(connection=="Connected") "● LAN" else "○ OFFLINE",fontSize=10.sp,color=if(connection=="Connected") { if(page=="reward") White else Cyan } else Grey)
                            toolbarMenu()
                        }
                        if (viewer) TabletViewerBanner(repo)
                        val message = localError.ifEmpty { storageError }.ifEmpty { stored.state.notice }
                        if (message.isNotEmpty()) Text(message, color = Yellow, fontSize = 12.sp)
                        FadeSlidePage(page, Modifier.weight(1f).fillMaxWidth()) { visible ->
                            when (visible) {
                                "achievements" -> AchievementScreen(repo) { achievementsPage = false;homeSelected=true }
                                "viewer" -> TabletSessionScreen(repo)
                                "reward" -> RewardScreen(stored.state, pending, ::action)
                                "focus" -> FocusScreen(stored.state, repo, stored.dimDesk, pending, ::action)
                                else -> CompositionLocalProvider(LocalDashboardLayout provides homeLayout, LocalAmbientEnabled provides (page == "home" && !historyDialog && !activityHistory && !dailyTimeline && !pairDialog && !settingsDialog && !soundDialog && !developerDialog && reportDialog == null && !menu)) {
                                    HomeScreen(repo, stored.pairing != null, connection,
                                        history = { activityHistory = true }, pair = { pairDialog = true }, openReport = { reportKey = it.key }, expandAnalytics = { dailyTimeline=true }, openSession={homeSelected=false},achievements={achievementsPage=true;homeSelected=false})
                                }
                            }
                        }
                        if(!stored.immersive) DashboardNavigation(page,home={achievementsPage=false;homeSelected=true},achievements={achievementsPage=true;homeSelected=false},
                            analytics={historyDialog=true},focus=if(stored.state.session!=null) ({achievementsPage=false;homeSelected=false}) else null)
                    }
                }
                endRequest?.let { request ->
                    val wholeSession = request.first == "stop"
                    AlertDialog(onDismissRequest = { endRequest = null },
                        title = { Text(if (wholeSession) "End this session?" else "End this block?") },
                        text = { Text(if (wholeSession)
                            "${request.third}\n\nSave your measured time and completed blocks in a partial report. Remaining blocks will not be completed. This ends the attempt; it cannot be resumed."
                            else "${request.third}\n\nSave time already spent and mark this block Ended early, without completion credit or a reward. Continue to the next block.") },
                        confirmButton = { TextButton(enabled = !pending, onClick = {
                            endRequest = null; submitAction(request.first, request.second)
                        }) { Text(if (wholeSession) "End session" else "End block", color = endColor) } },
                        dismissButton = { TextButton(onClick = { endRequest = null }) { Text("Cancel") } })
                }
                if(devicesDialog) DeviceManagementDialog(stored.pairing,connection,{devicesDialog=false},{devicesDialog=false;pairDialog=true},{startForegroundService(Intent(this@MainActivity,FocusService::class.java))},{stopService(Intent(this@MainActivity,FocusService::class.java))})
                if (pairDialog) PairDialog(stored.pairing, { pairDialog = false },stored.deviceId,
                    trustChangeAllowed=stored.state.phase !in listOf("active","paused","ready","between","quality","reward","session_reward")) { pairing, expected ->
                    repo.pair(pairing,expected); pairDialog=false
                    startForegroundService(Intent(this@MainActivity, FocusService::class.java))
                }
                if (settingsDialog) SettingsDialog(stored, connection, { settingsDialog = false },
                    save = { awake, immersive, sound, dim, motion, theme, rate -> scope.launch { repo.preferences(awake, immersive, sound, dim, motion, theme, rate) } },
                    connect = { startForegroundService(Intent(this@MainActivity, FocusService::class.java)) },
                    disconnect = { stopService(Intent(this@MainActivity, FocusService::class.java)) },
                    developer = { enabled -> scope.launch { repo.developerMode(enabled) } })
                if(soundDialog) SoundSettingsDialog(stored,repo) { soundDialog=false }
                if(developerDialog && stored.developerMode) DeveloperPreviewDialog { developerDialog=false }
                if(activityHistory) ActivityHistoryDialog(repo,{activityHistory=false},{reportKey=it.key},{activityHistory=false;historyDialog=true})
                if(dailyTimeline) RepositoryTimelineDialog(repo,{dailyTimeline=false},{reportKey=it.key})
                if (historyDialog) StudyHistoryDialog(repo, { historyDialog = false;historyInitialPeriod=HistoryPeriod.WEEK;historyExpanded=false;achievementsPage=false;homeSelected=true }, ::copySummary, historyInitialPeriod, initialExpanded=historyExpanded, home={historyDialog=false;historyExpanded=false;achievementsPage=false;homeSelected=true}, achievements={historyDialog=false;historyExpanded=false;achievementsPage=true;homeSelected=false}, focus=navigation.focus?.let { go -> { historyDialog=false;historyExpanded=false;go() } })
                reportDialog?.let { report -> ReportDetailsDialog(report, { reportKey = null }, { copySummary(report) },
                    delivered = report.key in stored.delivered, online = !viewer && connection == "Connected",
                    send = if (!viewer && stored.reports.any { it.key == report.key }) ({ scope.launch { repo.resendReport(report.key) }; Unit }) else null,currentReport={repo.history.value.find { it.key==report.key }}) }
                } // Primary destination navigation is also inherited by app dialogs.
            }
        }
    }

    private fun copySummary(report: Report) {
        lifecycleScope.launch {
            val canonical=repo.history.value.find { it.key==report.key } ?: return@launch
            val text=withContext(Dispatchers.Default) { canonical.summary() }
            if(repo.history.value.none { it.key==canonical.key }) return@launch
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("FocusDeck report",text))
        }
    }
}

@Composable
private fun FocusScreen(state: DeckState, repo: DeckRepository, dimDesk: Boolean, busy: Boolean, action: (String) -> Unit) {
    val now by repo.now.collectAsStateWithLifecycle()
    val session = state.session ?: return
    val block = session.blocks[state.index]
    val timer = TimerEngine(state)
    val stopwatch = block.stopwatch
    val activeMs = timer.currentActive(now)
    val remaining = timer.remaining(now) ?: 0
    val overtime = timer.isOvertime(now)
    val excess = timer.overtime(now)
    val fraction = if (stopwatch) stopwatchMinuteFraction(activeMs)
        else (activeMs.toFloat() / (state.budgetMs ?: 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    // Untimed session progress measures DONE blocks, never an invented duration.
    val progress = if (stopwatch) state.results.count { it.outcome == "done" }.toFloat() / session.blocks.size
        else (state.index + fraction) / session.blocks.size
    val accent = if (overtime) Red else if (block.type == "break") Yellow else Cyan
    val label = when {
        stopwatch && state.phase == "paused" -> "stopwatch · paused"
        stopwatch && state.phase in listOf("ready", "between") -> "stopwatch · ready"
        stopwatch -> "STOPWATCH"
        overtime && state.phase == "paused" -> "overtime · paused"
        overtime -> "running in excess"
        state.phase == "paused" -> "paused"
        state.phase in listOf("ready", "between") -> "ready"
        block.type == "break" -> "take a break"
        else -> "in focus"
    }
    if (dimDesk) {
        val shift = ((now.mono / 60_000) % 9).toInt()
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).offset(x = ((shift % 3 - 1) * 4).dp, y = ((shift / 3 - 1) * 4).dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(block.title, color = Grey.copy(alpha = 0.65f), fontSize = 18.sp,
                    textAlign = TextAlign.Center, maxLines = 3)
                Spacer(Modifier.height(20.dp))
                Text(if (stopwatch) stopwatchText(activeMs) else timerText(if (overtime) excess else remaining, overtime), color = accent.copy(alpha = 0.65f),
                    fontSize = 44.sp, fontWeight = FontWeight.ExtraLight)
                Spacer(Modifier.height(10.dp))
                Text(label, color = Grey.copy(alpha = 0.65f), fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { action(if (state.phase in listOf("ready", "between")) "resume" else "next") },
                    enabled = !busy, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(if (state.phase in listOf("ready", "between")) "START" else "DONE", color = accent)
                }
                if (state.phase in listOf("active", "paused")) OutlinedButton(
                    onClick = { action(if (state.phase == "paused") "resume" else "pause") }, enabled = !busy,
                    modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(if (state.phase == "paused") "RESUME" else "PAUSE", color = Grey)
                }
            }
        }
        return
    }
    val next = session.blocks.getOrNull(state.index + 1)
    val futureMs = session.blocks.drop(state.index + 1).sumOf { it.plannedMs ?: 0 }
    val finish = if (stopwatch || session.blocks.drop(state.index + 1).any { it.stopwatch }) "N/A (stopwatch)" else Instant.ofEpochMilli(now.wall + remaining + futureMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val dialSize = if (landscape) minOf(maxHeight * 0.62f, maxWidth * 0.42f)
            else minOf(maxWidth * 0.94f, maxHeight * 0.49f)
        @Composable fun mainDial(modifier: Modifier) {
            Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(session.title, color = Grey, fontSize = 18.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text(block.title, fontSize = if (landscape) 22.sp else 28.sp, fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center, maxLines = 3, color = accent)
                Spacer(Modifier.height(if (landscape) 12.dp else 28.dp))
                if (stopwatch) StopwatchDial(activeMs, label, accent, Grey, Modifier.size(dialSize))
                else Dial(if (overtime) excess else remaining, fraction, label, accent, Modifier.size(dialSize), overtime)
            }
        }
        @Composable fun footer(modifier: Modifier) {
            Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(4.dp),
                        color = accent, trackColor = DeckColors.track)
                    Spacer(Modifier.width(16.dp)); Text("${(progress * 100).toInt()}%${if (stopwatch) " DONE" else ""}", color = Grey, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Block", "${state.index + 1} / ${session.blocks.size}")
                    Metric("Next", next?.let { "${it.title}\n${if (it.stopwatch) "STOPWATCH" else humanTime(it.plannedMs)}" } ?: "Finish", Modifier.weight(1f))
                    Metric(if (state.phase == "paused") "Finish if resumed" else "Est. finish", finish)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.phase in listOf("ready", "between")) {
                        Button(onClick = { action("resume") }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Background)) { Text("S T A R T") }
                    } else {
                        OutlinedButton(onClick = { action("next") }, enabled = !busy, modifier = Modifier.weight(1.4f).height(56.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accent)) {
                            Text("D O N E", color = accent, fontWeight = FontWeight.Normal)
                        }
                        OutlinedButton(onClick = { action(if (state.phase == "paused") "resume" else "pause") }, enabled = !busy,
                            modifier = Modifier.weight(1f).height(56.dp)) { Text(if (state.phase == "paused") "RESUME" else "PAUSE", color = Grey, fontSize = 12.sp) }
                    }
                }
            }
        }
        if (landscape) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            mainDial(Modifier.weight(1f)); footer(Modifier.weight(1f).verticalScroll(rememberScrollState()))
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(18.dp)); mainDial(Modifier.fillMaxWidth()); Spacer(Modifier.height(36.dp))
            footer(Modifier.fillMaxWidth()); Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Grey, fontSize = 11.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp)); Text(value, fontSize = 15.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, maxLines = 3)
    }
}

@Composable
private fun Dial(remaining: Long, fraction: Float, label: String, accent: Color, modifier: Modifier, overtime: Boolean = false) {
    val time = timerText(remaining, overtime)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2 - 6.dp.toPx()
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(Color(0xFF3A3A3A), radius, center, style = Stroke(3.dp.toPx()))
            val inset = size.minDimension / 2 - radius
            drawArc(accent, -90f, fraction * 360f, false, topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            val angle = Math.toRadians((fraction * 360 - 90).toDouble())
            drawCircle(accent, 4.dp.toPx(), center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, fontSize = if (time.length > 6) 44.sp else 66.sp, fontWeight = FontWeight.ExtraLight, color = accent)
            Spacer(Modifier.height(8.dp)); Text(label, fontSize = 13.sp, fontWeight = FontWeight.Light, color = Grey, letterSpacing = 3.sp)
        }
    }
}

private fun timerText(remaining: Long, overtime: Boolean): String {
    val seconds = if (overtime) remaining / 1000 else (remaining + 999) / 1000
    return (if (overtime) "+" else "") +
        if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
        else "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
internal fun SettingsDialog(stored: StoredDeck, connection: String, dismiss: () -> Unit,
    save: (Boolean, Boolean, Boolean, Boolean, String, String, Int) -> Unit, connect: () -> Unit, disconnect: () -> Unit, developer: (Boolean) -> Unit,connectionControls: Boolean=true) {
    var awake by remember { mutableStateOf(stored.keepAwake) }
    var immersive by remember { mutableStateOf(stored.immersive) }
    var sound by remember { mutableStateOf(stored.sound) }
    var dim by remember { mutableStateOf(stored.dimDesk) }
    var motion by remember { mutableStateOf(stored.motion) }
    var theme by remember { mutableStateOf(stored.theme) }
    var rate by remember { mutableStateOf(stored.timerRefreshRate) }
    var dev by remember { mutableStateOf(stored.developerMode) }
    DeckAlertDialog(onDismissRequest = dismiss, title = { Text("Display & connection") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(awake, { awake = it }); Text("Keep screen awake") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(immersive, { immersive = it }); Text("Immersive display") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sound, { sound = it }); Text("Subtle completion sound") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(dim, { dim = it }); Text("Dim desk view") }
            Text("Small timer, no dial, 8% app brightness. Uses the normal screen; not Samsung AOD. Turn off screen-awake and lock the phone for lowest display power.",
                fontSize = 12.sp, color = Grey)
            Text("Motion", color = Cyan)
            Row {
                for (mode in listOf("FULL", "REDUCED", "OFF")) TextButton(onClick = { motion = mode }) {
                    Text(mode, color = if (motion == mode) Cyan else Grey)
                }
            }
            Text("Global palette", color = Cyan)
            DeckPalettes.all.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth()) {
                row.forEach { palette -> TextButton(onClick = { theme = palette.id }, modifier = Modifier.weight(1f)) {
                    Text((if (theme == palette.id) "✓ " else "● ") + palette.title, color = palette.accent, fontSize = 12.sp)
                } }
            } }
            Text("Timer refresh rate", color = Cyan)
            Row { listOf(30, 60, 120).forEach { value -> TextButton(onClick = { rate = value }) {
                Text("$value Hz", color = if (rate == value) Cyan else Grey)
            } } }
            Text("Navigation requests up to 120 Hz. Dim timer requests 30 Hz. Actual refresh depends on phone display and power settings.", fontSize = 12.sp, color = Grey)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(dev, { dev = it }); Text("Developer mode") }
            Text("Shows the read-only three-year synthetic preview at its fixed simulated date. Use real data returns to your saved activity.", fontSize = 12.sp, color = Grey)
            Text(connection, fontSize = 12.sp, color = Grey)
            if(connectionControls) {TextButton(connect) { Text("CONNECT DESKTOP") };TextButton(disconnect) { Text("DISCONNECT DESKTOP") }}
        }
    }, confirmButton = { TextButton(onClick = { save(awake, immersive, sound, dim, motion, theme, rate); developer(dev); dismiss() }) { Text("SAVE") } },
        dismissButton = { TextButton(dismiss) { Text("CANCEL") } })
}
