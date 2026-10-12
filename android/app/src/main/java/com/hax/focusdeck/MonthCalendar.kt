package com.hax.focusdeck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal enum class CalendarMetric(val label: String) { EXECUTION_SCORE("Execution Score"), FOCUS_HOURS("Focus Hours"), COMPLETION_RATE("Completion Rate") }
internal fun calendarMetricValue(day: CalendarDay?,score: ExecutionScore?,metric: CalendarMetric): Double? = when(metric) {
    CalendarMetric.FOCUS_HOURS->day?.takeIf { it.hasRecord }?.focusMs?.div(3600000.0)
    CalendarMetric.EXECUTION_SCORE->score?.score
    CalendarMetric.COMPLETION_RATE->score?.completion?.times(100)
}
internal fun calendarEvidenceState(date: LocalDate,day: CalendarDay?,score: ExecutionScore?,metric: CalendarMetric,today: LocalDate=LocalDate.now()): String {
    val v=calendarMetricValue(day,score,metric)
    return when { date>today->"future";day?.incomplete==true || score?.missing_duration_blocks?.let { it>0 }==true->"incomplete records";
        metric!=CalendarMetric.FOCUS_HOURS && score?.eligible_planned_ms?.let { it>0 }!=true->"no valid plan";v==null->"no recorded activity";v==0.0->"valid zero";else->"recorded value" }
}
internal fun calendarFocusBins(day: CalendarDay?): List<Double?> = if(day?.hasRecord==true && day.hourCoverage)
    (0..2).map { bin -> day.hours.subList(bin*8,bin*8+8).sum()/3600000.0 } else List(3) { null }

