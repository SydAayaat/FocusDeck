package com.hax.focusdeck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

internal data class AnalyticsPopup(val title: String,val text: String,val anchor: Rect? = null)
internal data class AnalyticsScreenActions(val edit: ()->Unit,val expanded: ()->Unit,val history: ()->Unit)
internal val LocalAnalyticsActions=staticCompositionLocalOf<AnalyticsScreenActions?> { null }
/** The approved compact screen; existing expanded timelines/reports remain available in the menu. */
@Composable internal fun AnalyticsDashboard(data: CalendarDataset,reports: List<Report>,period: HistoryPeriod,anchor: LocalDate,
    setPeriod: (HistoryPeriod)->Unit,setAnchor: (LocalDate)->Unit,modifier: Modifier=Modifier,
    openReport: (Report)->Unit,copy: (Report)->Unit,delete: ((Report)->Unit)?,header: @Composable ()->Unit,archiveActions: @Composable ()->Unit,
    store: AchievementStore=AchievementStore(),initialExpanded: Boolean=false,copyPerformance: ((HistoryPeriod,LocalDate)->Unit)?=null) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    var archive by remember { mutableStateOf(false) }
    val bounds=remember { mutableMapOf<String,Rect>() }
    val latestReports by rememberUpdatedState(reports)
    var popup by remember(period,anchor) { mutableStateOf<AnalyticsPopup?>(null) }
    val layout=rememberDashboardLayout(if(period==HistoryPeriod.DAY) "analytics-day" else if(period==HistoryPeriod.WEEK) "analytics-week" else "analytics-aggregate")
    var drillStack by remember { mutableStateOf(emptyList<Pair<HistoryPeriod,LocalDate>>()) }
    var expected by remember { mutableStateOf<Pair<HistoryPeriod,LocalDate>?>(null) }
    LaunchedEffect(period,anchor) { if(expected!=(period to anchor)) drillStack=emptyList();expected=null }
    fun drill(next: HistoryPeriod,date: LocalDate) { drillStack=drillStack+(period to anchor);expected=next to date;setPeriod(next);setAnchor(date) }
    fun back() { val target=drillStack.last();drillStack=drillStack.dropLast(1);expected=target;setPeriod(target.first);setAnchor(target.second) }
    BackHandler(expanded || drillStack.isNotEmpty() || layout.editing) { if(layout.editing) layout.editing=false else if(expanded) expanded=false else back() }
    if(expanded) {
        LegacyAnalyticsDashboard(data,reports,period,anchor,setPeriod,setAnchor,modifier,openReport,copy,delete,
            header={header();if(expanded) TextButton({expanded=false}) { Text("Back to dashboard") }},archiveActions=archiveActions)
        return
    }
    var evidenceMinute by remember { mutableLongStateOf(System.currentTimeMillis()/60000) }
    LaunchedEffect(Unit) { while(true) { kotlinx.coroutines.delay(60000);evidenceMinute=System.currentTimeMillis()/60000 } }
    val evidence by produceState<DashboardAnalyticsEvidence?>(null,data,reports,period,anchor,store,evidenceMinute) {
        value=withContext(Dispatchers.Default) { dashboardAnalytics(data,reports,period,anchor,store) }
    }
    var sortBy by remember { mutableStateOf("Time") }
    var ascending by remember { mutableStateOf(false) }
    var metric by remember { mutableStateOf(CalendarMetric.EXECUTION_SCORE) }
    var calendarMonth by remember(anchor) { mutableStateOf(anchor.withDayOfMonth(1)) }
    var selectedActivity by remember(period,anchor) { mutableStateOf<String?>(null) }
    val e=evidence
    if(e==null) { Box(modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() };return }
    fun inspect(title: String,text: String,source: String=title) { popup=AnalyticsPopup(title,text,bounds[source] ?: bounds["Plan Execution Score"]) }
    fun card(id: String,title: String,height: Int=150,span: Int=12,body: @Composable ColumnScope.()->Unit)=DashboardCard(id,span,height) {
        FDCard(Modifier.fillMaxWidth().testTag("analytics-$id").onGloballyPositioned { bounds[title]=it.boundsInWindow() }) { FDTitle(title);body() }
    }
    val cards=mutableListOf<DashboardCard>()
    cards+=DashboardCard("analytics-summary",12,80) { AnalyticsKpis(e,period) }
    cards+=card("plan-score","Plan Execution Score",155) {
        val x=e.performance.execution
        Text((if(x.provisional) "Provisional · " else "")+(if(x.partial) "Partial component coverage" else "Captured planned work"),fontSize=11.sp,color=DeckColors.muted)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            ScoreRing(e.score,Modifier.size(92.dp).clickable { inspect("Plan Execution Score",executionDetails(e.performance)) })
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                AnalyticsMeasure("Plan Completion",performancePercent(x.completion),x.completion?.toFloat(),DeckColors.accent) { inspect("Plan Completion",executionDetails(e.performance)) }
                AnalyticsMeasure("Start Punctuality",performancePercent(x.punctuality),x.punctuality?.toFloat(),DeckColors.accent) { inspect("Start Punctuality",executionDetails(e.performance)) }
                AnalyticsMeasure("Time Efficiency",performancePercent(x.efficiency),x.efficiency?.toFloat(),DeckColors.secondary) { inspect("Time Efficiency",executionDetails(e.performance)) }
            }
        }
    }
    if(period!=HistoryPeriod.DAY) cards+=card("focus",if(period==HistoryPeriod.WEEK) "Daily Focus Trend" else "Productivity Calendar",if(period==HistoryPeriod.WEEK) 190 else 350) {
        val buckets=analyticsBuckets(period,anchor,data)
        val scores=e.performance.daily.mapKeys { LocalDate.parse(it.key) }
        NativeFocusBars(buckets.map { (if(period==HistoryPeriod.MONTH) it.start.dayOfMonth.toString() else it.label) to it.value },
            key=if(period==HistoryPeriod.WEEK) "weekly:$anchor" else "focus:$period:$anchor",
            overtime=buckets.map { b->data.days.values.filter { it.date in b.start..b.end }.sumOf { it.overtimeMs }.toDouble() },
            pauses=buckets.map { b->data.days.values.filter { it.date in b.start..b.end }.sumOf { it.pausedMs }.toDouble() },
            onInspect={label,_-> val b=buckets.firstOrNull { (if(period==HistoryPeriod.MONTH) it.start.dayOfMonth.toString() else it.label)==label };if(b!=null) inspect("Daily Focus Trend",focusBucketDetails(data,b.start,b.end),if(period==HistoryPeriod.WEEK) "Daily Focus Trend" else "Productivity Calendar") },
            openPeriod={i->buckets[i].next?.let { drill(it,buckets[i].start) }})
        Text("Gold active · coral included overtime · cyan non-focus pauses",fontSize=10.sp,color=DeckColors.muted)
        if(period!=HistoryPeriod.WEEK) {
            Row(verticalAlignment=Alignment.CenterVertically) { TextButton({calendarMonth=calendarMonth.minusMonths(1)}) { Text("‹") };Text(calendarMonth.format(DateTimeFormatter.ofPattern("MMM yyyy")),Modifier.weight(1f),fontSize=11.sp);TextButton({calendarMonth=calendarMonth.plusMonths(1)}) { Text("›") } }
            NativeMonthCalendar(data,calendarMonth,{drill(HistoryPeriod.DAY,it)},{drill(HistoryPeriod.WEEK,it)},scores=e.calendarScores,metric=metric,setMetric={metric=it})
        }
    }
    fun hourlyCard()=card("hourly","Focus by Hour",125) {
        val located=e.days.filter { it.hourCoverage };val complete=e.days.filter { it.hourComplete }
        val values=(0..23).map { h -> "%02d".format(java.util.Locale.ROOT,h) to located.takeIf { it.isNotEmpty() }?.sumOf { it.hours[h] }?.toDouble() }
        NativeFocusBars(values,minutes=period==HistoryPeriod.DAY,key="hourly:$period:$anchor",comparison=(0..23).map { h->complete.takeIf { it.isNotEmpty() }?.map { it.hours[h].toDouble() }?.average() },
            onInspect={label,_->inspect("$label:00",hourDetails(data,e.performance,label.toInt()),"Focus by Hour")})
        Text("Cyan average: ${complete.size} complete located days; partial/live excluded",fontSize=10.sp,color=DeckColors.muted)
        val total=e.days.sumOf { it.hours.sum() };if(total!=e.focus) Text("Located ${dashboardDuration(total)} · unlocated ${dashboardDuration(e.focus-total)}",fontSize=10.sp,color=DeckColors.muted)
    }
    if(period==HistoryPeriod.DAY) cards+=hourlyCard()
    cards+=card("execution","Execution",if(period==HistoryPeriod.DAY) 135 else 280,6) {
        AnalyticsMeasure("Blocks",e.plannedCount?.let { "${e.done}/$it" } ?: "${e.done} DONE",e.completion,DeckColors.accent) { inspect("Blocks", "${e.done} DONE · plan count ${e.plannedCount ?: "N/A"}") }
        AnalyticsMeasure("Punctual",e.punctualFraction?.let { "${(it*100).roundToInt()}%" } ?: "N/A",e.punctualFraction,DeckColors.accent) { inspect("Punctual", "${e.punctual.count { it<=0 }} on time/early of ${e.punctual.size} explicit comparisons") }
        AnalyticsMeasure("Estimation",performancePercent(e.performance.execution.efficiency),e.performance.execution.efficiency?.toFloat(),DeckColors.secondary) { inspect("Estimation",executionDetails(e.performance)) }
        if(period!=HistoryPeriod.DAY) {
            val buckets=analyticsBuckets(period,anchor,data)
            Text("Start Punctuality Trend",fontSize=11.sp,color=DeckColors.muted)
            AnalyticsChart(buckets.map { it.label to it.punctuality },signed=true,key="start:$period:$anchor",onBarSelect={i->val b=buckets[i];inspect(b.label,startTrendDetails(data,b.start,b.end),"Execution")})
        }
    }
    cards+=card("distribution","Time Split",135,6) { NativeTimeSplit(e) { title,text -> inspect(title,text) } }
    cards+=card("top","Top Projects & Sessions",150) {
        if(e.projects.isEmpty()) Text("No recorded projects or sessions",color=DeckColors.muted,fontSize=12.sp)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Project / Session",Modifier.weight(1f),fontSize=11.sp,color=DeckColors.muted)
            listOf("Time","Total").forEach { title->TextButton({ascending=if(sortBy==title) !ascending else false;sortBy=title},contentPadding=PaddingValues(3.dp)) { Text(title+if(sortBy==title) if(ascending) " ↑" else " ↓" else " ↕",fontSize=10.sp) } }
        }
        val projects=e.projects.sortedWith(compareBy<DashboardProject> { if(ascending) it.focusMs else -it.focusMs }.thenBy { it.title })
        projects.forEach { project ->
            Row(Modifier.fillMaxWidth().heightIn(min=34.dp).clickable {
                val related=data.activities.values.filter { it.reportKey in project.reportKeys };selectedActivity=related.firstOrNull()?.id
                inspect(project.title,(related.joinToString("\n\n") { sessionDetailText(it) }.ifBlank { "No block evidence" }+ (project.progress?.let { "\n\nAchievement progress ${AchievementEngine.progressValue(it.total)} / ${AchievementEngine.progressValue(it.target)} ${it.unit} · earlier ${AchievementEngine.progressValue(it.before)} · selected period ${AchievementEngine.progressValue(it.within)}" } ?: "")),"Top Projects & Sessions")
            },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(project.title,Modifier.weight(1f),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(dashboardDuration(project.focusMs),fontSize=10.sp,color=DeckColors.muted)
                Text(if(e.focus>0) "${(project.focusMs*100.0/e.focus).roundToInt()}%" else "N/A",Modifier.width(38.dp),fontSize=10.sp,color=DeckColors.secondary)
            }
        }
    }
    if(period==HistoryPeriod.DAY) cards+=card("estimation","Time Estimation Accuracy",270) {
        TimeEstimationCard(e)
        var details by remember { mutableStateOf(false) }
        TextButton({details=!details}) { Text(if(details) "Hide Session Detail" else "Daily Timeline & Session Detail",fontSize=11.sp) }
        if(details) {
            DailyTimeline(data,anchor,selectedId=selectedActivity,select={selectedActivity=it})
            data.activities[selectedActivity]?.let { ActivityMetadata(it,true) }
            e.activities.filter { it.schedule==null }.forEach { a->TextButton({selectedActivity=a.id}) { Text(a.title,fontSize=11.sp) } }
        }
    }
    if(period!=HistoryPeriod.DAY) cards+=hourlyCard()
    cards+=card("telemetry","GPT Performance Report",if(period==HistoryPeriod.DAY) 170 else 230) {
        Text("Deterministic factual summary",fontSize=10.sp,color=DeckColors.muted)
        Text("Summary",fontSize=11.sp,fontWeight=FontWeight.Bold)
        TelemetryInsights(e,period)
        Text("Notable trends",fontSize=11.sp,fontWeight=FontWeight.Bold)
        TextButton({inspect("Historical comparison",comparisonDetails(e.performance),"GPT Performance Report")}) { Text(if(period==HistoryPeriod.ALL) "Lifetime trend" else e.performance.comparison?.focus_ms?.percentage_change?.let { "%+.1f%% vs previous".format(java.util.Locale.ROOT,it) } ?: "Comparison details · percentage N/A",fontSize=11.sp) }
        TextButton({inspect("Estimation & consistency",calibrationDetails(e.performance),"GPT Performance Report")}) { Text("Planning calibration & consistency",fontSize=11.sp) }
        Text("Areas to improve · evidence to review: ${dashboardDuration(e.performance.unlocated_ms)} unlocated; ${e.performance.execution.missing_duration_blocks} blocks with unknown planned duration",fontSize=11.sp,color=DeckColors.muted)
        if(copyPerformance!=null) TextButton({copyPerformance(period,anchor)}) { Text("Copy Report",fontSize=11.sp) }
    }
    CompositionLocalProvider(LocalDashboardLayout provides layout,LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontSize=14.sp), LocalAnalyticsActions provides AnalyticsScreenActions({layout.editing=true},{expanded=true},{archive=true})) {
        Box(modifier) {
            DashboardGrid(cards,layout,Modifier.fillMaxSize(),scrollTag="analytics-grid",header={
                header()
                if(drillStack.isNotEmpty()) TextButton({back()}) { Text("‹ Back to ${drillStack.last().first.label}",fontSize=11.sp) }
                if(layout.editing) LayoutControls(layout)
            })
            popup?.let { item -> FloatingAnalyticsPopup(item) { popup=null } }
        }
    }
    if(archive) Dialog(onDismissRequest={archive=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        DialogNavigationRefresh()
        FDCard(Modifier.fillMaxWidth().heightIn(max=650.dp).testTag("analytics-history-dialog")) {
            Row { Text("History & Export",Modifier.weight(1f),fontWeight=FontWeight.Bold);TextButton({archive=false}) { Text("Close") } }
            LazyColumn(Modifier.weight(1f,fill=false),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(e.reports,key={it.key}) { report ->
                    Text(report.title,fontSize=14.sp,fontWeight=FontWeight.Bold)
                    Text("${dashboardDuration(report.focusedMs)} · ${report.statusLabel}",fontSize=12.sp,color=DeckColors.muted)
                    Row { TextButton({latestReports.find { it.key==report.key }?.let(openReport)}){Text("Report")};TextButton({latestReports.find { it.key==report.key }?.let(copy)}){Text("Copy")};if(delete!=null) TextButton({latestReports.find { it.key==report.key }?.let(delete)}){Text("Delete",color=DeckColors.error)} }
                }
                item(key="archive-actions") { archiveActions() }
            }
        }
    }
}

