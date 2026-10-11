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
    openSession: () -> Unit = {}) {
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
        history,pair,openReport,expandAnalytics,openSession,paired,sourceZone=zone)
}

/** Stateless data boundary also used by the isolated native screenshot runner. */
@Composable internal fun HomeDashboard(calendar: CalendarDataset?, latest: Report?, total: Long?, upcoming: Session?,
    today: LocalDate, now: Long, viewer: Boolean, history: () -> Unit, pair: () -> Unit,
    openReport: (Report) -> Unit, expandAnalytics: () -> Unit, openSession: () -> Unit, paired: Boolean,
    initialPopup: String? = null,sourceZone: ZoneId=ZoneId.systemDefault()) {
    val layout=LocalDashboardLayout.current ?: rememberDashboardLayout("overview")
    BackHandler(layout.editing) { layout.editing=false }
    val day=calendar?.days?.get(today)
    val weekStart=today.minusDays((today.dayOfWeek.value-1).toLong())
    val week=calendar?.days?.values?.filter { it.hasRecord && it.date>=weekStart && it.date<=today }?.sumOf { it.focusMs }
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
        cards+=DashboardCard("upcoming-session",12,116) {
            FDCard(Modifier.fillMaxWidth()) {
                FDTitle("Upcoming Session",if(upcoming!=null) "View plan  →" else null,openSession)
                HorizontalDivider(color=DeckColors.track)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    SessionBadge()
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(upcoming?.title ?: "No plan loaded",fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(upcoming?.schedule?.let { s -> val z=s.timezone?.let(ZoneId::of) ?: sourceZone;"${Instant.ofEpochMilli(s.start).atZone(z).format(DateTimeFormatter.ofPattern("HH:mm"))} – ${s.end?.let { Instant.ofEpochMilli(it).atZone(z).format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "N/A"}" }
                            ?: if(upcoming!=null) "No absolute schedule" else "Send your next plan from the desktop.",fontSize=11.sp,color=DeckColors.muted)
                    }
                    if(upcoming!=null && !viewer) Button(openSession,contentPadding=PaddingValues(horizontal=14.dp,vertical=8.dp)) { Text("▶  Start",fontSize=12.sp) }
                }
            }
        }
        DashboardGrid(cards,layout,Modifier.fillMaxSize(),scrollTag="home-grid",header={
            Column(Modifier.fillMaxWidth().padding(top=14.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(today.format(DateTimeFormatter.ofPattern("EEE, d MMMM yyyy")).uppercase(Locale.getDefault()),fontSize=12.sp,color=DeckColors.muted,letterSpacing=1.sp)
                Column {
                    Text(if(viewer) "PHONE OVERVIEW" else "READY FOR YOUR",fontSize=27.sp,fontWeight=FontWeight.ExtraBold)
                    Text(if(viewer) "READ-ONLY VIEW" else "NEXT SESSION",color=DeckColors.accent,fontSize=27.sp,fontWeight=FontWeight.ExtraBold)
                }
                Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    RefLabel("TODAY’S FOCUS")
                    Text(if(calendar==null) "—" else dashboardDuration(day?.focusMs ?: 0),fontSize=42.sp,fontWeight=FontWeight.Bold)
                    Text(day?.let { "${it.sessions.size} recorded sessions · ${it.done} blocks done" } ?: "No recorded activity today",fontSize=12.sp,color=DeckColors.muted)
                }
                HorizontalDivider(color=DeckColors.track)
                LayoutPanel("weekly-statistics",100) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    FDCard(Modifier.weight(1f)) { RefLabel("THIS WEEK");Text(dashboardDuration(week),fontSize=21.sp,fontWeight=FontWeight.Bold) }
                    FDCard(Modifier.weight(1f)) { RefLabel("ALL-TIME FOCUS");Text(dashboardDuration(total),fontSize=21.sp,fontWeight=FontWeight.Bold) }
                } }
                OutlinedButton(history,Modifier.fillMaxWidth().height(44.dp),shape=RoundedCornerShape(14.dp),
                    colors=ButtonDefaults.outlinedButtonColors(containerColor=DeckColors.surface),border=BorderStroke(1.dp,DeckColors.accent)) {
                    Spacer(Modifier.weight(1f));BundledCardIcon("chart-no-axes-column",Modifier.size(21.dp),DeckColors.accent)
                    Spacer(Modifier.width(12.dp));Text("Analytics",fontWeight=FontWeight.Bold,fontSize=16.sp)
                    Spacer(Modifier.weight(1f));Text("›",fontSize=24.sp)
                }
                if(!paired) TextButton(pair) { Text("Pair desktop",fontSize=12.sp) }
                if(layout.editing) LayoutControls(layout)
            }
        })
    }
}
@Composable private fun SessionBadge() {
    Surface(shape=RoundedCornerShape(50),color=DashboardRaised,border=BorderStroke(1.dp,DeckColors.accent)) {
        BundledCardIcon("book-open",Modifier.padding(10.dp).size(24.dp),DeckColors.accent)
    }
}
