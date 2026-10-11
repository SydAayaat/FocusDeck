package com.hax.focusdeck

import kotlinx.serialization.Serializable
import java.time.ZoneId

@Serializable data class IndexTopic(val id: String, val title: String, val parent_id: String? = null)
@Serializable data class IndexDocument(val topics: List<IndexTopic>)
@Serializable data class IndexState(val completed: Boolean = false, val atMs: Long = 0, val note: String = "", val evidence: String = "")
@Serializable data class IndexEvent(val id: String, val topicId: String, val title: String, val delta: Int,
    val atMs: Long, val date: String, val zone: String, val note: String = "", val evidence: String = "")
@Serializable data class IndexMilestone(val id: String, val atMs: Long, val title: String)
@Serializable data class IndexRevision(val id: String, val atMs: Long, val document: IndexDocument)
@Serializable data class IndexLedger(val configuration: String = "HYBRID", val document: IndexDocument,
    val revision: Long = 1, val states: Map<String, IndexState> = emptyMap(), val events: List<IndexEvent> = emptyList(),
    val milestones: List<IndexMilestone> = emptyList(), val revisions: List<IndexRevision> = emptyList(),
    val originalDefinition: AchievementDefinition? = null, val legacySegments: List<AchievementSegment> = emptyList()) {
    fun leaves(): List<IndexTopic> { val parents = document.topics.mapNotNull { it.parent_id }.toSet(); return document.topics.filter { it.id !in parents } }
    fun completed(): Int = leaves().count { states[it.id]?.completed == true }
}

