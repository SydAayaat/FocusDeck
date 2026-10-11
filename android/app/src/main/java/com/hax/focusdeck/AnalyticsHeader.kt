package com.hax.focusdeck

import androidx.compose.foundation.*
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
    setAnchor: (LocalDate)->Unit,connection: String,chooseDate: ()->Unit,current: ()->Unit,close: ()->Unit) {
    val actions=LocalAnalyticsActions.current
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(connection=="Connected") "●  Connected" else "○  $connection",Modifier.weight(1f),fontSize=11.sp,color=if(connection=="Connected") DeckColors.success else DeckColors.muted)
            Text(when(period){HistoryPeriod.WEEK->"WEEK ${anchor.get(WeekFields.ISO.weekOfWeekBasedYear())}";HistoryPeriod.YEAR->"YEAR ${anchor.year}";else->analyticsPeriodLabel(period,anchor)},fontSize=10.sp,color=DeckColors.muted)
            Box {
                TextButton({menu=true},Modifier.height(32.dp).testTag("analytics-options").semantics { contentDescription="Analytics options" },contentPadding=PaddingValues(horizontal=6.dp)) { Text("⋮",fontSize=21.sp) }
                DropdownMenu(menu,{menu=false}) {
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
            val stacked=maxWidth<340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.25f
            val navigator: @Composable ()->Unit = {
                Surface(Modifier.testTag("analytics-date"),shape=RoundedCornerShape(12.dp),color=DashboardRaised) {
                    Row(Modifier.heightIn(min=42.dp),verticalAlignment=Alignment.CenterVertically) {
                        IconButton({setAnchor(StudyHistory.shift(period,anchor,-1))},enabled=period!=HistoryPeriod.ALL,modifier=Modifier.size(36.dp).semantics { contentDescription="Previous ${period.label}" }) { BundledCardIcon("chevron-left",Modifier.size(16.dp),DeckColors.muted) }
                        Text(analyticsPeriodLabel(period,anchor),Modifier.weight(1f),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        IconButton({setAnchor(StudyHistory.shift(period,anchor,1))},enabled=period!=HistoryPeriod.ALL,modifier=Modifier.size(36.dp).semantics { contentDescription="Next ${period.label}" }) { BundledCardIcon("chevron-right",Modifier.size(16.dp),DeckColors.muted) }
                    }
                }
            }
            if(stacked) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { Text("Analytics",Modifier.testTag("analytics-title"),fontSize=27.sp,fontWeight=FontWeight.Bold);navigator() }
            else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Analytics",Modifier.testTag("analytics-title"),fontSize=26.sp,fontWeight=FontWeight.Bold)
                Box(Modifier.weight(1f)) { navigator() }
            }
        }
        Surface(shape=RoundedCornerShape(12.dp),color=DashboardInset) {
            Row(Modifier.fillMaxWidth().padding(3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                HistoryPeriod.entries.forEach { value ->
                    Box(Modifier.weight(1f).heightIn(min=40.dp).background(if(period==value) DashboardRaised else androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(10.dp))
                        .semantics { selected=period==value;role=Role.Tab }.testTag("period-${value.name}").clickable { setPeriod(value) }.padding(9.dp),contentAlignment=Alignment.Center) {
                        Text(value.label,fontSize=11.sp,color=if(period==value) androidx.compose.ui.graphics.Color.White else DeckColors.muted)
                    }
                }
            }
        }
    }
}
