package com.hax.focusdeck

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.*

/** Rows remain grid items; their two original card IDs retain shared resize/persistence handles.
 * Normalization keeps saved widths within the viewport, including older full-span score sizes.
 */
@Composable internal fun AnalyticsPairedRow(layout: DashboardLayoutState,leftId: String,rightId: String,
    leftShare: Float,height: Int,left: @Composable (Modifier)->Unit,right: @Composable (Modifier)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width=maxWidth
        val savedLeft=layout.value.cards[leftId]?.span?.toFloat() ?: leftShare*12
        val savedRight=layout.value.cards[rightId]?.span?.toFloat() ?: (1-leftShare)*12
        val share=savedLeft/(savedLeft+savedRight)
        val available=width-8.dp
        val minimumLeft=if(leftShare>.6f) 252.dp else 170.dp
        val minimumRight=if(leftShare>.6f) 100.dp else 170.dp
        val paired=available>=minimumLeft+minimumRight && LocalDensity.current.fontScale<=1.25f
        val leftWidth=if(paired) (available*share).coerceIn(minimumLeft,available-minimumRight) else width
        fun defaults(fraction: Float)=CardDimensions((fraction*12).roundToInt().coerceIn(1,12),height,false)
        val leftBody: @Composable (Modifier)->Unit = { modifier ->
            ResizableCardFrame(leftId,layout,defaults(leftShare),width,modifier) { left(Modifier.fillMaxWidth().then(if(paired) Modifier.fillMaxHeight() else Modifier)) }
        }
        val rightBody: @Composable (Modifier)->Unit = { modifier ->
            ResizableCardFrame(rightId,layout,defaults(1-leftShare),width,modifier) { right(Modifier.fillMaxWidth().then(if(paired) Modifier.fillMaxHeight() else Modifier)) }
        }
        val rowHeight=maxOf(layout.value.cards[leftId]?.takeIf { it.heightSized }?.height ?: height,layout.value.cards[rightId]?.takeIf { it.heightSized }?.height ?: height).dp
        if(paired) Row(Modifier.fillMaxWidth().height(rowHeight),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            leftBody(Modifier.width(leftWidth).fillMaxHeight())
            rightBody(Modifier.width(available-leftWidth).fillMaxHeight())
        } else Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            leftBody(Modifier.fillMaxWidth());rightBody(Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AnalyticsKpiRow(metrics: List<AnalyticsKpi>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=if(maxWidth<350.dp || LocalDensity.current.fontScale>1.25f) 2 else 4
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            metrics.chunked(columns).forEach { group ->
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    group.forEach { metric ->
                        val tint=when(metric.label) { "FOCUS"->DeckColors.accent;"VARIANCE"->DeckColors.error;else->OmssColors.analytical }
                        FDCard(Modifier.weight(1f).fillMaxRowHeight().testTag("analytics-kpi-${metric.label}"),padding=6.dp) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                if(metric.label=="FOCUS") AnalyticsClockIcon(Modifier.size(16.dp))
                                else BundledCardIcon(when(metric.label) { "SESSIONS"->"chart-no-axes-column";"BLOCKS DONE"->"target";else->"activity" },Modifier.size(16.dp),tint)
                                Text(metric.label,fontSize=8.sp,maxLines=2,color=DeckColors.muted)
                            }
                            Text(metric.value,fontSize=18.sp,style=OmssTypography.metric,
                                color=if(metric.label=="VARIANCE") punctualityColor(metric.varianceMs) else OmssColors.text)
                            if(metric.label=="VARIANCE") Text("avg delta",fontSize=10.sp,color=DeckColors.muted)
                            else Text(metric.trend?.let { "${if(it>=0) "↑" else "↓"} ${"%.0f".format(java.util.Locale.ROOT,abs(it))}%" } ?: "Trend N/A",
                                fontSize=10.sp,color=metric.trend?.let { if(it>=0) DeckColors.success else DeckColors.error } ?: DeckColors.muted)
                        }
                    }
                }
            }
        }
    }
}

