package com.hax.focusdeck

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.util.UUID

@Composable internal fun IndexDetail(store: AchievementStore, def: AchievementDefinition, now: Long, zone: ZoneId,
    readOnly: Boolean, send: (AchievementCommand) -> Unit) {
    val ledger = store.indexes[def.id] ?: return
    val accent = DeckColors.accent
    var search by remember(def.id) { mutableStateOf("") }
    var collapsed by remember(def.id) { mutableStateOf(emptySet<String>()) }
    var window by remember { mutableStateOf(14) }
    var selectedDay by remember { mutableStateOf<String?>(null) }
    var selected by remember(def.id) { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }; var evidence by remember { mutableStateOf("") }
    val nodes = remember(ledger.document) { ledger.document.topics.associateBy { it.id } }
    val leaves = remember(ledger.document) { ledger.leaves().map { it.id }.toSet() }
    val descendants = remember(ledger.document) {
        val result = mutableMapOf<String, MutableList<String>>()
        ledger.leaves().forEach { t -> var p: String? = t.id; while (p != null) { result.getOrPut(p) { mutableListOf() }.add(t.id); p = nodes[p]?.parent_id } }; result
    }
    val ordered = remember(ledger.document) {
        val groups = ledger.document.topics.groupBy { it.parent_id }
        val output = mutableListOf<IndexTopic>()
        fun visit(parent: String?) { groups[parent].orEmpty().forEach { output.add(it); visit(it.id) } }
        visit(null); output
    }
    val visible = remember(ordered, collapsed, search) { ordered.filter { t ->
        if (search.isNotBlank()) (t.id + " " + t.title).contains(search, true)
        else { var p = t.parent_id; var hidden = false; while (p != null) { if (p in collapsed) hidden = true; p = nodes[p]?.parent_id }; !hidden }
    } }
    fun command(op: String, topic: String? = null, done: Boolean? = null) = AchievementCommand(5, op, UUID.randomUUID().toString(),
        Instant.now().toString(), source = "phone", achievement_id = def.id, topic_id = topic, completed = done,
        expected_index_revision = if (op == "index_topic") ledger.revision else null,
        event = if (op == "event") "completed" else null, note = if (op == "index_topic") note else null, evidence = if (op == "index_topic") evidence else null)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${ledger.completed()} / ${leaves.size} topics • ${"%.2f".format(java.util.Locale.ROOT, ledger.completed() * 100.0 / leaves.size)}%", color = accent)
        if (ledger.configuration == "HYBRID") {
            Text("Actual study today: ${AchievementEngine.status(store, def, AchievementEngine.date(now, zone))}")
            if (!readOnly) TextButton(onClick = { send(command("event")) }) { Text("CONFIRM ACTUAL STUDY TODAY") }
            Text("Topic completion and daily activity are independent.", color = Color.Gray)
        }
        Text("Topics completed per day")
        Row { listOf(7,14,30,90).forEach { n -> TextButton(onClick = { window = n }) { Text("${n}d${if (n == window) " ✓" else ""}") } } }
        val today = LocalDate.parse(AchievementEngine.date(now, zone))
        val events = remember(ledger.events) { ledger.events.distinctBy { it.id }.groupBy { it.date } }
        val dates = (window-1 downTo 0).map { today.minusDays(it.toLong()).toString() }
        val maximum = dates.maxOf { d -> events[d].orEmpty().count { it.delta > 0 } }.coerceAtLeast(1)
        LazyRow(Modifier.height(125.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dates, key = { it }) { date ->
                val es = events[date].orEmpty(); val gross = es.count { it.delta > 0 }; val net = es.sumOf { it.delta }
                val height by animateFloatAsState(gross.toFloat()/maximum, tween(LocalAchievementMotion.current.duration), label = "Topics")
                Column(Modifier.width(54.dp).clickable { selectedDay = date }, verticalArrangement = Arrangement.Bottom) {
                    Text("+$gross / $net", color = if (net < 0) Color.Red else accent)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height((height * 65).dp).background(accent))
                    Text(date.substring(5), color = Color.Gray)
                }
            }
        }
        Text("Bars: gross completions. Labels: gross / net progress, including reopens.", color = Color.Gray)
        selectedDay?.let { date -> Text(date); events[date].orEmpty().forEach { e -> Text("${if(e.delta>0) "+" else "−"} ${e.topicId}: ${e.title} • ${Instant.ofEpochMilli(e.atMs).atZone(zone)}") } }
        Text("Recent milestones")
        var allMilestones by remember { mutableStateOf(false) }
        ledger.milestones.asReversed().take(if (allMilestones) 20 else 5).forEach { m -> Text("${Instant.ofEpochMilli(m.atMs).atZone(zone)} • ${m.title}") }
        TextButton(onClick = { allMilestones = !allMilestones }) { Text(if (allMilestones) "SHOW RECENT" else "EXPAND HISTORY BELOW") }
        OutlinedTextField(search, { search = it }, label = { Text("Search topic ID or title") }, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
            items(visible, key = { it.id }) { topic ->
                val children = descendants[topic.id].orEmpty(); val count = children.count { ledger.states[it]?.completed == true }
                Row(Modifier.fillMaxWidth().clickable { selected = topic.id }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (topic.id in leaves) Checkbox(ledger.states[topic.id]?.completed == true, if(readOnly) null else { _: Boolean -> pending = topic.id; note = ""; evidence = "" })
                    else TextButton(onClick = { collapsed = if(topic.id in collapsed) collapsed-topic.id else collapsed+topic.id }) { Text(if(topic.id in collapsed) "+" else "−") }
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) { Text("${topic.id} ${topic.title}"); Text("$count / ${children.size} • ${if(children.isEmpty()) 0 else count*100/children.size}%", color = Color.Gray) }
                }
            }
        }
        selected?.let { id -> nodes[id]?.let { t -> val state = ledger.states[id]; Text("Selected: ${t.id} ${t.title}"); Text("${if(state?.completed == true) "COMPLETED" else "NOT COMPLETED"}"); if(state != null) Text("${Instant.ofEpochMilli(state.atMs).atZone(zone)}\n${state.note}\n${state.evidence}") } }
        Text("Inline topic / revision / milestone history")
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            items(ledger.events.asReversed(), key = { "event:${it.id}" }) { e -> Text("${Instant.ofEpochMilli(e.atMs).atZone(zone)} • ${e.topicId} ${e.title} ${if(e.delta>0) "completed" else "reopened"}\n${e.note}\n${e.evidence}") }
            items(ledger.revisions.asReversed(), key = { "revision:${it.id}" }) { r -> Text("${Instant.ofEpochMilli(r.atMs).atZone(zone)} • Index revision ${r.id}: ${r.document.topics.size} nodes") }
            if(allMilestones) items(ledger.milestones.asReversed(), key = { "milestone:${it.id}" }) { m -> Text("${Instant.ofEpochMilli(m.atMs).atZone(zone)} • ${m.title}") }
        }
        if (ledger.originalDefinition != null) Text("Legacy numeric evidence (unmapped): ${ledger.originalDefinition.progress?.currentValue ?: "N/A"} / ${ledger.originalDefinition.progress?.targetValue ?: "N/A"}. Prior numeric history remains below; no topic completions were inferred.", color = Color.Gray)
    }
    pending?.let { id -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("${if(ledger.states[id]?.completed == true) "Reopen" else "Complete"} topic $id?") },
        text = { Column { OutlinedTextField(note, { note=it.take(2000) }, label={Text("Optional note")}); OutlinedTextField(evidence, { evidence=it.take(4000) }, label={Text("Optional evidence")}) } },
        confirmButton = { TextButton(onClick = { send(command("index_topic",id,ledger.states[id]?.completed != true)); pending=null }) { Text("RECORD") } },
        dismissButton = { TextButton(onClick={pending=null}) { Text("CANCEL") } }) }
}
