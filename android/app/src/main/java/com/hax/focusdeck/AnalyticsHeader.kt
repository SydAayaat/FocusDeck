package com.hax.focusdeck

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

internal fun analyticsPeriodLabel(period: HistoryPeriod,date: LocalDate): String=when(period) {
    HistoryPeriod.DAY->date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    HistoryPeriod.WEEK->date.minusDays((date.dayOfWeek.value-1).toLong()).let { "${it.dayOfMonth} – ${it.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}" }
    HistoryPeriod.MONTH->date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
    HistoryPeriod.YEAR->date.year.toString()
    HistoryPeriod.ALL->"All history"
}
@Composable internal fun AnalyticsHeader(period: HistoryPeriod,anchor: LocalDate,setPeriod: (HistoryPeriod)->Unit,
    setAnchor: (LocalDate)->Unit,connection: String,chooseDate: ()->Unit,current: ()->Unit,close: ()->Unit,
    home: (() -> Unit)?=null,achievements: (() -> Unit)?=null) {
    val actions=LocalAnalyticsActions.current
    val dateLabel=analyticsPeriodLabel(period,anchor)
    val textMeasurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val dateStyle=MaterialTheme.typography.bodyLarge.copy(fontSize=11.sp)
    val dateWidth=remember(dateLabel,dateStyle,density.density,density.fontScale) {
        with(density) { textMeasurer.measure(dateLabel,dateStyle).size.width.toDp() }+98.dp
    }
    var menu by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            if(LocalDeckImmersive.current && LocalPrimaryNavigation.current!=null) {
                ImmersiveTopNavigation()
            } else Text(if(connection=="Connected") "●  Connected" else "○  $connection",Modifier.weight(1f),fontSize=11.sp,color=if(connection=="Connected") DeckColors.success else DeckColors.muted)
            Text(when(period){HistoryPeriod.WEEK->"WEEK ${anchor.get(WeekFields.ISO.weekOfWeekBasedYear())}";HistoryPeriod.YEAR->"YEAR ${anchor.year}";else->analyticsPeriodLabel(period,anchor)},Modifier.then(if(LocalDeckImmersive.current && LocalPrimaryNavigation.current!=null) Modifier.weight(1f) else Modifier),fontSize=10.sp,maxLines=2,color=DeckColors.muted)
            Box {
                IconButton({menu=true},Modifier.size(48.dp).testTag("analytics-options").semantics { contentDescription="Analytics options" }) { Text("⋮",fontSize=21.sp) }
                DropdownMenu(menu,{menu=false}) {
                    home?.let { DropdownMenuItem(text={Text("Home")},onClick={menu=false;it()}) }
                    achievements?.let { DropdownMenuItem(text={Text("Achievements")},onClick={menu=false;it()}) }
                    actions?.let { a ->
                        DropdownMenuItem(text={Text("Edit layout")},onClick={menu=false;a.edit()})
                        DropdownMenuItem(text={Text("Expanded timeline & charts")},onClick={menu=false;a.expanded()})
                        DropdownMenuItem(text={Text("History & export")},onClick={menu=false;a.history()})
                    }
                    DropdownMenuItem(text={Text("Choose date")},onClick={menu=false;chooseDate()})
                    DropdownMenuItem(text={Text("Current period")},onClick={menu=false;current()})
                    DropdownMenuItem(text={Text("All saved history")},onClick={menu=false;setPeriod(HistoryPeriod.ALL)})
                    DropdownMenuItem(text={Text("Close Analytics")},onClick={menu=false;close()})
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked=maxWidth<350.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.25f
            val navigator: @Composable ()->Unit = {
                // Explicit text measurement avoids weighted-row intrinsic collapse for Month/Year.
                // width() remains constrained by the real header slot; long ranges can wrap.
                OmssSurface(Modifier.width(dateWidth).testTag("analytics-date"),nested=true) {
                    Row(Modifier.fillMaxWidth().heightIn(min=40.dp),verticalAlignment=Alignment.CenterVertically) {
                        IconButton({setAnchor(StudyHistory.shift(period,anchor,-1))},enabled=period!=HistoryPeriod.ALL,modifier=Modifier.size(40.dp).semantics { contentDescription="Previous ${period.label}" }) { BundledCardIcon("chevron-left",Modifier.size(16.dp),DeckColors.muted) }
                        Text(dateLabel,Modifier.weight(1f).clickable(onClick=chooseDate).padding(horizontal=3.dp),style=dateStyle,maxLines=2)
                        IconButton({setAnchor(StudyHistory.shift(period,anchor,1))},enabled=period!=HistoryPeriod.ALL,modifier=Modifier.size(40.dp).semantics { contentDescription="Next ${period.label}" }) { BundledCardIcon("chevron-right",Modifier.size(16.dp),DeckColors.muted) }
                    }
                }
            }
            if(stacked) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { Text("Analytics",Modifier.testTag("analytics-title"),fontSize=27.sp,fontWeight=FontWeight.Bold);navigator() }
            else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Analytics",Modifier.testTag("analytics-title"),fontSize=26.sp,fontWeight=FontWeight.Bold)
                Box(Modifier.weight(1f),contentAlignment=Alignment.CenterEnd) { navigator() }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            // All remains available through the existing menu and when already selected.
            (if(period==HistoryPeriod.ALL) HistoryPeriod.entries else HistoryPeriod.entries.filter { it!=HistoryPeriod.ALL }).forEach { value ->
                OmssFlatTab(value.label,period==value,Modifier.weight(1f).testTag("period-${value.name}")) { setPeriod(value) }
            }
        }
    }
}
}
