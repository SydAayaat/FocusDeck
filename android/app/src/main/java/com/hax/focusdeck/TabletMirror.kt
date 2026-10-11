package com.hax.focusdeck

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable data class ViewerState(
    @SerialName("desktop_id") val desktopId: String,
    @SerialName("authority_device_id") val authorityId: String,
    val state: DeckState, val clock: ClockNow)
@Serializable data class ViewerLedger(
    @SerialName("desktop_id") val desktopId: String,
    @SerialName("authority_device_id") val authorityId: String,
    val ledger: AchievementStore,
    @SerialName("as_of_ms") val atMs: Long, val zone: String)
@Serializable data class ViewerReports(val desktopId: String, val authorityId: String, val reports: List<Report>, val atMs: Long = 0)
data class TabletSnapshot(val desktopId: String = "", val authorityId: String? = null,
    val state: ViewerState? = null, val ledger: ViewerLedger? = null, val reports: List<Report> = emptyList(),
    val phoneConnected: Boolean = false, val phoneSupportsViewer: Boolean = false, val reportsAtMs: Long? = null)

/** Separate cache only. Never imports into the timer, achievements ledger, or phone archive. */
class TabletMirror(private val directory: File) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(TabletSnapshot())
    val snapshot = mutable.asStateFlow()
    private var transfer: String? = null
    private var document = ""
    private var part = 0
    private var count = 0
    private var spool: File? = null
    private var reportsSync: String? = null
    private var pendingReports = linkedMapOf<String, Report>()

    private fun write(name: String, text: String) {
        directory.mkdirs()
        val temporary = File(directory, "$name.tmp")
        java.io.FileOutputStream(temporary).use { out -> out.write(text.toByteArray()); out.fd.sync() }
        Files.move(temporary.toPath(), File(directory, name).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    suspend fun load(pairing: Pairing?) = withContext(Dispatchers.IO) { mutex.withLock {
        mutable.value = TabletSnapshot()
        directory.listFiles()?.filter { it.name.startsWith("incoming-") && it.name.endsWith(".json") }?.forEach { it.delete() }
        if (pairing?.viewer != true) return@withLock
        fun read(name: String) = File(directory, name).takeIf { it.exists() }?.readText()
        val state = runCatching { read("state.json")?.let { WireJson.decodeFromString<ViewerState>(it) } }.getOrNull()
        val ledger = runCatching { read("ledger.json")?.let { WireJson.decodeFromString<ViewerLedger>(it) } }.getOrNull()
        val reports = runCatching { read("reports.json")?.let { WireJson.decodeFromString<ViewerReports>(it) } }.getOrNull()
        val source = runCatching { read("source.json")?.let { WireJson.parseToJsonElement(it).jsonObject } }.getOrNull()
        val authority = if (source != null && source["desktop_id"]?.jsonPrimitive?.content == pairing.desktopId) {
            source["authority_device_id"]?.jsonPrimitive?.contentOrNull
        } else {
            state?.takeIf { it.desktopId == pairing.desktopId }?.authorityId
                ?: ledger?.takeIf { it.desktopId == pairing.desktopId }?.authorityId
        }
        mutable.value = TabletSnapshot(pairing.desktopId, authority,
            state?.takeIf { it.desktopId == pairing.desktopId && it.authorityId == authority },
            ledger?.takeIf { it.desktopId == pairing.desktopId && it.authorityId == authority },
            reports?.takeIf { it.desktopId == pairing.desktopId && it.authorityId == authority }?.reports ?: emptyList(), reportsAtMs = reports?.takeIf { it.desktopId == pairing.desktopId && it.authorityId == authority && it.atMs > 0 }?.atMs)
    } }
    suspend fun resetTransfer() = withContext(Dispatchers.IO) { mutex.withLock {
        spool?.delete(); spool = null; transfer = null; part = 0; reportsSync = null; pendingReports.clear()
        mutable.value = mutable.value.copy(phoneConnected = false)
    } }
    private fun source(desktop: String, authority: String, pairing: Pairing) {
        require(pairing.viewer && desktop == pairing.desktopId && authority == mutable.value.authorityId) { "Viewer source identity changed" }
    }
    suspend fun receive(msg: JsonObject, pairing: Pairing): JsonObject? = withContext(Dispatchers.IO) { mutex.withLock {
        require(pairing.viewer) { "Not a viewer pairing" }
        when (msg.getValue("type").jsonPrimitive.content) {
            "viewer_status" -> {
                val desktop = msg.getValue("desktop_id").jsonPrimitive.content
                require(desktop == pairing.desktopId) { "Wrong viewer desktop" }
                val authority = msg["authority_device_id"]?.jsonPrimitive?.contentOrNull
                write("source.json", buildJsonObject { put("desktop_id", desktop); put("authority_device_id", authority?.let(::JsonPrimitive) ?: JsonNull) }.toString())
                if (mutable.value.desktopId != desktop || mutable.value.authorityId != authority) {
                    mutable.value = TabletSnapshot(desktop, authority)
                    spool?.delete(); spool = null; transfer = null; reportsSync = null; pendingReports.clear()
                }
                mutable.value = mutable.value.copy(phoneConnected = msg.getValue("phone_connected").jsonPrimitive.boolean,
                    phoneSupportsViewer = msg.getValue("phone_supports_viewer").jsonPrimitive.boolean)
            }
            "viewer_reports_begin" -> {
                source(msg.getValue("desktop_id").jsonPrimitive.content, msg.getValue("authority_device_id").jsonPrimitive.content, pairing)
                reportsSync = msg.getValue("sync_id").jsonPrimitive.content
                pendingReports = linkedMapOf()
            }
            "viewer_reports_end" -> {
                require(reportsSync != null && reportsSync == msg.getValue("sync_id").jsonPrimitive.content) { "Incomplete viewer report batch" }
                val reports = pendingReports.values.sortedBy { it.startedAtMs }
                val atMs = msg.getValue("as_of_ms").jsonPrimitive.long; require(atMs >= 0)
                write("reports.json", WireJson.encodeToString(ViewerReports(pairing.desktopId, requireNotNull(mutable.value.authorityId), reports, atMs)))
                mutable.value = mutable.value.copy(reports = reports, reportsAtMs = atMs)
                reportsSync = null; pendingReports.clear()
            }
            "viewer_chunk" -> {
                val id = msg.getValue("transfer_id").jsonPrimitive.content
                val index = msg.getValue("index").jsonPrimitive.int
                val total = msg.getValue("count").jsonPrimitive.int
                val kind = msg.getValue("document").jsonPrimitive.content
                val data = msg.getValue("data").jsonPrimitive.content
                require(id.length in 1..100 && total in 1..1_000_000 && index in 0 until total && data.length <= 32_000 && kind in setOf("state", "ledger", "report")) { "Invalid viewer chunk" }
                if (index == 0) {
                    require(transfer == null) { "Viewer transfer already in progress" }
                    directory.mkdirs(); spool = File.createTempFile("incoming-", ".json", directory)
                    transfer = id; part = 0; count = total; document = kind
                }
                require(id == transfer && index == part && total == count && kind == document) { "Out-of-order viewer transfer" }
                spool!!.appendText(data); part++
                if (part == count) {
                    val raw = spool!!.readText()
                    when (document) {
                        "state" -> {
                            val value = WireJson.decodeFromString<ViewerState>(raw); source(value.desktopId, value.authorityId, pairing)
                            require(value.clock.wall >= 0 && value.clock.mono >= 0 && value.state.phase in setOf("idle", "ready", "active", "paused", "between", "quality", "reward", "session_reward", "complete", "stopped"))
                            value.state.session?.let { it.validate(); require(value.state.index in it.blocks.indices) }
                            require(value.state.activeMs >= 0 && value.state.pausedMs >= 0)
                            write("state.json", raw); mutable.value = mutable.value.copy(state = value)
                        }
                        "ledger" -> {
                            val value = WireJson.decodeFromString<ViewerLedger>(raw); source(value.desktopId, value.authorityId, pairing)
                            require(value.atMs >= 0 && value.ledger.achievement_version == 1); ZoneId.of(value.zone)
                            write("ledger.json", raw); mutable.value = mutable.value.copy(ledger = value)
                        }
                        "report" -> {
                            val payload = WireJson.parseToJsonElement(raw).jsonObject
                            require(reportsSync != null && reportsSync == payload.getValue("sync_id").jsonPrimitive.content)
                            val value = WireJson.decodeFromJsonElement<Report>(payload.getValue("report")); value.validateHistory()
                            require(pendingReports.put(value.key, value) == null) { "Duplicate report in viewer batch" }
                        }
                    }
                    spool!!.delete(); spool = null; transfer = null
                }
                return@withLock buildJsonObject { put("protocol_version", 1); put("type", "viewer_ack"); put("transfer_id", id); put("index", index) }
            }
            else -> error("Viewer connection received a writable or unsupported message")
        }
        null
    } }
}
