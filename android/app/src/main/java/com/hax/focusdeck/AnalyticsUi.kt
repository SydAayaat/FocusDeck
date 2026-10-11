package com.hax.focusdeck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt


/** Geometry comes from the selected recorded bar; compact Home never installs this provider. */
@Stable private class AnalyticsConnection {
    var origin by mutableStateOf<Rect?>(null)
    var reverseIndex by mutableStateOf<Int?>(null)
    var revision by mutableIntStateOf(0)
    val progress=Animatable(1f)
    var area: Rect? = null
    fun begin(rect: Rect?, reverse: Int? = null) { origin=rect;reverseIndex=reverse;revision++ }
}
private val LocalAnalyticsConnection=staticCompositionLocalOf<AnalyticsConnection?> { null }
private fun blend(a: Float,b: Float,p: Float)=a+(b-a)*p

internal fun punctualityLabel(value: Double?): String = if(value==null) "N/A" else (if(value>0) "+" else if(value<0) "−" else "")+String.format(java.util.Locale.ROOT,"%.1f",kotlin.math.abs(value)/60000).removeSuffix(".0")+"m"
@Composable internal fun PunctualityText(value: Double?, modifier: Modifier = Modifier) {
    Text(punctualityLabel(value),modifier,color=if(value==null || value==0.0) DeckColors.muted else if(value<0) DeckColors.success else DeckColors.error,fontSize=12.sp)
}
private fun timeLabel(ms: Long?, zone: ZoneId): String = ms?.let { Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM HH:mm z")) } ?: "N/A"
@Composable internal fun ActivityMetadata(a: CalendarActivity, full: Boolean = false) {
    Text(a.title,fontWeight=FontWeight.Medium,fontSize=14.sp)
    Text("Plan: ${a.schedule?.let { timeLabel(it.start,it.timezone?.let(ZoneId::of) ?: a.zone)+" → "+timeLabel(it.end,it.timezone?.let(ZoneId::of) ?: a.zone) } ?: "Unscheduled"}",fontSize=11.sp,color=DeckColors.muted)
    Text("Actual: ${timeLabel(a.start,a.zone)} → ${timeLabel(a.end,a.zone)}",fontSize=11.sp,color=DeckColors.muted)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Text("Punctuality",fontSize=11.sp);PunctualityText(a.punctuality?.toDouble()) }
    if(full) {
        Text("Achievement / Index topic: ${listOfNotNull(a.achievementId,a.indexTopicId).joinToString(" / ").ifBlank { "N/A" }}",fontSize=12.sp)
        Text("Planned active ${humanTime(a.plannedMs)}",fontSize=12.sp)
        Text("Elapsed ${humanTime(a.elapsedMs)} · active focus ${humanTime(a.focusMs)}",fontSize=12.sp)
        Text("Paused ${humanTime(a.pausedMs)} · overtime ${humanTime(a.overtimeMs)}",fontSize=12.sp)
        estimationMeasurement(a.plannedMs,a.focusMs)?.takeIf { a.status=="done" }?.let { est ->
            Text("Signed error ${if(est.signed_error_ms>0) "+" else if(est.signed_error_ms<0) "−" else ""}${humanTime(kotlin.math.abs(est.signed_error_ms))} · ${"%.1f".format(java.util.Locale.ROOT,est.percentage_error)}% · accuracy ${est.accuracy.roundToInt()}%",fontSize=12.sp)
            Text(est.interpretation,fontSize=11.sp,color=DeckColors.muted)
        }
        Text("Project ${a.project} · Category ${a.category} · Pause count ${a.pauseCount ?: "N/A"}",fontSize=12.sp)
        Text("Recorded status: ${a.status}",fontSize=12.sp,color=DeckColors.muted)
    }
}
private fun activityColor(a: CalendarActivity?, kind: String): Color = if(DeckColors.palette.id=="monochrome") DeckColors.accent else when {
    "paused" in kind -> Color(0xFFAAB4BC)
    "break" in kind -> Color(0xFF6E94B3)
    a?.category=="index" -> Color(0xFFFFB24F)
    a?.category=="projects" -> Color(0xFFB184FA)
    a?.category=="college" -> Color(0xFF42BFF5)
    a?.category=="health" -> Color(0xFF59E89A)
    else -> Color(0xFFFFB84C)
}
@Composable internal fun DailyTimeline(data: CalendarDataset, date: LocalDate, compact: Boolean = false, now: Long? = null,
    expand: (() -> Unit)? = null, selectedId: String? = null, select: ((String) -> Unit)? = null, displayZone: ZoneId? = null) {
    val day=data.days[date]
    if(displayZone==null && day!=null && day.zones.size>1) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { day.zones.forEach { z ->
            key(z.id) { Text(z.id,fontSize=10.sp,color=DeckColors.muted);DailyTimeline(data,date,compact,now,expand,selectedId,select,z) }
        } };return
    }
    if(compact) { CompactDailyTimeline(data,date,now,expand,selectedId,displayZone);return }
    val zone=displayZone ?: day?.zones?.firstOrNull() ?: ZoneId.systemDefault()
    val (start,end)=CalendarAnalytics.bounds(date,zone)
    var popup by remember(date) { mutableStateOf<String?>(null) }
    var popupAnchor by remember(date) { mutableStateOf(IntOffset.Zero) }
    val motion=LocalAchievementMotion.current
    val connection=LocalAnalyticsConnection.current
    var chartBounds by remember { mutableStateOf(Rect.Zero) }
    // Multiple lanes keep simultaneous observations inspectable without overlap.
    val ends=mutableMapOf("planned" to mutableListOf<Float>(),"actual" to mutableListOf<Float>())
    val segments=day?.segments.orEmpty().filter { displayZone==null || it.zone==displayZone }.sortedBy { it.left }.map { s ->
        val row=ends.getValue(s.track);val lane=row.indexOfFirst { it<=s.left }.takeIf { it>=0 } ?: row.size
        if(lane==row.size) row+=s.right else row[lane]=s.right
        s to lane
    }
    val rows=maxOf(1,ends.getValue("planned").size)+maxOf(1,ends.getValue("actual").size)
    val laneHeight=if(compact) 24.dp else 44.dp
    val segmentHeight=if(compact) 20.dp else 36.dp
    val chartHeight=maxOf(if(compact) 100 else 160,rows*laneHeight.value.toInt()+45).dp
    val available=LocalDashboardCardHeight.current
    val height=if(available!=null) maxOf(chartHeight,available-70.dp) else chartHeight
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(compact) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text("Daily Timeline",fontSize=17.sp,fontWeight=FontWeight.Medium);Text("Planned vs actual focus across your day",fontSize=11.sp,color=DeckColors.muted) }
            if(expand!=null) TextButton(expand) { Text("⤢",color=DeckColors.accent,fontSize=20.sp) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fullWidth=maxWidth;val labelWidth=42.dp;val plotWidth=(fullWidth-labelWidth-8.dp).coerceAtLeast(1.dp)
            val density=LocalDensity.current
            val safeTop=WindowInsets.safeDrawing.getTop(density);val safeBottom=WindowInsets.safeDrawing.getBottom(density)
            Box(Modifier.fillMaxWidth().height(height).onGloballyPositioned { chartBounds=it.boundsInRoot();connection?.area=chartBounds }) {
                Canvas(Modifier.fillMaxSize()) {
                    val l=labelWidth.toPx();val w=plotWidth.toPx()
                    for(h in listOf(0,6,12,18,24)) {
                        val tick=if(h==24) 1f else (date.atTime(h,0).atZone(zone).toInstant().toEpochMilli()-start).toFloat()/(end-start)
                        drawLine(DeckColors.track,Offset(l+w*tick,4.dp.toPx()),Offset(l+w*tick,size.height-22.dp.toPx()),1.dp.toPx())
                    }
                    if(now!=null && now in start until end) { val x=l+w*(now-start).toFloat()/(end-start);drawLine(DeckColors.error,Offset(x,0f),Offset(x,size.height-20.dp.toPx()),1.dp.toPx()) }
                }
                var rowTop=6.dp
                for(track in listOf("planned","actual")) {
                    val laneCount=maxOf(1,ends.getValue(track).size)
                    Text(if(track=="planned") "PLAN" else "ACTUAL",Modifier.offset(y=rowTop+5.dp),fontSize=9.sp,color=DeckColors.muted)
                    segments.filter { it.first.track==track }.forEach { (s,lane) ->
                        val a=data.activities[s.activityId];val x=labelWidth+plotWidth*s.left;val y=rowTop+laneHeight*lane
                        val color=activityColor(a,s.kind)
                        Box(Modifier.offset(x,y).width((plotWidth*(s.right-s.left)).coerceAtLeast(3.dp)).height(segmentHeight)
                            .graphicsLayer {
                                val source=connection?.origin;val p=connection?.progress?.value ?: 1f
                                if(source!=null && p<1f) {
                                    val width=(plotWidth*(s.right-s.left)).coerceAtLeast(3.dp).toPx();val h=segmentHeight.toPx()
                                    translationX=(source.center.x-chartBounds.left-x.toPx()-width/2)*(1-p)
                                    translationY=(source.center.y-chartBounds.top-y.toPx()-h/2)*(1-p)
                                    scaleX=blend(source.width/width,1f,p);scaleY=blend(source.height/h,1f,p)
                                }
                            }
                            .background(color.copy(alpha=if(s.activityId==selectedId || s.activityId==popup) .95f else .65f),androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                            .border(1.dp,color,androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                            .semantics { contentDescription="${a?.title} $track ${timeLabel(s.start,s.zone)} to ${timeLabel(s.end,s.zone)}" }
                            .clickable { if(select!=null) select(s.activityId) else { popup=if(popup==s.activityId) null else s.activityId;popupAnchor=with(density) { IntOffset((x+plotWidth*(s.right-s.left)/2).roundToPx(),(y+laneHeight).roundToPx()) } } }) {
                            if(!compact && plotWidth*(s.right-s.left)>64.dp) Text(a?.title ?: "Activity",Modifier.padding(5.dp),color=Color.White,fontSize=10.sp,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    if(segments.none { it.first.track==track }) Text(if(track=="planned") "No absolute schedule" else "No located intervals",Modifier.offset(labelWidth,rowTop+4.dp),fontSize=10.sp,color=DeckColors.muted)
                    rowTop+=laneHeight*laneCount+10.dp
                }
                for(h in listOf(0,6,12,18,24)) {
                    val tick=if(h==24) 1f else (date.atTime(h,0).atZone(zone).toInstant().toEpochMilli()-start).toFloat()/(end-start)
                    Text(String.format(java.util.Locale.ROOT,"%02d:00",h),Modifier.align(Alignment.BottomStart).offset((labelWidth+plotWidth*tick-14.dp).coerceIn(0.dp,(fullWidth-30.dp).coerceAtLeast(0.dp))),fontSize=9.sp,color=DeckColors.muted)
                }
                popup?.let { id -> data.activities[id]?.let { a ->
                    Popup(popupPositionProvider=remember(popupAnchor) { object: PopupPositionProvider {
                        override fun calculatePosition(anchorBounds: IntRect,windowSize: IntSize,layoutDirection: LayoutDirection,popupContentSize: IntSize): IntOffset {
                            val x=(anchorBounds.left+popupAnchor.x-popupContentSize.width/2).coerceIn(8,maxOf(8,windowSize.width-popupContentSize.width-8))
                            val y=(anchorBounds.top+popupAnchor.y+8).coerceIn(safeTop+8,maxOf(safeTop+8,windowSize.height-safeBottom-popupContentSize.height-8))
                            return IntOffset(x,y)
                        }
                    } },onDismissRequest={popup=null},properties=PopupProperties(focusable=true,clippingEnabled=true)) {
                        EntryMotion {
                            Column(Modifier.widthIn(max=300.dp).background(DeckColors.surface,androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .border(1.dp,DeckColors.accent,androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text("▲",color=DeckColors.accent,modifier=Modifier.align(Alignment.CenterHorizontally),fontSize=9.sp)
                                ActivityMetadata(a)
                            }
                        }
                    }
                } }
            }
        }
        if(!compact) Text("${date} · ${zone.id} · ${(end-start)/3_600_000} elapsed hours",fontSize=10.sp,color=DeckColors.muted)
        if(day?.unlocatedMs?.let { it>0 }==true) Text("${humanTime(day.unlocatedMs)} lacks reliable interval placement.",fontSize=10.sp,color=DeckColors.muted)
    }
}
internal data class AnalyticsBucket(val label: String, val start: LocalDate, val end: LocalDate, val next: HistoryPeriod?,
    val value: Double?, val punctuality: Double?, val comparisons: Int, val done: Double?)
internal fun analyticsRange(period: HistoryPeriod,anchor: LocalDate,data: CalendarDataset): Pair<LocalDate,LocalDate> = when(period) {
    HistoryPeriod.DAY->anchor to anchor
    HistoryPeriod.WEEK->anchor.minusDays((anchor.dayOfWeek.value-1).toLong()).let { it to it.plusDays(6) }
    HistoryPeriod.MONTH->anchor.withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) }
    HistoryPeriod.YEAR->anchor.withDayOfYear(1).let { it to it.plusYears(1).minusDays(1) }
    HistoryPeriod.ALL->(data.days.keys.minOrNull() ?: anchor) to (data.days.keys.maxOrNull() ?: anchor)
}
internal fun analyticsBuckets(period: HistoryPeriod,anchor: LocalDate,data: CalendarDataset): List<AnalyticsBucket> {
    val (start,end)=analyticsRange(period,anchor,data);val groups=mutableListOf<Triple<LocalDate,LocalDate,HistoryPeriod?>>()
    var cursor=start
    when(period) {
        HistoryPeriod.ALL-> { for(y in start.year..end.year) groups+=Triple(LocalDate.of(y,1,1),LocalDate.of(y,12,31),HistoryPeriod.YEAR) }
        HistoryPeriod.YEAR-> { for(m in 1..12) { val a=LocalDate.of(start.year,m,1);groups+=Triple(a,a.plusMonths(1).minusDays(1),HistoryPeriod.MONTH) } }
        HistoryPeriod.MONTH-> { while(cursor<=end) { groups+=Triple(cursor,cursor,HistoryPeriod.WEEK);cursor=cursor.plusDays(1) } }
        else-> { while(cursor<=end) { groups+=Triple(cursor,cursor,HistoryPeriod.DAY);cursor=cursor.plusDays(1) } }
    }
    return groups.map { (a,b,next) ->
        val days=data.days.values.filter { it.hasRecord && it.date>=a && it.date<=b };val p=days.flatMap { it.punctuality }
        val label=when(period) { HistoryPeriod.ALL->a.year.toString();HistoryPeriod.YEAR->a.month.toString().take(3);HistoryPeriod.MONTH->"${a.dayOfMonth}–${b.dayOfMonth}";else->a.dayOfWeek.toString().take(3)+" "+a.dayOfMonth }
        AnalyticsBucket(label,a,b,next,days.takeIf { it.isNotEmpty() }?.sumOf { it.focusMs }?.toDouble(),p.takeIf { it.isNotEmpty() }?.average(),p.size,days.takeIf { it.isNotEmpty() }?.sumOf { it.done }?.toDouble())
    }
}
@Composable internal fun AnalyticsChart(values: List<Pair<String,Double?>>, signed: Boolean = false, references: Boolean = false,
    count: Boolean = false, key: String = "chart", onBarSelect: ((Int) -> Unit)? = null, initialSelection: Int? = null, connected: Boolean = false) {
    var selected by remember(values,initialSelection) { mutableStateOf(initialSelection) }
    val connection=LocalAnalyticsConnection.current
    var chartBounds by remember { mutableStateOf(Rect.Zero) }
    val mode=LocalAchievementMotion.current
    val reveal=remember(key) { Animatable(if(mode==AchievementMotion.OFF) 1f else 0f) }
    LaunchedEffect(key,mode) { reveal.snapTo(if(mode==AchievementMotion.OFF) 1f else .15f);reveal.animateTo(1f,tween(if(mode==AchievementMotion.FULL) 400 else mode.duration)) }
    val max=(values.mapNotNull { it.second?.let { v -> kotlin.math.abs(v) } }.maxOrNull() ?: 1.0).coerceAtLeast(1.0)*1.12
    val color=if(DeckColors.palette.id=="monochrome") DeckColors.accent else Color(0xFF42DFFF);val height=(LocalDashboardCardHeight.current?.minus(85.dp) ?: 200.dp).coerceIn(160.dp,700.dp)
    fun format(v: Double?): String = if(v==null) "N/A · no record" else if(count) v.roundToInt().toString() else if(signed) punctualityLabel(v) else humanTime(v.toLong())
    val density=LocalDensity.current
    val safeTop=WindowInsets.safeDrawing.getTop(density);val safeBottom=WindowInsets.safeDrawing.getBottom(density)
    fun selectedBounds(i: Int): Rect {
        val left=with(density){48.dp.toPx()};val right=chartBounds.width-with(density){10.dp.toPx()}
        val top=with(density){12.dp.toPx()};val bottom=chartBounds.height-with(density){26.dp.toPx()}
        val slot=(right-left)/maxOf(1,values.size);val base=if(signed) (top+bottom)/2 else bottom
        val y=base-((values[i].second ?: 0.0)/max*(bottom-top)*(if(signed) .5 else 1.0)).toFloat()
        return Rect(chartBounds.left+left+(i+.19f)*slot,chartBounds.top+minOf(base,y),chartBounds.left+left+(i+.81f)*slot,chartBounds.top+maxOf(base,y)+1)
    }
    fun choose(i: Int) { selected=i;if(connected) connection?.begin(selectedBounds(i));onBarSelect?.invoke(i) }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(references) { val valid=values.mapNotNull { it.second };Text("Average ${format(valid.takeIf { it.isNotEmpty() }?.average())} · Best ${format(valid.maxOrNull())}",fontSize=10.sp,color=DeckColors.muted) }
        BoxWithConstraints {
            val chartWidth=if(values.size>31) maxOf(maxWidth,(values.size*14).dp) else maxWidth
            val scroll=rememberScrollState()
            Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                Canvas(Modifier.width(chartWidth).height(height).onGloballyPositioned { coordinates ->
                    val position=coordinates.positionInRoot();chartBounds=Rect(position,Size(coordinates.size.width.toFloat(),coordinates.size.height.toFloat()))
                    if(connected) connection?.area=chartBounds
                }.semantics {
                    contentDescription=values.joinToString { "${it.first}: ${format(it.second)}" }
                    customActions=values.mapIndexed { i,v -> CustomAccessibilityAction("${v.first}: ${format(v.second)}") { choose(i);true } }
                }.pointerInput(values,key) { detectTapGestures { p -> val i=((p.x-48.dp.toPx())/(size.width-58.dp.toPx())*values.size).toInt().coerceIn(0,maxOf(0,values.lastIndex));choose(i) } }) {
                    val left=48.dp.toPx();val right=size.width-10.dp.toPx();val top=12.dp.toPx();val bottom=size.height-26.dp.toPx();val plot=bottom-top
                    val base=if(signed) (top+bottom)/2 else bottom;val slot=(right-left)/maxOf(1,values.size)
                    fun y(v: Double)=base-(v/max*plot*(if(signed) .5f else 1f)*(if(connected && connection?.origin!=null) 1f else reveal.value)).toFloat()
                    for(i in 0..4) { val yy=top+plot*i/4;drawLine(color.copy(alpha=.18f),Offset(left,yy),Offset(right,yy),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),4.dp.toPx()))) }
                    drawLine(DeckColors.muted,Offset(left,base),Offset(right,base),1.dp.toPx())
                    if(references) {
                        val valid=values.mapNotNull { it.second }
                        if(valid.isNotEmpty()) listOf(valid.average() to color,valid.max() to DeckColors.accent).forEach { (v,c) ->
                            drawLine(c.copy(alpha=.65f),Offset(left,y(v)),Offset(right,y(v)),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx())))
                        }
                    }
                    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color=DeckColors.muted.toArgb();textSize=9.sp.toPx();textAlign=android.graphics.Paint.Align.CENTER }
                    var previous: Offset?=null;val path=Path()
                    values.forEachIndexed { i,(label,v) ->
                        val x=left+(i+.5f)*slot
                        if(v!=null) {
                            val yy=y(v);val tint=if(signed) if(v<=0) DeckColors.success else DeckColors.error else if(DeckColors.palette.id=="monochrome") DeckColors.accent else Color(0xFFFFB84C)
                            val phase=connection?.progress?.value ?: 1f;val source=connection?.origin
                            val morph=connected && source!=null && phase<1f && (connection?.reverseIndex==null || connection?.reverseIndex==i)
                            val sourceX=(source?.center?.x ?: 0f)-chartBounds.left;val sourceY=(source?.top ?: 0f)-chartBounds.top
                            val px=if(morph) blend(sourceX,x,phase) else x;val py=if(morph) blend(sourceY,yy,phase) else yy
                            val width=if(morph) blend(source!!.width,slot*.62f,phase) else slot*.62f
                            val bottomY=if(morph) blend(source!!.bottom-chartBounds.top,base,phase) else base
                            val alpha=if(connected && connection?.reverseIndex!=null && connection?.reverseIndex!=i) phase else 1f
                            drawRoundRect(tint.copy(alpha=alpha),Offset(px-width/2,minOf(bottomY,py)),Size(width,kotlin.math.abs(bottomY-py).coerceAtLeast(1f)),CornerRadius(2.dp.toPx()))
                            val p=Offset(px,py);previous?.let { q -> val mid=(q.x+p.x)/2;path.cubicTo(mid,q.y,mid,p.y,p.x,p.y) } ?: path.moveTo(p.x,p.y)
                            previous=p;drawCircle(color,3.dp.toPx(),p)
                            if(i==selected) drawLine(color.copy(alpha=.5f),Offset(x,top),Offset(x,bottom),1.dp.toPx())
                        } else { previous=null;drawIntoCanvas { it.nativeCanvas.drawText("–",x,base-4.dp.toPx(),paint) } }
                        if(i%maxOf(1,values.size/7)==0 || i==values.lastIndex) drawIntoCanvas { it.nativeCanvas.drawText(if(slot<18.dp.toPx()) label.take(1) else if(slot<34.dp.toPx()) label.take(3) else label,x,size.height-6.dp.toPx(),paint) }
                    }
                    drawPath(path,color.copy(alpha=.15f),style=Stroke(5.dp.toPx()));drawPath(path,color,style=Stroke(1.8.dp.toPx()))
                    paint.textAlign=android.graphics.Paint.Align.RIGHT
                    drawIntoCanvas { it.nativeCanvas.drawText(format(max),left-4.dp.toPx(),top+8.dp.toPx(),paint);it.nativeCanvas.drawText(if(signed) "0m" else "0",left-4.dp.toPx(),base,paint) }
                }
            }
            if(onBarSelect==null) selected?.let { i ->
                Popup(popupPositionProvider=remember(i,chartBounds,scroll.value) { object: PopupPositionProvider {
                    override fun calculatePosition(anchorBounds: IntRect,windowSize: IntSize,layoutDirection: LayoutDirection,popupContentSize: IntSize): IntOffset {
                        val b=selectedBounds(i)
                        val x=(anchorBounds.left+b.center.x-chartBounds.left-scroll.value-popupContentSize.width/2).roundToInt().coerceIn(8,maxOf(8,windowSize.width-popupContentSize.width-8))
                        val y=(anchorBounds.top+b.top-chartBounds.top+with(density){14.dp.toPx()}).roundToInt().coerceIn(safeTop+8,maxOf(safeTop+8,windowSize.height-safeBottom-popupContentSize.height-8))
                        return IntOffset(x,y)
                    }
                } },onDismissRequest={selected=null},properties=PopupProperties(focusable=true,clippingEnabled=true)) {
                    Surface(color=DeckColors.surface,border=BorderStroke(1.dp,DeckColors.accent),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                        Text("${values[i].first} · ${format(values[i].second)}",Modifier.widthIn(max=280.dp).padding(10.dp),fontSize=12.sp)
                    }
                }
            }
        }
    }
}
@Composable internal fun AnalyticsDonut(days: List<CalendarDay>) {
    val values=listOf(days.sumOf { it.focusMs },days.sumOf { it.breakMs },days.sumOf { it.pausedMs },days.sumOf { it.otherMs });val total=values.sum()
    val colors=if(DeckColors.palette.id=="monochrome") listOf(Color(0xFFEEEEEE),Color(0xFFAAAAAA),Color(0xFF777777),Color(0xFF444444)) else listOf(Color(0xFFFFB84C),Color(0xFF6E94B3),Color(0xFFAAB4BC),Color(0xFFB184FA))
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.widthIn(max=160.dp).fillMaxWidth().aspectRatio(1f),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { var angle=-90f;drawCircle(DeckColors.track,size.minDimension*.4f,style=Stroke(18.dp.toPx()));if(total>0) values.forEachIndexed { i,v -> val sweep=v.toFloat()/total*360;drawArc(colors[i],angle,sweep,false,Offset(16.dp.toPx(),16.dp.toPx()),Size(size.width-32.dp.toPx(),size.height-32.dp.toPx()),style=Stroke(18.dp.toPx()));angle+=sweep } }
            Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(humanTime(total),fontSize=16.sp);Text("recorded tracked",fontSize=9.sp,color=DeckColors.muted) }
        }
        values.forEachIndexed { i,v -> Text("${listOf("Focus","Breaks","Paused","Other recorded")[i]} ${humanTime(v)} · ${if(total>0) "${v*100/total}%" else "N/A"}",color=colors[i],fontSize=11.sp) }
    }
}

