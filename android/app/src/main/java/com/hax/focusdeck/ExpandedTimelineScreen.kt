package com.hax.focusdeck

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*
import java.time.format.DateTimeFormatter

/** Coordinates are transient, non-observable and never persisted or written on frames. */
internal class TimelineNavigationState { var origin: Rect?=null;var summaryOrigin: Rect?=null }
internal val LocalTimelineNavigation=staticCompositionLocalOf<TimelineNavigationState?> { null }
internal data class TimelineHalfHour(val start: Long,val end: Long,val activities: List<CalendarActivity>)
internal fun timelineHalfHours(data: CalendarDataset,date: LocalDate,zone: ZoneId): List<TimelineHalfHour> {
    val (start,end)=CalendarAnalytics.bounds(date,zone)
    val activities=data.activities.values.filter { a -> (if(a.start!=null) a.zone else a.schedule?.timezone?.let(ZoneId::of) ?: a.zone)==zone && (a.start ?: a.schedule?.start)?.let { it in start until end }==true }
    return (start until end step 1800000L).map { t -> TimelineHalfHour(t,minOf(t+1800000,end),activities.filter { (it.start ?: it.schedule!!.start) in t until minOf(t+1800000,end) }.sortedWith(compareBy({it.start ?: it.schedule!!.start},{it.id}))) }
}
@Composable internal fun RepositoryTimelineDialog(repo: DeckRepository,dismiss: ()->Unit,openReport: (Report)->Unit) {
    val reports by repo.history.collectAsStateWithLifecycle()
    val stored by repo.display.collectAsStateWithLifecycle()
    val observed by repo.now.collectAsStateWithLifecycle()
    val zone by repo.displayZone.collectAsStateWithLifecycle()
    val data by produceState<CalendarDataset?>(null,reports,stored.state) { value=withContext(Dispatchers.Default) { timelineDataset(reports,stored.state) } }
    val date=rememberSaveable(saver=LocalDateSaver) { Instant.ofEpochMilli(observed.wall).atZone(ZoneId.of(zone)).toLocalDate() }
    data?.let { ExpandedTimelineDialog(it,reports,date,dismiss,openReport) }
}
@Composable internal fun ExpandedTimelineDialog(data: CalendarDataset,reports: List<Report>,date: LocalDate,dismiss: ()->Unit,openReport: (Report)->Unit) {
    Dialog(dismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        DialogNavigationRefresh()
        Surface(Modifier.fillMaxSize(),color=DashboardBackground) { ExpandedTimelineScreen(data,reports,date,dismiss,openReport) }
    }
}
@Composable internal fun ExpandedTimelineScreen(data: CalendarDataset,reports: List<Report>,date: LocalDate,dismiss: ()->Unit,openReport: (Report)->Unit) {
    val mode=LocalAchievementMotion.current
    val navigator=LocalTimelineNavigation.current
    val origin=remember { navigator?.origin }
    val reveal=remember { Animatable(if(mode==AchievementMotion.OFF) 1f else 0f) }
    val scope=rememberCoroutineScope()
    LaunchedEffect(mode) { reveal.animateTo(1f,tween(if(mode==AchievementMotion.FULL) 420 else mode.duration)) }
    fun back() { scope.launch { reveal.animateTo(0f,tween(mode.duration));dismiss() } }
    BackHandler { back() }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(dashboardViewportInsets())) {
        val width=maxWidth;val height=maxHeight
        Column(Modifier.fillMaxSize().padding(horizontal=12.dp).graphicsLayer { alpha=reveal.value;translationY=if(mode==AchievementMotion.FULL) (1-reveal.value)*12.dp.toPx() else 0f }) {
            if(LocalSyntheticPreview.current) Text("SYNTHETIC · READ ONLY · Simulated ${LocalDashboardDate.current}",color=DeckColors.secondary,fontSize=11.sp)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton({back()}) { ActivityGlyph("close",Modifier.size(22.dp),DeckColors.muted) }
                Column(Modifier.weight(1f)) { Text("Daily Timeline",fontSize=24.sp,fontWeight=FontWeight.Bold);Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),fontSize=12.sp,color=DeckColors.muted) }
            }
            val zones=data.days[date]?.zones?.toList().orEmpty().ifEmpty { listOf(ZoneId.systemDefault()) }
            var zoneId by rememberSaveable(date.toString()) { mutableStateOf(zones.first().id) }
            val zone=zones.find { it.id==zoneId } ?: zones.first()
            if(zones.size>1) Row(Modifier.horizontalScroll(rememberScrollState())) { zones.forEach { z -> TextButton({zoneId=z.id}) { Text(z.id,color=if(z==zone) DeckColors.accent else DeckColors.muted,fontSize=11.sp) } } }
            Text("${zone.id} · 30-minute intervals expand to fit evidence",fontSize=10.sp,color=DeckColors.muted)
            val buckets=remember(data,date,zone) { timelineHalfHours(data,date,zone) }
            val visibleIds=buckets.flatMap { it.activities }.map { it.id }.toSet()
            val unplaced=remember(data,date,zone,visibleIds) { data.activities.values.filter { a -> a.zone==zone && a.id !in visibleIds && (a.planDate==date || data.days[date]?.segments?.any { it.activityId==a.id }==true) } }
            val list=rememberLazyListState(initialFirstVisibleItemIndex=buckets.indexOfFirst { it.activities.isNotEmpty() }.coerceAtLeast(0))
            LazyColumn(Modifier.weight(1f).testTag("expanded-timeline"),state=list,contentPadding=PaddingValues(top=16.dp,bottom=32.dp)) {
                items(buckets,key={"${zone.id}:${it.start}"}) { bucket ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().height(32.dp),verticalAlignment=Alignment.CenterVertically) {
                            val dst=(CalendarAnalytics.bounds(date,zone).let { it.second-it.first })!=86400000L
                            Text(if(dst) Instant.ofEpochMilli(bucket.start).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm\nXXX")) else compactClock(bucket.start,zone),Modifier.width(48.dp),fontSize=if(dst) 9.sp else 11.sp,color=DeckColors.muted)
                            Box(Modifier.width(2.dp).fillMaxHeight().background(DeckColors.track))
                            Spacer(Modifier.width(10.dp));HorizontalDivider(color=DeckColors.track.copy(alpha=.4f))
                        }
                        bucket.activities.forEach { a -> key(a.id) {
                            val preceding=reports.find { it.key==a.reportKey }?.let { r -> r.blocks.indexOfFirst { r.key+":"+it.id==a.id }.takeIf { it>0 }?.let { data.activities[r.key+":"+r.blocks[it-1].id] } }
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                Box(Modifier.width(50.dp).fillMaxHeight()) {
                                    Box(Modifier.align(Alignment.CenterEnd).width(2.dp).fillMaxHeight().background(DeckColors.track))
                                    Text(compactClock(a.start ?: a.schedule?.start,zone),fontSize=10.sp,color=DeckColors.muted,modifier=Modifier.padding(end=9.dp,top=8.dp))
                                    Box(Modifier.align(Alignment.TopEnd).offset(x=3.dp,y=9.dp).size(8.dp).background(statusColor(a.status),androidx.compose.foundation.shape.CircleShape))
                                }
                                Column(Modifier.weight(1f).padding(start=10.dp,bottom=10.dp)) {
                                    TimelineActivityCard(a,preceding,reports.find { it.key==a.reportKey },openReport)
                                }
                            }
                        } }
                    }
                }
                item { Row { Text("24:00",Modifier.width(50.dp),fontSize=11.sp,color=DeckColors.muted);HorizontalDivider() } }
                if(unplaced.isNotEmpty()) item { Text("Continued or unlocated activity · original times retained",fontSize=12.sp,color=DeckColors.muted,modifier=Modifier.padding(vertical=12.dp)) }
                items(unplaced,key={"unplaced:${it.id}"}) { a -> TimelineActivityCard(a,null,reports.find { it.key==a.reportKey },openReport) }
                if(buckets.all { it.activities.isEmpty() } && unplaced.isEmpty()) item { Text("No recorded activity or absolute schedule for this day.",color=DeckColors.muted,modifier=Modifier.padding(12.dp)) }
                data.days[date]?.unlocatedMs?.takeIf { it>0 }?.let { ms -> item { Text("${humanTime(ms)} without reliable interval placement",color=DeckColors.muted,fontSize=11.sp) } }
            }
        }
        // Interpolate the actual compact track endpoints into the visible left axis once.
        if(mode==AchievementMotion.FULL && origin!=null && reveal.value<1f) Canvas(Modifier.fillMaxSize()) {
            val p=reveal.value
            fun blend(a: Float,b: Float)=a+(b-a)*p
            repeat(2) { lane ->
                val y=origin.top+(10+13*lane).dp.toPx()
                drawLine(DeckColors.accent.copy(alpha=1-p),Offset(blend(origin.left,62.dp.toPx()),blend(y,96.dp.toPx())),Offset(blend(origin.right,62.dp.toPx()),blend(y,size.height-32.dp.toPx())),(5-3*p).dp.toPx())
            }
        }
    }
}
@Composable internal fun TimelineActivityCard(a: CalendarActivity,previous: CalendarActivity?,report: Report?,openReport: (Report)->Unit) {
    var expanded by rememberSaveable(a.id) { mutableStateOf(false) }
    val mode=LocalAchievementMotion.current
    val navigator=LocalTimelineNavigation.current
    val cardBounds=remember { TimelineNavigationState() }
    val estimate=durationComparison(a.plannedMs,if(a.type=="focus") a.focusMs else a.elapsedMs?.minus(a.pausedMs ?: 0),a.status=="done" && !a.provisional)
    FDCard(Modifier.fillMaxWidth().testTag("timeline-event-${a.id}").activityExpansion(mode).onGloballyPositioned { cardBounds.origin=it.boundsInRoot() },onClick={expanded=!expanded},padding=12.dp) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ActivityGlyph(if(a.type=="break") "coffee" else "book-open",Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(a.project.ifBlank { "Recorded activity" },fontSize=10.sp,color=DeckColors.accent)
                Text(a.title,fontSize=15.sp,fontWeight=FontWeight.Bold)
                Text("${a.type.replaceFirstChar { it.titlecase() }} · ${a.timerMode ?: "N/A"}",fontSize=11.sp,color=DeckColors.muted)
            }
            ActivityGlyph(if(expanded) "chevron-up" else "chevron-down",Modifier.size(18.dp),DeckColors.muted)
        }
        ExecutionStatus(a.status)
        Text("Planned ${compactClock(a.schedule?.start,a.schedule?.timezone?.let(ZoneId::of) ?: a.zone)} → ${compactClock(a.schedule?.end,a.schedule?.timezone?.let(ZoneId::of) ?: a.zone)}\nActual ${compactClock(a.start,a.zone)} → ${compactClock(a.end,a.zone)}",fontSize=11.sp,color=DeckColors.muted)
        Row(Modifier.fillMaxWidth()) { Text("Focus ${humanTime(a.focusMs)}",Modifier.weight(1f),fontSize=12.sp);PunctualityText(a.punctuality?.toDouble()) }
        if(expanded) {
            HorizontalDivider(color=DeckColors.track)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                TimelineMetric("Start",compactClock(a.start,a.zone),"play",Modifier.weight(1f))
                TimelineMetric("End",compactClock(a.end,a.zone),"square",Modifier.weight(1f))
                TimelineMetric("Elapsed",humanTime(a.elapsedMs),"clock",Modifier.weight(1f))
                TimelineMetric("Estimated",humanTime(a.plannedMs),"timer",Modifier.weight(1f))
            }
            Row { Text("Start ",fontSize=11.sp);PunctualityText(a.punctuality?.toDouble());Spacer(Modifier.width(12.dp));Text("End ",fontSize=11.sp);PunctualityText(a.end?.let { end -> a.schedule?.end?.let { (end-it).toDouble() } }) }
            HorizontalDivider(color=DeckColors.track)
            Row(Modifier.fillMaxWidth()) {
                TimelineMetric("Focus",humanTime(a.focusMs),"target",Modifier.weight(1f))
                TimelineMetric("Paused",humanTime(a.pausedMs),"pause",Modifier.weight(1f))
                TimelineMetric("Pauses",a.pauseCount?.toString() ?: "N/A","pause",Modifier.weight(1f))
            }
            HorizontalDivider(color=DeckColors.track)
            Row { Text("Start Delay Breakdown",Modifier.weight(1f),fontSize=12.sp);PunctualityText(a.punctuality?.toDouble()) }
            val attribution=delayAttribution(a,previous)
            if(attribution!=null && a.punctuality!!>0) {
                Row(Modifier.fillMaxWidth().height(26.dp)) {
                    listOf(attribution.inherited to DeckColors.accent,attribution.additional to DeckColors.error).filter { it.first>0 }.forEach { (ms,color) ->
                        Box(Modifier.weight((ms.toDouble()/a.punctuality!!).toFloat()).fillMaxHeight().background(color).semanticsDelay(ms)) {
                            if(ms.toDouble()/a.punctuality!!>.2) Text(punctualityLabel(ms.toDouble()),Modifier.align(Alignment.Center),fontSize=10.sp,color=androidx.compose.ui.graphics.Color.Black)
                        }
                    }
                }
            } else Text(if(a.punctuality==null) "No absolute start comparison" else if(a.punctuality!!<0) "Started early · no late-delay attribution" else "Attribution N/A · insufficient adjacent-work evidence",fontSize=11.sp,color=DeckColors.muted)
            HorizontalDivider(color=DeckColors.track)
            DurationAccuracy(estimate,a.provisional)
            Text("${executionLabel(a.status)} · Countdown overtime ${humanTime(a.overtimeMs)}",color=DeckColors.muted,fontSize=11.sp)
            Text("Quality criteria: ${report?.blocks?.find { report.key+":"+it.id==a.id }?.performance?.let { "${it.qualityCriteria.size} criteria · ${it.quality}" } ?: "N/A"}",color=DeckColors.muted,fontSize=11.sp)
            if(report!=null) TextButton({navigator?.summaryOrigin=cardBounds.origin;openReport(report)},Modifier.testTag("timeline-summary-${a.id}")) { Text("Open Session Summary  ›",fontSize=12.sp) }
        }
    }
}
private fun Modifier.semanticsDelay(ms: Long): Modifier = this.then(Modifier.semantics { contentDescription="${punctualityLabel(ms.toDouble())}; delay segment" })
@Composable private fun TimelineMetric(label: String,value: String,icon: String,modifier: Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { ActivityGlyph(icon,Modifier.size(15.dp));Spacer(Modifier.width(4.dp));Text(label,fontSize=9.sp,color=DeckColors.muted) }
        Text(value,fontSize=14.sp,style=OmssTypography.numbers)
    }
}
