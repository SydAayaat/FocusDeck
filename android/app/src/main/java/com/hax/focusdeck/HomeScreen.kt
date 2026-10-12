package com.hax.focusdeck

import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(repo: DeckRepository, paired: Boolean, connection: String,
    history: () -> Unit, pair: () -> Unit, openReport: (Report) -> Unit, expandAnalytics: () -> Unit = history,
    openSession: () -> Unit = {}, achievements: () -> Unit = {}) {
    val reports by repo.history.collectAsStateWithLifecycle()
    val now by repo.now.collectAsStateWithLifecycle()
    val prefs by repo.display.collectAsStateWithLifecycle()
    val zoneId by repo.displayZone.collectAsStateWithLifecycle()
    val zone=ZoneId.of(zoneId)
    val calendar by produceState<CalendarDataset?>(null,reports,prefs.state) {
        value=withContext(Dispatchers.Default) { CalendarAnalytics.dataset(reports,prefs.state) }
    }
    val latest by produceState<Report?>(null,reports) { value=withContext(Dispatchers.Default) { reports.maxByOrNull { it.finishedAtMs } } }
    val total by produceState<Long?>(null,reports) { value=withContext(Dispatchers.Default) { reports.sumOf { it.focusedMs } } }
    HomeDashboard(calendar, latest, total, prefs.state.session?.takeIf { prefs.state.phase !in setOf("idle","complete","stopped") },
        Instant.ofEpochMilli(now.wall).atZone(zone).toLocalDate(),now.wall,prefs.pairing?.viewer==true,
        history,pair,openReport,expandAnalytics,openSession,paired,sourceZone=zone,currentPhase=prefs.state.phase,currentActive=currentSessionFocus(prefs.state,now),currentStart=prefs.state.blockStartedAtMs,achievements=achievements)
}