@Composable private fun AnalyticsKpis(e: DashboardAnalyticsEvidence,period: HistoryPeriod) {
    val list=if(period==HistoryPeriod.DAY) listOf("FOCUS" to dashboardDuration(e.focus.takeIf { e.days.isNotEmpty() }),"SESSIONS" to e.reports.size.toString(),"BLOCKS DONE" to e.done.toString(),"BREAK TIME" to dashboardDuration(e.breaks.takeIf { e.days.isNotEmpty() }))
        else listOf("FOCUS" to dashboardDuration(e.focus.takeIf { e.days.isNotEmpty() }),"SESSIONS" to e.reports.size.toString(),"DONE RATE" to (e.completion?.let { "${(it*100).roundToInt()}%" } ?: "N/A"),"VARIANCE" to punctualityLabel(e.punctual.takeIf { it.isNotEmpty() }?.average()))
    val large=LocalDensity.current.fontScale>1.25f || LocalConfiguration.current.screenWidthDp<360
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { list.chunked(if(large) 2 else 4).forEach { group ->
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { group.forEach { (label,value)->
            FDCard(Modifier.weight(1f),padding=10.dp) { Text(label,fontSize=10.sp,color=DeckColors.muted,maxLines=2);Text(value,fontSize=17.sp,fontWeight=FontWeight.Bold,maxLines=2) }
        } }
    } }
}
@Composable internal fun ScoreRing(score: Double?,modifier: Modifier) {
    Box(modifier,contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke=size.minDimension*.10f;val r=(size.minDimension-stroke)/2
            drawCircle(DeckColors.track,r,style=Stroke(stroke))
            if(score!=null && score>0) drawArc(DeckColors.accent,-90f,(score.toFloat()/100).coerceIn(0f,1f)*360,false,Offset(center.x-r,center.y-r),Size(r*2,r*2),style=Stroke(stroke,cap=StrokeCap.Butt))
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(score?.let { "%.0f".format(java.util.Locale.ROOT,it) } ?: "N/A",fontSize=26.sp,fontWeight=FontWeight.Bold);Text("/ 100",fontSize=11.sp,color=DeckColors.muted) }
    }
}
@Composable private fun AnalyticsMeasure(label: String,value: String,fraction: Float?,color: Color,action: ()->Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick=action).semantics { contentDescription="$label $value" },verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) { Text(label,Modifier.weight(1f),fontSize=11.sp,maxLines=2);Text(value,fontSize=11.sp,color=color) }
        FDTrack(fraction,color=color)
    }
}
@Composable internal fun SegmentedCompletionTrack(p: CompletionContribution?) {
    if(p==null) { Text("N/A",fontSize=10.sp,color=DeckColors.muted);return }
    Box(Modifier.fillMaxWidth().height(6.dp).background(DeckColors.track,RoundedCornerShape(4.dp)).semantics { contentDescription=p?.let { "Earlier ${(it.earlierFraction*100).roundToInt()} percent, selected period ${(it.periodFraction*100).roundToInt()} percent" } ?: "Completion unavailable" }) {
        Row(Modifier.fillMaxSize()) {
            if(p.earlierFraction>0) Box(Modifier.fillMaxWidth(p.earlierFraction).fillMaxHeight().background(DeckColors.accent))
            if(p.periodFraction>0) Box(Modifier.fillMaxWidth((p.periodFraction/(1-p.earlierFraction).coerceAtLeast(.001f)).coerceIn(0f,1f)).fillMaxHeight().background(DeckColors.secondary))
        }
    }
}
@Composable internal fun FloatingAnalyticsPopup(item: AnalyticsPopup,dismiss: ()->Unit) {
    DashboardPopup(dismiss,explicitAnchor=item.anchor) {
        FDCard(Modifier.widthIn(max=290.dp).heightIn(max=420.dp).verticalScroll(rememberScrollState()).testTag("analytics-popup")) { Text(item.title,fontSize=14.sp,fontWeight=FontWeight.Bold);Text(item.text,fontSize=12.sp,color=DeckColors.muted) }
    }
}
