package com.hax.focusdeck

import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.math.roundToInt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Serializable data class CardDimensions(val span: Int = 12, val height: Int = 240, val heightSized: Boolean = true)
@Serializable data class SavedDashboardLayout(val version: Int = 1, val cards: Map<String,CardDimensions> = emptyMap(), val order: List<String> = emptyList())
@Stable internal class DashboardLayoutState(initial: SavedDashboardLayout, val save: (SavedDashboardLayout) -> Unit) {
    var value by mutableStateOf(initial)
    var editing by mutableStateOf(false)
    fun dimensions(id: String, default: CardDimensions) = value.cards[id] ?: default
    fun preview(id: String, dimensions: CardDimensions) { value=value.copy(cards=value.cards+(id to dimensions)) }
    fun persist() = save(value)
    fun reset() { value=SavedDashboardLayout();save(value) }
    fun move(ids: List<String>, id: String, before: String) {
        val order=ids.toMutableList();val from=order.indexOf(id);val target=order.indexOf(before)
        if(from>=0 && target>=0) { order.removeAt(from);order.add(target.coerceAtMost(order.size),id);value=value.copy(order=order);persist() }
    }
}
private val dashboardSaveScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
private val dashboardSaveMutex=Mutex()
private val dashboardSavedLayouts=mutableMapOf<String,SavedDashboardLayout>()
internal val LocalDashboardPreview=staticCompositionLocalOf { false }
internal val LocalDashboardLayout = staticCompositionLocalOf<DashboardLayoutState?> { null }
internal val LocalDashboardCardHeight = staticCompositionLocalOf<Dp?> { null }
@Composable internal fun rememberDashboardLayout(page: String): DashboardLayoutState {
    if(LocalDashboardPreview.current) return remember(page) { DashboardLayoutState(SavedDashboardLayout()) {} }
    val context=LocalContext.current.applicationContext
    val config=LocalConfiguration.current
    val form=if(config.smallestScreenWidthDp>=600) "tablet" else "phone"
    val orientation=if(config.screenWidthDp>config.screenHeightDp) "landscape" else "portrait"
    val key="$page.$form.$orientation.v1"
    val prefs=remember(context) { context.getSharedPreferences("dashboard_layouts_v1",0) }
    return remember(key) {
        val legacyKey=if(page=="analytics-week") "analytics-aggregate.$form.$orientation.v1" else key
        val initial=dashboardSavedLayouts[key] ?: (if(!prefs.contains(key)) dashboardSavedLayouts[legacyKey] else null) ?: runCatching { WireJson.decodeFromString<SavedDashboardLayout>(prefs.getString(key,null) ?: prefs.getString(legacyKey,null) ?: "{}") }.getOrDefault(SavedDashboardLayout()).let { saved ->
            if(saved.version!=1) SavedDashboardLayout() else saved.copy(cards=saved.cards.filterKeys { it.length in 1..200 }.mapValues { (_,s)->s.copy(span=s.span.coerceIn(1,12),height=s.height.coerceIn(120,2400)) },order=saved.order.distinct().take(1000))
        }
        DashboardLayoutState(initial) { snapshot -> dashboardSavedLayouts[key]=snapshot;dashboardSaveScope.launch { dashboardSaveMutex.withLock { prefs.edit().putString(key,WireJson.encodeToString(snapshot)).commit() } } }
    }
}
@Composable internal fun LayoutControls(state: DashboardLayoutState, resetOrder: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        TextButton({state.editing=!state.editing}) { Text(if(state.editing) "DONE" else "EDIT LAYOUT",fontSize=11.sp,color=DeckColors.accent) }
        if(state.editing || state.value.cards.isNotEmpty() || state.value.order.isNotEmpty()) TextButton({state.reset()}) { Text("RESET LAYOUT",fontSize=10.sp,color=DeckColors.muted) }
        if(resetOrder!=null && state.editing) TextButton(resetOrder) { Text("RESET ORDER",fontSize=10.sp,color=DeckColors.muted) }
    }
    if(state.editing) Text("Grip: reorder · edges/corner: resize",fontSize=11.sp,color=DeckColors.muted)
}

