package com.hax.focusdeck

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

internal val LocalAmbientEnabled = staticCompositionLocalOf { true }
@Composable internal fun RefLabel(text: String, modifier: Modifier = Modifier) {
    Text(text,modifier,color=DeckColors.muted,fontSize=11.sp,letterSpacing=1.sp)
}
@Composable internal fun RefCard(modifier: Modifier = Modifier, accented: Boolean = false, onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val mode=LocalAchievementMotion.current
    val scale=animateFloatAsState(if(pressed && mode!=AchievementMotion.OFF) .985f else 1f,tween(mode.duration),label="Card press")
    Surface(modifier.graphicsLayer {scaleX=scale.value;scaleY=scale.value}.then(if(onClick==null) Modifier else Modifier.clickable(interactionSource=interaction,indication=null,onClick=onClick)),
        shape=RoundedCornerShape(9.dp),color=DeckColors.surface,border=BorderStroke(1.dp,if(accented) DeckColors.accent.copy(alpha=.65f) else DeckColors.track)) {
        Column(Modifier.animateContentSize(tween(mode.duration)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable internal fun RefAction(text: String, icon: String, modifier: Modifier = Modifier, color: Color = DeckColors.accent,
    enabled: Boolean = true, onClick: () -> Unit) {
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val mode=LocalAchievementMotion.current
    val scale=animateFloatAsState(if(pressed && mode!=AchievementMotion.OFF) .98f else 1f,tween(mode.duration),label="Button press")
    OutlinedButton(onClick,modifier.graphicsLayer {scaleX=scale.value;scaleY=scale.value}.heightIn(min=44.dp),enabled=enabled,
        shape=RoundedCornerShape(50),border=BorderStroke(1.dp,color),interactionSource=interaction,contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp)) {
        UiGlyph(icon,Modifier.size(18.dp),color); Spacer(Modifier.width(10.dp));Text(text,color=color,fontSize=11.sp)
    }
}
/** Small UI glyphs share the existing pack's 24-unit rounded stroke geometry. */
@Composable internal fun UiGlyph(name: String, modifier: Modifier = Modifier, color: Color = DeckColors.accent) {
    if(name == "chart") {BundledCardIcon("chart-no-axes-column",modifier,color);return}
    if(name in CardIconPolicy.names) {BundledCardIcon(name,modifier,color);return}
    Canvas(modifier) {
        val unit=size.minDimension/24f;val stroke=1.8f*unit
        fun point(x:Float,y:Float)=Offset(x*unit,y*unit)
        fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(color,point(x,y),point(x2,y2),stroke,StrokeCap.Round)
        fun circle(x:Float,y:Float,r:Float)=drawCircle(color,r*unit,point(x,y),style=Stroke(stroke))
        when(name) {
            "close" -> {line(5f,5f,19f,19f);line(19f,5f,5f,19f)}
            "pause" -> {circle(12f,12f,9f);line(9f,8f,9f,16f);line(15f,8f,15f,16f)}
            "calendar" -> {drawRoundRect(color,point(3f,5f),Size(18*unit,16*unit),androidx.compose.ui.geometry.CornerRadius(2*unit),style=Stroke(stroke));line(3f,10f,21f,10f);line(8f,3f,8f,7f);line(16f,3f,16f,7f);for(x in listOf(8f,12f,16f)) for(y in listOf(14f,18f)) drawCircle(color,unit*.6f,point(x,y))}
            "hourglass" -> {line(5f,3f,19f,3f);line(5f,21f,19f,21f);line(7f,3f,7f,7f);line(17f,3f,17f,7f);line(7f,7f,17f,17f);line(17f,7f,7f,17f);line(7f,17f,7f,21f);line(17f,17f,17f,21f)}
            "layers" -> {val p=Path().apply {moveTo(12*unit,3*unit);lineTo(22*unit,8*unit);lineTo(12*unit,13*unit);lineTo(2*unit,8*unit);close()};drawPath(p,color,style=Stroke(stroke));line(2f,13f,12f,18f);line(12f,18f,22f,13f);line(2f,18f,12f,23f);line(12f,23f,22f,18f)}
            "infinity" -> {val p=Path().apply {moveTo(12*unit,12*unit);cubicTo(4*unit,-1*unit,0f,8*unit,3*unit,15*unit);cubicTo(6*unit,22*unit,13*unit,6*unit,17*unit,6*unit);cubicTo(26*unit,6*unit,22*unit,23*unit,12*unit,12*unit)};drawPath(p,color,style=Stroke(stroke))}
            "download","upload" -> {line(12f,3f,12f,16f);if(name=="download"){line(7f,11f,12f,16f);line(17f,11f,12f,16f)}else {line(7f,8f,12f,3f);line(17f,8f,12f,3f)};line(3f,16f,3f,21f);line(3f,21f,21f,21f);line(21f,21f,21f,16f)}
            "copy","file" -> {drawRoundRect(color,point(8f,3f),Size(13*unit,16*unit),androidx.compose.ui.geometry.CornerRadius(unit),style=Stroke(stroke));if(name=="copy"){line(4f,7f,4f,23f);line(4f,23f,17f,23f)}else {line(12f,9f,17f,9f);line(12f,13f,17f,13f)}}
            "trash" -> {line(4f,6f,20f,6f);line(9f,3f,15f,3f);line(7f,6f,8f,22f);line(8f,22f,16f,22f);line(16f,22f,17f,6f);line(11f,10f,11f,18f);line(14f,10f,14f,18f)}
            "check-ring" -> {circle(12f,12f,9f);line(7f,12f,10.5f,15.5f);line(10.5f,15.5f,17f,8.5f)}
            "chevron-down" -> {line(5f,9f,12f,16f);line(12f,16f,19f,9f)}
            "chevron-up" -> {line(5f,15f,12f,8f);line(12f,8f,19f,15f)}
            else -> {circle(12f,12f,9f);line(12f,7f,12f,12f);line(12f,12f,16f,14f)}
        }
    }
}
/** Density-independent art is decoded off-thread and retained across page visits. */
private object DeckArtworkCache {
    val bitmaps = ConcurrentHashMap<Int, ImageBitmap>()
}
/** Grayscale light follows the selected accent; near-black pixels remain OLED black. */
private fun artworkFilter(accent: Color): ColorFilter {
    val gain = 255f / 247f
    val r = accent.red * gain; val g = accent.green * gain; val b = accent.blue * gain
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        .2126f*r, .7152f*r, .0722f*r, 0f, -8f*r,
        .2126f*g, .7152f*g, .0722f*g, 0f, -8f*g,
        .2126f*b, .7152f*b, .0722f*b, 0f, -8f*b,
        0f, 0f, 0f, 1f, 0f
    )))
}
@Composable internal fun ThemedArtwork(resourceId: Int, modifier: Modifier,
    alignment: Alignment = Alignment.Center) {
    val context = LocalContext.current
    val artwork by produceState<ImageBitmap?>(DeckArtworkCache.bitmaps[resourceId], resourceId, context.resources) {
        if (value == null) value = withContext(Dispatchers.IO) {
            DeckArtworkCache.bitmaps.getOrPut(resourceId) {
                requireNotNull(BitmapFactory.decodeResource(context.resources, resourceId,
                    BitmapFactory.Options().apply { inScaled = false })).asImageBitmap()
            }
        }
    }
    val accent = DeckColors.accent
    val filter = remember(accent) { artworkFilter(accent) }
    artwork?.let { image ->
        Image(image, contentDescription = null, alignment = alignment, contentScale = ContentScale.Fit,
            colorFilter = filter, modifier = modifier)
    }
}
/** Extremely slow, draw-only drift. Full artwork is fitted, including both curved limbs and moon. */
@Composable internal fun EclipseBackdrop(modifier: Modifier, ambient: Boolean = false) {
    val mode = LocalAchievementMotion.current
    val enabled = LocalAmbientEnabled.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val phase = remember { Animatable(0f) }
    LaunchedEffect(ambient, mode, enabled, lifecycle) {
        if (ambient && enabled && mode == AchievementMotion.FULL) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { delay(66); phase.snapTo((phase.value + 66f/120000f) % 1f) }
        }
    }
    ThemedArtwork(R.drawable.home_eclipse_art, modifier.padding(4.dp).graphicsLayer {
                // Extra inset keeps the complete image inside its bounds even at the drift extremes.
                val angle = phase.value * 2.0 * PI
                translationX = if (mode == AchievementMotion.FULL) sin(angle).toFloat()*1.5.dp.toPx() else 0f
                translationY = if (mode == AchievementMotion.FULL) (cos(angle).toFloat()-1f)*.75.dp.toPx() else 0f
            }, alignment = Alignment.CenterEnd)
}
@Composable internal fun HaloRing(fraction: Float, modifier: Modifier, illuminate: Boolean = false,
    secondary: Color? = null, content: @Composable BoxScope.() -> Unit = {}) {
    val mode=LocalAchievementMotion.current
    val value=animateFloatAsState(fraction.coerceIn(0f,1f),tween(if(mode==AchievementMotion.FULL) 480 else mode.duration),label="Ring value")
    val bloom=remember {Animatable(if(illuminate && mode==AchievementMotion.FULL) 1f else 0f)}
    LaunchedEffect(illuminate,mode) {bloom.animateTo(0f,tween(if(mode==AchievementMotion.FULL) 900 else 0))}
    Box(modifier,contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius=size.minDimension*.41f;val width=size.minDimension*.06f;val tint=DeckColors.accent
            drawCircle(Brush.radialGradient(colorStops=arrayOf(0f to Color.Transparent,.64f to Color.Transparent,.83f to tint.copy(alpha=.22f+bloom.value*.15f),1f to Color.Transparent),center=center,radius=size.minDimension*.5f),size.minDimension*.5f,center)
            drawCircle(DeckColors.track,radius,center,style=Stroke(width))
            secondary?.let {drawCircle(it,radius,center,style=Stroke(width))}
            drawArc(tint,-90f,360f*value.value,false,Offset(center.x-radius,center.y-radius),Size(radius*2,radius*2),style=Stroke(width,cap=StrokeCap.Butt))
        }
        content()
    }
}
