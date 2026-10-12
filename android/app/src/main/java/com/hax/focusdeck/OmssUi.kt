package com.hax.focusdeck

import android.graphics.Paint
import android.graphics.Path as NativePath
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.ceil
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue

/** Opaque dashboard materials. Saved palettes still own accents and icon tint. */
internal object OmssColors {
    val canvas = Color(0xFF090A0E)
    val card = Color(0xFF17191E)
    val nested = Color(0xFF1C1F24)
    val hub = Color(0xFF111318)
    val track = Color(0xFF30343C)
    val separator = Color(0xFF292D35)
    val text = Color(0xFFF2F3F5)
    val secondaryText = Color(0xFFA4ADBB)
    val amber = Color(0xFFFFA726)
    val cyan = Color(0xFF00D5E8)
    fun material(color: Color): Color = if (DeckColors.palette.id == "monochrome") {
        val luminance = color.red * .2126f + color.green * .7152f + color.blue * .0722f
        Color(luminance, luminance, luminance)
    } else color
    val analytical get() = if (DeckColors.palette.id == "monochrome") DeckColors.secondary else cyan
}

internal object OmssTypography {
    val metric = TextStyle(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum",
        shadow = Shadow(Color.Black.copy(alpha=.25f), Offset(1f,2f), 2f))
    val numbers = TextStyle(fontFeatureSettings = "tnum")
}

internal data class OmssElevation(val x: Dp, val y: Dp, val blur: Dp, val opacity: Float)
internal object OmssElevations {
    val card = OmssElevation(4.dp, 8.dp, 16.dp, .58f)
    val nested = OmssElevation(3.dp, 5.dp, 10.dp, .48f)
    val component = OmssElevation(2.dp, 4.dp, 7.dp, .50f)
    val micro = OmssElevation(1.dp, 2.dp, 3.dp, .28f)
}

/** The reference timeline callout has one restrained accent outline and a pointing foot. */
internal object OmssCalloutShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val foot=with(density) { 7.dp.toPx() }
        val radius=with(density) { 10.dp.toPx() }
        val bottom=(size.height-foot).coerceAtLeast(0f)
        return Outline.Generic(Path().apply {
            addRoundRect(RoundRect(Rect(0f,0f,size.width,bottom),CornerRadius(radius)))
            moveTo(size.width/2-foot,bottom-.5f)
            lineTo(size.width/2,size.height)
            lineTo(size.width/2+foot,bottom-.5f)
            close()
        })
    }
}

/** A cached GPU display list, never a generated shadow bitmap or a software layer. */
internal class OmssShadow(path: NativePath, width: Float, height: Float, x: Float, y: Float,
    blur: Float, opacity: Float, stroke: Float = 0f, rounded: Boolean = false) {
    private val margin = ceil(blur * 2 + kotlin.math.abs(x) + kotlin.math.abs(y) + stroke).toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL
        strokeWidth = stroke
        strokeCap = if (rounded) Paint.Cap.ROUND else Paint.Cap.BUTT
    }
    private val fallback = NativePath(path).apply { offset(x, y) }
    private val node = RenderNode("FocusDeck OMSS shadow").apply {
        setPosition(-margin, -margin, ceil(width).toInt() + margin, ceil(height).toInt() + margin)
        setClipToBounds(false)
        if (blur > 0) setRenderEffect(RenderEffect.createBlurEffect(blur / 2, blur / 2, Shader.TileMode.DECAL))
        val canvas = beginRecording()
        canvas.translate(margin + x, margin + y)
        canvas.drawPath(path, paint)
        endRecording()
    }
    fun draw(canvas: android.graphics.Canvas) {
        if (canvas.isHardwareAccelerated) canvas.drawRenderNode(node)
        else canvas.drawPath(fallback, paint) // Software previews retain direction without allocating a bitmap.
    }
}

/** Shadow precedes the opaque body; the owning Surface clips content, never this modifier. */
internal fun Modifier.omssDirectionalShadow(shape: Shape = RoundedCornerShape(12.dp),
    elevation: OmssElevation = OmssElevations.card): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
    }
    val shadow = OmssShadow(path.asAndroidPath(), size.width, size.height,
        elevation.x.toPx(), elevation.y.toPx(), elevation.blur.toPx(), elevation.opacity)
    onDrawWithContent {
        drawIntoCanvas { shadow.draw(it.nativeCanvas) }
        drawContent()
    }
}

@Composable internal fun OmssSurface(modifier: Modifier = Modifier, nested: Boolean = false,
    shape: Shape = RoundedCornerShape(if (nested) 10.dp else 12.dp), color: Color = OmssColors.material(if (nested) OmssColors.nested else OmssColors.card),
    content: @Composable () -> Unit) {
    Surface(modifier.omssDirectionalShadow(shape, if (nested) OmssElevations.nested else OmssElevations.card),
        shape = shape, color = color, tonalElevation = 0.dp, shadowElevation = 0.dp, content = content)
}

