package com.hax.focusdeck

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

internal data class AchievementSegmentFacts(val progressByUnit: Map<String, Double>, val activity: Long,
    val contract: String, val focusBlocks: Long, val focusMs: Long)
internal data class AchievementDetailData(val progressHistory: List<ProgressHistory>,
    val segments: List<AchievementSegmentFacts>, val objectives: List<ObjectiveProgress>,
    val todayStatus: String, val day: AchievementDay?)

/** Read-only presentation snapshot. History scans happen off the UI thread, never on animation frames. */
internal fun achievementDetailData(store: AchievementStore, def: AchievementDefinition,
    stats: AchievementStats, today: String, zone: ZoneId): AchievementDetailData {
    val history = store.progressHistory.filter { it.achievementId == def.id }
    val contractDef = store.definitions.find { it.id == "daily_objectives" }
    val daysBySegment = List(stats.completed.size) { mutableListOf<AchievementDay>() }
    val progressBySegment = List(stats.completed.size) { mutableMapOf<String, Double>() }
    // Closed segments are chronological and non-overlapping. Index once instead of scanning
    // the complete day/progress ledger separately for every historical bar.
    fun segmentFor(date: String): Int? {
        var low = 0; var high = stats.completed.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            val segment = stats.completed[middle]
            if (date < segment.start) high = middle - 1
            else if (date > segment.end) low = middle + 1
            else return middle
        }
        return null
    }
    if (def.kind != "abstinence") {
        store.days.values.forEach { day -> segmentFor(day.date)?.let { daysBySegment[it].add(day) } }
        history.forEach { h -> segmentFor(AchievementEngine.date(h.timestamp, zone))?.let { index ->
            val totals = progressBySegment[index]
            totals[h.unit] = (totals[h.unit] ?: 0.0) + h.delta
        } }
    }
    val facts = stats.completed.mapIndexed { index, segment ->
        val days = daysBySegment[index]
        val contractDays = if (contractDef == null) emptyList() else days.map { AchievementEngine.status(store, contractDef, it.date) }
        val calendarDays = ChronoUnit.DAYS.between(LocalDate.parse(segment.start), LocalDate.parse(segment.end)) + 1
        val met = contractDays.count { it == "MET" }
        val contract = when {
            contractDef == null || contractDays.none { it != "NO_TARGET" } -> "N/A"
            contractDays.any { it == "UNMET" } -> "No"
            contractDays.any { it == "PENDING" } -> "Pending"
            met.toLong() == calendarDays -> "Yes (all days)"
            else -> "Yes (target days)"
        }
        AchievementSegmentFacts(progressBySegment[index].toMap(),
            days.sumOf { it.activities[def.id] ?: 0 }, contract, days.sumOf { it.focusBlocks }, days.sumOf { it.focusMs })
    }
    return AchievementDetailData(history.asReversed(), facts, AchievementEngine.progress(store, today),
        AchievementEngine.status(store, def, today), store.days[today])
}

