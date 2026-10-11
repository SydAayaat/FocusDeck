package com.hax.focusdeck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.delay

private val RewardMuted get() = DeckColors.muted
private val RewardBorder get() = DeckColors.border
private val RewardShape = RoundedCornerShape(14.dp)

@Composable
fun RewardScreen(state: DeckState, busy: Boolean, action: (String) -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = DeckColors.accent,
        onPrimary = Color.Black, secondary = DeckColors.secondary, surface = Color.Black, onSurface = Color.White)) {
        RewardScreenContent(state, busy, action)
    }
}

@Composable
private fun RewardScreenContent(state: DeckState, busy: Boolean, action: (String) -> Unit) {
    val plan = state.session ?: return
    val block = plan.blocks.getOrNull(state.index) ?: return
    val result = state.results.lastOrNull()
    val p = result?.performance
    val c = state.competition
    val session = state.phase == "session_reward"
    val reward = if (session) c?.reward else p?.reward
    val unlocked = reward?.actions?.isNotEmpty() == true
    val score = if (session) c?.beatScore else p?.beatScore
    val rank = if (session) c?.rank.orEmpty() else p?.rank.orEmpty()
    val next = if (session) null else plan.blocks.getOrNull(state.index + 1)
    LaunchedEffect(state.revision, busy) {
        if (!busy && state.phase in setOf("reward", "session_reward") && reward?.actions?.isEmpty() == true) {
            delay(2200)
            action("reward_done")
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (session) "SESSION COMPLETE" else "BLOCK COMPLETE", color = Color.White,
            fontSize = 21.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        HorizontalDivider(Modifier.width(22.dp), color = RewardMuted)
        Text(if (session) plan.title else block.title, color = Color.White, fontSize = 18.sp,
            fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
        if (!session && result != null) {
            RewardPanel {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (result.plannedMs != null) { RewardMetric(humanTime(result.plannedMs), "PLANNED", Modifier.weight(1f)); RewardSeparator() }
                    RewardMetric(humanTime(result.activeMs), "ACTIVE", Modifier.weight(1f))
                    RewardSeparator()
                    RewardMetric(humanTime(result.pausedMs), "PAUSED", Modifier.weight(1f))
                    RewardSeparator()
                    RewardMetric(humanTime(p?.wallMs ?: (result.activeMs + result.pausedMs)), "WALL", Modifier.weight(1f))
                }
            }
            val ahead = result.plannedMs?.let { it - result.activeMs }
            if (ahead != null) Text("${humanTime(kotlin.math.abs(ahead))} ${if (ahead >= 0) "AHEAD" else "BEHIND"}",
                color = Color.White, fontSize = 14.sp, letterSpacing = 1.sp)
        } else if (session && c != null) {
            Text("${c.completedFocusBlocks} / ${c.plannedFocusBlocks} focus blocks done" +
                if (c.scoreScope == "none") "" else " · ${c.onOrAheadCount} ${if (c.scoreScope == "countdown_only") "countdown " else ""}on or ahead",
                color = RewardMuted, textAlign = TextAlign.Center)
        }
        RewardPanel {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.05f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (session) if (c?.scoreScope == "countdown_only") "COUNTDOWN SCORE" else if (c?.scoreScope == "none") "SESSION DONE" else "SESSION SCORE" else if (result?.timerMode == "stopwatch") "STOPWATCH DONE" else p?.performanceClass.orEmpty().replace('_', ' ').uppercase(Locale.ROOT),
                        color = Color.White, fontSize = 10.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
                    Text(if (session) if (c?.weighted?.normalizedSessionScore == null) "Score N/A" else "${rewardNumber(c?.weighted?.normalizedSessionScore)} / 100" else "weight ${p?.sessionWeight ?: 1.0}",
                        color = RewardMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                RewardSeparator()
                RewardInlineMetric("PACE", rewardNumber(if (session) c?.averagePace else p?.paceIndex), Modifier.weight(1f))
                RewardSeparator()
                RewardInlineMetric("FOCUS", "${rewardNumber(if (session) c?.focusRatio else p?.focusRatio)}%", Modifier.weight(1f))
            }
        }
        if (score != null) {
            if (session && c?.scoreScope == "countdown_only") Text("COUNTDOWN-ONLY BEATSCORE", color = RewardMuted, fontSize = 12.sp)
            BeatScoreDial(score, rank)
        } else Text("Time-based Pace / BeatScore: N/A", color = RewardMuted, fontSize = 13.sp)
        val benchmark = if (session) c?.previousBenchmark else p?.previousBenchmark
        val pb = if (session) c?.newPersonalBest == true else p?.newPersonalBest == true
        val delta = if (session) c?.pbDelta else p?.pbDelta
        if (score != null) Text(if (benchmark == null) "NO BENCHMARK YET" else
            "${if (pb) "NEW PERSONAL BEST" else "PREVIOUS BEST ${rewardNumber(benchmark.score)}"} · ${rewardNumber(delta)}",
            color = RewardMuted, fontSize = 12.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
        if (p?.recalibrationNeeded == true && !session) {
            Text("Estimate needs recalibration · speed bonus held", color = RewardMuted, fontSize = 12.sp,
                textAlign = TextAlign.Center)
        }
        if (session && c?.scoreScope != "none") c?.weighted?.let { w ->
            Text("Points ${rewardNumber(w.rawWeightedPoints)} · weight ${rewardNumber(w.totalWeight)}\n" +
                w.classCounts.entries.joinToString(" · ") { "${it.value} ${it.key.replace('_', ' ')}" },
                color = RewardMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = RewardBorder)
            Text(if (unlocked) "REWARD UNLOCKED · ${reward!!.tier.replace('_', ' ').uppercase(Locale.ROOT)}" else "RESULT",
                modifier = Modifier.weight(3f),
                color = RewardMuted, fontSize = 10.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
            HorizontalDivider(Modifier.weight(1f), color = RewardBorder)
        }
        RewardPanel {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RewardGlyph(Modifier.size(48.dp))
                RewardSeparator(52)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (unlocked) reward!!.label() else if (session) "SESSION COMPLETE" else "BLOCK COMPLETE",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Light)
                    Text(reward?.reason ?: "No reward assigned.", color = RewardMuted, fontSize = 12.sp)
                }
            }
        }
        reward?.takeIf { it.actions.any { a -> a.kind in physicalKinds } }?.let {
            Text("Acknowledged today before this reward · ${it.dailyRepsBefore["pushups"] ?: 0} pushups · ${it.dailyRepsBefore["squats"] ?: 0} squats · ${it.dailyPhysicalSecondsBefore} physical seconds",
                color = RewardMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        if (next != null) RewardPanel {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NextTaskGlyph(Modifier.size(40.dp))
                RewardSeparator(44)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("NEXT", color = Color.White, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(next.title, color = Color.White, fontSize = 14.sp)
                    Text(if (next.stopwatch) "STOPWATCH" else humanTime(next.plannedMs), color = RewardMuted, fontSize = 12.sp)
                }
                Text("›", color = RewardMuted, fontSize = 30.sp)
            }
        }
        Button(onClick = { action("reward_done") }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = DeckColors.accent, contentColor = Color.Black)) {
            Text(if (unlocked) "REWARD DONE" else "CONTINUE", letterSpacing = 1.sp)
        }
        if (unlocked) OutlinedButton(onClick = { action("reward_continue") }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(50),
            border = androidx.compose.foundation.BorderStroke(1.dp, DeckColors.accent)) {
            Text("CONTINUE", color = Color.White, letterSpacing = 1.sp)
        }
        Text("DONE declares task completion. Reward acknowledgement records the activity; CONTINUE advances without acknowledging it. Result/reset time is outside active and paused totals.",
            color = RewardMuted, fontSize = 11.sp, lineHeight = 17.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

private fun rewardNumber(value: Double?) = value?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "N/A"

@Composable
private fun RewardPanel(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().border(1.dp, RewardBorder, RewardShape)) { content() }
}

@Composable
private fun RewardMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Text(label, color = RewardMuted, fontSize = 9.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RewardInlineMetric(label: String, value: String, modifier: Modifier) {
    // Keep label and value together as in the reference, wrapping on narrow screens
    // or with larger system text rather than clipping the metric.
    FlowRow(modifier.padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RewardMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RewardSeparator(height: Int = 34) {
    VerticalDivider(Modifier.height(height.dp), color = RewardBorder)
}

@Composable
private fun BeatScoreDial(score: Double?, rank: String) {
    // A static gauge: no frame loop or animation while the result is on screen.
    Box(Modifier.widthIn(max = 240.dp).fillMaxWidth().padding(top = 4.dp)
        .aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription = "BeatScore ${rewardNumber(score)} out of 111, rank $rank"
        }) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2 + 3.dp.toPx()
            val diameter = size.minDimension - inset * 2
            val origin = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            // Open bottom, with a small seam at twelve o'clock. Fill clockwise
            // from that seam and wrap to the left, leaving the unfilled portion
            // at the upper left as in the supplied reference.
            val style = Stroke(stroke, cap = StrokeCap.Butt)
            val rightSweep = 138f
            val leftSweep = 132f
            drawArc(RewardBorder, 270f, rightSweep, false, origin, arcSize, style = style)
            drawArc(RewardBorder, 132f, leftSweep, false, origin, arcSize, style = style)
            val sweep = (((score ?: 0.0) / 111.0).coerceIn(0.0, 1.0) * (rightSweep + leftSweep)).toFloat()
            if (sweep > 0f) drawArc(Color.White, 270f, minOf(sweep, rightSweep), false, origin, arcSize, style = style)
            if (sweep > rightSweep) drawArc(Color.White, 132f, sweep - rightSweep, false, origin, arcSize, style = style)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("BEATSCORE", color = RewardMuted, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(rewardNumber(score), color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
            HorizontalDivider(Modifier.width(28.dp), color = RewardMuted)
            Text(rank, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun RewardGlyph(modifier: Modifier) {
    // Generic reward symbol only; reward text never selects or restricts an activity catalog.
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = Stroke(1.5.dp.toPx())
        fun coin(x: Float, y: Float) {
            val top = Offset(s * x, s * y)
            val oval = Size(s * .58f, s * .25f)
            drawOval(Color.White, top, oval, style = stroke)
            drawLine(Color.White, Offset(top.x, top.y + oval.height / 2), Offset(top.x, top.y + oval.height), stroke.width)
            drawLine(Color.White, Offset(top.x + oval.width, top.y + oval.height / 2), Offset(top.x + oval.width, top.y + oval.height), stroke.width)
            drawArc(Color.White, 0f, 180f, false, Offset(top.x, top.y + oval.height / 2), oval, style = stroke)
        }
        coin(.06f, .51f)
        coin(.06f, .36f)
        coin(.34f, .22f)
        coin(.34f, .07f)
    }
}

@Composable
private fun NextTaskGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val line = 1.dp.toPx()
        drawCircle(RewardMuted, s / 2 - line, style = Stroke(line))
        drawRect(Color.White, Offset(s * .32f, s * .27f), Size(s * .36f, s * .46f), style = Stroke(line))
        for (y in listOf(.39f, .5f, .61f)) {
            drawLine(Color.White, Offset(s * .39f, s * y), Offset(s * .61f, s * y), line)
        }
    }
}