internal fun punctualityColor(delta: Double?): Color = when { delta==null->DeckColors.muted;delta>0->DeckColors.error;else->DeckColors.success }
@Composable private fun AnalyticsClockIcon(modifier: Modifier) {
    Image(painterResource(R.drawable.analytics_clock),contentDescription=null,modifier=modifier,colorFilter=ColorFilter.tint(DeckColors.accent))
}

@Composable internal fun CompactAnalyticsMeasure(label: String,evidence: String,fraction: Float?,color: Color,action: ()->Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick=action).semantics { contentDescription="$label $evidence, ${fraction?.let { "${(it*100).roundToInt()} percent" } ?: "N/A"}" },verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),fontSize=9.sp)
            if(evidence.isNotEmpty()) Text(evidence,fontSize=9.sp,color=DeckColors.muted)
            Text(fraction?.let { "${(it*100).roundToInt()}%" } ?: "N/A",fontSize=9.sp,color=color)
        }
        FDTrack(fraction,color=color)
    }
}

@Composable internal fun CompactPlanExecutionScore(e: DashboardAnalyticsEvidence,modifier: Modifier,trend: Double?,
    captions: Map<String,String>,inspect: (String)->Unit) {
    val x=e.performance.execution
    FDCard(modifier.testTag("analytics-plan-score"),padding=8.dp) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Plan Execution Score",Modifier.weight(1f),fontSize=12.sp,fontWeight=FontWeight.Bold)
            OmssStatusPill(if(x.provisional) "Provisional" else if(x.partial) "Partial" else if(e.score==null) "N/A" else "Recorded")
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gauge=(maxWidth*.34f).coerceIn(80.dp,112.dp)
            val rows: @Composable ()->Unit = {
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    CompactAnalyticsMeasure("Plan Completion",captions.getValue("completion"),x.completion?.toFloat(),DeckColors.accent) { inspect("Plan Completion") }
                    CompactAnalyticsMeasure("Start Punctuality",captions.getValue("punctuality"),x.punctuality?.toFloat(),DeckColors.accent) { inspect("Start Punctuality") }
                    CompactAnalyticsMeasure("Time Efficiency",captions.getValue("efficiency"),x.efficiency?.toFloat(),OmssColors.analytical) { inspect("Time Efficiency") }
                }
            }
            if(maxWidth>=230.dp && LocalDensity.current.fontScale<=1.25f) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ScoreRing(e.score,Modifier.size(gauge).clickable { inspect("Plan Execution Score") },trend)
                Box(Modifier.weight(1f)) { rows() }
            } else Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                ScoreRing(e.score,Modifier.size(gauge).align(Alignment.CenterHorizontally).clickable { inspect("Plan Execution Score") },trend);rows()
            }
        }
    }
}

@Composable internal fun CompactTimeSplitCard(e: DashboardAnalyticsEvidence,modifier: Modifier,inspect: (String,String)->Unit) {
    FDCard(modifier.testTag("analytics-distribution"),padding=8.dp) {
        Text("Time Split",fontSize=12.sp,fontWeight=FontWeight.Bold)
        Text(dashboardDuration(e.tracked.takeIf { e.days.isNotEmpty() }),fontSize=11.sp,color=DeckColors.muted)
        NativeTimeSplit(e,inspect)
    }
}

@Composable internal fun CompactExecutionCard(e: DashboardAnalyticsEvidence,modifier: Modifier,captions: Map<String,String>,inspect: (String,String)->Unit) {
    FDCard(modifier.testTag("analytics-execution"),padding=8.dp) {
        Text("Execution",fontSize=12.sp,fontWeight=FontWeight.Bold)
        CompactAnalyticsMeasure("Blocks",e.plannedCount?.let { "${e.done}/$it" } ?: e.done.takeIf { e.days.isNotEmpty() }?.let { "$it DONE" } ?: "N/A",e.completion,DeckColors.accent) { inspect("Blocks","${e.done} DONE · plan count ${e.plannedCount ?: "N/A"}; score is duration-weighted") }
        CompactAnalyticsMeasure("Punctual",e.punctual.takeIf { it.isNotEmpty() }?.let { "${it.count { d->d<=0 }}/${it.size}" } ?: "N/A",e.punctualFraction,DeckColors.accent) { inspect("Punctual","${e.punctual.count { it<=0 }} on time/early of ${e.punctual.size} explicit comparisons") }
        CompactAnalyticsMeasure("Efficiency",captions.getValue("efficiency"),e.performance.execution.efficiency?.toFloat(),OmssColors.analytical) { inspect("Efficiency",executionDetails(e.performance)) }
    }
}

