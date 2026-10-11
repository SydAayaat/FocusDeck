package com.hax.focusdeck

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.spring
import java.util.UUID

internal val ACyan get() = DeckColors.accent
internal val AGreen get() = DeckColors.success
internal val ARed get() = DeckColors.error
internal val AGrey get() = DeckColors.muted
internal val ABorder get() = DeckColors.border

@Composable
fun AchievementScreen(repo: DeckRepository, close: () -> Unit) {
    val stored by repo.achievements.collectAsStateWithLifecycle()
    val timer by repo.display.collectAsStateWithLifecycle()
    val now by repo.now.collectAsStateWithLifecycle()
    val zoneId by repo.displayZone.collectAsStateWithLifecycle()
    val zone = ZoneId.of(zoneId)
    val readOnly = timer.pairing?.viewer == true
    // Preview measured progress between durable timer checkpoints; never writes from composition.
    val store by produceState(stored, stored, timer.state, now, zone, readOnly) {
        value = withContext(Dispatchers.Default) {
            if (readOnly) stored else AchievementEngine.finalize(AchievementEngine.capture(stored, timer.state, now, zone), now.wall, zone)
        }
    }
    val scope = rememberCoroutineScope()
    var cardError by remember { mutableStateOf("") }
    AchievementPages(store, now.wall, zone, repo::recordViewed, close,
        onLayout = { id, size, target -> scope.launch {
            try { repo.cardLayout(id, size, target); cardError = "" }
            catch (e: Exception) { cardError = e.message.orEmpty() }
        } }, onManage = { id, action -> scope.launch {
            try {
                val ack = repo.achievementCommand(AchievementCommand(achievement_version = 2, operation = action,
                    id = UUID.randomUUID().toString(), timestamp = Instant.now().toString(), source = "phone",
                    achievement_id = id, confirm = action == "delete"))
                cardError = ack.error.orEmpty()
            } catch (e: Exception) { cardError = e.message.orEmpty() }
        } }, error = cardError, readOnly = readOnly, onIndexCommand = { command -> scope.launch {
            try { cardError = repo.achievementCommand(command).error.orEmpty() } catch(e: Exception) { cardError=e.message.orEmpty() }
        } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AchievementPages(store: AchievementStore, now: Long, zone: ZoneId, onRecordViewed: suspend (String) -> Unit, close: () -> Unit,
    onLayout: (String, String?, String?) -> Unit = { _, _, _ -> }, onManage: (String, String) -> Unit = { _, _ -> }, error: String = "", readOnly: Boolean = false, onIndexCommand: (AchievementCommand) -> Unit = {}) {
    val layout=rememberDashboardLayout("achievements")
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val editing=layout.editing
    var archived by remember { mutableStateOf(false) }
    var layoutMenu by remember { mutableStateOf(false) }
    val mode = LocalAchievementMotion.current
    val pager = rememberPagerState(pageCount = { AchievementEngine.categories.size })
    val scope = rememberCoroutineScope()
    BackHandler { if(selected!=null) selected=null else if(layout.editing) layout.editing=false else close() }
    val statsById by produceState<Map<String, AchievementStats>>(emptyMap(), store, now, zone) {
        value = withContext(Dispatchers.Default) { store.definitions.associate { it.id to AchievementEngine.stats(store, it, now, zone) } }
    }
    CompositionLocalProvider(LocalDashboardLayout provides layout) {
    FadeSlidePage(selected?.takeIf { id -> store.definitions.any { it.id == id } }, Modifier.fillMaxSize()) { visible ->
    val def = store.definitions.find { it.id == visible }
    if (def != null) AchievementDetail(store, def, now, zone, onRecordViewed, { selected = null }, close, statsById[def.id], readOnly, onIndexCommand, error)
    else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EntryMotion { Column(verticalArrangement=Arrangement.spacedBy(5.dp)) { Text("Achievements",fontSize=30.sp,fontWeight=FontWeight.Bold);Text("Track your streaks, goals, and progress.",fontSize=12.sp,lineHeight=16.sp,color=AGrey) } }
        if (error.isNotBlank()) Text(error, color = DeckColors.secondary, fontSize = 12.sp)
        EntryMotion(delay = 35) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val normal=maxWidth>=350.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale<=1.25f
                val fit=normal && AchievementEngine.categories.size<=6
                val tabWidth=(maxWidth/6).coerceAtLeast(62.dp)
                Row(Modifier.fillMaxWidth().then(if(fit) Modifier else Modifier.horizontalScroll(rememberScrollState())).semantics { contentDescription="Achievement category filters" }) {
                    AchievementEngine.categories.forEachIndexed { index,name->
                        val chosen=pager.currentPage==index
                        Column((if(fit) Modifier.weight(1f) else if(normal) Modifier.width(tabWidth) else Modifier).selectable(chosen,role=Role.Tab,onClick={scope.launch {
                            if(mode==AchievementMotion.OFF) pager.scrollToPage(index) else pager.animateScrollToPage(index,animationSpec=tween(mode.duration))
                        }}).semantics { contentDescription="Category $name" },horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(name,Modifier.padding(horizontal=if(normal) 1.dp else 12.dp,vertical=8.dp),fontSize=11.sp,lineHeight=14.sp,letterSpacing=0.sp,maxLines=1,color=if(chosen) ACyan else AGrey)
                            Box((if(normal) Modifier.fillMaxWidth() else Modifier.width((name.length*6+24).dp)).height(2.dp).background(if(chosen) ACyan else DeckColors.track))
                        }
                    }
                }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = !editing,
            key = { AchievementEngine.categories[it] }) { page ->
            val category = AchievementEngine.categories[page]
            Box(Modifier.fillMaxSize().then(if(page!=pager.currentPage) Modifier.clearAndSetSemantics {} else Modifier)) {
            AchievementCards(store, AchievementEngine.filtered(store, category), now, zone, editing, category,
                onRecordViewed = onRecordViewed, onOpen = { selected = it },
                onEdit = { layout.editing = true }, onLayout = if(readOnly) ({_,_,_->}) else onLayout, onManage = if(readOnly) ({_,_->}) else onManage, statsById = statsById, readOnly=readOnly, header = {
        EntryMotion(delay = 65) { if (statsById.isEmpty() && store.definitions.isNotEmpty()) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ACyan) else LayoutPanel("achievement-overview",180) { AchievementOverview(store, now, zone, statsById) } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Achievements",fontSize=15.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if(editing) TextButton({layout.editing=false}) { Text("DONE",fontSize=11.sp) }
            Box {
                IconButton({layoutMenu=true},Modifier.size(32.dp).testTag("achievement-options")) { Text("⋮",color=AGrey,fontSize=24.sp) }
                DropdownMenu(layoutMenu,{layoutMenu=false}) {
                    DropdownMenuItem(text={Text("Archived cards")},onClick={layoutMenu=false;archived=true})
                    DropdownMenuItem(text={Text("Edit layout")},onClick={layoutMenu=false;layout.editing=true})
                }
            }
        }
        if (editing) {
            Row { TextButton({layout.reset()}) { Text("RESET LAYOUT",fontSize=10.sp) }
                if(!readOnly) TextButton({onLayout("__reset_order__",null,null)}) { Text("RESET ORDER",fontSize=10.sp) } }
            Text("Grip: reorder · edges/corner: resize. Tap for existing card options.",color=AGrey,fontSize=12.sp)
        }
            })
            }
        }
    }
    }
    }
    if (archived) DeckAlertDialog(onDismissRequest = { archived = false }, title = { Text("Archived cards") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val hidden = store.definitions.filter { it.archived }
            if (hidden.isEmpty()) Text("No archived cards")
            hidden.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, Modifier.weight(1f))
                    if (!readOnly) TextButton(onClick = { onManage(item.id, "restore") }) { Text("RESTORE") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { archived = false }) { Text("CLOSE") } })
}

