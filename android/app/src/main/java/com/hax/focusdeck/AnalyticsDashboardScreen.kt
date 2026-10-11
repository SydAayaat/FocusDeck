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
    fun card(id: String,title: String,height: Int=150,span: Int=12,trailing: String?=null,badge: String?=null,body: @Composable ColumnScope.()->Unit)=DashboardCard(id,span,height) {
        FDCard(Modifier.fillMaxWidth().testTag("analytics-$id").onGloballyPositioned { bounds[title]=it.boundsInWindow() }) { FDTitle(title,trailing,badge=badge);body() }
    }
    val cards=mutableListOf<DashboardCard>()
    cards+=DashboardCard("analytics-summary",12,80) { AnalyticsKpis(e,period) }
    cards+=card("plan-score","Plan Execution Score",155,badge=if(e.score==null) "Score N/A" else if(e.performance.execution.provisional) "Provisional" else "Recorded") {
        val x=e.performance.execution
        Text((if(x.provisional) "Provisional · " else "")+(if(x.partial) "Partial component coverage" else "Captured planned work"),fontSize=11.sp,color=DeckColors.muted)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            ScoreRing(e.score,Modifier.size(92.dp).clickable { inspect("Plan Execution Score",executionDetails(e.performance)) })
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                AnalyticsMeasure("Plan Completion",performancePercent(x.completion),x.completion?.toFloat(),DeckColors.accent) { inspect("Plan Completion",executionDetails(e.performance)) }
                AnalyticsMeasure("Start Punctuality",performancePercent(x.punctuality),x.punctuality?.toFloat(),DeckColors.accent) { inspect("Start Punctuality",executionDetails(e.performance)) }
                AnalyticsMeasure("Time Efficiency",performancePercent(x.efficiency),x.efficiency?.toFloat(),OmssColors.analytical) { inspect("Time Efficiency",executionDetails(e.performance)) }
            }
        }
    }
    if(period!=HistoryPeriod.DAY) {
        val title=when(period) { HistoryPeriod.WEEK->"Weekly Focus Distribution";HistoryPeriod.MONTH->"Productivity Calendar";HistoryPeriod.YEAR->"Productivity by Month";else->"Productivity by Year" }
        cards+=card("focus",title,190,trailing=if(period==HistoryPeriod.MONTH) null else "Total  ${dashboardDuration(e.focus.takeIf { e.days.isNotEmpty() })}") {
            if(period==HistoryPeriod.MONTH) {
                NativeMonthCalendar(data,calendarMonth,{drill(HistoryPeriod.DAY,it)},{drill(HistoryPeriod.WEEK,it)},scores=e.calendarScores,metric=metric,setMetric={metric=it},shiftMonth={calendarMonth=calendarMonth.plusMonths(it)})
            } else {
                val buckets=analyticsBuckets(period,anchor,data)
                val labels=buckets.map { b->if(period==HistoryPeriod.WEEK) b.start.format(DateTimeFormatter.ofPattern("EEE")) else b.label.lowercase(java.util.Locale.ROOT).replaceFirstChar { it.uppercase() } }
                NativeFocusBars(buckets.mapIndexed { i,b->labels[i] to b.value },
                    key=if(period==HistoryPeriod.WEEK) "weekly:$anchor" else "focus:$period:$anchor",
                    benchmark=if(period==HistoryPeriod.WEEK) dailyFocusReference(e.days) else null,
                    overtime=buckets.map { b->data.days.values.filter { it.date in b.start..b.end }.sumOf { it.overtimeMs }.toDouble() },
                    pauses=buckets.map { b->data.days.values.filter { it.date in b.start..b.end }.sumOf { it.pausedMs }.toDouble() },
                    onInspect={label,_->val i=labels.indexOf(label);if(i>=0) inspect(title,focusBucketDetails(data,buckets[i].start,buckets[i].end),title)},
                    openPeriod={i->buckets[i].next?.let { drill(it,buckets[i].start) }})
            }
        }
    }
    fun hourlyCard()=card("hourly","Focus by Hour",125,trailing="Total  ${dashboardDuration(e.focus.takeIf { e.days.isNotEmpty() })}") {
        val located=e.days.filter { it.hourCoverage };val complete=e.days.filter { it.hourComplete }
        val values=(0..23).map { h -> "%02d".format(java.util.Locale.ROOT,h) to located.takeIf { it.isNotEmpty() }?.sumOf { it.hours[h] }?.toDouble() }
        NativeFocusBars(values,minutes=period==HistoryPeriod.DAY,key="hourly:$period:$anchor",benchmark=hourlyFocusReference(e.days),comparison=(0..23).map { h->complete.takeIf { it.isNotEmpty() }?.map { it.hours[h].toDouble() }?.average() },
            onInspect={label,_->inspect("$label:00",hourDetails(data,e.performance,label.toInt()),"Focus by Hour")})
        Text("Reference: ${complete.size} complete located days; partial/live excluded",fontSize=10.sp,color=DeckColors.muted)
        val total=e.days.sumOf { it.hours.sum() };if(total!=e.focus) Text("Located ${dashboardDuration(total)} · unlocated ${dashboardDuration(e.focus-total)}",fontSize=10.sp,color=DeckColors.muted)
    }
    if(period==HistoryPeriod.DAY) cards+=hourlyCard()
    cards+=card("execution","Execution",135,6) {
        AnalyticsMeasure("Blocks",e.plannedCount?.let { "${e.done}/$it" } ?: "${e.done} DONE",e.completion,DeckColors.accent) { inspect("Blocks", "${e.done} DONE · plan count ${e.plannedCount ?: "N/A"}") }
        AnalyticsMeasure("Punctual",e.punctualFraction?.let { "${(it*100).roundToInt()}%" } ?: "N/A",e.punctualFraction,DeckColors.accent) { inspect("Punctual", "${e.punctual.count { it<=0 }} on time/early of ${e.punctual.size} explicit comparisons") }
        AnalyticsMeasure("Estimation",performancePercent(e.performance.execution.efficiency),e.performance.execution.efficiency?.toFloat(),OmssColors.analytical) { inspect("Estimation",executionDetails(e.performance)) }

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
                Box(Modifier.size(8.dp).background(DeckColors.accent, androidx.compose.foundation.shape.CircleShape))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(project.title,Modifier.weight(1f),fontSize=11.sp)
                        Text(dashboardDuration(project.focusMs),fontSize=10.sp,color=DeckColors.muted)
                        Text(if(e.focus>0) "${(project.focusMs*100.0/e.focus).roundToInt()}%" else "N/A",fontSize=10.sp,color=DeckColors.muted)
                    }
                    if(project.progress!=null) {
                        val p=project.progress
                        Text("Progress ${AchievementEngine.progressValue(p.total)} / ${AchievementEngine.progressValue(p.target)} ${p.unit}",fontSize=9.sp,color=DeckColors.muted)
                        SegmentedCompletionTrack(p)
                    }
                    else FDTrack(if(e.focus>0) project.focusMs.toFloat()/e.focus else null)
                }
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
    cards+=card("telemetry","Executive Telemetry Insights",230,badge=if(e.performance.execution.provisional) "Provisional" else if(e.performance.execution.partial) "Partial" else if(e.days.isEmpty()) "Evidence N/A" else "Recorded") {
        TelemetryInsights(e,period)
        var details by remember { mutableStateOf(false) }
        TextButton({details=!details},contentPadding=PaddingValues(0.dp)) { Text(if(details) "Hide report details" else "GPT Performance Report  ›",fontSize=11.sp) }
        if(details) {
            Text("Deterministic factual summary · Notable trends",fontSize=10.sp,color=DeckColors.muted)
            TextButton({inspect("Historical comparison",comparisonDetails(e.performance),"Executive Telemetry Insights")}) { Text(if(period==HistoryPeriod.ALL) "Lifetime trend" else e.performance.comparison?.focus_ms?.percentage_change?.let { "%+.1f%% vs previous".format(java.util.Locale.ROOT,it) } ?: "Comparison details · percentage N/A",fontSize=11.sp) }
            TextButton({inspect("Estimation & consistency",calibrationDetails(e.performance),"Executive Telemetry Insights")}) { Text("Planning calibration & consistency",fontSize=11.sp) }
            Text("Evidence to review: ${dashboardDuration(e.performance.unlocated_ms)} unlocated; ${e.performance.execution.missing_duration_blocks} blocks with unknown planned duration",fontSize=11.sp,color=DeckColors.muted)
        }
        if(copyPerformance!=null) TextButton({copyPerformance(period,anchor)},contentPadding=PaddingValues(0.dp)) { Text("Copy Report",fontSize=11.sp) }
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
    val list=listOf("FOCUS" to dashboardDuration(e.focus.takeIf { e.days.isNotEmpty() }),"SESSIONS" to e.reports.size.toString(),"BLOCKS DONE" to e.done.toString(),"BREAK TIME" to dashboardDuration(e.breaks.takeIf { e.days.isNotEmpty() }))
    val icons=mapOf("FOCUS" to "target","SESSIONS" to "trophy","BLOCKS DONE" to "cpu","BREAK TIME" to "zap")
    val large=LocalDensity.current.fontScale>1.25f || LocalConfiguration.current.screenWidthDp<360
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { list.chunked(if(large) 2 else 4).forEach { group ->
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { group.forEach { (label,value)->
            FDCard(Modifier.weight(1f),padding=8.dp) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    BundledCardIcon(icons.getValue(label),Modifier.size(18.dp),DeckColors.accent)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(label,fontSize=9.sp,color=DeckColors.muted,maxLines=2)
                        Text(value,fontSize=15.sp,style=OmssTypography.metric,maxLines=2)
                    }
                }
            }
        } }
    } }
}
@Composable internal fun ScoreRing(score: Double?,modifier: Modifier) {
    OmssCircularGauge(score?.toFloat()?.div(100),modifier,rounded=false) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(score?.let { "%.0f".format(java.util.Locale.ROOT,it) } ?: "N/A",fontSize=26.sp,style=OmssTypography.metric);Text("/ 100",fontSize=11.sp,color=DeckColors.muted) }
    }
}