/** Stateless data boundary also used by the isolated native screenshot runner. */
@Composable internal fun HomeDashboard(calendar: CalendarDataset?, latest: Report?, total: Long?, upcoming: Session?,
    today: LocalDate, now: Long, viewer: Boolean, history: () -> Unit, pair: () -> Unit,
    openReport: (Report) -> Unit, expandAnalytics: () -> Unit, openSession: () -> Unit, paired: Boolean,
    initialPopup: String? = null,sourceZone: ZoneId=ZoneId.systemDefault(),presentation: HomePresentation?=null,currentPhase: String="idle",currentActive: Long?=null,currentStart: Long?=null,achievements: () -> Unit = LocalPrimaryNavigation.current?.achievements ?: {}) {
    val visual=presentation.takeIf { LocalDashboardPreview.current }
    val layout=LocalDashboardLayout.current ?: rememberDashboardLayout("overview")
    BackHandler(layout.editing) { layout.editing=false }
    val day=calendar?.days?.get(today)
    val weekStart=today.minusDays((today.dayOfWeek.value-1).toLong())
    val week=calendar?.days?.values?.filter { it.hasRecord && it.date>=weekStart && it.date<=today }?.takeIf { it.isNotEmpty() }?.sumOf { it.focusMs }
    CompositionLocalProvider(LocalDashboardLayout provides layout, LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontSize=14.sp)) {
        val cards=mutableListOf<DashboardCard>()
        cards+=DashboardCard("timeline",12,110) {
            FDCard(Modifier.fillMaxWidth()) { calendar?.let { DailyTimeline(it,today,compact=true,now=now,expand=expandAnalytics,selectedId=initialPopup) } ?: Text("Loading timeline…",color=DeckColors.muted) }
        }
        cards+=DashboardCard("last-session",12,122) {
            FDCard(Modifier.fillMaxWidth(),onClick={if(latest!=null) openReport(latest) else history()}) {
                FDTitle("Last Session","View all  →",history)
                HorizontalDivider(color=DeckColors.track)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    SessionBadge()
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(latest?.title ?: "No previous session",fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(latest?.let { "${dashboardDuration(it.focusedMs)} focus · ${it.statusLabel}" } ?: "Your recorded sessions appear here.",fontSize=12.sp,color=DeckColors.muted)
                        latest?.let { Text(Instant.ofEpochMilli(it.finishedAtMs).atZone(CalendarAnalytics.zone(it)).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")),fontSize=11.sp,color=DeckColors.muted) }
                    }
                    Text("›",fontSize=24.sp,color=DeckColors.muted)
                }
            }
        }
        cards.add(1,DashboardCard("upcoming-session",12,116) {
            FDCard(Modifier.fillMaxWidth().testTag("home-current-session"),onClick=if(upcoming!=null) openSession else null) {
                FDTitle("Current Session",if(upcoming!=null) "Open  →" else null,openSession)
                HorizontalDivider(color=DeckColors.track)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    CurrentSessionIndicator(currentPhase,upcoming!=null)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(upcoming?.title ?: "No active session",fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(if(upcoming==null) "Your next session appears here." else when(currentPhase) {
                            "active" -> "Active · ${humanTime(currentActive)} focus · started ${compactClock(currentStart?.takeIf { it>0 },sourceZone)}"
                            "paused" -> "Paused · ${humanTime(currentActive)} focus"
                            else -> "Ready to open · ${executionLabel(currentPhase)}"
                        },fontSize=11.sp,color=DeckColors.muted)
                    }
                }
            }
        })
        DashboardGrid(cards,layout,Modifier.fillMaxSize(),scrollTag="home-grid",header={
            Column(Modifier.fillMaxWidth().padding(top=14.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(if(viewer) "PHONE OVERVIEW" else "READY FOR YOUR",fontSize=27.sp,fontWeight=FontWeight.ExtraBold)
                    Text(if(viewer) "READ-ONLY VIEW" else "NEXT SESSION",color=DeckColors.accent,fontSize=27.sp,fontWeight=FontWeight.ExtraBold)
                }
                Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    RefLabel("TODAY’S FOCUS")
                    Text(if(calendar==null) "—" else dashboardDuration(visual?.focus ?: day?.takeIf { it.hasRecord }?.focusMs),fontSize=42.sp,style=OmssTypography.metric)
                    Text(visual?.let { "${it.sessions} ${if(LocalSyntheticPreview.current) "recorded" else "completed"} sessions · ${it.done} blocks done" } ?: day?.let { "${it.sessions.size} recorded sessions · ${it.done} blocks done" } ?: "No recorded activity today",fontSize=12.sp,color=DeckColors.muted)
                }
                LayoutPanel("weekly-statistics",100) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    FDCard(Modifier.weight(1f)) { RefLabel("THIS WEEK");Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { Text(dashboardDuration(visual?.week ?: week),fontSize=21.sp,fontWeight=FontWeight.Bold);visual?.weeklyChange?.let { Text("${if(it>=0) "↑" else "↓"} ${kotlin.math.abs(it).toInt()}%",fontSize=14.sp,color=if(it>=0) DeckColors.success else DeckColors.error) } } }
                    FDCard(Modifier.weight(1f)) { RefLabel("ALL-TIME FOCUS");Text(dashboardDuration(visual?.lifetime ?: total),fontSize=21.sp,fontWeight=FontWeight.Bold) }
                } }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(LocalPrimaryNavigation.current?.analytics ?: history,Modifier.weight(1f).height(44.dp).omssDirectionalShadow(RoundedCornerShape(14.dp),OmssElevations.component),shape=RoundedCornerShape(14.dp),
                    colors=ButtonDefaults.outlinedButtonColors(containerColor=DeckColors.surface),border=BorderStroke(1.dp,DeckColors.accent)) {
                    Spacer(Modifier.weight(1f));ActivityGlyph("chart",Modifier.size(21.dp),DeckColors.accent)
                    Spacer(Modifier.width(12.dp));Text("Analytics",fontWeight=FontWeight.Bold,fontSize=16.sp)
                    Spacer(Modifier.weight(1f));Text("›",fontSize=24.sp)
                }
                OutlinedButton(achievements,Modifier.size(width=50.dp,height=44.dp).testTag("home-achievements").semantics { contentDescription="Open Achievements" },shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=DeckColors.surface),border=BorderStroke(1.dp,DeckColors.accent),contentPadding=PaddingValues(0.dp)) {
                    ActivityGlyph("trophy",Modifier.size(23.dp),DeckColors.accent)
                }
                }
                if(!paired) TextButton(pair) { Text("Pair desktop",fontSize=12.sp) }
                if(layout.editing) LayoutControls(layout)
            }
        })
    }
}
@Composable private fun SessionBadge() {
    OmssCircularGauge(1f,Modifier.size(56.dp)) {
        BundledCardIcon("book-open",Modifier.size(25.dp),DeckColors.accent)
    }
}
