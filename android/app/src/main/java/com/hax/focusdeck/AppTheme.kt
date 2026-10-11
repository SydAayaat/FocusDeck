package com.hax.focusdeck

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

internal data class DeckPalette(val id: String, val title: String, val accent: Color, val secondary: Color,
    val muted: Color = OmssColors.secondaryText, val success: Color = Color(0xFF00DFA6), val error: Color = Color(0xFFFF5252)) {
    val border get() = accent.copy(alpha = .22f)
    val track get() = OmssColors.track
    val surface get() = if (id == "monochrome") Color(0xFF181818) else OmssColors.card
}
internal object DeckPalettes {
    val all = listOf(
        DeckPalette("cyan", "Cyan", Color(0xFF00C8EF), Color(0xFFFFD166)),
        DeckPalette("monochrome", "Monochrome / grayscale", Color(0xFFE8E8E8), Color(0xFFAAAAAA), Color(0xFFAAAAAA), Color.White, Color(0xFFBBBBBB)),
        DeckPalette("gold", "Gold / yellow", OmssColors.amber, OmssColors.cyan),
        DeckPalette("blue", "Ocean blue", Color(0xFF62A7FF), Color(0xFF9BD8FF)),
        DeckPalette("purple", "Amethyst", Color(0xFFBB9BFF), Color(0xFFF2BCFF)),
        DeckPalette("green", "Emerald", Color(0xFF55E6AF), Color(0xFFA8F0C2)),
        DeckPalette("rose", "Rose", Color(0xFFFF91B5), Color(0xFFFFCBDD)),
        DeckPalette("orange", "Amber / orange", Color(0xFFFFAC62), Color(0xFFFFD29B)))
    fun find(id: String) = all.find { it.id == id } ?: all.first()
}
/** One application-wide observable palette, including platform Dialog compositions and canvas draw phases. */
internal object DeckColors {
    var palette by mutableStateOf(DeckPalettes.all.first())
    val accent get() = palette.accent
    val secondary get() = palette.secondary
    val muted get() = palette.muted
    val success get() = palette.success
    val error get() = palette.error
    val border get() = palette.border
    val track get() = if (palette.id == "monochrome") Color(0xFF303030) else palette.track
    val surface get() = palette.surface
}
@Composable internal fun DeckTheme(id: String, motion: String, immersive: Boolean = false, content: @Composable () -> Unit) {
    val palette = remember(id) { DeckPalettes.find(id) }
    SideEffect { DeckColors.palette = palette }
    MaterialTheme(colorScheme = darkColorScheme(primary = palette.accent, secondary = palette.secondary,
        background = Color.Black, surface = palette.surface, onBackground = if (id == "monochrome") Color(0xFFF4F4F4) else Color(0xFFF1F4F6),
        onSurface = if (id == "monochrome") Color(0xFFF4F4F4) else OmssColors.text, error = palette.error, outline = palette.border,
        onPrimary = Color.Black, onSecondary = Color.Black)) {
        CompositionLocalProvider(LocalDeckImmersive provides immersive, LocalContentColor provides MaterialTheme.colorScheme.onSurface) { MotionProvider(motion, content) }
    }
}
/** Panel hint only; rendering and system battery/display policies determine actual FPS. */
internal object NavigationRefresh {
    fun requested(timerVisible: Boolean, dim: Boolean, timerRate: Int, supported: Float): Float =
        if (timerVisible) (if (dim) 30 else timerRate).toFloat().coerceAtMost(supported)
        else supported.coerceAtMost(120f)
}


/** Fullscreen modal history has its own Android window; don't inherit the timer's low-rate request. */
@Composable internal fun DialogNavigationRefresh() {
    val view = androidx.compose.ui.platform.LocalView.current
    val immersive = LocalDeckImmersive.current
    DisposableEffect(view, immersive) {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        val previous = window?.attributes?.preferredRefreshRate ?: 0f
        val maximum = view.display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f
        if (window != null) {
            window.attributes = window.attributes.apply { preferredRefreshRate = maximum.coerceAtMost(120f) }
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersive) controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            else controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (window != null) window.attributes = window.attributes.apply { preferredRefreshRate = previous } }
    }
}

internal val LocalDeckImmersive = staticCompositionLocalOf { false }
/** Material alerts own a window too. Slots run inside that window's composition. */
@Composable internal fun DeckAlertDialog(onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    dismissButton: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null) {
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
        dismissButton = dismissButton, title = { DialogNavigationRefresh(); title?.invoke() }, text = text)
}
