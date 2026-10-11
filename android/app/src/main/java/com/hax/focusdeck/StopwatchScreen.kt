package com.hax.focusdeck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

fun stopwatchText(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
}

/** Six degrees per completed active minute; wraps each hour, with no time target. */
fun stopwatchMinuteFraction(activeMs: Long): Float =
    ((activeMs.coerceAtLeast(0) / 60_000) % 60).toFloat() / 60f

/** Only the dial differs; FocusScreen supplies the same layout, footer and controls. */
@Composable
fun StopwatchDial(activeMs: Long, label: String, accent: Color, grey: Color, modifier: Modifier) {
    val time = stopwatchText(activeMs)
    val fraction = stopwatchMinuteFraction(activeMs)
    Box(modifier.semantics {
        contentDescription = "Elapsed focus time $time. Hour dial: ${(activeMs.coerceAtLeast(0) / 60_000) % 60} minutes into this hour; no deadline."
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2 - 6.dp.toPx()
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(Color(0xFF4B5056), radius, center, style = Stroke(3.dp.toPx()))
            // Reference clock markings: 60 inner ticks, longer every five minutes.
            val tickRadius = radius - 12.dp.toPx()
            for (minute in 0 until 60) {
                val major = minute % 5 == 0
                val angle = Math.toRadians((minute * 6 - 90).toDouble())
                val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                val length = (if (major) 14.dp else 6.dp).toPx()
                drawLine(if (major) accent else grey.copy(alpha = .7f),
                    start = center + direction * (tickRadius - length), end = center + direction * tickRadius,
                    strokeWidth = (if (major) 1.5.dp else .75.dp).toPx())
            }
            val inset = size.minDimension / 2 - radius
            drawArc(accent, -90f, fraction * 360f, false, topLeft = Offset(inset, inset),
                size = Size(radius * 2, radius * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            val angle = Math.toRadians((fraction * 360 - 90).toDouble())
            drawCircle(accent, 4.dp.toPx(), center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, fontSize = 44.sp, fontWeight = FontWeight.ExtraLight, color = accent)
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Light, color = grey, letterSpacing = 3.sp)
        }
    }
}
