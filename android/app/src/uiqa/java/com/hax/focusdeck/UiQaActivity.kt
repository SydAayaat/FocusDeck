package com.hax.focusdeck

import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.core.view.*

/** The manifest uses plain Application and removes production activities/services and network permissions. */
class UiQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation=if(intent.getStringExtra("orientation")=="landscape") ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val immersive=intent.getBooleanExtra("immersive",true)
        WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars=false
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if(immersive) hide(WindowInsetsCompat.Type.systemBars())
        }
        window.attributes=window.attributes.apply { preferredRefreshRate=display?.supportedModes?.maxOfOrNull { it.refreshRate }?.coerceAtMost(120f) ?: 60f }
        setContent {
            DeckTheme("gold",intent.getStringExtra("motion") ?: "OFF",immersive) {
                if((intent.getStringExtra("fixture") ?: "REFERENCE_3Y")=="REFERENCE_3Y") {
                    val density=LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density,intent.getFloatExtra("fontScale",density.fontScale))) {
                        SyntheticPreviewLoader({finish()},intent.getStringExtra("screen") ?: "home",intent.getStringExtra("scenario") ?: "idle")
                    }
                    return@DeckTheme
                }
                val density=LocalDensity.current
                val fontScale=intent.getFloatExtra("fontScale",density.fontScale).coerceIn(.8f,2f)
                val fixture=remember { VisualTestDataProvider(VisualTestState.entries.find { it.name.equals(intent.getStringExtra("fixture"),true) } ?: VisualTestState.REFERENCE) }
                var screen by remember { mutableStateOf(intent.getStringExtra("screen") ?: "home") }
                var period by remember { mutableStateOf(when(screen) { "day"->HistoryPeriod.DAY;"week"->HistoryPeriod.WEEK;"month"->HistoryPeriod.MONTH;else->HistoryPeriod.YEAR }) }
                var anchor by remember { mutableStateOf(fixture.date) }
                var report by remember { mutableStateOf<Report?>(if(screen=="summary") fixture.partialReport ?: fixture.reports.firstOrNull { CalendarAnalytics.date(it.startedAtMs,fixture.zone)==fixture.date } else null) }
                var timeline by remember { mutableStateOf(screen=="timeline") }
                val timelineNavigation=remember { TimelineNavigationState() }
                var menu by remember { mutableStateOf(false) }
                CompositionLocalProvider(LocalDashboardPreview provides true,LocalDashboardDate provides fixture.date,
                    LocalTimelineNavigation provides timelineNavigation,LocalAmbientEnabled provides (report==null && !timeline && !menu),
                    LocalDensity provides Density(density.density,fontScale),
                    LocalPrimaryNavigation provides PrimaryNavigation(if(screen.startsWith("home")) "home" else if(screen=="achievements") "achievements" else "analytics",
                        {screen="home"},{screen="achievements"},{screen="day";period=HistoryPeriod.DAY},null)) {
                    Surface(Modifier.fillMaxSize(),color=DashboardBackground) {
                        Column(Modifier.windowInsetsPadding(dashboardViewportInsets()).padding(horizontal=if(screen.startsWith("home") || screen=="achievements") 12.dp else OmssGeometry.analyticsGutter.dp,vertical=4.dp)) {
                            if(screen=="achievements" && immersive) ImmersiveTopNavigation()
                            if(screen.startsWith("home")) DashboardHomeHeader("Connected") {
                                Box {
                                    TextButton({menu=true}) { Text("⋮",color=DeckColors.muted) }
                                    DropdownMenu(menu,{menu=false}) {
                                        DropdownMenuItem(text={Text("Achievements")},onClick={menu=false;screen="achievements"})
                                        DropdownMenuItem(text={Text("Analytics")},onClick={menu=false;screen="day";period=HistoryPeriod.DAY})
                                    }
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                when {
                                    screen.startsWith("home") -> HomeDashboard(fixture.calendar,fixture.homeLatest,fixture.reports.sumOf { it.focusedMs },fixture.upcoming,
                                        fixture.date,fixture.now,false,{screen="history"},{},{report=it},{timeline=true},{},true,
                                        initialPopup=if(screen=="home-popup") fixture.calendar.activities.values.firstOrNull { it.schedule?.start==fixture.date.atTime(6,0).atZone(fixture.zone).toInstant().toEpochMilli() }?.id else null,
                                        sourceZone=fixture.zone,presentation=fixture.home,currentPhase=fixture.sessionState?.phase ?: "idle",currentActive=fixture.sessionState?.activeMs,currentStart=fixture.sessionState?.blockStartedAtMs)
                                    screen=="achievements" -> AchievementPages(fixture.store,fixture.now,fixture.zone,{},{screen="home"},readOnly=true)
                                    screen=="history" -> ActivityHistoryScreen(fixture.reports,fixture.store,fixture.zone,fixture.date,{screen="home"},{report=it},{screen="day";period=HistoryPeriod.DAY})
                                    screen in setOf("summary","timeline") -> Unit
                                    else -> AnalyticsDashboard(fixture.calendar,fixture.reports,period,anchor,{period=it},{anchor=it},openReport={report=it},copy={},delete=null,
                                        header={AnalyticsHeader(period,anchor,{period=it},{anchor=it},"Connected",{},{anchor=fixture.date},{screen="home"},{screen="home"},{screen="achievements"})},
                                        archiveActions={},store=fixture.store,presentation=fixture.analytics(period,anchor),observationNow=fixture.now)
                                }
                            }
                            if(!immersive) DashboardNavigation(if(screen.startsWith("home")) "home" else if(screen=="achievements") "achievements" else "analytics",
                                {screen="home"},{screen="achievements"},{screen="day";period=HistoryPeriod.DAY})
                        }
                    }
                    if(timeline) ExpandedTimelineDialog(fixture.calendar,fixture.reports,fixture.date,{timeline=false;if(screen=="timeline") screen="home"},{report=it})
                    report?.let { r -> ReportDetailsDialog(r,{report=null;if(screen=="summary") screen="home"},{}) }
                }
            }
        }
    }
}