@Composable private fun Label(text: String) { Text(text, color = AGrey, fontSize = 11.sp, letterSpacing = 1.sp) }
@Composable private fun Outline(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = DeckColors.surface, border = BorderStroke(1.dp, ABorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable private fun AchievementOverview(store: AchievementStore, now: Long, zone: ZoneId, statsById: Map<String, AchievementStats>) {
    val definitions=AchievementEngine.filtered(store,"All")
    val all=definitions.filter { it.streakEnabled }.mapNotNull { statsById[it.id] }
    val daily=remember(store,now,zone) { AchievementEngine.progress(store,AchievementEngine.date(now,zone)) }
    val records=all.sumOf { it.completed.count { segment -> segment.personalRecord } }+all.count { it.newRecord }
    val progressCards=definitions.filter { it.progressEnabled }
    FDCard(Modifier.fillMaxWidth()) {
        Text("Overview",fontSize=15.sp,fontWeight=FontWeight.Bold)
        DashboardMetricStrip(listOf(
            DashboardMetric("overview-active","Active streaks","${all.count { it.current>0 }} / ${all.size}","flame"),
            DashboardMetric("overview-records","Personal bests",records.toString(),"trophy"),
            DashboardMetric("overview-daily","Daily target",if(daily.isEmpty()) "Not set" else "${daily.count { it.met }} / ${daily.size}","target"),
            DashboardMetric("overview-progress","Progress cards","${progressCards.count { it.progress?.targetValue!=null }} / ${progressCards.size}","chart-no-axes-column")
        ))
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun AchievementTile(stats: AchievementStats, store: AchievementStore, onRecordViewed: suspend (String) -> Unit,
    modifier: Modifier, size: String = "compact", longPress: (() -> Unit)? = null, options: (() -> Unit)? = longPress, open: () -> Unit) {
    val mode=LocalAchievementMotion.current
    val interaction=remember { MutableInteractionSource() };val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed && mode!=AchievementMotion.OFF) .985f else 1f,tween(mode.duration),label="Card press")
    val def=stats.definition;val p=def.progress
    val touch=Modifier.combinedClickable(interactionSource=interaction,indication=null,onClick=open,onLongClick=longPress)
    FDCard(modifier.graphicsLayer { scaleX=scale;scaleY=scale }.then(touch).semantics { contentDescription="Open ${def.title} achievement" },padding=10.dp) {
        if(size=="compact") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                AchievementRing(stats,store,onRecordViewed,Modifier.size(44.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(def.title,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    Text(def.category.replaceFirstChar { it.uppercase() },fontSize=10.sp,color=AGrey)
                }
            }
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            AchievementRing(stats,store,onRecordViewed,Modifier.size(if(size=="full") 84.dp else 62.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(def.title,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                Text(def.category.replaceFirstChar { it.uppercase() },fontSize=10.sp,color=AGrey)
            }
        }
        if(LocalDashboardLayout.current?.editing==true && options!=null) TextButton(options,contentPadding=PaddingValues(0.dp)) { Text("Card options",fontSize=10.sp,color=AGrey) }
        if(def.progressEnabled) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                Text("${AchievementEngine.progressValue(p?.currentValue ?: 0.0)} / ${p?.targetValue?.let(AchievementEngine::progressValue) ?: "—"}",Modifier.weight(1f),fontSize=11.sp)
                Text(p?.percent?.let { "${it.toInt()}%" } ?: "N/A",fontSize=10.sp,color=ACyan)
            }
            Text(p?.unit ?: "units",fontSize=10.sp,color=AGrey)
            val fraction by animateFloatAsState(((p?.percent ?: 0.0)/100).toFloat().coerceIn(0f,1f),tween(mode.duration),label="Card progress")
            FDTrack(fraction,color=if(p?.complete==true) AGreen else ACyan)
            if(p?.complete==true) Text("COMPLETE",fontSize=10.sp,color=AGreen)
        }
        if(def.streakEnabled) {
            HorizontalDivider(color=DeckColors.track)
            CompactStreakMetrics(stats)
            if(stats.newRecord) RecordBadge()
        }
    }
}
@Composable private fun CompactStreakMetrics(stats: AchievementStats) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        listOf("Current" to if(stats.initialized) stats.current else null,"Best" to (stats.previousBest ?: stats.best),"Gap" to stats.gap).forEach { (label,value)->
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(AchievementEngine.value(value,stats.definition.kind=="abstinence",label=="Gap"),fontSize=10.sp,color=if(label=="Gap" && value!=null && value!=0.0) if(value>0) AGreen else ARed else Color.White)
                Text(label,fontSize=9.sp,letterSpacing=(-.2).sp,maxLines=1,color=AGrey)
            }
        }
    }
}
@Composable private fun StreakMetrics(stats: AchievementStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Metric("Current", if (stats.initialized) stats.current else null, stats, Modifier.weight(1f))
        Metric("Best", stats.previousBest ?: stats.best, stats, Modifier.weight(1f))
        Metric("Gap", stats.gap, stats, Modifier.weight(1f), gap = true)
    }
}
@Composable private fun ProgressNumbers(p: AchievementProgress?, fontSize: Int = 20) {
    val number by animateFloatAsState(p?.currentValue?.toFloat() ?: 0f, tween(LocalAchievementMotion.current.duration), label = "Progress total")
    val displayed = if ((p?.currentValue ?: 0.0) > 1e7 || kotlin.math.abs(number.toDouble() - (p?.currentValue ?: 0.0)) < .001) p?.currentValue ?: 0.0
        else if (p?.currentValue?.rem(1.0) == 0.0) kotlin.math.round(number.toDouble()) else number.toDouble()
    Text("${AchievementEngine.progressValue(displayed)} / ${p?.targetValue?.let(AchievementEngine::progressValue) ?: "—"}", fontSize = fontSize.sp)
}
@Composable private fun ProgressPanel(def: AchievementDefinition) {
    val p = def.progress
    val percent by animateFloatAsState((p?.percent ?: 0.0).toFloat(), tween(LocalAchievementMotion.current.duration), label = "Progress panel")
    val accent = if (p?.complete == true) AGreen else ACyan
    Outline(Modifier.fillMaxWidth()) {
        Label(def.metadata["progress_label"] ?: "PROGRESS")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { ProgressNumbers(p); Text(p?.unit ?: "units", color = AGrey, fontSize = 12.sp) }
            Text(if (p?.percent == null) "N/A" else "${percent.toInt()}%", color = accent, fontSize = 24.sp)
        }
        LinearProgressIndicator(progress = { percent/100f }, modifier = Modifier.fillMaxWidth().height(7.dp), color = accent, trackColor = ABorder)
        if (p?.complete == true) Text("COMPLETE", color = AGreen, fontSize = 12.sp)
        if (p?.targetValue == null) Text("GPT supplies the total for this goal.", color = AGrey, fontSize = 12.sp)
    }
}
@Composable private fun Metric(label: String, value: Double?, stats: AchievementStats, modifier: Modifier, gap: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        val animated by animateFloatAsState(value?.toFloat() ?: 0f, tween(LocalAchievementMotion.current.duration), label = label)
        Text(AchievementEngine.value(if (value == null) null else animated.toDouble(), stats.definition.kind == "abstinence", gap),
            color = if (!gap || value == null || value == 0.0) Color.White else if (value > 0) AGreen else ARed,
            fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(label, color = AGrey, fontSize = 10.sp)
    }
}
@Composable private fun RecordBadge() {
    Text("NEW RECORD", Modifier.background(AGreen.copy(alpha = .10f), RoundedCornerShape(5.dp)).padding(6.dp),
        color = AGreen, fontSize = 9.sp, letterSpacing = .7.sp)
}
@Composable internal fun AchievementRing(stats: AchievementStats, store: AchievementStore, onRecordViewed: suspend (String) -> Unit, modifier: Modifier, detail: Boolean = false) {
    val mode = LocalAchievementMotion.current
    val haptic = LocalHapticFeedback.current
    val p = stats.definition.progress.takeIf { stats.definition.progressEnabled }
    val success = stats.newRecord || p?.complete == true
    val eventKey = if (stats.newRecord) stats.recordKey else "${stats.definition.id}:progress:${p?.completedAt}"
    val entry = remember(stats.definition.id) { Animatable(if (mode == AchievementMotion.OFF) 0f else 1f) }
    val pulse = remember(eventKey) { Animatable(0f) }
    val sparkle = remember(stats.definition.id, detail) { Animatable(1f) }
    LaunchedEffect(detail, mode) {
        sparkle.snapTo(1f)
        if (detail && mode == AchievementMotion.FULL) { sparkle.snapTo(0f); sparkle.animateTo(1f, tween(2400, easing = androidx.compose.animation.core.LinearEasing)) }
    }
    LaunchedEffect(mode) {
        if (detail && mode == AchievementMotion.FULL) entry.animateTo(0f, tween(1000)) else entry.snapTo(0f)
    }
    LaunchedEffect(success, eventKey, mode) {
        if (success && eventKey !in store.seenRecords) {
            onRecordViewed(eventKey)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (mode == AchievementMotion.FULL) { pulse.snapTo(1f); pulse.animateTo(0f, tween(1000)) }
        }
    }
    val accent = animateColorAsState(if (success) AGreen else ACyan, tween(mode.duration), label = "Ring state")
    val target = if(detail && !stats.definition.progressEnabled) 1f else achievementRingFraction(stats)
    val fraction = remember(stats.definition.id) { Animatable(target) }
    LaunchedEffect(target, mode) { fraction.animateTo(target, tween(if (detail && mode == AchievementMotion.FULL) 650 else mode.duration)) }
    // Keep the previously approved detail hero/glow; dashboard cards use the matte assembly.
    if(!detail) {
        OmssCircularGauge(fraction.value,modifier.semantics {
            contentDescription = if(stats.definition.progressEnabled) "Progress ${p?.percent?.toInt()?.let { "$it%" } ?: "N/A"}" else if(stats.newRecord) "New record ring" else "Current streak ring"
        },color=accent.value,radiusFraction=.36f) {
            CardIconView(stats.definition,store,Modifier.size(22.dp),ACyan)
        }
        return
    }
    Box(modifier.semantics { contentDescription = if (stats.definition.progressEnabled) "Progress ${p?.percent?.toInt()?.let { "$it%" } ?: "N/A"}" else if (stats.newRecord) "New record ring" else "Current streak ring" }) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * if (detail) .33f else .36f
            val stroke = size.minDimension * if(!detail && target==0f) .04f else .085f
            val tint = accent.value
            val glow = .32f + (if (detail) .16f*entry.value + .16f*pulse.value else 0f)
            val outer = (r + stroke*1.6f).coerceAtMost(size.minDimension*.5f)
            if(detail && fraction.value>0) drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(
                colorStops = arrayOf(0f to Color.Transparent, .56f to tint.copy(alpha = .01f), .73f to tint.copy(alpha = .15f), .83f to tint.copy(alpha = .28f), 1f to Color.Transparent),
                center = center, radius = outer), outer, center)
            // Layered GPU strokes: soft static halo, finite opening/completion bloom, no blur/offscreen bitmap per frame.
            for (layer in 0..(if(detail) 2 else 1)) {
                val spread = when(layer) { 0 -> 2.7f; 1 -> 1.9f; else -> 1.35f }
                val opacity = when(layer) { 0 -> .24f; 1 -> .45f; else -> .75f }
                drawArc(tint.copy(alpha = glow*opacity), -90f, 360f*fraction.value, false,
                    Offset(center.x-r,center.y-r), Size(r*2,r*2), style = Stroke(stroke*spread, cap = StrokeCap.Round))
            }
            drawCircle(DeckColors.track, r, center, style = Stroke(stroke))
            drawArc(tint, -90f, 360f*fraction.value, false, Offset(center.x-r,center.y-r), Size(r*2,r*2),
                style = Stroke(stroke, cap = StrokeCap.Round))
            if(detail && fraction.value>0) drawCircle(tint.copy(alpha = .30f), r + stroke*.72f, center, style = Stroke(.8.dp.toPx()))
            if (detail && mode == AchievementMotion.FULL && sparkle.value < 1f) {
                val phase = sparkle.value
                for (i in 0..8) {
                    val angle = (i*40.0 - 18.0 + phase*12.0)*Math.PI/180.0
                    val orbit = size.minDimension*(if (i%2 == 0) .45f else .47f)
                    val point = center + Offset(kotlin.math.cos(angle).toFloat()*orbit, kotlin.math.sin(angle).toFloat()*orbit)
                    val alpha = (kotlin.math.sin((phase*3.0 + i*.19)*Math.PI).toFloat().coerceAtLeast(0f)*(1f-phase)).coerceIn(0f,1f)
                    val radius = size.minDimension*(if (i%3 == 0) .023f else .012f)
                    drawCircle(tint.copy(alpha = alpha*.12f), radius*2.3f, point)
                    if (i%3 == 0) {
                        val star = Path().apply { moveTo(point.x,point.y-radius); lineTo(point.x+radius*.3f,point.y-radius*.3f); lineTo(point.x+radius,point.y); lineTo(point.x+radius*.3f,point.y+radius*.3f); lineTo(point.x,point.y+radius); lineTo(point.x-radius*.3f,point.y+radius*.3f); lineTo(point.x-radius,point.y); lineTo(point.x-radius*.3f,point.y-radius*.3f); close() }
                        drawPath(star,tint.copy(alpha=alpha))
                    } else drawCircle(tint.copy(alpha=alpha),radius*.45f,point)
                }
            }
        }
        CardIconView(stats.definition, store, Modifier.align(Alignment.Center)
            .size(if (detail) 44.dp else 25.dp), if(!detail && target==0f) AGrey else accent.value)
    }
}

