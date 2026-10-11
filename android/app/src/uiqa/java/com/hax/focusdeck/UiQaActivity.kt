package com.hax.focusdeck

import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.time.*

/** Isolated QA package: no DeckApplication, repository, service, storage or network. */
class UiQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation=if(intent.getStringExtra("orientation")=="landscape") ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightStatusBars=false
        setContent {
            DeckTheme("gold",intent.getStringExtra("motion") ?: "OFF") {
                val nativeDensity=LocalDensity.current
                val fontScale=intent.getFloatExtra("fontScale",nativeDensity.fontScale).coerceIn(.8f,2f)
                CompositionLocalProvider(LocalDashboardPreview provides true,LocalDensity provides Density(nativeDensity.density,fontScale)) {
                    Surface(Modifier.fillMaxSize(),color=DashboardBackground) {
                        Column(Modifier.safeDrawingPadding().padding(horizontal=if(intent.getStringExtra("screen")?.let { it.startsWith("home") || it=="achievements" }==true || androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp<380 || fontScale>1.25f) 16.dp else 40.dp,vertical=if(intent.getStringExtra("screen")?.let { it.startsWith("home") || it=="achievements" }==true) 12.dp else 0.dp)) {
                            val reports=remember { UiQaFixtures.reports() }
                            val data=remember { CalendarAnalytics.dataset(reports) }
                            val popup=if(intent.getStringExtra("screen")=="home-popup") data.activities.values.firstOrNull { it.schedule?.start==LocalDate.of(2026,10,9).atTime(6,0).atZone(UiQaFixtures.zone).toInstant().toEpochMilli() }?.id else null
                            val screen=intent.getStringExtra("screen") ?: "home"
                            if(screen.startsWith("home")) DashboardHomeHeader("Connected") { TextButton({}) { Text("⋮",color=DeckColors.muted) } }
                            var period by remember { mutableStateOf(when(screen){"day"->HistoryPeriod.DAY;"week"->HistoryPeriod.WEEK;"month"->HistoryPeriod.MONTH;else->HistoryPeriod.YEAR}) }
                            var anchor by remember { mutableStateOf(UiQaFixtures.date) }
                            Box(Modifier.weight(1f)) {
                                if(screen.startsWith("home")) HomeDashboard(data,reports.last(),reports.sumOf { it.focusedMs },UiQaFixtures.upcoming,
                                    UiQaFixtures.date,UiQaFixtures.now,false,{},{},{},{},{},true,initialPopup=popup)
                                else if(screen=="achievements") AchievementPages(UiQaFixtures.store(),UiQaFixtures.now,UiQaFixtures.zone,{},{})
                                else AnalyticsDashboard(data,reports,period,anchor,{period=it},{anchor=it},openReport={},copy={},delete=null,
                                    header={AnalyticsHeader(period,anchor,{period=it},{anchor=it},"Connected",{},{},{})},archiveActions={},store=UiQaFixtures.store())
                            }
                            DashboardNavigation(if(screen.startsWith("home")) "home" else if(screen=="achievements") "achievements" else "analytics",{},{},{})
                        }
                    }
                }
            }
        }
    }
}
