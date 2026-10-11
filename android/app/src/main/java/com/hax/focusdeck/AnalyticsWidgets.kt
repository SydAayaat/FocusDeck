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
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import kotlin.math.*

/** One amber focus series. Null bins remain unknown; no break/line series or averaged partial days. */
@Composable internal fun NativeFocusBars(values: List<Pair<String,Double?>>,minutes: Boolean=false,key: String="focus",
    benchmark: Double?=null,overtime: List<Double> = emptyList(),pauses: List<Double> = emptyList(),comparison: List<Double?> = emptyList(),onInspect: (String,Double?)->Unit,openPeriod: ((Int)->Unit)?=null) {
    val max=(values.mapNotNull { it.second }+listOfNotNull(benchmark)).maxOrNull() ?: 0.0
    val unit=if(minutes) 60000.0 else 3600000.0
    val nice=listOf(1.0,2.0,4.0,6.0,12.0,24.0,48.0,60.0,120.0,240.0,480.0,960.0)
    val ceiling=nice.firstOrNull { it>=max/unit }?.times(unit) ?: ceil(max/unit/100)*100*unit
    val scale=ceiling.coerceAtLeast(unit)
    val density=LocalDensity.current
    var selected by remember(key) { mutableStateOf<Int?>(null) }
    var anchor by remember(key) { mutableStateOf(Offset.Zero) }
    val actions=values.mapIndexed { i,(label,value)->CustomAccessibilityAction("$label: ${dashboardDuration(value?.toLong())}") { selected=i;true } }
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(if(values.size==24) 68.dp else 84.dp).testTag("focus-bars-$key")
            .semantics { contentDescription="Focus duration chart, ${if(minutes) "minutes" else "hours"}; missing data N/A";customActions=actions }
            .pointerInput(values,key) { detectTapGestures { at->
                if(values.isNotEmpty()) {
                    val left=with(density){28.dp.toPx()};val width=size.width-left-with(density) { if(benchmark!=null) 52.dp.toPx()*fontScale else 0f }
                    val i=((at.x-left)/width*values.size).toInt().coerceIn(0,values.lastIndex)
                    selected=if(selected==i) null else i;anchor=at
                }
            } }.drawWithCache {
                val left=28.dp.toPx();val top=8.dp.toPx();val bottom=size.height-20.dp.toPx()
                val plot=(bottom-top).coerceAtLeast(1f);val width=(size.width-left-(if(benchmark!=null) 52.dp.toPx()*fontScale else 0f)).coerceAtLeast(1f)
                val slot=width/values.size.coerceAtLeast(1)
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=DeckColors.muted.toArgb();textSize=9.sp.toPx() }
                val barPath=android.graphics.Path()
                val bars=values.mapIndexed { i,(_,value)->
                    val height=((value ?: 0.0)/scale*plot).toFloat().coerceIn(0f,plot)
                    val w=(slot*.62f).coerceAtMost(18.dp.toPx());val x=left+slot*(i+.5f)-w/2
                    if(height>0) barPath.addRoundRect(x,bottom-height,x+w,bottom,1.3.dp.toPx(),1.3.dp.toPx(),android.graphics.Path.Direction.CW)
                    Offset(x,bottom-height) to Size(w,height)
                }
                val barShadow=OmssShadow(barPath,size.width,size.height,1.dp.toPx(),2.dp.toPx(),3.dp.toPx(),.4f)
                val cyan=OmssColors.analytical
                val grid=OmssColors.material(OmssColors.separator)
                val amber=DeckColors.accent
                val dash=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),2.dp.toPx()))
                onDrawBehind {
                    for(tick in 0..2) {
                        val y=top+plot*(1-tick/2f)
                        drawLine(grid,Offset(left,y),Offset(size.width,y),.6.dp.toPx())
                        drawIntoCanvas { paint.textAlign=android.graphics.Paint.Align.RIGHT;it.nativeCanvas.drawText("${(scale/unit*tick/2).let { n->if(n%1.0==0.0) n.toInt().toString() else "%.1f".format(java.util.Locale.ROOT,n) }}${if(minutes) "m" else "h"}",left-4.dp.toPx(),y+3.dp.toPx(),paint) }
                    }
                    values.indices.forEach { i->if(values.size<=12 || i%2==0) drawLine(grid.copy(alpha=.5f),Offset(left+slot*i,top),Offset(left+slot*i,bottom),.5.dp.toPx()) }
                    // Reference first, then shadow and opaque bars: bars occlude the dotted line.
                    val referenceY=benchmark?.takeIf { it>=0 && it<=scale }?.let { bottom-(it/scale*plot).toFloat() }
                    if(referenceY!=null) drawLine(cyan,Offset(left,referenceY),Offset(size.width,referenceY),.8.dp.toPx(),pathEffect=dash)
                    drawIntoCanvas { barShadow.draw(it.nativeCanvas) }
                    bars.forEachIndexed { i,(at,bar)->
                        if(bar.height>0) drawRoundRect(amber,at,bar,CornerRadius(1.3.dp.toPx()))
                        if(values[i].second==null) drawLine(DeckColors.muted.copy(alpha=.35f),Offset(at.x,bottom-2.dp.toPx()),Offset(at.x+bar.width,bottom-2.dp.toPx()),1.dp.toPx())
                    }
                    values.forEachIndexed { i,(label,_)->
                        if(values.size<=12 || i%2==0) drawIntoCanvas {
                            paint.textAlign=android.graphics.Paint.Align.CENTER
                            it.nativeCanvas.drawText(label,left+slot*(i+.5f),size.height-3.dp.toPx(),paint)
                        }
                    }
                    if(referenceY!=null) drawIntoCanvas {
                        paint.color=cyan.toArgb();paint.textAlign=android.graphics.Paint.Align.RIGHT
                        it.nativeCanvas.drawText(referenceDuration(benchmark!!),size.width,referenceY-4.dp.toPx(),paint)
                        paint.color=DeckColors.muted.toArgb()
                    }
                }
            })
        selected?.let { i-> values.getOrNull(i)?.let { (label,value)->
            AnchoredWidgetPopup(anchor,{selected=null}) {
                Text(label,fontWeight=FontWeight.Bold,fontSize=13.sp)
                Text(dashboardDuration(value?.toLong()),fontSize=12.sp)
                if((overtime.getOrNull(i) ?: 0.0)>0) Text("Included overtime ${dashboardDuration(overtime[i].toLong())}",fontSize=11.sp,color=DeckColors.error)
                if((pauses.getOrNull(i) ?: 0.0)>0) Text("Non-focus pauses ${dashboardDuration(pauses[i].toLong())}",fontSize=11.sp,color=OmssColors.analytical)
                comparison.getOrNull(i)?.let { Text("Eligible day average ${dashboardDuration(it.toLong())}",fontSize=11.sp,color=OmssColors.analytical) }
                if(openPeriod!=null) TextButton({selected=null;openPeriod(i)}) { Text("Open period",fontSize=12.sp) }
                TextButton({selected=null;onInspect(label,value)}) { Text("Details",fontSize=11.sp) }
            }
        } }
    }
}
@Composable internal fun AnchoredWidgetPopup(anchor: Offset,dismiss: ()->Unit,content: @Composable ColumnScope.()->Unit) {
    DashboardPopup(dismiss,anchorPoint=anchor) {
        FDCard(Modifier.widthIn(min=130.dp,max=280.dp),content=content)
    }
}
@Composable internal fun NativeTimeSplit(e: DashboardAnalyticsEvidence,inspect: (String,String)->Unit) {
    val amounts=listOf(e.focus,e.breaks,(e.tracked-e.focus-e.breaks).coerceAtLeast(0))
    val labels=listOf("Focus","Breaks","Paused / other recorded")
    val colors=listOf(DeckColors.accent,OmssColors.analytical,DeckColors.muted)
    var selected by remember(e) { mutableStateOf<Int?>(null) }
    var anchor by remember(e) { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxWidth().height(82.dp),contentAlignment=Alignment.Center) {
        Text(dashboardDuration(e.tracked.takeIf { e.days.isNotEmpty() }),Modifier.align(Alignment.TopEnd),fontSize=11.sp,color=DeckColors.muted)
        OmssSegmentedGauge(amounts.mapIndexed { i,amount->(if(e.tracked>0) amount.toFloat()/e.tracked else 0f) to colors[i] },
            Modifier.size(82.dp).semantics {
                contentDescription=if(e.tracked>0) "Time Split, ${(e.focus*100/e.tracked)} percent focus" else "Time Split, no measured data"
                customActions=labels.indices.map { i->CustomAccessibilityAction("Inspect ${labels[i]}, ${dashboardDuration(amounts[i])}") {selected=i;true} }
            }.pointerInput(amounts) { detectTapGestures { at ->
                if(e.tracked>0) {
                    val angle=((atan2(at.y-size.height/2,at.x-size.width/2)*180/PI+450)%360).toFloat()
                    var boundary=0f;val i=amounts.indexOfFirst { boundary+=it.toFloat()/e.tracked*360;angle<boundary }.coerceAtLeast(0)
                    selected=if(selected==i) null else i;anchor=at
                }
            } }) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(e.focusShare?.let { "${(it*100).roundToInt()}%" } ?: "N/A",fontSize=21.sp,style=OmssTypography.metric);Text("Focus",fontSize=11.sp,color=DeckColors.muted) }
        }
        Text("›",Modifier.align(Alignment.CenterEnd).clickable { inspect("Time Split",amounts.mapIndexed { i,ms->"${labels[i]}: ${dashboardDuration(ms)}" }.joinToString("\n")) },fontSize=22.sp,color=DeckColors.muted)
        selected?.let { i->AnchoredWidgetPopup(anchor,{selected=null}) {
            Text(labels[i],fontSize=13.sp,fontWeight=FontWeight.Bold,color=colors[i]);Text("${dashboardDuration(amounts[i])} · ${if(e.tracked>0) amounts[i]*100/e.tracked else 0}%",fontSize=12.sp)
        } }
    }
}
@Composable internal fun TimeEstimationCard(e: DashboardAnalyticsEvidence) {
    val estimate=e.estimation
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { BundledCardIcon("target",Modifier.size(23.dp),DeckColors.accent);Text("Accuracy",fontSize=10.sp,color=DeckColors.muted);Text(estimate?.accuracy?.let { "${it.roundToInt()}%" } ?: "N/A",fontSize=21.sp,fontWeight=FontWeight.Bold) }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { BundledCardIcon("clock",Modifier.size(23.dp),DeckColors.accent);Text("Duration deviation",fontSize=10.sp,color=DeckColors.muted);Text(estimate?.let { (if(it.delta>0) "+" else if(it.delta<0) "−" else "")+dashboardDuration(abs(it.delta)) } ?: "N/A",fontSize=17.sp,color=if((estimate?.delta ?: 0)>0) DeckColors.error else DeckColors.success) }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { BundledCardIcon("chart-no-axes-column",Modifier.size(23.dp),DeckColors.accent);Text("Est. vs. actual",fontSize=10.sp,color=DeckColors.muted);Text(estimate?.let { "${dashboardDuration(it.planned)} → ${dashboardDuration(it.actual)}" } ?: "N/A",fontSize=11.sp) }
    }
    var calibration by remember { mutableStateOf(false) }
    TextButton({calibration=!calibration},Modifier.height(28.dp),contentPadding=PaddingValues(0.dp)) { Text(if(calibration) "Hide project calibration" else "By project type  ›",fontSize=11.sp) }
    if(calibration) {
    HorizontalDivider(color=OmssColors.separator)
    Text("By project type",fontSize=12.sp,fontWeight=FontWeight.Bold)
    if(e.estimationGroups.isEmpty()) Text("No comparable timed DONE blocks",fontSize=12.sp,color=DeckColors.muted)
    e.estimationGroups.forEach { (category,est)->
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) { Text(category.replaceFirstChar { it.uppercase() },Modifier.weight(1f),fontSize=11.sp);Text(est.accuracy?.let { "${it.roundToInt()}%" } ?: "N/A",fontSize=11.sp);Spacer(Modifier.width(8.dp));Text((if(est.delta>0) "+" else if(est.delta<0) "−" else "")+dashboardDuration(abs(est.delta)),fontSize=11.sp,color=if(est.delta>0) DeckColors.error else DeckColors.success) }
            FDTrack(est.accuracy?.toFloat()?.div(100),color=if(est.delta>0) DeckColors.accent else DeckColors.secondary)
        }
    }
}
}
@Composable internal fun TelemetryInsights(e: DashboardAnalyticsEvidence,period: HistoryPeriod) {
    val summary=buildAnnotatedString {
        if(e.days.isEmpty()) append("No recorded measurements for this ${period.label.lowercase()} period. Plan Execution Score N/A.")
        else {
            withStyle(SpanStyle(color=DeckColors.accent)) { append(dashboardDuration(e.focus)) }
            append(" measured focus across ${e.days.size} recorded ${if(e.days.size==1) "day" else "days"}. ${e.done} blocks marked DONE. ${e.punctual.size} scheduled start comparisons. ")
            append("Execution score ")
            withStyle(SpanStyle(color=DeckColors.accent)) { append(e.score?.let { "${it.roundToInt()}/100" } ?: "N/A") }
            append(".")
        }
    }
    Text(summary,fontSize=12.sp,color=OmssColors.text)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OmssMiniCard(Modifier.weight(1f).fillMaxHeight()) {
            Text("KEY STRENGTHS",fontSize=10.sp,color=OmssColors.analytical)
            Text((if(e.days.isEmpty()) "• DONE evidence N/A" else "• ${e.done} DONE blocks")+"\n"+
                (if(e.punctual.isEmpty()) "• Start evidence N/A" else "• ${e.punctual.count { it<=0 }} starts on time or early"),fontSize=11.sp)
        }
        OmssMiniCard(Modifier.weight(1f).fillMaxHeight()) {
            Text("FRICTION POINTS",fontSize=10.sp,color=DeckColors.accent)
            Text((if(e.punctual.isEmpty()) "• Late start evidence N/A" else "• ${e.punctual.count { it>0 }} late starts")+"\n"+
                "• ${dashboardDuration(e.days.takeIf { it.isNotEmpty() }?.sumOf { it.unlocatedMs })} unlocated evidence",fontSize=11.sp)
        }
    }
}