@Composable private fun AchievementDetail(store: AchievementStore, def: AchievementDefinition, now: Long, zone: ZoneId,
    onRecordViewed: suspend (String) -> Unit, back: () -> Unit, close: () -> Unit, suppliedStats: AchievementStats?, readOnly: Boolean, onIndexCommand: (AchievementCommand) -> Unit, error: String) {
    val stats = suppliedStats ?: return
    var selected by rememberSaveable(def.id) { mutableStateOf(stats.completed.lastIndex.takeIf { it >= 0 }) }
    val today = AchievementEngine.date(now, zone)
    val detailData by produceState<AchievementDetailData?>(null, store, def, stats.completed, today, zone) {
        value = withContext(Dispatchers.Default) { achievementDetailData(store, def, stats, today, zone) }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("achievement-detail-list"), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "detail-header") { Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { BundledCardIcon("chevron-left", Modifier.size(25.dp), ACyan) }
            Text("Achievement detail", Modifier.weight(1f), fontSize = 18.sp)
            TextButton(onClick = close) { Text("CLOSE", color = AGrey, fontSize = 10.sp) }
        }
        LayoutPanel("detail-hero",220) { BoxWithConstraints(Modifier.fillMaxWidth()) {
            if(maxWidth<300.dp) Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                AchievementRing(stats,store,onRecordViewed,Modifier.size(140.dp),detail=true)
                AchievementHeroLabels(def,Modifier.fillMaxWidth())
            } else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                AchievementRing(stats,store,onRecordViewed,Modifier.size(140.dp),detail=true)
                AchievementHeroLabels(def,Modifier.weight(1f))
            }
        } }
        LayoutControls(requireNotNull(LocalDashboardLayout.current))
        if (def.progressEnabled) LayoutPanel("detail-progress",180) { ProgressPanel(def) }
        } } // Keep the approved upper section/ring/progress panel unchanged.
        if (error.isNotBlank()) item { Text(error, color = DeckColors.secondary) }
        if (def.id in store.indexes) item(key = "index-detail") { LayoutPanel("index-detail",360) { IndexDetail(store, def, now, zone, readOnly, onIndexCommand) } }
        achievementDetailSections(store, def, stats, today, zone, detailData, selected) { selected = it }
    }
}