/** Phone-owned evidence; a progress projection is never a second editable counter. */
object IndexTracker {
    fun validate(doc: IndexDocument) {
        require(doc.topics.size in 1..5000) { "Index needs 1..5000 named nodes" }
        val nodes = doc.topics.associateBy { it.id }
        require(nodes.size == doc.topics.size) { "Duplicate topic ID" }
        val aliases = mutableSetOf<String>()
        doc.topics.forEach { t ->
            require(t.id.matches(Regex("[A-Za-z0-9_.:-]{1,100}")) && t.title.isNotBlank() && t.title.length <= 500) { "Invalid topic ID/title" }
            val key = if (t.id.matches(Regex("[0-9]+(\\.[0-9]+)*"))) t.id.split('.').joinToString(".") { it.trimStart('0').ifEmpty { "0" } } else t.id
            require(aliases.add(key)) { "Ambiguous topic numbering" }
            val seen = mutableSetOf(t.id); var parent = t.parent_id
            while (parent != null) {
                require(parent in nodes && seen.add(parent) && seen.size <= 32) { "Missing parent, cycle or excessive depth" }
                parent = nodes.getValue(parent).parent_id
            }
        }
        require(doc.topics.sumOf { it.id.length + it.title.length + (it.parent_id?.length ?: 0) } <= 130000) { "Index document too large" }
    }
    fun project(store: AchievementStore): AchievementStore = store.copy(definitions = store.definitions.map { d ->
        store.indexes[d.id]?.let { l -> d.copy(progress = AchievementProgress(l.completed().toDouble(), l.leaves().size.toDouble(), "topics",
            l.events.lastOrNull()?.atMs?.takeIf { l.completed() == l.leaves().size }, l.events.lastOrNull()?.atMs ?: 0, l.events.lastOrNull()?.id ?: "")) } ?: d
    })
    fun apply(store: AchievementStore, c: AchievementCommand, at: Long, zone: ZoneId): AchievementStore {
        require(c.achievement_version == 5 && c.current_value == null && c.target_value == null && c.unit == null && c.event == null && c.icon == null && c.objectives == null && c.date == null && c.amount == 1L && c.text.isEmpty()) { "Invalid Index fields" }
        val id = c.achievement_id ?: c.definition?.id ?: error("Card ID required")
        var ledger = store.indexes[id]
        var next = store
        when (c.operation) {
            "index_create", "index_migrate" -> {
                require(ledger == null) { "Already an Index card" }
                val doc = requireNotNull(c.document); validate(doc)
                val config = c.configuration ?: "HYBRID"
                require(config in setOf("HYBRID", "PROGRESS_ONLY")) { "Invalid Index configuration" }
                require(c.topic_id == null && c.completed == null && c.note == null && c.evidence == null && c.expected_index_revision == null) { "Unsupported creation fields" }
                val old = store.definitions.find { it.id == id }
                val base = if (c.operation == "index_migrate") {
                    require(c.confirm && c.expected_store_revision == store.revision && c.definition == null) { "Migration requires explicit confirmation and current store revision" }
                    require(old != null && !old.archived && id !in AchievementEngine.builtInIds && old.kind in setOf("daily", "scheduled")) { "Only active custom manual cards can migrate" }; old
                } else {
                    require(old == null && id !in store.deletedIds && id !in AchievementEngine.builtInIds && !c.confirm) { "Create requires an unused card ID" }
                    val d = requireNotNull(c.definition)
                    require(d.id.matches(Regex("[a-z][a-z0-9_]{0,99}")) && d.title.isNotBlank() && d.title.length <= 200 && d.description.length <= 1000 && d.category == "index" && d.kind in setOf("daily", "scheduled") && d.progress == null && !d.archived && d.metadata.size <= 30 && d.metadata.all { it.key.length <= 100 && it.value.length <= 2000 }) { "Invalid Index definition" }
                    d.copy(createdDate = AchievementEngine.date(at, zone))
                }
                val replacement = base.copy(category = "index", progressEnabled = true, streakEnabled = config == "HYBRID")
                ledger = IndexLedger(config, doc, revisions = listOf(IndexRevision(c.id, at, doc)), originalDefinition = old,
                    legacySegments = store.segments.filter { it.achievementId == id })
                next = store.copy(definitions = if (old == null) store.definitions + replacement else store.definitions.map { if (it.id == id) replacement else it })
            }
            "index_reconcile" -> {
                val l = requireNotNull(ledger) { "Not an Index card" }
                require(c.confirm && c.expected_index_revision == l.revision && c.definition == null && c.configuration == null) { "Review and confirm current Index revision" }
                require(c.topic_id == null && c.completed == null && c.note == null && c.evidence == null && c.expected_store_revision == null) { "Unsupported reconciliation fields" }
                val doc = requireNotNull(c.document); validate(doc)
                ledger = l.copy(document = doc, revision = l.revision + 1, revisions = l.revisions + IndexRevision(c.id, at, doc),
                    milestones = l.milestones + IndexMilestone(c.id, at, "Index revision imported; prior item evidence retained"))
            }
            "index_topic" -> {
                val l = requireNotNull(ledger) { "Not an Index card" }
                require(c.expected_index_revision == l.revision && c.document == null && c.definition == null && c.configuration == null && !c.confirm) { "Topic update requires current Index revision" }
                require(c.expected_store_revision == null) { "Unsupported topic fields" }
                val topic = l.leaves().find { it.id == c.topic_id } ?: error("Unknown or non-leaf topic")
                require(next.definitions.any { it.id == id && !it.archived }) { "Archived card" }
                val done = requireNotNull(c.completed)
                val old = l.states[topic.id] ?: IndexState()
                require(at >= old.atMs) { "Topic events must be chronological" }
                require((c.note?.length ?: 0) <= 2000 && (c.evidence?.length ?: 0) <= 4000) { "Evidence too large" }
                if (old.completed != done) {
                    val updated = l.copy(states = l.states + (topic.id to IndexState(done, at, c.note.orEmpty(), c.evidence.orEmpty())))
                    val event = IndexEvent(c.id, topic.id, topic.title, if (done) 1 else -1, at, AchievementEngine.date(at, zone), zone.id, c.note.orEmpty(), c.evidence.orEmpty())
                    val milestones = mutableListOf(IndexMilestone(c.id, at, "Topic ${topic.id} ${if (done) "completed" else "reopened"}: ${topic.title}"))
                    if (done) {
                        var parent = topic.parent_id
                        while (parent != null) {
                            val section = parent
                            fun descendants(x: IndexLedger) = x.leaves().filter { leaf ->
                                var p = leaf.parent_id; var found = false
                                while (p != null) { if (p == section) found = true; p = x.document.topics.firstOrNull { it.id == p }?.parent_id }; found
                            }
                            val children = descendants(l)
                            if (children.isNotEmpty() && children.any { l.states[it.id]?.completed != true } && children.all { updated.states[it.id]?.completed == true })
                                milestones += IndexMilestone("${c.id}:section:$section", at, "Section $section fully completed")
                            parent = l.document.topics.first { it.id == section }.parent_id
                        }
                        for (percent in listOf(25, 50, 75, 100)) if (l.completed() * 100 < l.leaves().size * percent && updated.completed() * 100 >= l.leaves().size * percent)
                            milestones += IndexMilestone("${c.id}:$percent", at, "$percent% of Index completed")
                    }
                    ledger = updated.copy(revision = l.revision + 1, events = l.events + event, milestones = l.milestones + milestones)
                }
            }
            else -> error("Unknown Index operation")
        }
        return project(next.copy(indexes = next.indexes + (id to requireNotNull(ledger))))
    }
}