/** Shared handle overlay; only handles consume drag input. Saves once at release, never on frames. */
@Composable internal fun ResizableCardFrame(id: String, state: DashboardLayoutState, default: CardDimensions,
    availableWidth: Dp, modifier: Modifier = Modifier, minimumHeight: Int = 120, reorder: Modifier? = null,
    content: @Composable () -> Unit) {
    val density=LocalDensity.current;val mode=LocalAchievementMotion.current
    val size=state.dimensions(id,default)
    var measuredWidth by remember(id) { mutableStateOf(availableWidth) }
    var measuredHeight by remember(id) { mutableIntStateOf(default.height) }
    val custom=id in state.value.cards && size.heightSized
    var before by remember(id) { mutableStateOf(size) }
    var original by remember(id) { mutableStateOf<CardDimensions?>(null) }
    val latestSize by rememberUpdatedState(size)
    val latestWidth by rememberUpdatedState(availableWidth)
    val h = size.height.coerceIn(minimumHeight,2400).dp
    Box(modifier.onSizeChanged { measuredWidth=with(density) { it.width.toDp() };measuredHeight=with(density) { it.height.toDp().value.roundToInt() } }.then(if(custom) Modifier.height(h) else Modifier)) {
        CompositionLocalProvider(LocalDashboardCardHeight provides if(custom) h else null) {
            if(custom) Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=if(state.editing) 20.dp else 0.dp)) { content() }
            else content()
        }
        if(state.editing) {
            if(reorder!=null) Text("⠿",Modifier.align(Alignment.TopEnd).size(48.dp).then(reorder).background(DeckColors.surface,RoundedCornerShape(8.dp)).semantics { contentDescription="Reorder card" },color=DeckColors.accent,fontSize=24.sp)
            for(axis in listOf("x","y","xy")) {
                var delta by remember(id,axis) { mutableStateOf(Offset.Zero) }
                val alignment=when(axis){"x"->Alignment.CenterEnd;"y"->Alignment.BottomCenter;else->Alignment.BottomEnd}
                val label=when(axis){"x"->"width";"y"->"height";else->"width and height"}
                val handle=Modifier.align(alignment).size(48.dp).background(DeckColors.surface.copy(alpha=.9f),RoundedCornerShape(8.dp))
                    .semantics { contentDescription="Resize card $label";customActions=listOf(
                        CustomAccessibilityAction("Increase $label") { val s=state.dimensions(id,default.copy(height=measuredHeight.coerceAtLeast(minimumHeight),heightSized=false));state.preview(id,s.copy(span=if('x' in axis) (s.span+1).coerceAtMost(12) else s.span,height=if('y' in axis) (s.height+24).coerceAtMost(2400) else s.height,heightSized=s.heightSized || 'y' in axis));state.persist();true },
                        CustomAccessibilityAction("Decrease $label") { val s=state.dimensions(id,default.copy(height=measuredHeight.coerceAtLeast(minimumHeight),heightSized=false));state.preview(id,s.copy(span=if('x' in axis) (s.span-1).coerceAtLeast(1) else s.span,height=if('y' in axis) (s.height-24).coerceAtLeast(minimumHeight) else s.height,heightSized=s.heightSized || 'y' in axis));state.persist();true }) }
                    .pointerInput(id,axis,state,state.editing) {
                        detectDragGestures(onDragStart={original=state.value.cards[id];before=latestSize.copy(span=((measuredWidth.value+12)/(latestWidth.value+12)*12).roundToInt().coerceIn(1,12),height=measuredHeight.coerceAtLeast(minimumHeight),heightSized=state.value.cards[id]?.heightSized ?: false);delta=Offset.Zero},onDragCancel={state.value=state.value.copy(cards=original?.let { state.value.cards+(id to it) } ?: (state.value.cards-id))},onDragEnd={state.persist()},onDrag={change,amount->
                            change.consume();delta+=amount
                            val dx=with(density){delta.x.toDp().value};val dy=with(density){delta.y.toDp().value}
                            val step=1
                            val minSpan=1
                            val span=if('x' in axis) (((before.span+dx/(latestWidth.value+12)*12)/step).roundToInt()*step).coerceIn(minSpan,12) else before.span
                            val height=if('y' in axis) (((before.height+dy)/24).roundToInt()*24).coerceIn(minimumHeight,2400) else before.height
                            state.preview(id,CardDimensions(span,height,before.heightSized || 'y' in axis))
                        })
                    }
                Box(handle,contentAlignment=Alignment.Center) { Text(if(axis=="x") "↔" else if(axis=="y") "↕" else "◢",color=DeckColors.accent,fontSize=20.sp) }
            }
        }
    }
}
internal data class DashboardCard(val id: String, val span: Int = 12, val height: Int = 260, val minimumWidth: Int = 144, val content: @Composable () -> Unit)
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun DashboardGrid(cards: List<DashboardCard>, state: DashboardLayoutState, modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null, allowReorder: Boolean = true, onReorder: ((String,String) -> Unit)? = null, scrollTag: String? = null) {
    val mode=LocalAchievementMotion.current;val grid=rememberLazyGridState()
    var pendingTarget by remember { mutableStateOf<String?>(null) }
    var dragged by remember { mutableStateOf<String?>(null) };var offset by remember { mutableStateOf(Offset.Zero) };var origin by remember { mutableStateOf(Offset.Zero) }
    val bounds=remember { mutableMapOf<String,Rect>() }
    val order=state.value.order
    val base=if(onReorder!=null) cards else cards.sortedBy { order.indexOf(it.id).takeIf { i->i>=0 } ?: (1000+cards.indexOf(it)) }
    val latestBase by rememberUpdatedState(base)
    val latestReorder by rememberUpdatedState(onReorder)
    val ordered=if(dragged!=null && pendingTarget!=null) base.toMutableList().also { list ->
        val from=list.indexOfFirst { it.id==dragged };val to=list.indexOfFirst { it.id==pendingTarget }
        if(from>=0 && to>=0) { val moved=list.removeAt(from);list.add(to.coerceAtMost(list.size),moved) }
    } else base
    BoxWithConstraints(modifier) {
        val totalWidth=maxWidth;val columns=12
        fun effectiveSpan(card: DashboardCard): Int {
            val minimum=kotlin.math.ceil((card.minimumWidth+12f)/(totalWidth.value+12f)*12).toInt().coerceIn(1,12)
            return state.dimensions(card.id,CardDimensions(card.span,card.height)).span.coerceIn(minimum,12)
        }
        LazyVerticalGrid(GridCells.Fixed(columns),state=grid,modifier=Modifier.fillMaxSize().then(if(scrollTag!=null) Modifier.testTag(scrollTag) else Modifier),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            if(header!=null) item(key="layout-header",span={GridItemSpan(maxLineSpan)}) { header() }
            items(ordered,key={it.id},span={card->GridItemSpan(effectiveSpan(card))}) { card ->
                val reorder=Modifier.semantics {
                    val position=base.indexOfFirst { it.id==card.id }
                    customActions=listOf(-1 to "Move earlier",1 to "Move later").mapNotNull { (direction,label) ->
                        base.getOrNull(position+direction)?.let { target -> CustomAccessibilityAction(label) { if(onReorder!=null) onReorder(card.id,target.id) else state.move(base.map { it.id },card.id,target.id);true } }
                    }
                }.pointerInput(card.id,state,state.editing) {
                    detectDragGestures(onDragStart={p->pendingTarget=null;dragged=card.id;origin=(bounds[card.id]?.topLeft ?: Offset.Zero)+p;offset=Offset.Zero},onDrag={change,amount->change.consume();offset+=amount
                        bounds.entries.firstOrNull { it.key!=card.id && latestBase.any { card -> card.id==it.key } && it.value.contains(origin+offset) }?.key?.let { pendingTarget=it }
                    },onDragCancel={dragged=null;offset=Offset.Zero;pendingTarget=null},onDragEnd={
                        val target=pendingTarget
                        if(target!=null) { if(latestReorder!=null) latestReorder?.invoke(card.id,target) else state.move(latestBase.map { it.id },card.id,target) }
                        dragged=null;offset=Offset.Zero;pendingTarget=null
                    })
                }
                ResizableCardFrame(card.id,state,CardDimensions(effectiveSpan(card),card.height),totalWidth,
                    Modifier.fillMaxWidth().animateItem(fadeInSpec=tween(mode.duration),placementSpec=if(mode==AchievementMotion.FULL) spring() else tween(mode.duration),fadeOutSpec=tween(mode.duration))
                        .onGloballyPositioned { bounds[card.id]=it.boundsInRoot() }.zIndex(if(dragged==card.id) 4f else 0f)
                        .graphicsLayer { if(dragged==card.id) { translationX=offset.x;translationY=offset.y;if(mode==AchievementMotion.FULL) { scaleX=1.02f;scaleY=1.02f;shadowElevation=8.dp.toPx() } } },reorder=if(allowReorder) reorder else null,content=card.content)
            }
        }
    }
}
/** Detail/history panels share the same persisted dimensions and handle implementation. */
@Composable internal fun LayoutPanel(id: String, defaultHeight: Int = 260, content: @Composable () -> Unit) {
    val state=LocalDashboardLayout.current
    if(state==null) { content();return }
    val mode=LocalAchievementMotion.current
    BoxWithConstraints(Modifier.fillMaxWidth().animateContentSize(animationSpec=if(mode==AchievementMotion.FULL) spring() else tween(mode.duration))) {
        val width=maxWidth;val span=state.dimensions(id,CardDimensions(12,defaultHeight)).span
        val w=(width*span/12f).coerceIn(minOf(width,180.dp),width)
        ResizableCardFrame(id,state,CardDimensions(12,defaultHeight),width,Modifier.width(w),content=content)
    }
}

