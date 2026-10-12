package com.hax.focusdeck

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.CornerRadius
import kotlin.math.ceil
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HistoryWhite get() = if (DeckColors.palette.id == "monochrome") Color(0xFFF4F4F4) else Color(0xFFF1F4F6)
private val HistoryGrey get() = DeckColors.muted
private val HistoryCyan get() = DeckColors.accent
private val HistoryYellow get() = DeckColors.secondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyHistoryDialog(repo: DeckRepository, dismiss: () -> Unit, copy: (Report) -> Unit, initialPeriod: HistoryPeriod = HistoryPeriod.WEEK, initialExpanded: Boolean=false, home: () -> Unit = dismiss, achievements: () -> Unit = dismiss, focus: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val achievementStore by repo.achievements.collectAsStateWithLifecycle()
    val connection by repo.connection.collectAsStateWithLifecycle()
    val reports by repo.history.collectAsStateWithLifecycle()
    val stored by repo.display.collectAsStateWithLifecycle()
    val historyNotice by repo.historyNotice.collectAsStateWithLifecycle()
    var period by remember { mutableStateOf(initialPeriod) }
    val observedClock by repo.now.collectAsStateWithLifecycle()
    val zoneId by repo.displayZone.collectAsStateWithLifecycle()
    var anchor by remember { mutableStateOf(Instant.ofEpochMilli(observedClock.wall).atZone(ZoneId.of(zoneId)).toLocalDate()) }
    val zone = ZoneId.of(zoneId)
    val viewer = stored.pairing?.viewer == true
    val today = Instant.ofEpochMilli(observedClock.wall).atZone(zone).toLocalDate()
    val summaryState by produceState<HistorySummary?>(null, reports, period, anchor, zone) {
        value = withContext(Dispatchers.Default) { StudyHistory.summarize(reports, period, anchor, zone) }
    }
    val allTimeFocus by produceState(0L, reports) { value = withContext(Dispatchers.Default) { reports.sumOf { it.focusedMs } } }
    val calendar by produceState<CalendarDataset?>(null,reports,stored.state) { value=withContext(Dispatchers.Default) { CalendarAnalytics.dataset(reports,stored.state) } }
    val summary = summaryState
    val window = remember(period, anchor) { StudyHistory.window(period, anchor) }
    var pickingDate by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var csvPayload by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Report?>(null) }
    var deleteAll by remember { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<Report?>(null) }
    LaunchedEffect(viewer) { if (viewer) { deleteAll = false; deleteTarget = null } }
    LaunchedEffect(reports) {
        if (selectedReport?.let { selected -> reports.none { it.key == selected.key } } == true) selectedReport = null
        if (deleteTarget?.let { selected -> reports.none { it.key == selected.key } } == true) deleteTarget = null
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            exporting = true
            try {
                val snapshot = csvPayload
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open export destination")
                    output.use { it.write(("\uFEFF" + snapshot).toByteArray(Charsets.UTF_8)) }
                }
                message = "CSV saved to your chosen location."
            } catch (e: Exception) { message = "CSV could not save: ${e.message}" }
            finally { exporting = false }
        }
    }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                repo.historyFolder(uri.toString())
                message = "Automatic folder saving enabled. Choose a local folder to keep files on this phone."
            } catch (e: Exception) { message = "Could not use this folder: ${e.message}" }
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            exporting = true
            try {
                val incoming = withContext(Dispatchers.IO) {
                    val raw = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(20 * 1024 * 1024 + 1) }
                        ?: error("Cannot open history file")
                    require(raw.size <= 20 * 1024 * 1024) { "Import is larger than 20 MiB" }
                    val element = WireJson.parseToJsonElement(raw.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
                    if (element is JsonArray) element.map { WireJson.decodeFromJsonElement<Report>(it) }
                    else listOf(WireJson.decodeFromJsonElement<Report>(element))
                }
                message = "Imported ${repo.importHistory(incoming)} new sessions. Existing attempts were kept."
            } catch (e: Exception) { message = "Import could not complete: ${e.message}. Any reports already saved are retained." }
            finally { exporting = false }
        }
    }

    CompositionLocalProvider(LocalPrimaryNavigation provides PrimaryNavigation("analytics",home,achievements,{},focus)) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogNavigationRefresh()
        Surface(Modifier.fillMaxSize(), color = DashboardBackground) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(dashboardViewportInsets()).padding(horizontal=OmssGeometry.analyticsGutter.dp)) {
            Box(Modifier.weight(1f)) {
            EntryMotion(Modifier.fillMaxSize()) {
            if (summary == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HistoryCyan) }
            else if(calendar != null) AnalyticsDashboard(calendar!!,reports,period,anchor,{period=it},{anchor=it},
                Modifier.fillMaxSize(),store=achievementStore,initialExpanded=initialExpanded,copyPerformance={selectedPeriod,selectedAnchor->
                    scope.launch {
                        val text=withContext(Dispatchers.Default) { PerformanceAnalytics.export(PerformanceAnalytics.report(CalendarAnalytics.dataset(repo.history.value,repo.display.value.state),selectedPeriod,selectedAnchor)) }
                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("FocusDeck performance",text))
                        message="Factual performance report copied for GPT."
                    }
                },openReport={selectedReport=it},copy=copy,delete=if(viewer) null else ({deleteTarget=it}),header={
                    if (viewer) TabletViewerBanner(repo)
                    AnalyticsHeader(period,anchor,{period=it},{anchor=it},connection,{pickingDate=true},{anchor=today},dismiss,home,achievements)
                },archiveActions={
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            RefAction("EXPORT CSV", "download", Modifier.fillMaxWidth(), enabled = !exporting) { exportMenu = true }
                            DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                                listOf("Selected period attempts (full tasks)" to (reports.filter { report -> calendar!!.days.values.any { d -> (window.start==null || d.date>=window.start) && (window.end==null || d.date<window.end) && report.key in d.sessions } } to "blocks"),
                                    "All tasks" to (reports to "blocks"), "All sessions" to (reports to "sessions"),
                                    "All events" to (reports to "events"), "All activity intervals" to (reports to "intervals")).forEach { (label, selection) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        exportMenu = false
                                        scope.launch {
                                            exporting = true
                                            try {
                                                csvPayload = withContext(Dispatchers.IO) {
                                                    if (selection.second in listOf("events", "intervals")) TimelineCsv.encode(selection.first, intervals = selection.second == "intervals")
                                                    else HistoryCsv.encode(selection.first, sessions = selection.second == "sessions")
                                                }
                                                saveCsv.launch("focusdeck-${selection.second}-${LocalDate.now()}.csv")
                                            } catch (e: Exception) { message = "CSV could not prepare: ${e.message}" }
                                            finally { exporting = false }
                                        }
                                    })
                                }
                            }
                        }
                        if (!viewer) RefAction("IMPORT JSON", "upload", Modifier.weight(1f), enabled = !exporting) { importJson.launch(arrayOf("application/json", "text/plain")) }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!viewer) RefAction(if (stored.historyTreeUri == null) "CHOOSE AUTOMATIC LOG FOLDER" else "CHANGE AUTOMATIC LOG FOLDER",
                        "folder", Modifier.fillMaxWidth(), enabled = !exporting) { chooseFolder.launch(null) }
                    Spacer(Modifier.height(8.dp))
                    Text(if (viewer) "Read-only phone history. Export copies locally; imports and deletion stay on the phone/PC." else if (stored.historyTreeUri == null) "All sessions and CSV logs save automatically inside the app. Choose a folder for visible copies on phone storage."
                        else "Automatic phone folder saving enabled: JSON records + session, task, event and activity-interval CSV logs.", color = HistoryGrey, fontSize = 12.sp)
                    if (!viewer && historyNotice.isNotBlank()) {
                        Text(historyNotice, color = HistoryYellow, fontSize = 12.sp)
                        TextButton(onClick = { scope.launch { repo.retryHistoryFiles() } }) { Text("RETRY LOG SAVING") }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!viewer) RefAction("DELETE ALL LOGS", "trash", Modifier.fillMaxWidth(), color = DeckColors.error) { deleteAll = true }
                    Spacer(Modifier.height(8.dp))
                    if (!viewer && stored.deletions.revision.isNotEmpty() && stored.deletions.revision != stored.deletionDelivered)
                        Text("Deletion saved · PC sync pending", color = HistoryYellow, fontSize = 12.sp)
                    if (message.isNotBlank()) Text(message, color = HistoryGrey, fontSize = 12.sp)
                    Text("Recorded timer activity is not proof of attention or learning quality.", color = HistoryGrey, fontSize = 11.sp)
                })
            }
            }
            if(!LocalDeckImmersive.current) DashboardNavigation("analytics",home,achievements,{},focus)
            }
        }
    }
    } // Main destination callbacks dismiss this dialog before selecting their existing route.
    if (!viewer && (deleteAll || deleteTarget != null)) DeckAlertDialog(
        onDismissRequest = { deleteAll = false; deleteTarget = null },
        title = { Text(if (deleteAll) "Delete all previous logs?" else "Delete this session?") },
        text = { Text("Removes study records and regenerates automatic CSV logs on this phone, its chosen log folder, and the paired PC when synced. Independently exported copies are retained. The current active session continues.") },
        confirmButton = { TextButton(onClick = {
            val key = if (deleteAll) null else deleteTarget?.key
            deleteAll = false; deleteTarget = null; selectedReport = null
            scope.launch {
                try { repo.deleteHistory(key); message = "Deletion saved. Automatic files update and PC sync follows." }
                catch (e: Exception) { message = "Deletion could not save: ${e.message}" }
            }
        }) { Text("DELETE", color = DeckColors.error) } },
        dismissButton = { TextButton(onClick = { deleteAll = false; deleteTarget = null }) { Text("CANCEL") } }
    )
    if(pickingDate) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=anchor.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={pickingDate=false},confirmButton={TextButton({picker.selectedDateMillis?.let { anchor=Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() };pickingDate=false}) { Text("SELECT") }},dismissButton={TextButton({pickingDate=false}) { Text("CANCEL") }}) { DatePicker(picker) }
    }
    selectedReport?.let { report -> ReportDetailsDialog(report, { selectedReport = null }, { repo.history.value.find { it.key==report.key }?.let(copy) },currentReport={repo.history.value.find { it.key==report.key }}) }
}