@Composable private fun AchievementHeroLabels(def: AchievementDefinition,modifier: Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Label("ACHIEVEMENT");Text(def.title,fontSize=26.sp,fontWeight=FontWeight.Light)
        Text(def.description,color=AGrey,fontSize=12.sp)
    }
}

@Composable internal fun AchievementMetadataLines(segment: AchievementSegment, stats: AchievementStats, store: AchievementStore, zone: ZoneId) {
    Text("Personal record: ${if (segment.personalRecord) "Yes" else "No"}", color = AGrey, fontSize = 12.sp)
    Text("Difference from average: ${AchievementEngine.value(stats.average?.let { segment.value-it },stats.definition.kind == "abstinence",true)}", color = AGrey, fontSize = 12.sp)
    segment.startMs?.let { Text("Start: ${Instant.ofEpochMilli(it).atZone(zone)}", color = AGrey, fontSize = 11.sp) }
    segment.endMs?.let { Text("End: ${Instant.ofEpochMilli(it).atZone(zone)}", color = AGrey, fontSize = 11.sp) }
    if (stats.definition.kind != "abstinence") {
        val days = store.days.values.filter { it.date >= segment.start && it.date <= segment.end }
        if (stats.definition.kind in setOf("daily", "scheduled")) {
            Text("Objectives completed: ${days.sumOf { it.activities[stats.definition.id] ?: 0 }}", color = AGrey, fontSize = 12.sp)
        }
        val changes = store.progressHistory.filter { it.achievementId == stats.definition.id && AchievementEngine.date(it.timestamp, zone) >= segment.start && AchievementEngine.date(it.timestamp, zone) <= segment.end }
        if (stats.definition.progressEnabled && changes.isNotEmpty()) {
            val amount = changes.sumOf { it.delta }
            Text("Progress: ${AchievementEngine.progressValue(amount)} ${stats.definition.progress?.unit ?: "units"}", color = AGrey, fontSize = 12.sp)
            Text("Daily average: ${AchievementEngine.progressValue(amount / segment.value.coerceAtLeast(1.0))}", color = AGrey, fontSize = 12.sp)
        }
        Text("Day totals · ${days.sumOf { it.focusBlocks }} focus blocks · ${humanTime(days.sumOf { it.focusMs })} active focus", color = AGrey, fontSize = 11.sp)
        if (stats.definition.kind == "contract") days.forEach { day ->
            val target = store.targets.find { it.id == day.targetId } ?: AchievementEngine.latest(store,day.date)
            Text("${day.date} · ${day.statuses[stats.definition.id]} · " + target?.objectives.orEmpty().joinToString(" + ") { it.text.ifEmpty { "${it.minimum} ${it.achievementId ?: it.kind}" } }, color = AGrey, fontSize = 11.sp)
        } else store.events.filter { it.achievementId == stats.definition.id && it.date >= segment.start && it.date <= segment.end }
            .forEach { Text("${it.date} · ${it.amount} objective(s) ${it.text}",color=AGrey,fontSize=11.sp) }
    }
}