/** One visual summary panel, with independent metric sizing using the shared handles. */
internal data class DashboardMetric(val id: String, val label: String, val value: String, val icon: String? = null, val color: androidx.compose.ui.graphics.Color? = null)
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DashboardMetricStrip(metrics: List<DashboardMetric>) {
    val state=LocalDashboardLayout.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width=maxWidth
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            metrics.chunked(4).forEachIndexed { row, group ->
                if(row>0) HorizontalDivider(color=DeckColors.track)
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    group.forEach { metric ->
                        val default=CardDimensions(12/group.size,140)
                        val span=state?.dimensions(metric.id,default)?.span ?: default.span
                        val w=((width+8.dp)*span/12f-8.dp).coerceIn(minOf(width,72.dp),width)
                        val body: @Composable () -> Unit = {
                            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                metric.icon?.let { BundledCardIcon(it,Modifier.size(24.dp),DeckColors.accent) }
                                Text(metric.value,fontSize=18.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Medium,color=metric.color ?: androidx.compose.ui.graphics.Color.White)
                                Text(metric.label,color=DeckColors.muted,fontSize=10.sp)
                            }
                        }
                        if(state!=null) ResizableCardFrame(metric.id,state,default,width,Modifier.width(w),content=body) else Box(Modifier.width(w)) { body() }
                    }
                }
            }
        }
    }
}
