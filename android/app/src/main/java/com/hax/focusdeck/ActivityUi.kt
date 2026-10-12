package com.hax.focusdeck

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable internal fun statusColor(status: String): Color = when(status) {
    "done","complete" -> DeckColors.success
    "stopped","timed_out" -> DeckColors.error
    "skipped","paused" -> DeckColors.accent
    "active","In progress" -> OmssColors.analytical
    else -> DeckColors.muted
}
@Composable internal fun ExecutionStatus(status: String,label: String=executionLabel(status)) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        ActivityGlyph(when(status) { "done","complete" -> "check-ring";"paused" -> "pause";"skipped" -> "skip-forward";"stopped" -> "square";else -> "clock" },Modifier.size(18.dp),statusColor(status))
        Text(label,color=statusColor(status),fontSize=11.sp)
    }
}
@Composable internal fun CurrentSessionIndicator(phase: String,hasSession: Boolean) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,_ -> resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer) }
    }
    val active=hasSession && phase=="active"
    val pulse=active && LocalAchievementMotion.current==AchievementMotion.FULL && LocalAmbientEnabled.current && resumed
    val alpha=if(pulse) {
        val transition=rememberInfiniteTransition(label="Active session recording")
        val value by transition.animateFloat(.55f,1f,infiniteRepeatable(tween(1400),RepeatMode.Reverse),label="Recording opacity")
        value
    } else 1f
    if(active) Box(Modifier.size(14.dp).semantics { contentDescription="Active session recording" }.background(DeckColors.error.copy(alpha=alpha),CircleShape))
    else ActivityGlyph(if(hasSession && phase=="paused") "pause" else "clock",Modifier.size(24.dp),if(hasSession && phase=="paused") DeckColors.accent else DeckColors.muted)
}
internal fun Modifier.activityExpansion(mode: AchievementMotion): Modifier = if(mode==AchievementMotion.OFF) this else animateContentSize(tween(mode.duration))
@Composable internal fun DurationAccuracy(comparison: DurationComparison?, provisional: Boolean=false) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            ActivityGlyph("chart",Modifier.size(21.dp));Spacer(Modifier.width(8.dp))
            Text("Time Estimation Accuracy",Modifier.weight(1f),fontSize=13.sp)
            Text(comparison?.let { "%.1f%%".format(java.util.Locale.ROOT,it.accuracy) } ?: "N/A",color=DeckColors.accent,fontSize=18.sp)
        }
        OmssProgressTrack(comparison?.let { (it.accuracy/100).toFloat() })
        Text(comparison?.let { "Estimated ${humanTime(it.estimated)} · actual ${humanTime(it.actual)}\n${it.caption}" } ?: if(provisional) "Live measurement · no finalized accuracy" else "No comparable finalized duration",color=DeckColors.muted,fontSize=11.sp)
    }
}

/** Shadow follows the glyph alpha, rather than casting a rectangular icon-container shadow. */
@Composable internal fun ActivityGlyph(name: String,modifier: Modifier=Modifier,color: Color=DeckColors.accent) {
    Box(modifier) {
        UiGlyph(name,Modifier.matchParentSize().offset(2.dp,3.dp).graphicsLayer {
            renderEffect=BlurEffect(3.dp.toPx(),3.dp.toPx(),TileMode.Decal);clip=false
        },Color.Black.copy(alpha=.75f))
        UiGlyph(name,Modifier.matchParentSize(),color)
    }
}