/** This starts exactly at Current/Best/Record gap/Average; the upper hero stays in AchievementScreen. */
internal fun LazyListScope.achievementDetailSections(store: AchievementStore, def: AchievementDefinition,
    stats: AchievementStats, today: String, zone: ZoneId, data: AchievementDetailData?,
    selected: Int?, select: (Int) -> Unit) {
    if (def.streakEnabled) {
        item(key = "detail-statistics") { LayoutPanel("detail-statistics",180) { DetailStatistics(stats) } }
        if (stats.newRecord) item(key = "detail-record") {
            Text("NEW RECORD", color = AGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        item(key = "detail-policy") { LayoutPanel("detail-policy",200) { DetailPolicy(def, stats) } }
        item(key = "detail-chart") {
            LayoutPanel("detail-chart",260) { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Streak history", fontSize = 22.sp)
                if (stats.completed.isEmpty()) Text("NO HISTORY YET", color = AGrey, fontSize = 12.sp)
                else DetailHistoryChart(stats, selected, select)
            }
        }
        }
        if (selected != null && selected in stats.completed.indices) item(key = "selected-streak") {
            LayoutPanel("selected-streak",240) { Crossfade(selected, animationSpec = tween(LocalAchievementMotion.current.duration), label = "Selected streak") { index ->
                stats.completed.getOrNull(index)?.let { segment ->
                    DetailSegmentPanel(segment, index, stats, data?.segments?.getOrNull(index), zone)
                }
            }
        }
    }
    }
    if (def.kind != "abstinence" && (def.id !in store.indexes || def.streakEnabled)) item(key = "detail-today") { LayoutPanel("detail-today",240) { DetailToday(store, def, today, data) } }
    if (def.streakEnabled) {
        item(key = "streak-heading") { DetailHistoryHeading("Detailed history") }
        if (stats.completed.isEmpty()) item(key = "streak-empty") {
            Text("Completed streaks will appear here.", color = AGrey, fontSize = 12.sp)
        }
        items(stats.completed.indices.reversed().toList(), key = { "streak:$it" }) { index ->
            val segment = stats.completed[index]
            var expanded by rememberSaveable(def.id, index) { mutableStateOf(false) }
            LayoutPanel("streak-${def.id}-$index",160) { DetailSurface(onClick = { select(index); expanded = !expanded }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("#${index + 1}  ${detailDate(segment.start)} – ${detailDate(segment.end)}", fontSize = 12.sp)
                        Text(AchievementEngine.value(segment.value, def.kind == "abstinence"), fontSize = 17.sp)
                    }
                    Text(if (segment.personalRecord) "BEST AT TIME" else "Completed", color = ACyan, fontSize = 9.sp)
                    UiGlyph(if (expanded) "chevron-up" else "chevron-down", Modifier.padding(start = 8.dp).size(16.dp))
                }
                if (expanded) AchievementMetadataLines(segment, stats, store, zone)
            } }
        }
    }
    if (def.progressEnabled) {
        item(key = "progress-heading") { DetailHistoryHeading("Progress history") }
        val history = data?.progressHistory.orEmpty()
        if (history.isEmpty()) item(key = "progress-empty") {
            Text(if (data == null) "Loading history…" else "Progress updates will appear here.", color = AGrey, fontSize = 12.sp)
        }
        items(history, key = { "progress:${it.id}" }) { h ->
            LayoutPanel("progress-${h.id}",160) { DetailSurface {
                Text(detailDate(AchievementEngine.date(h.timestamp, zone)), color = AGrey, fontSize = 11.sp)
                Text("${AchievementEngine.progressValue(h.previousValue)} → ${AchievementEngine.progressValue(h.newValue)} ${h.unit}", fontSize = 15.sp)
                Text("Target ${h.targetValueAtThatTime?.let(AchievementEngine::progressValue) ?: "—"} · ${h.source}", color = AGrey, fontSize = 11.sp)
            } }
        }
    }
}