private data class AnalyticsLocation(val period: HistoryPeriod,val anchor: LocalDate,val activity: String?,val selection: Int?)
@Composable internal fun LegacyAnalyticsDashboard(data: CalendarDataset,reports: List<Report>,period: HistoryPeriod,anchor: LocalDate,
    setPeriod: (HistoryPeriod) -> Unit,setAnchor: (LocalDate) -> Unit,modifier: Modifier = Modifier,
    openReport: (Report) -> Unit,copy: (Report) -> Unit,delete: ((Report) -> Unit)?,
    header: @Composable () -> Unit,archiveActions: @Composable () -> Unit) {
    val layout=rememberDashboardLayout(if(period==HistoryPeriod.DAY) "analytics-day" else "analytics-aggregate")
    var stack by remember { mutableStateOf(emptyList<AnalyticsLocation>()) }
    var activity by remember { mutableStateOf<String?>(null) }
    val connection=remember { AnalyticsConnection() };val motion=LocalAchievementMotion.current
    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    var expectedDestination by remember { mutableStateOf<Pair<HistoryPeriod,LocalDate>?>(null) }
    var previousLocation by remember { mutableStateOf(period to anchor) }
    LaunchedEffect(period,anchor) {
        val current=period to anchor
        if(current!=previousLocation && current!=expectedDestination) { stack=emptyList();focusedIndex=null;activity=null }
        previousLocation=current;expectedDestination=null
    }
    LaunchedEffect(connection.revision,motion) {
        if(connection.origin!=null) { connection.progress.snapTo(if(motion==AchievementMotion.OFF) 1f else 0f);connection.progress.animateTo(1f,tween(if(motion==AchievementMotion.FULL) when(period) {HistoryPeriod.YEAR->450;HistoryPeriod.MONTH->400;HistoryPeriod.WEEK->350;HistoryPeriod.DAY->500;else->350} else motion.duration,easing=FastOutSlowInEasing)) }
    }
    var selectedReport by remember { mutableStateOf<String?>(null) }
    fun back() { val previous=stack.last();stack=stack.dropLast(1);connection.begin(connection.area,previous.selection);expectedDestination=previous.period to previous.anchor;focusedIndex=previous.selection;setPeriod(previous.period);setAnchor(previous.anchor);activity=previous.activity }
    BackHandler(stack.isNotEmpty()) { back() }
    val (start,end)=analyticsRange(period,anchor,data)
    val days=data.days.values.filter { it.hasRecord && it.date>=start && it.date<=end }
    val focus=days.sumOf { it.focusMs };val breaks=days.sumOf { it.breakMs };val tracked=days.sumOf { it.trackedMs };val done=days.sumOf { it.done }
    val comparisons=days.flatMap { it.punctuality };val mean=comparisons.takeIf { it.isNotEmpty() }?.average()
    val keys=days.flatMap { it.sessions }.toSet();val periodReports=reports.filter { it.key in keys }
    val buckets=analyticsBuckets(period,anchor,data)
    fun card(id: String,title: String,span: Int=12,height: Int=280,body: @Composable ColumnScope.() -> Unit) = DashboardCard(id,span,height) {
        RefCard(Modifier.fillMaxWidth()) { Text(title,fontSize=17.sp,fontWeight=FontWeight.Medium);body() }
    }
    val cards=mutableListOf<DashboardCard>()
    val wide=LocalConfiguration.current.screenWidthDp>=700
    val pairedSpan=if(wide) 4 else 6
    val metrics=listOf("Focus time" to humanTime(focus),"Sessions" to keys.size.toString(),"Blocks DONE" to done.toString(),
        "Break time" to humanTime(breaks),"Tracked time" to humanTime(tracked),"Focus / tracked" to (if(tracked>0) "${focus*100/tracked}%" else "N/A"), "Punctuality" to punctualityLabel(mean))
    cards+=DashboardCard("analytics-summary",12,180) {
        RefCard(Modifier.fillMaxWidth()) {
            DashboardMetricStrip(metrics.mapIndexed { i,(label,value) -> DashboardMetric(if(i==6) "punctuality-kpi" else "kpi-$i",label,value,
                listOf("target","layers","check","coffee","timer","chart-no-axes-column","clock")[i],
                if(i==6 && mean!=null && mean!=0.0) { if(mean<0) DeckColors.success else DeckColors.error } else null) })
        }
    }
    if(period==HistoryPeriod.DAY) {
        cards+=card("timeline","Daily Timeline",if(wide) 8 else 12,280) { DailyTimeline(data,anchor,selectedId=activity,select={activity=it}) }
        cards+=card("activity-detail","Session Detail",if(wide) 4 else 12,280) { data.activities[activity]?.let { a -> key(a.id) { EntryMotion { Column(verticalArrangement=Arrangement.spacedBy(6.dp)) { ActivityMetadata(a,true) } } } } ?: Text("Select an activity to inspect its measurements.",color=DeckColors.muted,fontSize=12.sp)
            val unscheduled=days.flatMap { it.unscheduled };if(unscheduled.isNotEmpty()) { Text("Unscheduled",fontSize=12.sp);unscheduled.forEach { id -> TextButton({activity=id}) { Text(data.activities[id]?.title ?: id,fontSize=12.sp) } } } }
    } else {
        cards+=card("focus","Focus Time per ${when(period) {HistoryPeriod.WEEK->"Day";HistoryPeriod.MONTH->"Calendar-week Intersection";HistoryPeriod.YEAR->"Month";else->"Year"}}",if(wide) 4 else 12,300) {
            AnalyticsChart(buckets.map { it.label to it.value },references=true,key="focus:${period}:${anchor}",initialSelection=focusedIndex,connected=true,onBarSelect={ i -> val b=buckets[i];b.next?.let { next -> stack=stack+AnalyticsLocation(period,anchor,activity,i);activity=null;focusedIndex=null;expectedDestination=next to b.start;setPeriod(next);setAnchor(b.start) } })
            Text("Tap a bar to open its period. Missing dates are N/A.",fontSize=10.sp,color=DeckColors.muted)
        }
        cards+=card("punctuality","Punctuality Trend",pairedSpan,280) { AnalyticsChart(buckets.map { it.label to it.punctuality },signed=true,key="punctuality:$period:$anchor");Text("Green = early · red = late · ${comparisons.size} comparisons",fontSize=10.sp,color=DeckColors.muted) }
    }
    val located=days.filter { it.hourComplete }
    cards+=card("hourly",if(period==HistoryPeriod.DAY) "Focus by Hour" else "Average Focus by Hour",pairedSpan,280) {
        AnalyticsChart((0..23).map { h -> String.format(java.util.Locale.ROOT,"%02d",h) to if(located.isEmpty()) null else located.sumOf { it.hours[h] }.toDouble()/(if(period==HistoryPeriod.DAY) 1 else located.size) })
        Text("${located.size} fully located recorded days. Missing/incomplete days excluded; repeated DST hours are combined.",fontSize=10.sp,color=DeckColors.muted)
    }
    cards+=card("distribution",if(period==HistoryPeriod.DAY) "Focus vs Breaks" else "Focus vs Tracked Time",6,340) { AnalyticsDonut(days) }
    if(period!=HistoryPeriod.DAY) {
        val prevAnchor=StudyHistory.shift(period,anchor,-1);val (a,b)=analyticsRange(period,prevAnchor,data);val prev=data.days.values.filter { it.hasRecord && it.date>=a && it.date<=b }
        cards+=card("summary","Period Summary",6,200) { Text("Focus ${humanTime(focus)} · tracked ${humanTime(tracked)}",fontSize=13.sp);Text("$done recorded DONE blocks",fontSize=12.sp)
            val old=prev.sumOf { it.focusMs };Text(if(period!=HistoryPeriod.ALL && prev.isNotEmpty() && old>0) String.format(java.util.Locale.ROOT,"%+.1f%% focus vs equivalent previous period",(focus-old).toDouble()/old*100) else "Previous comparison N/A",fontSize=12.sp,color=DeckColors.muted) }
        cards+=card("weekday","Weekday Consistency",6,280) { AnalyticsChart((1..7).map { weekday -> val ds=days.filter { it.date.dayOfWeek.value==weekday };DayOfWeek.of(weekday).toString().take(3) to ds.takeIf { it.isNotEmpty() }?.sumOf { it.focusMs }?.toDouble() }) }
        cards+=card("completion","Completion Trend",12,280) { AnalyticsChart(buckets.map { it.label to it.done },count=true,key="done:$period:$anchor") }
        cards+=card("top","Top Sessions / Projects",6,250) { periodReports.sortedByDescending { r -> days.sumOf { it.sessionFocus[r.key] ?: 0 } }.take(8).forEach { r -> TextButton({selectedReport=r.key}) { Text("${r.title} · ${humanTime(days.sumOf { it.sessionFocus[r.key] ?: 0 })} in this period",fontSize=12.sp) } };if(periodReports.isEmpty()) Text("No recorded sessions",fontSize=12.sp,color=DeckColors.muted) }
    }
    if(period!=HistoryPeriod.DAY && !wide) {
        val order=listOf("analytics-summary","focus","hourly","punctuality","summary","distribution","weekday","top","completion")
        cards.sortBy { order.indexOf(it.id).let { i -> if(i<0) 1000 else i } }
    }
    cards+=card("sessions",if(period==HistoryPeriod.DAY) "Sessions for This Day" else "Session History",12,300) {
        if(periodReports.isEmpty()) Text("No saved sessions in this period.",fontSize=12.sp,color=DeckColors.muted)
        periodReports.sortedByDescending { it.finishedAtMs }.forEach { r ->
            Row(Modifier.fillMaxWidth().clickable { selectedReport=r.key },verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical=8.dp)) { Text(r.title,fontSize=13.sp);Text("${timeLabel(r.startedAtMs,CalendarAnalytics.zone(r))} → ${timeLabel(r.finishedAtMs,CalendarAnalytics.zone(r))}",fontSize=10.sp,color=DeckColors.muted);Text("${humanTime(r.focusedMs)} focus · ${r.statusLabel}",fontSize=11.sp,color=DeckColors.muted) }
                PunctualityText(CalendarAnalytics.sessionPunctuality(r)?.toDouble())
            }
            HorizontalDivider(color=DeckColors.track)
        }
    }
    reports.find { it.key==selectedReport }?.let { r -> cards+=card("report","Session Report",12,350) {
        Text(r.title,fontSize=16.sp);Text(r.summary(),fontSize=11.sp,color=DeckColors.muted)
        Row { TextButton({openReport(r)}) { Text("FULL REPORT",fontSize=10.sp) };TextButton({copy(r)}) { Text("COPY",fontSize=10.sp) };if(delete!=null) TextButton({delete(r)}) { Text("DELETE",fontSize=10.sp,color=DeckColors.error) } }
    } }
    cards+=card("archive","History & Export",12,280) { archiveActions() }
    cards+=card("coverage","Recording coverage",12,160) { Text("Dates keep each report's recorded timezone; legacy missing zones use UTC. Unlocated totals stay on the finish date and are excluded from hourly charts. Unrecorded days are unknown, not zero.",fontSize=11.sp,color=DeckColors.muted);Text("Unlocated ${humanTime(days.sumOf { it.unlocatedMs })}",fontSize=11.sp,color=DeckColors.muted) }
    CompositionLocalProvider(LocalDashboardLayout provides layout,LocalAnalyticsConnection provides connection) {
        DashboardGrid(cards,layout,modifier,header={
            header();LayoutControls(layout)
            if(stack.isNotEmpty()) TextButton({back()}) { Text("↶ BACK TO PREVIOUS PERIOD",fontSize=11.sp) }
        })
    }
}
