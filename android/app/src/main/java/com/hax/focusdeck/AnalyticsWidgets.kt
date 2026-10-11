package com.hax.focusdeck

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.*

/** One amber focus series. Null bins remain unknown; no break/line series or averaged partial days. */
@Composable internal fun NativeFocusBars(values: List<Pair<String,Double?>>,minutes: Boolean=false,key: String="focus",
    benchmark: Double?=null,overtime: List<Double> = emptyList(),pauses: List<Double> = emptyList(),comparison: List<Double?> = emptyList(),onInspect: (String,Double?)->Unit,openPeriod: ((Int)->Unit)?=null) {
    val max=(values.mapNotNull { it.second }+comparison.filterNotNull()).maxOrNull() ?: 0.0
    val unit=if(minutes) 60000.0 else 3600000.0
    val nice=listOf(1.0,2.0,4.0,6.0,12.0,24.0,48.0,60.0,120.0,240.0,480.0,960.0)
    val ceiling=nice.firstOrNull { it>=max/unit }?.times(unit) ?: ceil(max/unit/100)*100*unit
    val scale=ceiling.coerceAtLeast(unit)
    val density=LocalDensity.current
    var selected by remember(key) { mutableStateOf<Int?>(null) }
    var anchor by remember(key) { mutableStateOf(Offset.Zero) }
    val actions=values.mapIndexed { i,(label,value)->CustomAccessibilityAction("$label: ${dashboardDuration(value?.toLong())}") { selected=i;true } }
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(if(values.size==24) 96.dp else 122.dp).testTag("focus-bars-$key")
            .semantics { contentDescription="Focus duration chart, ${if(minutes) "minutes" else "hours"}; missing data N/A";customActions=actions }
            .pointerInput(values,key) { detectTapGestures { at->
                val left=with(density){28.dp.toPx()};val width=size.width-left
                val i=((at.x-left)/width*values.size).toInt().coerceIn(0,values.lastIndex)
                selected=if(selected==i) null else i;anchor=at
            } }) {
            val left=28.dp.toPx();val top=4.dp.toPx();val bottom=size.height-20.dp.toPx();val plot=bottom-top;val width=size.width-left
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=DeckColors.muted.toArgb();textSize=10.sp.toPx() }
            for(tick in 0..2) {
                val y=top+plot*(1-tick/2f)
                drawLine(DeckColors.track.copy(alpha=.55f),Offset(left,y),Offset(size.width,y),.6.dp.toPx())
                drawIntoCanvas { paint.textAlign=android.graphics.Paint.Align.RIGHT;it.nativeCanvas.drawText("${(scale/unit*tick/2).let { n->if(n%1.0==0.0) n.toInt().toString() else "%.1f".format(java.util.Locale.ROOT,n) }}${if(minutes) "m" else "h"}",left-4.dp.toPx(),y+3.dp.toPx(),paint) }
            }
            if(values.isNotEmpty()) {
                val slot=width/values.size
                values.forEachIndexed { i,(label,value)->
                    val height=((value ?: 0.0)/scale*plot).toFloat().coerceIn(0f,plot)
                    val w=(slot*.55f).coerceAtMost(18.dp.toPx());val x=left+slot*(i+.5f)-w/2
                    if(height>0) drawRoundRect(DeckColors.accent,Offset(x,bottom-height),Size(w,height),CornerRadius(1.3.dp.toPx()))
                    val ot=((overtime.getOrNull(i) ?: 0.0)/scale*plot).toFloat().coerceIn(0f,height)
                    if(ot>0) drawRect(DeckColors.error,Offset(x,bottom-height),Size(w,ot))
                    if((pauses.getOrNull(i) ?: 0.0)>0) drawLine(DeckColors.secondary,Offset(x,bottom-height-3.dp.toPx()),Offset(x+w,bottom-height-3.dp.toPx()),2.dp.toPx())
                    comparison.getOrNull(i)?.let { average->
                        val h=(average/scale*plot).toFloat().coerceIn(0f,plot)
                        drawLine(DeckColors.secondary,Offset(x+w+2.dp.toPx(),bottom),Offset(x+w+2.dp.toPx(),bottom-h),1.dp.toPx())
                    }
                    if(value==null) drawLine(DeckColors.muted.copy(alpha=.35f),Offset(x,bottom-2.dp.toPx()),Offset(x+w,bottom-2.dp.toPx()),1.dp.toPx())
                    if(values.size<=12 || i%4==0 || i==values.lastIndex) drawIntoCanvas {
                        paint.textAlign=android.graphics.Paint.Align.CENTER;it.nativeCanvas.drawText(label,left+slot*(i+.5f),size.height-3.dp.toPx(),paint)
                    }
                }
            }
            if(benchmark!=null && benchmark>0 && benchmark<=scale) {
                val y=bottom-(benchmark/scale*plot).toFloat()
                drawLine(DeckColors.secondary,Offset(left,y),Offset(size.width,y),.8.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),2.dp.toPx())))
            }
        }
        selected?.let { i-> values.getOrNull(i)?.let { (label,value)->
            AnchoredWidgetPopup(anchor,{selected=null}) {
                Text(label,fontWeight=FontWeight.Bold,fontSize=13.sp)
                Text(dashboardDuration(value?.toLong()),fontSize=12.sp)
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
    val colors=listOf(DeckColors.accent,DeckColors.secondary,DeckColors.muted)
    var selected by remember(e) { mutableStateOf<Int?>(null) }
    var anchor by remember(e) { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxWidth().height(106.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(98.dp).semantics {
            contentDescription=if(e.tracked>0) "Time Split, ${(e.focus*100/e.tracked)} percent focus" else "Time Split, no measured data"
            customActions=labels.indices.map { i->CustomAccessibilityAction("Inspect ${labels[i]}, ${dashboardDuration(amounts[i])}") {selected=i;true} }
        }.pointerInput(amounts) { detectTapGestures { at ->
            if(e.tracked>0) {
                val angle=((atan2(at.y-size.height/2,at.x-size.width/2)*180/PI+450)%360).toFloat()
                var boundary=0f;val i=amounts.indexOfFirst { boundary+=it.toFloat()/e.tracked*360;angle<boundary }.coerceAtLeast(0)
                selected=if(selected==i) null else i;anchor=at
            }
        } }) {
            val stroke=12.dp.toPx();val r=(size.minDimension-stroke)/2
            drawCircle(DeckColors.track,r,style=Stroke(stroke));var start=-90f
            if(e.tracked>0) amounts.forEachIndexed { i,value->val sweep=value.toFloat()/e.tracked*360
                if(sweep>0) drawArc(colors[i],start,sweep,false,Offset(center.x-r,center.y-r),Size(r*2,r*2),style=Stroke(stroke));start+=sweep }
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(e.focusShare?.let { "${(it*100).roundToInt()}%" } ?: "N/A",fontSize=23.sp,fontWeight=FontWeight.Bold);Text("Focus",fontSize=11.sp,color=DeckColors.muted) }
        selected?.let { i->AnchoredWidgetPopup(anchor,{selected=null}) {
            Text(labels[i],fontSize=13.sp,fontWeight=FontWeight.Bold,color=colors[i]);Text("${dashboardDuration(amounts[i])} · ${if(e.tracked>0) amounts[i]*100/e.tracked else 0}%",fontSize=12.sp)
        } }
    }
}
@Composable internal fun TimeEstimationCard(e: DashboardAnalyticsEvidence) {
    val estimate=e.estimation
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text("Accuracy",fontSize=10.sp,color=DeckColors.muted);Text(estimate?.accuracy?.let { "${it.roundToInt()}%" } ?: "N/A",fontSize=21.sp,fontWeight=FontWeight.Bold) }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text("Duration deviation",fontSize=10.sp,color=DeckColors.muted);Text(estimate?.let { (if(it.delta>0) "+" else if(it.delta<0) "−" else "")+dashboardDuration(abs(it.delta)) } ?: "N/A",fontSize=17.sp,color=if((estimate?.delta ?: 0)>0) DeckColors.error else DeckColors.success) }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text("Est. vs. actual",fontSize=10.sp,color=DeckColors.muted);Text(estimate?.let { "${dashboardDuration(it.planned)} → ${dashboardDuration(it.actual)}" } ?: "N/A",fontSize=11.sp) }
    }
    HorizontalDivider(color=DeckColors.track)
    Text("By project type",fontSize=12.sp,fontWeight=FontWeight.Bold)
    if(e.estimationGroups.isEmpty()) Text("No comparable timed DONE blocks",fontSize=12.sp,color=DeckColors.muted)
    e.estimationGroups.forEach { (category,est)->
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) { Text(category.replaceFirstChar { it.uppercase() },Modifier.weight(1f),fontSize=11.sp);Text(est.accuracy?.let { "${it.roundToInt()}%" } ?: "N/A",fontSize=11.sp);Spacer(Modifier.width(8.dp));Text((if(est.delta>0) "+" else if(est.delta<0) "−" else "")+dashboardDuration(abs(est.delta)),fontSize=11.sp,color=if(est.delta>0) DeckColors.error else DeckColors.success) }
            FDTrack(est.accuracy?.toFloat()?.div(100),color=if(est.delta>0) DeckColors.accent else DeckColors.secondary)
        }
    }
}
@Composable internal fun TelemetryInsights(e: DashboardAnalyticsEvidence,period: HistoryPeriod) {
    Text("${dashboardDuration(e.focus)} measured focus across ${e.days.size} recorded ${if(e.days.size==1) "day" else "days"}. ${e.done} blocks marked DONE. ${e.punctual.size} scheduled start comparisons${e.score?.let { "; execution score ${it.roundToInt()}/100" } ?: "; score N/A"}.",fontSize=12.sp,color=DeckColors.muted)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Surface(Modifier.weight(1f),color=DashboardInset,shape=RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { Text("KEY ACHIEVEMENTS",fontSize=10.sp,color=DeckColors.success);Text("• ${e.done} DONE blocks\n• ${e.punctual.count { it<=0 }} starts on time or early",fontSize=11.sp) }
        }
        Surface(Modifier.weight(1f),color=DashboardInset,shape=RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { Text("EVIDENCE TO REVIEW",fontSize=10.sp,color=DeckColors.accent);Text("• ${e.punctual.count { it>0 }} late starts\n• ${dashboardDuration(e.days.sumOf { it.unlocatedMs })} unlocated evidence",fontSize=11.sp) }
        }
    }
}