@Composable private fun DetailSurface(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val mode = LocalAchievementMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && mode != AchievementMotion.OFF) .985f else 1f,
        tween(mode.duration), label = "Detail panel press")
    Surface(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (onClick == null) Modifier else Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)),
        shape = RoundedCornerShape(9.dp), color = Color(0xFF070808), border = BorderStroke(1.dp, ACyan.copy(alpha = .38f))) {
        Column(Modifier.background(Brush.linearGradient(listOf(ACyan.copy(alpha = .025f), Color.Transparent)))
            .animateContentSize(tween(mode.duration)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable private fun DetailStatistics(stats: AchievementStats) {
    val values = listOf("Current" to stats.current.takeIf { stats.initialized },
        "Best" to (stats.previousBest ?: stats.best), "Record gap" to stats.gap, "Average" to stats.average)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 290.dp || fontScale > 1.35f) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            values.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    row.forEachIndexed { index, (label, value) ->
                        if (index > 0) VerticalDivider(Modifier.padding(horizontal = 10.dp).height(34.dp), color = ACyan.copy(alpha = .3f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, color = AGrey, fontSize = 10.sp)
                            Text(AchievementEngine.value(value, stats.definition.kind == "abstinence", label == "Record gap"),
                                color = if (label == "Record gap" && value != null && value != 0.0) {
                                    if (value > 0) AGreen else ARed
                                } else Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DetailPolicy(def: AchievementDefinition, stats: AchievementStats) {
    val text = when {
        !stats.initialized -> "Supply an initial timestamp through GPT to begin this streak."
        def.kind == "abstinence" -> "This streak measures elapsed time since your supplied starting timestamp. A recorded break begins a new streak."
        def.kind == "contract" -> "A day counts as complete when you meet the latest GPT-supplied daily objective for that day. Objectives can include focus blocks, study topics, and other personal targets."
        def.kind == "scheduled" -> "A scheduled day counts when this card’s latest GPT-supplied objective is met. Unscheduled days are neutral and do not break your streak."
        def.kind == "focus_blocks" -> "A day counts when you complete ${if (def.id == "focus_8") 8 else 5} focus blocks with DONE. These are your measured day totals."
        def.kind == "focus_time" -> "A day counts when you reach three hours of measured active focus. Paused time and breaks are excluded."
        else -> "A day counts when this activity is recorded as complete. Your daily contract is evaluated against the latest GPT-supplied objectives for that day."
    }
    DetailSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Canvas(Modifier.size(20.dp)) {
                val r = size.minDimension * .43f
                drawCircle(ACyan, r, center, style = Stroke(1.5.dp.toPx()))
                drawCircle(ACyan, 1.dp.toPx(), center - Offset(0f, 4.dp.toPx()))
                drawLine(ACyan, center - Offset(0f, .5.dp.toPx()), center + Offset(0f, 5.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            }
            Text(text, Modifier.weight(1f), color = AGrey, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

private fun detailDate(date: String): String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
private fun detailChartScale(observed: Double): Double {
    val raw = observed.coerceAtLeast(1.0) * 1.08 / 4
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 1.5, 2.0, 2.5, 5.0, 10.0).first { it >= raw / magnitude } * magnitude
    return step * 4
}

@Composable private fun DetailHistoryChart(stats: AchievementStats, selected: Int?, select: (Int) -> Unit) {
    val mode = LocalAchievementMotion.current
    val entry = remember(stats.definition.id) { Animatable(if (mode == AchievementMotion.OFF) 1f else 0f) }
    LaunchedEffect(stats.definition.id, stats.completed, mode) {
        entry.snapTo(if (mode == AchievementMotion.OFF) 1f else 0f)
        entry.animateTo(1f, tween(mode.duration))
    }
    val observed = maxOf(stats.completed.maxOf { it.value }, stats.previousBest ?: 0.0, stats.average ?: 0.0)
    val hourly = stats.definition.kind == "abstinence" && observed < 1
    val unit = if (hourly) 24.0 else 1.0
    val maximum = detailChartScale(observed * unit) / unit
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = if (stats.completed.size > 32) selected ?: 0 else 0)
    LaunchedEffect(selected, stats.completed.size, mode) {
        if (selected != null && stats.completed.size > 32 && scroll.layoutInfo.visibleItemsInfo.none { it.index == selected }) {
            if (mode == AchievementMotion.FULL) scroll.animateScrollToItem(selected) else scroll.scrollToItem(selected)
        }
    }
    Column {
        Text(if (hourly) "Hours" else "Days", color = AGrey, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Box(Modifier.width(24.dp).height(112.dp)) {
                for (tick in 0..4) Text(AchievementEngine.progressValue(maximum * unit * (4 - tick) / 4),
                    Modifier.align(Alignment.TopEnd).offset(y = (112f * tick / 4 - 7).dp).padding(end = 5.dp), color = AGrey, fontSize = 10.sp)
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                // Capture the outer width before the nested lazy-item scopes.
                val plotWidth = maxWidth
                val slot = plotWidth / stats.completed.size.coerceIn(1, 32)
                val barWidth = (slot * .7f).coerceAtMost(22.dp)
                Column {
                    Box(Modifier.fillMaxWidth().height(112.dp)) {
                        LazyRow(state = scroll, modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                            items(stats.completed.indices.toList(), key = { it }) { index ->
                                val segment = stats.completed[index]
                                Box(Modifier.width(slot).fillMaxHeight().clickable { select(index) }
                                    .semantics { contentDescription = "Select streak ${index + 1}, ${AchievementEngine.value(segment.value, stats.definition.kind == "abstinence")}" },
                                    contentAlignment = Alignment.BottomCenter) {
                                    Canvas(Modifier.width(barWidth).fillMaxHeight()) {
                                        val height = (size.height * segment.value / maximum * entry.value).toFloat().coerceAtLeast(2.dp.toPx())
                                        val top = Offset(0f, size.height - height)
                                        val corners = CornerRadius(2.dp.toPx())
                                        drawRoundRect(Brush.verticalGradient(listOf(ACyan, ACyan.copy(alpha = .76f))), top, Size(size.width, height), corners)
                                        if (selected == index) {
                                            drawRoundRect(ACyan.copy(alpha = .22f), top, Size(size.width, height), corners, style = Stroke(5.dp.toPx()))
                                            drawRoundRect(Color.White, top, Size(size.width, height), corners, style = Stroke(1.4.dp.toPx()))
                                        }
                                    }
                                }
                            }
                        }
                        Canvas(Modifier.matchParentSize()) {
                            drawLine(AGrey.copy(alpha = .55f), Offset.Zero, Offset(0f, size.height), 1.dp.toPx())
                            drawLine(AGrey.copy(alpha = .55f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                            for (tick in 0..4) {
                                val y = size.height * tick / 4
                                drawLine(AGrey.copy(alpha = .55f), Offset(0f, y), Offset(3.dp.toPx(), y), 1.dp.toPx())
                            }
                            fun reference(value: Double) {
                                val y = size.height * (1 - value / maximum).toFloat()
                                drawLine(ACyan, Offset(0f, y), Offset(size.width, y), .8.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                            }
                            stats.previousBest?.let(::reference)
                            stats.average?.let(::reference)
                        }
                        stats.previousBest?.let { value ->
                            Text("Best (${AchievementEngine.progressValue(value * unit)})", Modifier.align(Alignment.TopEnd)
                                .offset(y = (112 * (1 - value / maximum) - 16).dp), color = ACyan, fontSize = 10.sp)
                        }
                        stats.average?.let { value ->
                            val y = 112 * (1 - value / maximum)
                            val close = stats.previousBest?.let { kotlin.math.abs(it - value) / maximum * 112 < 16 } == true
                            Text("Average (${AchievementEngine.progressValue(value * unit)})", Modifier.align(Alignment.TopEnd)
                                .offset(y = (if (close) y + 1 else y - 16).dp), color = ACyan, fontSize = 10.sp)
                        }
                    }
                    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                        val item = scroll.layoutInfo.visibleItemsInfo.find { it.index == selected }
                        if (item != null) {
                            val x = item.offset + item.size / 2f
                            if (x in 0f..size.width) drawPath(Path().apply {
                                moveTo(x, 2.dp.toPx()); lineTo(x - 4.dp.toPx(), size.height); lineTo(x + 4.dp.toPx(), size.height); close()
                            }, ACyan)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DetailSegmentPanel(segment: AchievementSegment, index: Int, stats: AchievementStats,
    facts: AchievementSegmentFacts?, zone: ZoneId) {
    val def = stats.definition
    val abstinence = def.kind == "abstinence"
    val difference = stats.average?.let { segment.value - it }
    val unit = def.progress?.unit ?: "units"
    val total = facts?.progressByUnit?.get(unit)
    val progressLabel = if (total != null && total < 0) {
        if (unit == "words") "Words change" else "Progress change"
    } else if (unit == "words") "Words completed" else "Progress completed"
    val fontScale = LocalDensity.current.fontScale
    DetailSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${detailDate(segment.start)} – ${detailDate(segment.end)}", Modifier.weight(1f), fontSize = 14.sp)
            Text("STREAK #${index + 1}", Modifier.padding(start = 8.dp), color = AGrey, fontSize = 9.sp)
        }
        // Retain the selected-streak accessible summary used by history navigation.
        Box(Modifier.semantics { contentDescription = "Streak #${index + 1} · ${AchievementEngine.value(segment.value, abstinence)}" }) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val twoColumns = maxWidth >= 285.dp && fontScale <= 1.25f
                val left: @Composable ColumnScope.() -> Unit = {
                    DetailFact("Duration", AchievementEngine.value(segment.value, abstinence))
                    if (def.progressEnabled) DetailFact(progressLabel, total?.let { "${AchievementEngine.progressValue(it)} $unit" } ?: "N/A")
                    else if (!abstinence && def.kind in setOf("daily", "scheduled")) DetailFact("Objectives completed", facts?.activity?.toString() ?: "N/A")
                    DetailFact("Personal record", if (segment.personalRecord) "Yes" else "No", badge = segment.personalRecord)
                }
                val right: @Composable ColumnScope.() -> Unit = {
                    if (!abstinence) DetailFact("Day-contract met", facts?.contract ?: "N/A", badge = facts?.contract?.startsWith("Yes") == true)
                    DetailFact("Difference from average", AchievementEngine.value(difference, abstinence, true),
                        color = if (difference == null || difference == 0.0) Color.White else if (difference > 0) AGreen else ARed)
                    if (def.progressEnabled) DetailFact("Daily average (streak)", total?.let {
                        "${AchievementEngine.progressValue(it / segment.value.coerceAtLeast(1.0))} $unit"
                    } ?: "N/A")
                }
                if (twoColumns) Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = left)
                    VerticalDivider(Modifier.padding(horizontal = 12.dp).height(66.dp), color = ACyan.copy(alpha = .3f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = right)
                } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { left(); right() }
            }
        }
        if (abstinence) {
            segment.startMs?.let { Text("Start · ${Instant.ofEpochMilli(it).atZone(zone)}", color = AGrey, fontSize = 10.sp) }
            segment.endMs?.let { Text("End · ${Instant.ofEpochMilli(it).atZone(zone)}", color = AGrey, fontSize = 10.sp) }
        }
        if (def.progressEnabled && facts?.progressByUnit?.keys?.any { it != unit } == true) {
            Text("Recorded unit changes · " + facts.progressByUnit.entries.joinToString(" · ") { "${AchievementEngine.progressValue(it.value)} ${it.key}" }, color = AGrey, fontSize = 10.sp)
        }
    }
}

@Composable private fun DetailFact(label: String, value: String, color: Color = Color.White, badge: Boolean? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.weight(1f), color = AGrey, fontSize = 10.sp)
        if (badge != null && value != "N/A" && value != "Pending") {
            if (badge) UiGlyph("check-ring", Modifier.size(15.dp)) else Canvas(Modifier.size(15.dp)) {
                val d = size.minDimension * .18f
                drawCircle(AGrey, size.minDimension * .42f, center, style = Stroke(1.2.dp.toPx()))
                drawLine(AGrey, center - Offset(d, d), center + Offset(d, d), 1.2.dp.toPx(), StrokeCap.Round)
                drawLine(AGrey, center + Offset(d, -d), center + Offset(-d, d), 1.2.dp.toPx(), StrokeCap.Round)
            }
        }
        Text(value, color = color, fontSize = 11.sp)
    }
}

@Composable private fun DetailHistoryHeading(title: String) {
    DetailSurface {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BundledCardIcon("history", Modifier.size(24.dp), Color.White)
            Text(title, Modifier.weight(1f), fontSize = 15.sp)
            UiGlyph("chevron-down", Modifier.size(17.dp))
        }
    }
}

@Composable private fun DetailToday(store: AchievementStore, def: AchievementDefinition, today: String, data: AchievementDetailData?) {
    val actual = data?.day
    val own = data?.objectives.orEmpty().filter { it.objective.achievementId == def.id }
    val focus = data?.objectives.orEmpty().filter { it.objective.kind in setOf("focus_blocks", "focus_time") }
    val daily = if (def.kind == "contract") data?.objectives.orEmpty() else own
    val status = when {
        data == null -> "Loading…"
        !def.streakEnabled -> if (def.progress?.complete == true) "Complete" else "In progress"
        data.todayStatus == "MET" -> "On track"
        data.todayStatus == "NO_TARGET" -> "No target · neutral day"
        data.todayStatus == "UNMET" -> "Not met"
        else -> "Pending"
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today", Modifier.weight(1f), fontSize = 22.sp)
            Text(detailDate(today), color = AGrey, fontSize = 11.sp)
        }
        Text(if (def.streakEnabled) "Complete today’s objectives to extend your current streak." else "Your cumulative goal and latest recorded progress.", color = AGrey, fontSize = 12.sp)
        DetailSurface {
            if (def.progressEnabled) {
                val p = def.progress
                DetailTodayRow(def.title, "${AchievementEngine.progressValue(p?.currentValue ?: 0.0)} / ${p?.targetValue?.let(AchievementEngine::progressValue) ?: "—"} ${p?.unit ?: "units"}",
                    leading = { CardIconView(def, store, Modifier.size(23.dp), ACyan) },
                    detail = { Text(if (p?.complete == true) "Goal complete" else if (p?.targetValue == null) "Total not yet supplied" else "Cumulative progress across all recorded updates", color = AGrey, fontSize = 11.sp) })
                if (def.streakEnabled) HorizontalDivider(color = ACyan.copy(alpha = .18f))
            }
            if (def.streakEnabled) {
                val met = data?.todayStatus == "MET"
                val fraction = when {
                    daily.isNotEmpty() -> daily.count { it.met }.toFloat() / daily.size
                    met -> 1f
                    else -> 0f
                }
                val objectiveValue = when {
                    data == null -> "Loading…"
                    met -> "Done"
                    def.kind == "focus_blocks" -> "${actual?.focusBlocks ?: 0} / ${if (def.id == "focus_8") 8 else 5}"
                    def.kind == "focus_time" -> "${humanTime(actual?.focusMs ?: 0)} / 3h"
                    daily.isNotEmpty() -> "${daily.count { it.met }} / ${daily.size}"
                    data.todayStatus == "NO_TARGET" -> "No target"
                    else -> "Pending"
                }
                DetailTodayRow("Daily objective", objectiveValue, fraction, done = met, leading = { DetailMiniRing(fraction) }, detail = {
                    if (daily.isEmpty()) Text(when (def.kind) {
                        "focus_blocks", "focus_time" -> "Measured day totals; DONE completes focus blocks."
                        "contract", "scheduled" -> "No GPT objective supplied for this card today."
                        else -> "${actual?.activities?.get(def.id) ?: 0} activity completion(s) recorded today."
                    }, color = AGrey, fontSize = 11.sp)
                    else {
                        if (def.kind == "daily") Text("${actual?.activities?.get(def.id) ?: 0} activity completion(s) recorded. Daily contract objectives:", color = AGrey, fontSize = 11.sp)
                        daily.forEach { p -> Text(detailObjectiveText(p, store), color = AGrey, fontSize = 11.sp) }
                    }
                })
                focus.forEachIndexed { index, p ->
                    HorizontalDivider(color = ACyan.copy(alpha = .18f))
                    val fractionFocus = (p.actual.toDouble() / p.objective.minimum.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
                    DetailTodayRow(if (p.objective.kind == "focus_time") "Active focus" else "Focus blocks",
                        if (p.objective.kind == "focus_time") "${humanTime(p.actual)} / ${humanTime(p.objective.minimum)}" else "${p.actual} / ${p.objective.minimum}",
                        fractionFocus, leading = { DetailMiniRing(fractionFocus) },
                        detail = { Text("Day total · " + detailObjectiveText(p, store), color = AGrey, fontSize = 11.sp) }, key = "focus-$index")
                }
                if (focus.isEmpty() && def.kind !in setOf("focus_blocks", "focus_time")) {
                    HorizontalDivider(color = ACyan.copy(alpha = .18f))
                    DetailTodayRow("Day focus", "${actual?.focusBlocks ?: 0} blocks · ${humanTime(actual?.focusMs ?: 0)}",
                        leading = { BundledCardIcon("timer", Modifier.size(23.dp), ACyan) },
                        detail = { Text("Measured across all focus sessions today.", color = AGrey, fontSize = 11.sp) })
                }
            }
            HorizontalDivider(color = ACyan.copy(alpha = .18f))
            DetailTodayRow("Status", status, valueColor = if (status in setOf("On track", "Complete")) ACyan else AGrey,
                leading = { BundledCardIcon("target", Modifier.size(23.dp), ACyan) },
                detail = { Text(if (!def.streakEnabled) "Cumulative progress is independent of daily streak completion."
                    else if (data?.todayStatus == "NO_TARGET") "This day is neutral and does not break the streak."
                    else "${def.title} · $status", color = AGrey, fontSize = 11.sp) })
        }
    }
}

private fun detailObjectiveText(p: ObjectiveProgress, store: AchievementStore): String {
    val name = p.objective.text.ifBlank { when (p.objective.kind) {
        "focus_blocks" -> "Focus blocks"
        "focus_time" -> "Active focus"
        else -> store.definitions.find { it.id == p.objective.achievementId }?.title ?: "Activity"
    } }
    val value = if (p.objective.kind == "focus_time") "${humanTime(p.actual)} / ${humanTime(p.objective.minimum)}" else "${p.actual} / ${p.objective.minimum}"
    return "$name · $value${if (p.met) " · Done" else ""}"
}

@Composable private fun DetailTodayRow(label: String, value: String, fraction: Float? = null,
    done: Boolean = false, valueColor: Color = if (done) ACyan else Color.White, key: String = label,
    leading: @Composable () -> Unit, detail: @Composable () -> Unit) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    val mode = LocalAchievementMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && mode != AchievementMotion.OFF) .985f else 1f,
        tween(mode.duration), label = "Today row press")
    Column(Modifier.fillMaxWidth().animateContentSize(tween(mode.duration))) {
        Row(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null) { expanded = !expanded }.padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) { leading() }
            Text(label, Modifier.weight(1f), fontSize = 13.sp)
            if (done) UiGlyph("check-ring", Modifier.size(18.dp))
            Text(value, color = valueColor, fontSize = 12.sp)
            if (fraction != null && !done) DetailMiniRing(fraction, Modifier.size(19.dp))
            if (expanded) UiGlyph("chevron-down", Modifier.size(16.dp)) else BundledCardIcon("chevron-right", Modifier.size(16.dp), ACyan)
        }
        if (expanded) Box(Modifier.fillMaxWidth().padding(start = 37.dp, top = 8.dp, bottom = 3.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { detail() }
        }
    }
}

@Composable private fun DetailMiniRing(fraction: Float, modifier: Modifier = Modifier.size(23.dp)) {
    val value by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(LocalAchievementMotion.current.duration), label = "Today progress")
    Canvas(modifier) {
        val r = size.minDimension * .4f
        val stroke = size.minDimension * .14f
        drawCircle(ACyan.copy(alpha = .22f), r, center, style = Stroke(stroke))
        drawArc(ACyan, -90f, value * 360f, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