@Composable internal fun OmssMiniCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OmssSurface(modifier, nested = true) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
    }
}

/** Foundation, colored stroke, raised hub, then data/icon content. Unknown/zero has no lit arc. */
@Composable internal fun OmssCircularGauge(fraction: Float?, modifier: Modifier = Modifier,
    color: Color = DeckColors.accent, rounded: Boolean = true, radiusFraction: Float = .42f,
    content: @Composable BoxScope.() -> Unit) {
    val progress by animateFloatAsState((fraction ?: 0f).coerceIn(0f,1f),tween(LocalAchievementMotion.current.duration),label="Gauge progress")
    OmssSegmentedGauge(listOf(progress to color), modifier, rounded, radiusFraction, content)
}

@Composable internal fun OmssSegmentedGauge(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier,
    rounded: Boolean = false, radiusFraction: Float = .42f, content: @Composable BoxScope.() -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize((radiusFraction*2+.10f).coerceAtMost(.98f)).omssDirectionalShadow(CircleShape, OmssElevations.component)
            .background(OmssColors.material(OmssColors.hub), CircleShape))
        Box(Modifier.fillMaxSize().drawWithCache {
            // Keep the native achievement arc centerline at 36% of diameter for existing geometry.
            val r = size.minDimension * radiusFraction
            val stroke = size.minDimension * .085f
            val center = Offset(size.width / 2, size.height / 2)
            val bounds = android.graphics.RectF(center.x - r, center.y - r, center.x + r, center.y + r)
            var start = -90f
            val arcs = segments.map { (fraction, color) ->
                val sweep = fraction.coerceIn(0f, 1f) * 360
                val path = NativePath().apply { addArc(bounds, start, sweep) }
                val shadow = OmssShadow(path, size.width, size.height, 1.dp.toPx(), 2.dp.toPx(),
                    3.dp.toPx(), .5f, stroke, rounded && sweep < 360)
                val arc = Triple(start, sweep, color)
                start += sweep
                shadow to arc
            }
            onDrawBehind {
                drawCircle(OmssColors.material(OmssColors.track), r, style = Stroke(stroke))
                arcs.forEach { (shadow, arc) -> if (arc.second > 0) {
                    drawIntoCanvas { shadow.draw(it.nativeCanvas) }
                    drawArc(arc.third, arc.first, arc.second, false,
                        Offset(center.x - r, center.y - r), Size(r * 2, r * 2),
                        style = Stroke(stroke, cap = if (rounded && arc.second < 360) StrokeCap.Round else StrokeCap.Butt))
                } }
            }
        })
        Box(Modifier.fillMaxSize(radiusFraction*1.75f).omssDirectionalShadow(CircleShape, OmssElevations.component)
            .background(OmssColors.material(OmssColors.hub), CircleShape), contentAlignment = Alignment.Center) {}
        content()
    }
}

@Composable internal fun OmssProgressTrack(fraction: Float?, modifier: Modifier = Modifier,
    color: Color = DeckColors.accent) {
    val shape = RoundedCornerShape(4.dp)
    Box(modifier.fillMaxWidth().height(6.dp).omssDirectionalShadow(shape, OmssElevations.micro)
        .background(OmssColors.material(OmssColors.track), shape)) {
        if (fraction != null && fraction > 0) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
            .omssDirectionalShadow(shape, OmssElevations.micro).background(color, shape))
    }
}

@Composable internal fun OmssFlatTab(label: String, chosen: Boolean, modifier: Modifier = Modifier, action: () -> Unit) {
    Column(modifier.semantics { selected = chosen; role = Role.Tab }.clickable(onClick = action)
        .heightIn(min = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.padding(horizontal = 4.dp, vertical = 5.dp), fontSize = 12.sp,
            color = if (chosen) DeckColors.accent else DeckColors.muted)
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (chosen) DeckColors.accent else OmssColors.material(OmssColors.separator)))
    }
}

@Composable internal fun OmssStatusPill(text: String, color: Color = OmssColors.analytical) {
    Surface(Modifier.omssDirectionalShadow(RoundedCornerShape(50), OmssElevations.component),
        shape = RoundedCornerShape(50), color = OmssColors.material(OmssColors.nested)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp, color = color)
    }
}

@Composable internal fun OmssPrimaryButton(action: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Button(action, Modifier.omssDirectionalShadow(RoundedCornerShape(50), OmssElevations.component),
        shape = RoundedCornerShape(50), elevation = null,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), content = content)
}
