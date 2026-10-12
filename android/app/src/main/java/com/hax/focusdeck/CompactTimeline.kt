package com.hax.focusdeck

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal fun startDeltaDescription(delta: Long?): String = when {
    delta == null -> "Start comparison N/A"
    delta == 0L -> "On time"
    delta > 0 -> "${dashboardDuration(delta)} late"
    else -> "${dashboardDuration(-delta)} early"
}
/** Compact overview only. Expanded analytics retains lanes and connected drill motion. */
@Composable internal fun CompactDailyTimeline(data: CalendarDataset, date: LocalDate, now: Long?, expand: (() -> Unit)?,
    initialSelection: String? = null, displayZone: ZoneId? = null) {
    val navigation=LocalTimelineNavigation.current
    var trackBounds: Rect?=null
    val day=data.days[date]
    val zone=displayZone ?: day?.zones?.firstOrNull() ?: ZoneId.systemDefault()
    val (start,end)=CalendarAnalytics.bounds(date,zone)
    val segments=remember(data,date,zone) { day?.segments.orEmpty().filter { it.zone==zone }.sortedBy { it.start } }
    var selected by remember(date,zone) { mutableStateOf(initialSelection) }
    var anchor by remember(date,zone) { mutableStateOf(Offset.Zero) }
    val density=LocalDensity.current
    val safeTop=WindowInsets.safeDrawing.getTop(density)
    val safeBottom=WindowInsets.safeDrawing.getBottom(density)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            ActivityGlyph("clock",Modifier.size(22.dp),DeckColors.accent)
            Spacer(Modifier.width(8.dp))
            Text("Daily Timeline",Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.Bold)
            TextButton(onClick={navigation?.origin=trackBounds;expand?.invoke()},enabled=expand!=null,contentPadding=PaddingValues(0.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("d MMM"))+"  ›",fontSize=11.sp,color=DeckColors.muted)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width=maxWidth
            LaunchedEffect(initialSelection,width) {
                segments.firstOrNull { it.activityId==initialSelection }?.let { s -> anchor=Offset(with(density){(width*(s.left+s.right)/2).toPx()},0f) }
            }
            fun choose(s: CalendarSegment) { selected=if(selected==s.activityId) null else s.activityId;anchor=Offset(with(density){(width*(s.left+s.right)/2).toPx()},0f) }
            val actions=segments.distinctBy { it.activityId }.map { s -> CustomAccessibilityAction("Inspect ${data.activities[s.activityId]?.title ?: "activity"}") { choose(s);true } }
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                Canvas(Modifier.fillMaxWidth().height(48.dp).testTag("home-timeline-tracks").onGloballyPositioned { trackBounds=it.boundsInRoot() }.drawWithCache {
                    val path=android.graphics.Path()
                    repeat(2) { i ->
                        val y=10.dp.toPx()+i*13.dp.toPx()
                        path.addRoundRect(0f,y,size.width,y+5.dp.toPx(),2.dp.toPx(),2.dp.toPx(),android.graphics.Path.Direction.CW)
                    }
                    val shadow=OmssShadow(path,size.width,size.height,1.dp.toPx(),2.dp.toPx(),3.dp.toPx(),.4f)
                    onDrawWithContent { drawIntoCanvas { shadow.draw(it.nativeCanvas) };drawContent() }
                }
                    .semantics { contentDescription="Daily timeline, planned upper track and actual lower track, ${zone.id}";customActions=actions
                        onClick("Inspect first activity") { segments.firstOrNull()?.let(::choose);segments.isNotEmpty() } }
                    .pointerInput(segments) { detectTapGestures { at ->
                        val x=(at.x/size.width).coerceIn(0f,1f)
                        val track=if(at.y<with(density){18.dp.toPx()}) "planned" else "actual"
                        val candidates=segments.filter { it.track==track }
                        val hits=candidates.filter { x>=it.left-.015f && x<=it.right+.015f }
                        val s=hits.minByOrNull { kotlin.math.abs(x-(it.left+it.right)/2) }
                        if(s!=null) choose(s) else selected=null
                    } }) {
                    val line=5.dp.toPx()
                    for((i,track) in listOf("planned","actual").withIndex()) {
                        val y=10.dp.toPx()+i*13.dp.toPx()
                        drawRoundRect(DeckColors.track,Offset(0f,y),Size(size.width,line),CornerRadius(2.dp.toPx()))
                        segments.filter { it.track==track }.forEach { s ->
                            val tint=if(track=="planned") DeckColors.accent else if(s.kind=="focus_ms") DeckColors.secondary else DeckColors.muted
                            drawRoundRect(tint,Offset(size.width*s.left,y),Size((size.width*(s.right-s.left)).coerceAtLeast(1.dp.toPx()),line),CornerRadius(2.dp.toPx()))
                        }
                    }
                    if(now!=null && now in start until end) {
                        val x=size.width*(now-start).toFloat()/(end-start)
                        drawLine(Color.White,Offset(x,6.dp.toPx()),Offset(x,31.dp.toPx()),1.dp.toPx())
                        drawCircle(Color.White,3.dp.toPx(),Offset(x,6.dp.toPx()))
                    }
                    for(h in 0..24) {
                        val tick=if(h==24) 1f else (date.atTime(h,0).atZone(zone).toInstant().toEpochMilli()-start).toFloat()/(end-start)
                        drawLine(DeckColors.track,Offset(size.width*tick,34.dp.toPx()),Offset(size.width*tick,38.dp.toPx()),.7.dp.toPx())
                    }
                }
                Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter),horizontalArrangement=Arrangement.SpaceBetween) {
                    listOf("00:00","06:00","12:00","18:00","24:00").forEach { Text(it,fontSize=10.sp,color=DeckColors.muted) }
                }
                selected?.let { id -> data.activities[id]?.let { a ->
                    DashboardPopup(dismiss={selected=null},anchorPoint=anchor) {
                        OmssSurface(Modifier.widthIn(max=280.dp).testTag("home-task-popup")
                            .border(1.dp,DeckColors.accent,OmssCalloutShape),nested=true,shape=OmssCalloutShape) {
                            Column(Modifier.padding(start=12.dp,end=12.dp,top=12.dp,bottom=19.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(a.title,fontSize=13.sp,fontWeight=FontWeight.Bold)
                                val plannedZone=a.schedule?.timezone?.let(ZoneId::of) ?: a.zone
                                Text("Planned: ${compactClock(a.schedule?.start,plannedZone)} – ${compactClock(a.schedule?.end,plannedZone)}",fontSize=12.sp,color=DeckColors.muted)
                                Text("Actual: ${compactClock(a.start,a.zone)} – ${compactClock(a.end,a.zone)}",fontSize=12.sp,color=DeckColors.muted)
                                Text("Focus ${dashboardDuration(a.focusMs)}",fontSize=11.sp,color=DeckColors.muted)
                                Text(startDeltaDescription(a.punctuality),fontSize=12.sp,color=when { a.punctuality==null || a.punctuality==0L->DeckColors.muted;a.punctuality!!<0->DeckColors.success;else->DeckColors.error })
                            }
                        }
                    }
                } }
            }
        }
        if(segments.isEmpty()) Text("No located activity or absolute schedule",color=DeckColors.muted,fontSize=11.sp)
        if(day?.unlocatedMs?.let { it>0 }==true) Text("${dashboardDuration(day.unlocatedMs)} without reliable time placement",fontSize=10.sp,color=DeckColors.muted)
    }
}