@Composable
private fun HistoryMetric(label: String, value: String, icon: String, modifier: Modifier) {
    Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp), color = DeckColors.surface,
        border = BorderStroke(1.dp, DeckColors.track)) {
        Column(Modifier.heightIn(min = 82.dp).padding(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = HistoryGrey, fontSize = 9.sp, minLines = 2, maxLines = 2)
            UiGlyph(icon, Modifier.size(18.dp))
            Text(value, color = HistoryWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
@Composable private fun HistoryCardAction(label: String, icon: String, modifier: Modifier, color: Color = HistoryCyan, action: () -> Unit) {
    TextButton(action, modifier, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp)) {
        UiGlyph(icon, Modifier.size(14.dp), color); Spacer(Modifier.width(4.dp))
        Text(label, color = color, fontSize = 8.sp)
    }
}

@Composable
private fun FocusBars(buckets: List<HistoryBucket>) {
    val maximum = buckets.maxOfOrNull { it.focusedMs } ?: 0L
    val positive = buckets.filter { it.focusedMs > 0 }
    val average = if (positive.isEmpty()) 0L else positive.sumOf { it.focusedMs } / positive.size
    val step = when { maximum > 3_600_000 -> 3_600_000L; maximum > 300_000 -> 300_000L; maximum > 60_000 -> 60_000L; else -> 15_000L }
    val ceiling = (ceil(maximum.toDouble() / step).toLong() * step).coerceAtLeast(step)
    val mode = LocalAchievementMotion.current
    val reveal = remember(buckets) { Animatable(if (mode == AchievementMotion.OFF) 1f else 0f) }
    LaunchedEffect(buckets, mode) { reveal.animateTo(1f, tween(mode.duration)) }
    Text("Top ${humanTime(maximum)}", color = HistoryGrey, fontSize = 10.sp)
    Canvas(Modifier.fillMaxWidth().height(166.dp).padding(top = 18.dp, bottom = 22.dp)) {
        val left = 28.dp.toPx(); val right = size.width - 2.dp.toPx(); val width = right - left
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = HistoryGrey.toArgb(); textSize = 9.sp.toPx()
        }
        fun axis(ms: Long): String = when { ms >= 3_600_000 -> "${ms / 3_600_000}h"; ms >= 60_000 -> "${ms / 60_000}m"; ms > 0 -> "${ms / 1000}s"; else -> "0" }
        drawLine(DeckColors.track, Offset(left, 0f), Offset(left, size.height), 1.dp.toPx())
        drawLine(DeckColors.track, Offset(left, size.height), Offset(right, size.height), 1.dp.toPx())
        drawIntoCanvas { canvas ->
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            for (i in 0..3) {
                val value = ceiling * i / 3
                canvas.nativeCanvas.drawText(axis(value), left - 7.dp.toPx(), size.height * (1f - i / 3f) + 3.dp.toPx(), paint)
            }
        }
        if (maximum > 0) {
            for ((label, value) in listOf("Best" to maximum, "Average" to average)) {
                val y = size.height * (1f - value.toFloat() / ceiling)
                drawLine(HistoryCyan.copy(alpha = .45f), Offset(left, y), Offset(right, y), .7.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
                drawIntoCanvas { it.nativeCanvas.drawText("$label (${humanTime(value)})", right, y - 4.dp.toPx(), paint) }
            }
        }
        if (buckets.isNotEmpty()) {
            val slot = width / buckets.size
            val indices = if (buckets.size <= 7) buckets.indices.toList() else listOf(0, buckets.size / 4, buckets.size / 2, buckets.size * 3 / 4, buckets.lastIndex).distinct()
            buckets.forEachIndexed { index, bucket ->
                val height = (bucket.focusedMs.toDouble() / ceiling * size.height * reveal.value).toFloat()
                val barWidth = (slot * .5f).coerceAtMost(28.dp.toPx())
                if (height > 0f) drawRoundRect(Brush.verticalGradient(listOf(HistoryYellow, HistoryCyan)),
                    Offset(left + index * slot + (slot - barWidth) / 2, size.height - height), Size(barWidth, height), CornerRadius(3.dp.toPx()))
                if (maximum > 0 && bucket.focusedMs == maximum && slot >= 30.dp.toPx()) drawIntoCanvas {
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    it.nativeCanvas.drawText(humanTime(bucket.focusedMs), left + (index + .5f) * slot, size.height - height - 7.dp.toPx(), paint)
                }
                if (index in indices) drawIntoCanvas {
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    it.nativeCanvas.drawText(bucket.label, left + (index + .5f) * slot, size.height + 15.dp.toPx(), paint)
                }
            }
        }
    }
}