/** Monday-first cells, including leading/trailing blanks; never fabricate observed days. */
internal fun monthCalendarSlots(anchor: LocalDate): List<LocalDate?> {
    val first=anchor.withDayOfMonth(1);val lead=first.dayOfWeek.value-1
    val count=((lead+first.lengthOfMonth()+6)/7)*7
    return List(count) { i->(i-lead).takeIf { it in 0 until first.lengthOfMonth() }?.let { first.plusDays(it.toLong()) } }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun NativeMonthCalendar(data: CalendarDataset,anchor: LocalDate,openDay: (LocalDate)->Unit,openWeek: (LocalDate)->Unit,scores: Map<LocalDate,ExecutionScore> = emptyMap(),metric: CalendarMetric=CalendarMetric.FOCUS_HOURS,setMetric: ((CalendarMetric)->Unit)?=null,shiftMonth: ((Long)->Unit)?=null) {
    val cells=remember(anchor) { monthCalendarSlots(anchor) };val density=LocalDensity.current
    val today=LocalDashboardDate.current ?: LocalDate.now()
    val rowHeight=18.dp
    val focusBins=metric==CalendarMetric.FOCUS_HOURS
    val bins=remember(data,anchor) { cells.associateWith { calendarFocusBins(data.days[it]) } }
    val peak=if(metric==CalendarMetric.FOCUS_HOURS) cells.flatMap { calendarFocusBins(data.days[it]).filterNotNull() }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0 else 100.0
    var menu by remember { mutableStateOf(false) }
    var selected by remember(anchor) { mutableStateOf<LocalDate?>(null) };var point by remember { mutableStateOf(Offset.Zero) }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                if(shiftMonth!=null) IconButton({shiftMonth(-1)},Modifier.size(28.dp).semantics { contentDescription="Previous calendar month" }) { Text("‹") }
                Text(anchor.format(DateTimeFormatter.ofPattern("MMM yyyy")),fontSize=10.sp,color=DeckColors.muted)
                if(shiftMonth!=null) IconButton({shiftMonth(1)},Modifier.size(28.dp).semantics { contentDescription="Next calendar month" }) { Text("›") }
            }
            if(setMetric!=null) Box {
                TextButton({menu=true},Modifier.height(28.dp),contentPadding=PaddingValues(horizontal=2.dp,vertical=0.dp)) { Text(metric.label+" ▾",fontSize=9.sp) }
                DropdownMenu(menu,{menu=false}) { CalendarMetric.entries.forEach { v->DropdownMenuItem(text={Text(v.label)},onClick={menu=false;setMetric(v)}) } }
            }
            Row(Modifier.heightIn(min=28.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("● High",fontSize=9.sp,color=DeckColors.accent);Text("● Low",fontSize=9.sp,color=OmssColors.analytical)
            }
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height((22+cells.size/7*18).dp).testTag("month-calendar").semantics {
            contentDescription="Productivity Calendar, Monday to Sunday, ${anchor.month} ${anchor.year}. Metric ${metric.label}; three eight-hour cells per day in Focus Hours; outline valid zero, dashed incomplete, muted future."
            customActions=cells.filterNotNull().map { date->CustomAccessibilityAction("$date: ${dashboardDuration(data.days[date]?.takeIf { it.hasRecord }?.focusMs)}") { selected=date;true } }
        }.pointerInput(cells,data,metric,scores) { detectTapGestures { at->
            val col=(at.x/(size.width/7f)).toInt().coerceIn(0,6)
            val row=((at.y-with(density){22.dp.toPx()})/with(density){rowHeight.toPx()}).toInt()
            if(at.y>=with(density){22.dp.toPx()}) cells.getOrNull(row*7+col)?.let { selected=it;point=at }
        } }) {
            val slot=size.width/7;val cellHeight=12.dp.toPx();val step=rowHeight.toPx();val top=22.dp.toPx()
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=DeckColors.muted.toArgb();textSize=10.sp.toPx();textAlign=android.graphics.Paint.Align.CENTER }
            listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").forEachIndexed { i,label->drawIntoCanvas { it.nativeCanvas.drawText(label,slot*(i+.5f),12.dp.toPx(),paint) } }
            cells.forEachIndexed { i,date->if(date!=null) {
                val d=data.days[date];val value=calendarMetricValue(d,scores[date],metric);val state=calendarEvidenceState(date,d,scores[date],metric,today)
                val binCount=if(focusBins) 3 else 1
                val gap=2.dp.toPx()
                val cellWidth=(slot-gap*(binCount+1))/binCount
                val y=top+i/7*step
                repeat(binCount) { bin ->
                    // Each small square is one actual eight-hour interval, not a fabricated day.
                    // Unlocated evidence cannot illuminate any hour bin.
                    val binValue=if(focusBins) bins[date]?.get(bin) else value
                    val intensity=(binValue ?: 0.0)/peak
                    val color=when {
                        binValue==null || binValue==0.0 -> OmssColors.material(OmssColors.track)
                        intensity<.25 -> OmssColors.analytical
                        else -> lerp(OmssColors.material(OmssColors.track),DeckColors.accent,intensity.toFloat().coerceIn(.3f,1f))
                    }
                    val x=slot*(i%7)+gap+bin*(cellWidth+gap)
                    val at=Offset(x,y);val box=Size(cellWidth,cellHeight);val radius=CornerRadius(2.dp.toPx())
                    drawRoundRect(color,at,box,radius)
                    if(state=="future") drawRoundRect(Color.Black.copy(alpha=.65f),at,box,radius)
                    if(state=="incomplete records") drawRoundRect(DeckColors.muted,at,box,radius,style=Stroke(.8.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),2.dp.toPx()))))
                    if(binValue==0.0) drawRoundRect(DeckColors.muted,at,box,radius,style=Stroke(.7.dp.toPx()))
                }

            } }
        }
        selected?.let { date->AnchoredWidgetPopup(point,{selected=null}) {
            Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),fontSize=13.sp)
            Text(metric.label+": "+(calendarMetricValue(data.days[date],scores[date],metric)?.let { if(metric==CalendarMetric.FOCUS_HOURS) "%.2fh".format(java.util.Locale.ROOT,it) else "%.1f%%".format(java.util.Locale.ROOT,it) } ?: "N/A"),fontSize=12.sp)
            Text(calendarEvidenceState(date,data.days[date],scores[date],metric,today),fontSize=11.sp,color=DeckColors.muted)
            TextButton({selected=null;openDay(date)}) { Text("Open day",fontSize=12.sp) }
            TextButton({selected=null;openWeek(date)}) { Text("Open week",fontSize=12.sp) }
        } }
    }
    Text(if(focusBins) "Each day: 00–08 / 08–16 / 16–24 · outline zero · dashed partial · muted future" else "Low → high · unknown dark · outline zero · dashed incomplete · muted future",fontSize=9.sp,color=DeckColors.muted)
}