@Composable private fun AnalyticsMeasure(label: String,value: String,fraction: Float?,color: Color,action: ()->Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick=action).semantics { contentDescription="$label $value" },verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) { BundledCardIcon(when(label) { "Start Punctuality","Punctual"->"clock";"Time Efficiency","Estimation"->"zap";else->"target" },Modifier.size(16.dp),DeckColors.accent);Text(label,Modifier.weight(1f),fontSize=11.sp,maxLines=2);Text(value,fontSize=11.sp,color=color) }
        FDTrack(fraction,color=color)
    }
}
@Composable internal fun SegmentedCompletionTrack(p: CompletionContribution?) {
    if(p==null) { Text("N/A",fontSize=10.sp,color=DeckColors.muted);return }
    Box(Modifier.fillMaxWidth().height(6.dp).omssDirectionalShadow(RoundedCornerShape(4.dp),OmssElevations.micro).background(DeckColors.track,RoundedCornerShape(4.dp)).semantics { contentDescription=p?.let { "Earlier ${(it.earlierFraction*100).roundToInt()} percent, selected period ${(it.periodFraction*100).roundToInt()} percent" } ?: "Completion unavailable" }) {
        Row(Modifier.fillMaxSize()) {
            if(p.earlierFraction>0) Box(Modifier.fillMaxWidth(p.earlierFraction).fillMaxHeight().omssDirectionalShadow(RoundedCornerShape(4.dp),OmssElevations.micro).background(DeckColors.accent,RoundedCornerShape(4.dp)))
            if(p.periodFraction>0) Box(Modifier.fillMaxWidth((p.periodFraction/(1-p.earlierFraction).coerceAtLeast(.001f)).coerceIn(0f,1f)).fillMaxHeight().omssDirectionalShadow(RoundedCornerShape(4.dp),OmssElevations.micro).background(OmssColors.analytical,RoundedCornerShape(4.dp)))
        }
    }
}
@Composable internal fun FloatingAnalyticsPopup(item: AnalyticsPopup,dismiss: ()->Unit) {
    DashboardPopup(dismiss,explicitAnchor=item.anchor) {
        FDCard(Modifier.widthIn(max=290.dp).heightIn(max=420.dp).verticalScroll(rememberScrollState()).testTag("analytics-popup")) { Text(item.title,fontSize=14.sp,fontWeight=FontWeight.Bold);Text(item.text,fontSize=12.sp,color=DeckColors.muted) }
    }
}