/** Signed native bars: late positive/cyan, early negative/amber; no inverted data sign.
 * FULL reveal reads in drawing only; Reduced/Off render final geometry immediately.
 */
@Composable internal fun PunctualityTrendCard(trend: PunctualityTrend,key: String,modifier: Modifier,inspect: (String,String)->Unit) {
    FDCard(modifier.testTag("analytics-punctuality"),padding=8.dp) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            AnalyticsClockIcon(Modifier.size(14.dp))
            Text("Punctuality Trend",Modifier.weight(1f),fontSize=11.sp,fontWeight=FontWeight.Bold)
            Text("Avg",fontSize=9.sp,color=DeckColors.muted)
            Text(signedStartDelta(trend.averageMs),fontSize=13.sp,color=punctualityColor(trend.averageMs),fontWeight=FontWeight.Bold)
        }
        if(trend.averageMs==null) Box(Modifier.fillMaxWidth().height(78.dp),contentAlignment=Alignment.Center) { Text("N/A",color=DeckColors.muted,fontSize=13.sp) }
        else {
            val mode=LocalAchievementMotion.current
            val reveal=remember(key) { Animatable(if(mode==AchievementMotion.FULL) 0f else 1f) }
            LaunchedEffect(key,mode) { if(mode==AchievementMotion.FULL) reveal.animateTo(1f,tween(mode.duration)) else reveal.snapTo(1f) }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val chartWidth=maxOf(maxWidth,trend.buckets.size*22.dp)
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Box(Modifier.width(chartWidth).height(78.dp).testTag("punctuality-bars").semantics {
                        contentDescription="Start deviation chart; positive late, negative early; missing N/A"
                        customActions=trend.buckets.map { b -> CustomAccessibilityAction("${b.label}: ${signedStartDelta(b.meanMs)}, ${b.samples} starts") { inspect(b.label,"${signedStartDelta(b.meanMs)} · ${b.samples} actual start comparisons");true } }
                    }.drawWithCache {
                        val top=12.dp.toPx();val bottom=size.height-24.dp.toPx();val baseline=(top+bottom)/2
                        val slot=size.width/trend.buckets.size.coerceAtLeast(1)
                        val scale=(trend.buckets.mapNotNull { it.meanMs }.maxOfOrNull { abs(it) } ?: 0.0).coerceAtLeast(60000.0)
                        val amber=DeckColors.accent;val cyan=OmssColors.analytical;val muted=DeckColors.muted
                        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize=8.sp.toPx();textAlign=android.graphics.Paint.Align.CENTER;color=muted.toArgb() }
                        onDrawBehind {
                            drawLine(muted,Offset(0f,baseline),Offset(size.width,baseline),.7.dp.toPx())
                            trend.buckets.forEachIndexed { i,b ->
                                val x=slot*(i+.5f);val half=(bottom-top)/2
                                val extent=((b.meanMs ?: 0.0)/scale*half).toFloat()*reveal.value
                                val end=baseline-extent
                                if(b.meanMs!=null && extent!=0f) drawRoundRect(if(b.meanMs>0) cyan else amber,Offset(x-slot*.3f,min(baseline,end)),Size(slot*.6f,abs(extent)),CornerRadius(1.dp.toPx()))
                                drawIntoCanvas { canvas ->
                                    val valueY=if((b.meanMs ?: 0.0)>0) end-3.dp.toPx() else if((b.meanMs ?: 0.0)<0) end+9.dp.toPx() else baseline-3.dp.toPx()
                                    canvas.nativeCanvas.drawText(signedStartDelta(b.meanMs),x,valueY,paint)
                                    canvas.nativeCanvas.drawText(b.label,x,size.height-2.dp.toPx(),paint)
                                }
                            }
                        }
                    })
                }
            }
        }
    }
}
