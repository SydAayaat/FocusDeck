package com.hax.focusdeck

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReportDetailsDialog(report: Report, dismiss: () -> Unit, copy: () -> Unit,
    delivered: Boolean = false, online: Boolean = false, send: (() -> Unit)? = null) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogNavigationRefresh()
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            EntryMotion(Modifier.fillMaxSize()) { ReportScreen(report, dismiss, delivered, online, send, copy) }
        }
    }
}

private fun reportNumber(value: Double?): String = value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "N/A"
@Composable
private fun ReportScreen(report: Report, dismiss: () -> Unit, delivered: Boolean, online: Boolean, send: (() -> Unit)?, copy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saveMessage by remember(report.key) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var payload by remember { mutableStateOf("") }
    var timelineOpen by remember(report.key) { mutableStateOf(true) }
    var allEvents by remember(report.key) { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val eventFmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val range = remember(report, zone) {
        val start = Instant.ofEpochMilli(report.startedAtMs).atZone(zone)
        val finish = Instant.ofEpochMilli(report.finishedAtMs).atZone(zone)
        "${start.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} · ${start.format(fmt)} – ${finish.format(if (start.toLocalDate() == finish.toLocalDate()) fmt else DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"))}"
    }
    val saveReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            saving = true
            try {
                val snapshot = payload
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open report destination")
                    output.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) }
                }
                saveMessage = "Full report saved. You can import this JSON in Study history."
            } catch (e: Exception) { saveMessage = "Report could not save: ${e.message}" }
            finally { saving = false }
        }
    }
    val behavior by produceState<BehaviorMetrics?>(null, report) {
        value = withContext(Dispatchers.Default) { TimelineAnalysis.metrics(report) }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "heading") {
            Box(Modifier.fillMaxWidth()) {
                IconButton(dismiss, Modifier.align(Alignment.TopStart).size(36.dp)) { UiGlyph("close", Modifier.size(22.dp), DeckColors.muted) }
                Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(report.title, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text(range, color = DeckColors.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (report.status == "complete") "Session Complete" else report.statusLabel, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                CompletionOrbit(report.status == "complete", Modifier.size(width = 260.dp, height = 174.dp))
                Text(report.outcomeSummary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        item(key = "summary") {
            val c = report.competition
            RefCard(Modifier.fillMaxWidth(), accented = true) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ReportMetric("Planned", humanTime(report.plannedMs), "calendar", Modifier.weight(1f))
                    VerticalDivider(Modifier.height(42.dp), color = DeckColors.track)
                    ReportMetric("Active focus", humanTime(report.focusedMs), "clock", Modifier.weight(1f))
                    VerticalDivider(Modifier.height(42.dp), color = DeckColors.track)
                    ReportMetric("Paused", humanTime(report.pausedMs), "pause", Modifier.weight(1f))
                }
                HorizontalDivider(color = DeckColors.track)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ReportMetric("Wall time", humanTime(report.wallMs), "hourglass", Modifier.weight(1f))
                    VerticalDivider(Modifier.height(42.dp), color = DeckColors.track)
                    ReportMetric("Pace", reportNumber(c?.averagePace), "activity", Modifier.weight(1f))
                    VerticalDivider(Modifier.height(42.dp), color = DeckColors.track)
                    ReportMetric("BeatScore", reportNumber(c?.beatScore), "target", Modifier.weight(1f))
                }
                HorizontalDivider(color = DeckColors.border)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HaloRing(((c?.focusRatio ?: 0.0) / 100.0).toFloat(), Modifier.size(86.dp)) {
                        Text(c?.focusRatio?.let { "${it.toInt()}%" } ?: "N/A", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RefLabel("OVERALL FOCUS RATIO")
                        Text(c?.focusRatio?.let { "${reportNumber(it)}%" } ?: "N/A", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        if (c != null) {
                            Text("${if (c.scoreScope == "countdown_only") "Countdown-only " else ""}BeatScore ${reportNumber(c.beatScore)} · ${c.completedFocusBlocks} / ${c.plannedFocusBlocks} completed", color = DeckColors.muted, fontSize = 11.sp)
                            c.weighted?.let { w -> Text(if (c.scoreScope == "none") "Time-based score · N/A"
                                else "${if (c.scoreScope == "countdown_only") "Countdown-only score" else "Session score"} ${reportNumber(w.normalizedSessionScore)}/100 points",
                                color = DeckColors.muted, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
        item(key = "blocks-label") {
            Row(Modifier.fillMaxWidth()) { RefLabel("SESSION BLOCKS", Modifier.weight(1f)); RefLabel("${report.blocks.size} BLOCKS") }
        }
        itemsIndexed(report.blocks, key = { index, block -> "block-$index-${block.id}" }) { _, block -> ReportBlock(block) }
        item(key = "timeline-label") { RefLabel("DETAILED TIMELINE", Modifier.padding(top = 8.dp)) }
        item(key = "timeline") {
            RefCard(Modifier.fillMaxWidth(), onClick = { timelineOpen = !timelineOpen }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UiGlyph("clock", Modifier.size(26.dp), DeckColors.muted); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Detailed timeline", fontSize = 15.sp)
                        Text("${report.events.size} events · Session start to ${if (report.status == "complete") "completion" else "stop"}", color = DeckColors.muted, fontSize = 11.sp)
                    }
                    UiGlyph(if (timelineOpen) "chevron-up" else "chevron-down", Modifier.size(18.dp), DeckColors.muted)
                }
                if (timelineOpen) {
                    HorizontalDivider(color = DeckColors.track)
                    if (report.events.isEmpty()) Text("This older session has no individual pause/resume timestamps.", color = DeckColors.muted, fontSize = 12.sp)
                    report.events.take(5).forEach { event ->
                        ReportEvent(report, event, zone, eventFmt)
                        HorizontalDivider(color = DeckColors.track.copy(alpha = .5f))
                    }
                    if (report.events.size > 5) TextButton(onClick = { allEvents = !allEvents }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (allEvents) "Show fewer events  ⌃" else "Show ${report.events.size - 5} more events  ⌄", color = DeckColors.muted, fontSize = 11.sp)
                    }
                }
            }
        }
        // Expanded events remain lazy, with no arbitrary truncation of stored history.
        if (timelineOpen && allEvents) itemsIndexed(report.events.drop(5), key = { index, event -> "event-$index-${event.seq}" }) { _, event ->
            RefCard(Modifier.fillMaxWidth()) { ReportEvent(report, event, zone, eventFmt) }
        }
        item(key = "timeline-info") {
            behavior?.let { b -> if (b.pauseCount != null) Text("${b.pauseCount} pauses · ${b.extensionCount} extensions · longest uninterrupted ${humanTime(b.longestActiveMs)}",
                color = DeckColors.muted, fontSize = 11.sp) }
            Text("${zone.id} · Timeline coverage: ${report.timelineCoverage}", color = DeckColors.muted, fontSize = 10.sp)
            ReportExtraDetails(report)
        }
        item(key = "actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RefAction("SAVE REPORT", "download", Modifier.weight(1f), enabled = !saving) {
                    scope.launch {
                        saving = true
                        try {
                            payload = withContext(Dispatchers.Default) { WireJson.encodeToString(report) }
                            val safeId = report.sessionId.filter { it.isLetterOrDigit() || it == '-' }.take(48).ifEmpty { "session" }
                            saveReport.launch("focusdeck-$safeId-${report.startedAtMs}.json")
                        } catch (e: Exception) { saveMessage = "Report could not prepare: ${e.message}" }
                        finally { saving = false }
                    }
                }
                RefAction("COPY FULL REPORT", "copy", Modifier.weight(1f), onClick = copy)
            }
            if (saveMessage.isNotBlank()) Text(saveMessage, color = DeckColors.muted, fontSize = 12.sp)
            if (send != null) {
                Text(if (delivered) "✓ Report saved on desktop" else if (online) "Report syncing to desktop…" else "Report saved locally · syncs when desktop reconnects", color = DeckColors.muted, fontSize = 11.sp)
                TextButton(send) { Text("SEND REPORT", fontSize = 11.sp) }
            }
        }
    }
}

@Composable private fun ReportMetric(label: String, value: String, icon: String, modifier: Modifier) {
    Row(modifier.padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UiGlyph(icon, Modifier.size(19.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = DeckColors.muted, fontSize = 10.sp)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}
@Composable private fun ReportExtraDetails(report: Report) {
    var expanded by remember(report.key) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "LESS DETAILS  ⌃" else "MORE DETAILS  ⌄", color = DeckColors.muted, fontSize = 10.sp) }
    if (expanded) {
        Text("ID · ${report.sessionId}\nActual active ${humanTime(report.activeMs)} · Breaks ${humanTime(report.breakMs)} · Completed focus ${humanTime(report.completedFocusedMs)}\nOvertime ${humanTime(report.overtimeMs)} · Known timed planned portion ${humanTime(report.timedPlannedMs ?: report.plannedMs)}", color = DeckColors.muted, fontSize = 11.sp)
        report.competition?.let { c ->
            Text("${c.rank.orEmpty()} · Reward ${c.reward.label()} · acknowledged ${c.reward.acknowledged}", color = DeckColors.muted, fontSize = 11.sp)
            c.weighted?.let { w -> Text("Points ${w.rawWeightedPoints} / weight ${w.totalWeight}\n" + w.classCounts.entries.joinToString(" · ") { "${it.value} ${it.key.replace('_', ' ')}" }, color = DeckColors.muted, fontSize = 11.sp) }
        }
    }
}
@Composable private fun ReportBlock(block: BlockResult) {
    var expanded by remember(block.id) { mutableStateOf(false) }
    RefCard(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UiGlyph(if (block.outcome == "done") "check-ring" else "clock", Modifier.size(27.dp))
            Column(Modifier.weight(1f)) {
                Text(block.title, fontSize = 14.sp)
                Text(when (block.outcome) { "done" -> "Completed · DONE confirmed"; "skipped" -> "Skipped · no completion credit"; "stopped" -> "Ended early · no completion credit"; else -> block.outcome.replace('_', ' ') },
                    color = when (block.outcome) { "skipped" -> Color(0xFFFFC857); "stopped" -> Color(0xFFFF6B6B); else -> DeckColors.muted }, fontSize = 11.sp)
            }
            Text(humanTime(block.activeMs), fontSize = 13.sp)
            Text(if (expanded) "⌃" else "›", color = DeckColors.muted, fontSize = 22.sp)
        }
        if (expanded) {
            HorizontalDivider(color = DeckColors.track)
            Text("${block.timerMode} · ${block.type} · planned ${humanTime(block.plannedMs)}\nPaused ${humanTime(block.pausedMs)} · excess ${humanTime(block.overtimeMs)}", color = DeckColors.muted, fontSize = 11.sp)
            block.performance?.let { p -> Text("BeatScore ${reportNumber(p.beatScore)} ${p.rank.orEmpty()} · ${p.performanceClass ?: p.quality} · weight ${p.sessionWeight}\n${p.reward.label()} · acknowledged ${p.reward.acknowledged}", color = DeckColors.muted, fontSize = 11.sp) }
        }
    }
}
@Composable private fun ReportEvent(report: Report, event: TimelineEvent, zone: ZoneId, fmt: DateTimeFormatter) {
    val kind = when (event.kind) {
        "session_started" -> "Session started"
        "block_started" -> "Block ${event.blockIndex + 1} started · ${report.blocks.getOrNull(event.blockIndex)?.title.orEmpty()}"
        "block_done" -> "Block ${event.blockIndex + 1} completed"
        "block_skipped" -> "Block ${event.blockIndex + 1} skipped"
        "block_stopped" -> "Block ${event.blockIndex + 1} ended early"
        "session_stopped" -> "Session ended"
        "session_complete" -> "Session completed"
        else -> event.kind.replace('_', ' ').replaceFirstChar { it.titlecase() }
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 4.dp).size(6.dp).background(when(event.kind) { "session_started", "session_complete" -> DeckColors.success; "block_started" -> DeckColors.accent; else -> DeckColors.muted.copy(alpha = .65f) }, CircleShape))
        Text(Instant.ofEpochMilli(event.atMs).atZone(zone).format(fmt), color = DeckColors.muted, fontSize = 10.sp)
        Text(kind, color = DeckColors.muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
        if (event.kind == "block_started") report.blocks.getOrNull(event.blockIndex)?.let { Text(humanTime(it.activeMs), color = DeckColors.muted, fontSize = 10.sp) }
    }
}
@Composable private fun CompletionOrbit(complete: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        // The art contains only atmospheric light, tilted orbits and two moons.
        // Ring/check remain native, so stopped reports never inherit a baked completion mark.
        ThemedArtwork(R.drawable.session_orbit_art, Modifier.fillMaxSize())
        HaloRing(if (complete) 1f else 0f, Modifier.size(128.dp), illuminate = complete) {
            if (complete) UiGlyph("check", Modifier.size(48.dp)) else Text("—", color = DeckColors.accent, fontSize = 40.sp)
        }
    }
}
