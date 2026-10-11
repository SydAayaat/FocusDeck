package com.hax.focusdeck

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlin.math.roundToInt

/** Transparent native popup window. Explicit outside hit layer also handles OEM full-window popups. */
@Composable internal fun DashboardPopup(dismiss: ()->Unit,anchorPoint: Offset?=null,explicitAnchor: Rect?=null,
    content: @Composable ()->Unit) {
    val density=LocalDensity.current
    val safeTop=WindowInsets.safeDrawing.getTop(density);val safeBottom=WindowInsets.safeDrawing.getBottom(density)
    var source by remember { mutableStateOf(IntRect.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val currentDismiss by rememberUpdatedState(dismiss)
    Popup(popupPositionProvider=remember { object: PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect,windowSize: IntSize,layoutDirection: LayoutDirection,popupContentSize: IntSize): IntOffset {
            if(source!=anchorBounds) source=anchorBounds
            return IntOffset.Zero
        }
    } },onDismissRequest=dismiss,properties=PopupProperties(focusable=true,dismissOnClickOutside=true,clippingEnabled=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("dashboard-popup-dismiss-area").pointerInput(Unit) { detectTapGestures { currentDismiss() } }) {
            val viewport=IntSize(constraints.maxWidth,constraints.maxHeight)
            Box(Modifier.offset {
                val rect=explicitAnchor ?: Rect(source.left.toFloat(),source.top.toFloat(),source.right.toFloat(),source.bottom.toFloat())
                val xCenter=if(anchorPoint!=null) rect.left+anchorPoint.x else rect.center.x
                val top=if(anchorPoint!=null) rect.top+anchorPoint.y else rect.top
                val x=(xCenter-cardSize.width/2).roundToInt().coerceIn(8,maxOf(8,viewport.width-cardSize.width-8))
                val y=(top-cardSize.height-8).roundToInt().coerceIn(safeTop+8,maxOf(safeTop+8,viewport.height-safeBottom-cardSize.height-8))
                IntOffset(x,y)
            }.onSizeChanged { if(cardSize!=it) cardSize=it }
                .pointerInput(Unit) { detectTapGestures { /* consume card taps; outside alone dismisses */ } }) { content() }
        }
    }
}
