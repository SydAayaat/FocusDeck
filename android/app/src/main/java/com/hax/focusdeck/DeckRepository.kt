package com.hax.focusdeck

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID

private val Application.deckStore by preferencesDataStore("focusdeck")

class DeckApplication : Application() {
    val repository by lazy { DeckRepository(this) }
    internal val sounds by lazy { DeckSounds(this, repository).also { it.runClock(repository.scope) } }
}

class DeckRepository(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val storageKey = stringPreferencesKey("deck_v1")
    private val achievementKey = stringPreferencesKey("achievements_v1")
    private val achievementMutable = MutableStateFlow(AchievementStore())
    private fun localZone() = java.time.ZoneId.systemDefault()
    private val mutable = MutableStateFlow(StoredDeck(deviceId = ""))
    val stored: StateFlow<StoredDeck> = mutable.asStateFlow()
    private val bootCount = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private val clock = MutableStateFlow(readClock())
    val connection = MutableStateFlow("Disconnected")
    val error = MutableStateFlow("")
    val historyNotice = MutableStateFlow("")
    private val historical = MutableStateFlow<List<Report>>(emptyList())
    private val archive = ReportArchive(app)
    private val historyByKey = linkedMapOf<String, Report>()
    private val historyRequests = Channel<Unit>(Channel.CONFLATED)
    private val archiveIoMutex = Mutex()
    val boundaries = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    private val loaded = CompletableDeferred<Unit>()
    private var engine = TimerEngine()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val visible = MutableStateFlow(false)
    val uiVisible: StateFlow<Boolean> = visible.asStateFlow()
    val tablet = TabletMirror(java.io.File(app.filesDir, "tablet-viewer"))
    val isViewer: Boolean get() = mutable.value.pairing?.viewer == true
    val display: StateFlow<StoredDeck> = combine(mutable, tablet.snapshot) { local, mirror ->
        if (local.pairing?.viewer == true) local.copy(state = mirror.state?.state ?: DeckState(), reports = mirror.reports) else local
    }.stateIn(scope, SharingStarted.Eagerly, mutable.value)
    val achievements: StateFlow<AchievementStore> = combine(achievementMutable, mutable, tablet.snapshot) { local, prefs, mirror ->
        if (prefs.pairing?.viewer == true) mirror.ledger?.ledger ?: AchievementStore() else local
    }.stateIn(scope, SharingStarted.Eagerly, achievementMutable.value)
    val history: StateFlow<List<Report>> = combine(historical, mutable, tablet.snapshot) { local, prefs, mirror ->
        if (prefs.pairing?.viewer == true) mirror.reports else local
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val now: StateFlow<ClockNow> = combine(clock, mutable, tablet.snapshot) { local, prefs, mirror ->
        if (prefs.pairing?.viewer == true) mirror.state?.clock ?: ClockNow(mirror.ledger?.atMs ?: local.wall, 0, -1) else local
    }.stateIn(scope, SharingStarted.Eagerly, clock.value)
    val displayZone: StateFlow<String> = combine(mutable, tablet.snapshot) { prefs, mirror ->
        if (prefs.pairing?.viewer == true) mirror.ledger?.zone ?: localZone().id else localZone().id
    }.stateIn(scope, SharingStarted.Eagerly, localZone().id)
    private fun requireWriter() = require(!isViewer) { "Tablet viewer is read-only. Use the phone or PC for changes." }


    init {
        scope.launch {
            try {
                val preferences = app.deckStore.data.first()
                val saved = preferences[storageKey]
                mutable.value = if (saved == null) StoredDeck(UUID.randomUUID().toString())
                    else WireJson.decodeFromString<StoredDeck>(saved)
                achievementMutable.value = preferences[achievementKey]?.let { WireJson.decodeFromString<AchievementStore>(it) }
                    ?: AchievementEngine.seed(readClock().wall, localZone())
                require(achievementMutable.value.achievement_version == 1) { "Unsupported stored achievement contract; data retained" }
                engine = TimerEngine(mutable.value.state)
                val (records, problems) = withContext(Dispatchers.IO) { archive.load() }
                mutable.value.deletions.validate()
                records.filterNot { mutable.value.deletions.hides(it) }.forEach { historyByKey[it.key] = it }
                historical.value = historyByKey.values.sortedBy { it.startedAtMs }
                if (problems.isNotEmpty()) historyNotice.value = "${problems.size} archive file(s) could not be read. Restore them from the PC; existing files are retained."
                if (engine.state.phase in listOf("complete", "stopped") && engine.report(readClock())?.let { mutable.value.deletions.hides(it) } == true) engine.state = DeckState(revision = engine.state.revision + 1)
                tablet.load(mutable.value.pairing)
                if (!isViewer) engine.tick(readClock())
                commit(achievementDraft = AchievementEngine.migrateProgress(achievementMutable.value))
                loaded.complete(Unit)
                historyRequests.trySend(Unit)
                var lastCheckpoint = SystemClock.elapsedRealtime()
                while (isActive) {
                    mutex.withLock {
                        if (isViewer) return@withLock
                        val stamp = readClock()
                        if (visible.value) clock.value = stamp
                        val before = engine.state.revision
                        val previousCount = engine.state.results.size
                        val wasOvertime = engine.state.overtimeNotified
                        engine.tick(stamp)
                        if (engine.state.results.size > previousCount || (!wasOvertime && engine.state.overtimeNotified)) boundaries.tryEmit(Unit)
                        val interval = PowerPolicy.checkpointMs(engine.state.phase, visible.value)
                        val stale = withContext(Dispatchers.Default) { AchievementEngine.finalize(achievementMutable.value, stamp.wall, localZone()) }
                        val rolled = if (stale != achievementMutable.value) AchievementEngine.finalize(
                            AchievementEngine.capture(achievementMutable.value, engine.state, stamp, localZone()), stamp.wall, localZone()) else stale
                        if (engine.state.revision != before || rolled != achievementMutable.value || (interval != null && stamp.mono - lastCheckpoint >= interval)) {
                            engine.checkpoint(stamp)
                            commit(achievementDraft = rolled)
                            lastCheckpoint = stamp.mono
                        }
                    }
                    val wall = readClock().wall
                    val midnight = java.time.Instant.ofEpochMilli(wall).atZone(localZone()).toLocalDate()
                        .plusDays(1).atStartOfDay(localZone()).toInstant().toEpochMilli()
                    val untilMidnight = (midnight - wall).coerceAtLeast(1)
                    val achievementDelay = if (visible.value) minOf(60_000L, untilMidnight) else untilMidnight
                    val delayMs = if (isViewer) untilMidnight else minOf(PowerPolicy.tickMs(engine.state.phase, visible.value) ?: achievementDelay, achievementDelay)
                    withTimeoutOrNull(delayMs) { wake.receive() }
                }
            } catch (e: Exception) {
                error.value = "Local storage failure: ${e.message}. Existing data has not been replaced."
                if (!loaded.isCompleted) loaded.completeExceptionally(e)
            }
        }
        scope.launch(Dispatchers.IO) {
            loaded.await()
            for (request in historyRequests) {
                archiveIoMutex.withLock {
                    val draft = mutable.value
                    val reports = historical.value.filterNot { draft.deletions.hides(it) }
                    val tree = draft.historyTreeUri
                    try {
                        archive.prune(draft.deletions, null)
                        reports.filter { it.key !in archive.savedKeys }.forEach { archive.save(it) }
                        archive.csv(reports)
                        if (tree != null) { archive.prune(draft.deletions, tree, privateFiles = false); archive.mirror(tree, reports) }
                        if (historyNotice.value.startsWith("Automatic history files")) historyNotice.value = ""
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        historyNotice.value = "Automatic history files could not update: ${e.message}. Saved deletion intent and remaining reports are retained; retry from Study history."
                    }
                }
            }
        }
    }

    fun readClock() = ClockNow(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount)

    fun setUiVisible(value: Boolean) {
        visible.value = value
        if (value) { clock.value = readClock(); historyRequests.trySend(Unit) }
        wake.trySend(Unit)
    }

    suspend fun awaitLoaded() = loaded.await()

    suspend fun achievementCommand(command: AchievementCommand): CommandAck {
        loaded.await()
        if (command.icon?.kind == "image") {
            try { withContext(Dispatchers.Default) { validateIconBitmap(command.icon) } }
            catch (e: Exception) { return CommandAck(false, e.message ?: "Invalid icon image") }
        }
        return mutex.withLock {
            requireWriter()
            val stamp = readClock()
            val measured = withContext(Dispatchers.Default) { AchievementEngine.capture(achievementMutable.value, engine.state, stamp, localZone()) }
            val next = try { withContext(Dispatchers.Default) { AchievementEngine.apply(measured, command, stamp.wall, localZone()) } }
                catch (e: IllegalArgumentException) { return@withLock CommandAck(false, e.message) }
                catch (e: IllegalStateException) { return@withLock CommandAck(false, e.message) }
            commit(achievementDraft = next)
            CommandAck(true)
        }
    }

    suspend fun recordViewed(key: String) {
        loaded.await()
        mutex.withLock {
            if (isViewer) return@withLock
            if (key !in achievementMutable.value.seenRecords)
                commit(achievementDraft = achievementMutable.value.copy(seenRecords = achievementMutable.value.seenRecords + key))
        }
    }

    suspend fun cardLayout(id: String, size: String? = null, target: String? = null) {
        loaded.await()
        mutex.withLock {
            requireWriter()
            var next = achievementMutable.value
            if (id == "__reset_order__") next=next.copy(cardOrder=emptyList(),revision=next.revision+1)
            if (size != null) next = AchievementEngine.resizeCard(next, id, size)
            if (target != null) next = AchievementEngine.moveCard(next, id, target)
            if (next != achievementMutable.value) commit(achievementDraft = next)
        }
    }

    private suspend fun commit(draft: StoredDeck = mutable.value, achievementDraft: AchievementStore = achievementMutable.value) {
        val stamp = readClock()
        val achievements = if (draft.pairing?.viewer == true) achievementMutable.value else withContext(Dispatchers.Default) { AchievementEngine.finalize(AchievementEngine.capture(achievementDraft, engine.state, stamp, localZone()), stamp.wall, localZone()) }
        var reports = draft.reports.filterNot { draft.deletions.hides(it) }
        (if (draft.pairing?.viewer == true) null else engine.report(readClock()))?.let { report ->
            if (!draft.deletions.hides(report) && reports.none { it.key == report.key }) reports = reports + report
        }
        // The DataStore cache is bounded; separately archived reports are never age-pruned.
        val delivered = draft.delivered
        val retainedKeys = reports.filter { it.key in delivered }.takeLast(10).map { it.key }.toSet()
        reports = reports.filter { it.key !in delivered || it.key in retainedKeys || it.key !in archive.savedKeys }
        val next = draft.copy(state = engine.state, reports = reports,
            delivered = delivered.intersect(reports.map { it.key }.toSet()))
        val deckJson = withContext(Dispatchers.Default) { WireJson.encodeToString(next) }
        val achievementJson = if (achievements != achievementMutable.value) withContext(Dispatchers.Default) { WireJson.encodeToString(achievements) } else null
        app.deckStore.edit {
            it[storageKey] = deckJson
            if (achievementJson != null) it[achievementKey] = achievementJson
            else if (it[achievementKey] == null) it[achievementKey] = withContext(Dispatchers.Default) { WireJson.encodeToString(achievements) }
        }
        achievementMutable.value = achievements
        val deletionChanged = mutable.value.deletions != next.deletions
        val treeChanged = mutable.value.historyTreeUri != next.historyTreeUri
        mutable.value = next
        clock.value = readClock()
        wake.trySend(Unit)
        // State + ACK are durable before asynchronous archive/CSV work begins.
        var changed = historyByKey.entries.removeAll { draft.deletions.hides(it.value) }
        reports.forEach { report ->
            if (historyByKey[report.key] != report) {
                historyByKey[report.key] = report
                changed = true
            }
        }
        if (changed) historical.value = historyByKey.values.sortedBy { it.startedAtMs }
        if (changed || treeChanged || deletionChanged) historyRequests.trySend(Unit)
    }

    suspend fun historyFolder(uri: String) {
        loaded.await()
        mutex.withLock { requireWriter(); commit(mutable.value.copy(historyTreeUri = uri)) }
        historyRequests.trySend(Unit)
    }

    suspend fun retryHistoryFiles() {
        loaded.await(); requireWriter()
        historyRequests.trySend(Unit)
    }

    suspend fun importHistory(records: List<Report>): Int {
        withContext(Dispatchers.Default) {
            require(records.size <= 10_000) { "Import at most 10,000 sessions per file" }
            records.forEach { it.validateHistory() }
        }
        loaded.await()
        val incoming = records.associateBy { it.key }
        require(incoming.size == records.size) { "Import contains repeated session attempts" }
        mutex.withLock {
            requireWriter()
            incoming.forEach { (key, report) ->
                require(historyByKey[key] == null || historyByKey[key] == report) { "A different report already exists for $key" }
            }
        }
        val added = mutableListOf<Report>()
        try {
            withContext(Dispatchers.IO) {
                archiveIoMutex.withLock {
                    incoming.values.forEach { report ->
                        if (!mutable.value.deletions.hides(report) && report.key !in archive.savedKeys) { archive.save(report); added += report }
                    }
                }
            }
        } finally {
            mutex.withLock {
                added.forEach { report -> if (!mutable.value.deletions.hides(report) && report.key !in historyByKey) historyByKey[report.key] = report }
                historical.value = historyByKey.values.sortedBy { it.startedAtMs }
                historyRequests.trySend(Unit)
            }
        }
        return added.size
    }

    suspend fun deleteHistory(reportKey: String? = null) {
        loaded.await()
        val tombstone = if (reportKey == null) HistoryDeletion(beforeMs = maxOf(readClock().wall, historical.value.maxOfOrNull { it.finishedAtMs } ?: 0))
            else HistoryDeletion(reportKeys = setOf(reportKey))
        mergeDeletions(tombstone)
    }

    suspend fun mergeDeletions(incoming: HistoryDeletion) {
        incoming.validate(); loaded.await()
        mutex.withLock {
            requireWriter()
            val next = mutable.value.deletions.merge(incoming)
            val original = engine.state
            if (engine.state.phase in listOf("complete", "stopped") && engine.report(readClock())?.let { next.hides(it) } == true) engine.state = DeckState(revision = original.revision + 1)
            try { commit(mutable.value.copy(deletions = next)) } catch (e: Exception) { engine.state = original; throw e }
        }
        historyRequests.trySend(Unit)
    }

    suspend fun deletionDelivered(revision: String) {
        loaded.await()
        mutex.withLock { requireWriter(); if (mutable.value.deletions.revision == revision) commit(mutable.value.copy(deletionDelivered = revision)) }
    }

    suspend fun pair(pairing: Pairing, expectedExisting: Pairing? = mutable.value.pairing) {
        pairing.validate()
        loaded.await()
        mutex.withLock {
            val old = mutable.value.pairing
            require(old==expectedExisting) { "Pairing changed; review the current trusted desktop again" }
            val sameDesktop = old != null && old.desktopId == pairing.desktopId &&
                old.role == pairing.role && old.token == pairing.token && old.certificateSha256.equals(pairing.certificateSha256, ignoreCase = true)
            require(sameDesktop || engine.state.phase !in listOf("active", "paused", "ready", "between", "quality", "reward", "session_reward")) { "Stop the session before changing desktops" }
            tablet.resetTransfer()
            tablet.load(pairing)
            commit(mutable.value.copy(pairing = pairing))
        }
    }

    suspend fun preferences(awake: Boolean, immersive: Boolean, sound: Boolean, dimDesk: Boolean, motion: String = mutable.value.motion, theme: String = mutable.value.theme, timerRefreshRate: Int = mutable.value.timerRefreshRate) {
        loaded.await()
        mutex.withLock {
            require(motion in setOf("FULL", "REDUCED", "OFF"))
            require(DeckPalettes.all.any { it.id == theme })
            require(timerRefreshRate in setOf(30, 60, 120))
            commit(mutable.value.copy(keepAwake = awake, immersive = immersive, sound = sound, dimDesk = dimDesk, motion = motion, theme = theme, timerRefreshRate = timerRefreshRate))
        }
    }

    suspend fun soundPreferences(audio: SoundPreferences, enabled: Boolean) {
        loaded.await()
        mutex.withLock { audio.validate(); commit(mutable.value.copy(audio = audio, sound = enabled)) }
    }
    suspend fun developerMode(enabled: Boolean) {
        loaded.await()
        mutex.withLock { commit(mutable.value.copy(developerMode = enabled)) }
    }

    suspend fun reportDelivered(key: String) {
        loaded.await()
        mutex.withLock {
            requireWriter()
            if (mutable.value.reports.any { it.key == key }) {
                commit(mutable.value.copy(delivered = mutable.value.delivered + key))
            }
        }
    }

    suspend fun resendReport(key: String) {
        loaded.await()
        mutex.withLock {
            requireWriter()
            commit(mutable.value.copy(delivered = mutable.value.delivered - key))
        }
    }

    suspend fun command(id: String, action: String, session: Session? = null, expectedRevision: Long? = null): CommandAck {
        loaded.await()
        return mutex.withLock {
            if (isViewer) return@withLock CommandAck(false, "Tablet viewer is read-only. Session commands always run on the phone.")
            val stamp = readClock()
            val original = engine.state
            if (expectedRevision != null && original.revision != expectedRevision && id !in mutable.value.acknowledgements)
                return@withLock CommandAck(false, "Session changed. Open the menu again to choose an action.")
            val previousCount = engine.state.results.size
            val transaction = CommandProcessor.apply(original, mutable.value.acknowledgements, id, action, session, stamp, historical.value)
            engine.state = transaction.state
            // Durable state + dedup receipt together, before an ACK can leave the phone.
            try { commit(mutable.value.copy(acknowledgements = transaction.receipts)) }
            catch (e: Exception) {
                engine.state = original
                error.value = "Cannot save session state: ${e.message}. Command was not acknowledged."
                throw e
            }
            if (engine.state.results.size > previousCount && engine.state.results.last().outcome == "done") boundaries.tryEmit(Unit)
            transaction.ack
        }
    }

    fun stateEnvelope(): String = buildJsonObject {
        put("protocol_version", 1); put("type", "state")
        val stamp = readClock()
        val s = mutable.value.state
        val view = TimerEngine(s)
        put("state", buildJsonObject {
            put("phase", if (s.phase == "active" && view.isOvertime(stamp)) "overtime" else s.phase)
            put("session_id", s.session?.sessionId)
            put("title", s.session?.title); put("block_index", s.index)
            put("block_count", s.session?.blocks?.size ?: 0)
            put("timer_mode", s.session?.blocks?.getOrNull(s.index)?.effectiveMode)
            put("elapsed_active_ms", view.currentActive(stamp)); put("elapsed_paused_ms", view.currentPaused(stamp))
            put("remaining_ms", view.remaining(stamp)); put("revision", s.revision)
            put("overtime_ms", if (s.budgetMs == null) null else view.overtime(stamp)); put("is_overtime", view.isOvertime(stamp))
            put("observed_at_ms", stamp.wall); put("completed_blocks", s.results.count { it.outcome == "done" })
        })
    }.toString()
}
