package com.hax.focusdeck

import android.provider.Settings
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp

internal enum class AchievementMotion { FULL, REDUCED, OFF;
    val duration: Int get() = when (this) { FULL -> 280; REDUCED -> 120; OFF -> 0 }
}
internal val LocalAchievementMotion = staticCompositionLocalOf { AchievementMotion.FULL }

@Composable internal fun MotionProvider(preference: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val chosen = AchievementMotion.entries.find { it.name == preference } ?: AchievementMotion.FULL
    CompositionLocalProvider(LocalAchievementMotion provides if (scale == 0f) AchievementMotion.OFF else chosen, content = content)
}

@Composable internal fun EntryMotion(modifier: Modifier = Modifier, delay: Int = 0, content: @Composable () -> Unit) {
    val mode = LocalAchievementMotion.current
    val phase = remember { Animatable(if (mode == AchievementMotion.OFF) 1f else 0f) }
    LaunchedEffect(mode) { phase.animateTo(1f, tween(mode.duration, if (mode == AchievementMotion.FULL) delay else 0)) }
    Box(modifier.graphicsLayer { alpha = phase.value; translationY = if (mode == AchievementMotion.FULL) (1-phase.value)*10.dp.toPx() else 0f }) { content() }
}

/** Stable page keys avoid restarting navigation motion on timer ticks or data refreshes. */
@Composable internal fun <T> FadeSlidePage(target: T, modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit) {
    val mode = LocalAchievementMotion.current
    AnimatedContent(targetState = target, modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.TopStart,
        transitionSpec = {
            (fadeIn(tween(mode.duration)) + slideInVertically(tween(mode.duration)) { if (mode == AchievementMotion.FULL) it / 30 else 0 })
                .togetherWith(fadeOut(tween(mode.duration)) + slideOutVertically(tween(mode.duration)) { if (mode == AchievementMotion.FULL) -it / 40 else 0 })
                .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(mode.duration) }))
        }, label = "Page navigation") { page -> content(page) }
}
