package com.hax.focusdeck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.OffsetDateTime

/** Separate from illustrative QA projections: prevents native report exporters from serializing fixtures. */
internal val LocalSyntheticPreview=staticCompositionLocalOf { false }
internal data class PreviewHostActions(val settings: ()->Unit,val pairing: ()->Unit,val devices: ()->Unit,val sounds: (() -> Unit)?=null)
internal val LocalPreviewHostActions=staticCompositionLocalOf<PreviewHostActions?> { null }

@Composable internal fun SyntheticPreviewLoader(disable: ()->Unit,initialScreen: String="home",scenario: String="idle") {
    val assets=LocalContext.current.assets
    var error by remember { mutableStateOf<String?>(null) }
    val dataset by produceState<SyntheticDataset?>(null) {
        try { value=withContext(Dispatchers.IO) { SyntheticDataset.load { assets.open("${SyntheticDataset.ASSET_ROOT}/$it") } } }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { error=e.message ?: "Invalid fixture pack" }
    }
    // Fail closed: a loader error is displayed, never falls through to production import or sample generation.
    dataset?.let { SyntheticPreview(it,disable,initialScreen,scenario) } ?: Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) {
        Column { Text(error ?: "Validating synthetic assets…");if(error==null) CircularProgressIndicator();TextButton(disable) {Text("Disable Developer Mode")} }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SyntheticPreview(dataset: SyntheticDataset,disable: ()->Unit,initialScreen: String="home",initialScenario: String="idle") {
    val compact=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<500
    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    var scenario by rememberSaveable { mutableStateOf(initialScenario.lowercase()) }
    var menu by remember { mutableStateOf(false) }
    var scenarioMenu by remember { mutableStateOf(false) }
    var cardSandbox by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(when(initialScreen) {"day"->HistoryPeriod.DAY;"week"->HistoryPeriod.WEEK;"month"->HistoryPeriod.MONTH;else->HistoryPeriod.YEAR}) }
    var anchor by rememberSaveable(stateSaver=LocalDateSaver) { mutableStateOf(dataset.date) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var timeline by rememberSaveable { mutableStateOf(initialScreen=="timeline") }
    var reportKey by rememberSaveable { mutableStateOf(if(initialScreen=="summary") dataset.latest?.key else null) }
    val navigation=remember { TimelineNavigationState() }
    val empty=scenario=="empty";val zero=scenario=="zero"
    val longTitle=dataset.scenarios.first { it.getValue("id").jsonPrimitive.content=="long_text" }.getValue("session").jsonObject.getValue("title").jsonPrimitive.content
    val reports=remember(dataset,scenario) { if(empty || zero) emptyList() else if(scenario=="long_text") dataset.reports.mapIndexed { i,r->if(i==0) r.copy(title=longTitle) else r } else dataset.reports }
    val calendar=remember(dataset,scenario) { when {
        empty->CalendarDataset(emptyMap(),emptyMap())
        zero->CalendarDataset(mapOf(dataset.date to CalendarDay(dataset.date,zones=linkedSetOf(dataset.zone),hasRecord=true,hourCoverage=true)),emptyMap())
        else->dataset.calendar
    } }
    val store=remember(dataset,scenario) { if(empty || zero) AchievementStore() else if(scenario=="long_text") dataset.store.copy(definitions=dataset.store.definitions.mapIndexed { i,d->if(i==0) d.copy(title=longTitle) else d }) else dataset.store }
    val scene=dataset.scenarios.firstOrNull { it["id"]?.jsonPrimitive?.content==scenario }?.get("session") as? JsonObject
    val active=scenario in setOf("active","paused")
    val current=remember(scene,active) { if(active && scene!=null) Session(1,scene.getValue("id").jsonPrimitive.content,scene.getValue("title").jsonPrimitive.content,
        "2026-10-12T00:00:00Z",listOf(Block("scene","focus",scene.getValue("title").jsonPrimitive.content,scene.getValue("planned_seconds").jsonPrimitive.long))) else null }
    val report=reports.find { it.key==reportKey }
    fun home() { screen="home";reportKey=null;timeline=false }
    fun analytics() { screen="day";period=HistoryPeriod.DAY;reportKey=null;timeline=false }
    DestinationBackHandler(screen=="home",report==null && !timeline,::home)
    LaunchedEffect(scenario) {
        if(scenario=="ended_early") reportKey=reports.firstOrNull { it.status=="stopped" && it.blocks.any { b->b.outcome=="stopped" } }?.key
        else if(scenario in setOf("empty","zero")) reportKey=null
    }
    val overflow: @Composable ()->Unit = {
        Box {
                        TextButton({menu=true},Modifier.testTag("synthetic-menu")) {Text("⋮")}
                        DropdownMenu(menu,{menu=false}) {
                            listOf("home","achievements","index","day","history","timeline","summary").forEach { destination->DropdownMenuItem(text={Text(destination.replaceFirstChar { it.titlecase() })},onClick={
                                menu=false;screen=destination
                                if(destination=="timeline") timeline=true
                                if(destination=="summary") reportKey=reports.firstOrNull()?.key
                                if(destination=="day") period=HistoryPeriod.DAY
                            }) }
                            if(compact) dataset.scenarios.forEach { s->val id=s.getValue("id").jsonPrimitive.content
                                DropdownMenuItem(text={Text("Scene: $id")},onClick={scenario=id;menu=false})
                            }
                            LocalPreviewHostActions.current?.let { host ->
                                DropdownMenuItem(text={Text("Display settings")},onClick={menu=false;host.settings()})
                                DropdownMenuItem(text={Text("Developer Mode settings")},onClick={menu=false;host.settings()})
                                DropdownMenuItem(text={Text("Pair desktop")},onClick={menu=false;host.pairing()})
                                DropdownMenuItem(text={Text("Devices")},onClick={menu=false;host.devices()})
                                host.sounds?.let { go -> DropdownMenuItem(text={Text("Sounds")},onClick={menu=false;go()}) }
                            }
                            DropdownMenuItem(text={Text("Card sandbox")},onClick={menu=false;cardSandbox=true})
                            DropdownMenuItem(text={Text("Disable Developer Mode")},onClick=disable)
                        }
                    }
    }
    CompositionLocalProvider(LocalDashboardPreview provides true,LocalSyntheticPreview provides true,LocalDashboardDate provides dataset.date,
        LocalTimelineNavigation provides navigation,LocalAmbientEnabled provides (screen=="home" && report==null && !timeline && !menu),
        LocalPrimaryNavigation provides PrimaryNavigation(if(screen=="home") "home" else if(screen in setOf("achievements","index")) "achievements" else "analytics",::home,{screen="achievements"},::analytics,null)) {
        Surface(Modifier.fillMaxSize(),color=DashboardBackground) {
            Column(Modifier.windowInsetsPadding(dashboardViewportInsets()).padding(horizontal=12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SYNTHETIC · READ ONLY"+(if(compact) " · ${scenario.uppercase()}" else ""),color=DeckColors.secondary,fontSize=if(compact) 10.sp else 11.sp,modifier=Modifier.testTag("synthetic-banner"))
                        Text("Simulated ${dataset.date} · 23:59:30 Europe/Berlin",color=DeckColors.muted,fontSize=if(compact) 9.sp else 10.sp)
                    }
                    if(screen!="home") overflow()

                }
                if(!compact) Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Box { TextButton({scenarioMenu=true},Modifier.testTag("synthetic-scenario")) { Text("Scene: ${scenario.uppercase()}",fontSize=10.sp) }
                        DropdownMenu(scenarioMenu,{scenarioMenu=false}) { dataset.scenarios.forEach { s->val id=s.getValue("id").jsonPrimitive.content
                            DropdownMenuItem(text={Text(id)},onClick={scenario=id;scenarioMenu=false})
                        } }
                    }
                    TextButton(disable,Modifier.testTag("synthetic-disable")) {Text("Use real data",fontSize=10.sp)}
                }
                if(screen=="home") DashboardHomeHeader("Offline preview",overflow)
                Box(Modifier.weight(1f)) {
                    when(screen) {
                        "home"->HomeDashboard(calendar,reports.firstOrNull(),if(empty) null else if(zero) 0 else dataset.lifetime,current,
                            dataset.date,dataset.now,false,{screen="history"},{},{reportKey=it.key},{timeline=true},{},true,sourceZone=dataset.zone,
                            presentation=if(empty || zero) null else dataset.home,currentPhase=if(active) scenario else "idle",
                            currentActive=scene?.get("focus_seconds")?.jsonPrimitive?.longOrNull?.times(1000),
                            currentStart=scene?.get("started_at")?.jsonPrimitive?.contentOrNull?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() })
                        "achievements","index"->key(screen) { AchievementPages(store,dataset.now,dataset.zone,{},::home,readOnly=true,initialCategory=if(screen=="index") "Index" else "All") }
                        "history"->ActivityHistoryScreen(reports,store,dataset.zone,dataset.date,::home,{reportKey=it.key},{},syntheticSource=dataset.takeUnless { empty || zero })
                        "summary","timeline"->Unit
                        else->AnalyticsDashboard(calendar,reports,period,anchor,{period=it},{anchor=it},openReport={reportKey=it.key},copy={},delete=null,
                            header={AnalyticsHeader(period,anchor,{period=it},{anchor=it},"Offline preview",{pickingDate=true},{anchor=dataset.date},::home,::home,{screen="achievements"})},
                            archiveActions={},store=store,observationNow=dataset.now)
                    }
                }
                if(!LocalDeckImmersive.current) DashboardNavigation(if(screen=="home") "home" else if(screen in setOf("achievements","index")) "achievements" else "analytics",::home,{screen="achievements"},::analytics)
            }
        }
        if(pickingDate) {
            val picker=rememberDatePickerState(initialSelectedDateMillis=anchor.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                yearRange=dataset.daily.keys.min().year..dataset.date.year)
            DatePickerDialog({pickingDate=false},confirmButton={TextButton({picker.selectedDateMillis?.let { anchor=java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() };pickingDate=false}) {Text("Apply")}},dismissButton={TextButton({pickingDate=false}) {Text("Cancel")}}) {DatePicker(picker)}
        }
        if(timeline) ExpandedTimelineDialog(calendar,reports,anchor,{timeline=false;if(screen=="timeline") home()},{reportKey=it.key})
        report?.let { ReportDetailsDialog(it,{reportKey=null;if(screen=="summary") home()},{}) }
        if(cardSandbox) DeveloperPreviewDialog {cardSandbox=false}
    }
}
