package com.hax.focusdeck

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import okhttp3.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

/** Session and achievement command capabilities are independent protocol contracts. */
internal fun deviceHello(deviceId: String, viewer: Boolean = false): JsonObject = buildJsonObject {
    put("protocol_version", 1); put("type", "hello")
    put("supported_schedule_version", 1)
    put("supported_session_versions", buildJsonArray { add(1); add(2) })
    put("supported_achievement_versions", buildJsonArray { add(1); add(2); add(3); add(4); add(5); add(6) })
    put("device_id", deviceId); put("name", if (viewer) "FocusDeck tablet viewer" else "Android FocusDeck")
    put("role", if (viewer) "viewer" else "phone"); put("supported_viewer_version", 1)
}

class DeckNetwork(private val repo: DeckRepository, private val scope: CoroutineScope) {
    private var job: Job? = null
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            repo.awaitLoaded()
            repo.stored.map { it.pairing }.distinctUntilChanged().collectLatest { pairing ->
                if (pairing == null) { repo.connection.value = "Disconnected"; return@collectLatest }
                var backoff = 1L
                while (isActive) {
                    repo.connection.value = "Connecting"
                    try { connect(pairing); backoff = 1 }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { repo.connection.value = "Offline · ${e.message?.take(80)}" }
                    val pauseMs = backoff * 1000 + Random.nextLong(0, 500)
                    if (repo.uiVisible.value) delay(pauseMs)
                    else withTimeoutOrNull(pauseMs) { repo.uiVisible.first { it } }
                    backoff = (backoff * 2).coerceAtMost(PowerPolicy.retryCapSeconds(repo.uiVisible.value))
                }
            }
        }
    }

    fun stop() { job?.cancel(); job = null; repo.connection.value = "Disconnected" }

    private fun client(pairing: Pairing): OkHttpClient {
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                throw CertificateException("Client certificates unsupported")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull() ?: throw CertificateException("Missing server certificate")
                leaf.checkValidity()
                val hash = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString("") { "%02x".format(it) }
                if (!MessageDigest.isEqual(hash.toByteArray(), pairing.certificateSha256.lowercase().toByteArray()))
                    throw CertificateException("Desktop certificate changed. Pair again on a trusted network.")
            }
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        return OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, trust)
            // Identity is the exact certificate fingerprint supplied through trusted pairing,
            // not the changing LAN hostname/IP. This verifier still explicitly checks the pin.
            .hostnameVerifier { _, session ->
                runCatching { trust.checkServerTrusted(arrayOf(session.peerCertificates[0] as X509Certificate), "RSA"); true }.getOrDefault(false)
            }
            // Desktop alone sends keepalive pings. Avoid two independent ping clocks.
            .pingInterval(0, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS).build()
    }

    private suspend fun connect(pairing: Pairing) = coroutineScope {
        val messages = Channel<String>(32)
        val closed = CompletableDeferred<Unit>()
        val http = client(pairing)
        val request = Request.Builder().url(HttpUrl.Builder().scheme("https").host(pairing.host).port(pairing.port)
            .addPathSegments(if (pairing.viewer) "v1/viewer" else "v1/device").build()).header("Authorization", "Bearer ${pairing.token}").build()
        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(deviceHello(repo.stored.value.deviceId, pairing.viewer).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > 1048576 || messages.trySend(text).isFailure) {
                    closed.completeExceptionally(IllegalStateException("Protocol input overflow"))
                    webSocket.close(1008, "Input overflow")
                }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.completeExceptionally(IllegalStateException("Desktop disconnected ($code)"))
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                closed.completeExceptionally(IllegalStateException(if (response?.code == 401) "Pairing token rejected" else t.message ?: "Connection failed"))
            }
        })
        val authenticated = CompletableDeferred<Unit>()
        val snapshotAcks = Channel<Pair<String,Int>>(1)
        var viewerCapable = false
        val viewerInterest = kotlinx.coroutines.flow.MutableStateFlow(false)
        var achievementCapable = false
        var achievementVersions = emptySet<Int>()
        val receiver = launch {
            for (raw in messages) {
                val msg = WireJson.parseToJsonElement(raw).jsonObject
                require(msg["protocol_version"]?.jsonPrimitive?.int == 1) { "Unsupported protocol" }
                if (pairing.viewer) {
                    require(repo.stored.value.pairing == pairing) { "Viewer pairing changed" }
                    if (msg.getValue("type").jsonPrimitive.content == "hello_ack") {
                        require(msg["desktop_id"]?.jsonPrimitive?.content == pairing.desktopId && msg["role"]?.jsonPrimitive?.content == "viewer" && msg["supported_viewer_version"]?.jsonPrimitive?.int == 1) { "Desktop upgrade required for tablet viewer" }
                        repo.connection.value = "Connected"; authenticated.complete(Unit)
                    } else {
                        authenticated.await()
                        repo.tablet.receive(msg, pairing)?.let { check(socket.send(it.toString())) { "Viewer connection lost" } }
                    }
                    continue
                }

                when (msg["type"]?.jsonPrimitive?.content) {
                    "hello_ack" -> {
                        viewerCapable = msg["supported_viewer_version"]?.jsonPrimitive?.intOrNull == 1
                        require(msg["desktop_id"]?.jsonPrimitive?.content == pairing.desktopId) { "Unexpected desktop identity" }
                        repo.connection.value = "Connected"
                        achievementVersions = msg["supported_achievement_versions"]?.jsonArray?.map { it.jsonPrimitive.int }?.toSet().orEmpty()
                        achievementCapable = 1 in achievementVersions
                        authenticated.complete(Unit)
                    }
                    "command" -> {
                        authenticated.await()
                        val id = msg.getValue("command_id").jsonPrimitive.content
                        val ack = try {
                            if (msg.getValue("action").jsonPrimitive.content == "achievements") {
                                require(achievementCapable) { "Desktop does not support achievement contract 1" }
                                val command = WireJson.decodeFromJsonElement<AchievementCommand>(msg.getValue("achievement"))
                                require(command.achievement_version in achievementVersions) { "Unnegotiated achievement contract" }
                                repo.achievementCommand(command)
                            } else {
                                val session = msg["session"]?.let { WireJson.decodeFromJsonElement<Session>(it) }
                                repo.command(id, msg.getValue("action").jsonPrimitive.content, session)
                            }
                        } catch (e: Exception) { CommandAck(false, e.message ?: "Invalid command") }
                        socket.send(buildJsonObject {
                            put("protocol_version", 1); put("type", "ack"); put("command_id", id)
                            put("ok", ack.ok); if (ack.error != null) put("error", ack.error)
                        }.toString())
                        socket.send(repo.stateEnvelope())
                    }
                    "viewer_interest" -> {
                        require(viewerCapable) { "Unnegotiated viewer interest" }
                        viewerInterest.value = msg.getValue("enabled").jsonPrimitive.boolean
                    }
                    "achievement_snapshot_ack" -> {
                        require(achievementCapable) { "Unnegotiated achievement snapshot acknowledgement" }
                        snapshotAcks.send(msg.getValue("snapshot_id").jsonPrimitive.content to msg.getValue("index").jsonPrimitive.int)
                    }
                    "history_delete" -> {
                        authenticated.await()
                        val syncId = msg.getValue("sync_id").jsonPrimitive.content
                        val deletion = WireJson.decodeFromJsonElement<HistoryDeletion>(msg.getValue("deletions"))
                        repo.mergeDeletions(deletion)
                        socket.send(buildJsonObject {
                            put("protocol_version", 1); put("type", "history_delete_ack"); put("sync_id", syncId)
                        }.toString())
                    }
                    "history_delete_ack" -> repo.deletionDelivered(msg.getValue("sync_id").jsonPrimitive.content)
                    "report_ack" -> repo.reportDelivered(msg.getValue("report_key").jsonPrimitive.content)
                    "error" -> repo.error.value = msg["message"]?.jsonPrimitive?.content ?: "Desktop error"
                    else -> throw IllegalArgumentException("Unknown desktop message")
                }
            }
        }
        val sender = launch {
            withTimeout(10000) { authenticated.await() }
            if (pairing.viewer) {
                repo.uiVisible.collect { visible ->
                    check(socket.send(buildJsonObject { put("protocol_version", 1); put("type", "viewer_presence"); put("visible", visible) }.toString())) { "Viewer connection lost" }
                }
                return@launch
            }
            var lastAchievementRevision = -1L
            val meaningful = repo.stored.map { stored ->
                Triple(stored.state.revision, stored.state.phase,
                    stored.reports.filter { it.key !in stored.delivered }.map { it.key }) to
                    (stored.deletions.revision to stored.deletionDelivered)
            }.distinctUntilChanged()
            combine(meaningful, repo.uiVisible, viewerInterest, repo.achievements) { state, visible, interest, ledger ->
                Triple(state, visible, interest) to ledger.revision
            }.collectLatest { (input, _) ->
                val meaning = input.first
                val visible = input.second
                val interested = input.third
                val state = meaning.first
                // Commands, deadlines and reports send immediately; checkpoint-only writes do not.
                while (isActive) {
                    socket.send(repo.stateEnvelope())
                    if (viewerCapable) {
                        val value = repo.stored.value.state
                        val clock = repo.readClock()
                        val frame = buildJsonObject { put("protocol_version", 1); put("type", "viewer_state"); put("data", buildJsonObject {
                            put("state", WireJson.encodeToJsonElement(value)); put("clock", WireJson.encodeToJsonElement(clock))
                        }) }
                        check(socket.send(frame.toString())) { "Viewer state connection lost" }
                    }
                    if (achievementCapable && repo.achievements.value.revision != lastAchievementRevision) {
                        val ledger = repo.achievements.value
                        val stamp = repo.readClock()
                        val zone = java.time.ZoneId.systemDefault()
                        val summaries = withContext(Dispatchers.Default) { ledger.definitions.map { d ->
                            val stats = AchievementEngine.stats(ledger,d,stamp.wall,zone)
                            buildJsonObject {
                                put("id",d.id); put("title",d.title); put("category",d.category); put("archived",d.archived)
                                put("current",if(stats.initialized) JsonPrimitive(stats.current) else JsonNull)
                                put("best",stats.best?.let(::JsonPrimitive) ?: JsonNull)
                                put("previous_best",stats.previousBest?.let(::JsonPrimitive) ?: JsonNull)
                                put("record_gap",stats.gap?.let(::JsonPrimitive) ?: JsonNull)
                                put("average",stats.average?.let(::JsonPrimitive) ?: JsonNull)
                                put("new_record",stats.newRecord)
                                put("streak_enabled",d.streakEnabled); put("progress_enabled",d.progressEnabled)
                                put("progress",d.progress?.let { WireJson.encodeToJsonElement(it) } ?: JsonNull)
                                ledger.indexes[d.id]?.let { l -> put("index_revision",l.revision); put("index_configuration",l.configuration); put("topics_completed",l.completed()); put("topics_total",l.leaves().size) }
                                put("progress_percent",d.progress?.percent?.let(::JsonPrimitive) ?: JsonNull)
                                put("complete",d.progressEnabled && d.progress?.complete == true)
                                val appearance = ledger.cardIcons[d.id]
                                put("icon_kind", appearance?.icon?.kind ?: "default")
                                put("icon_name", appearance?.icon?.name?.let(::JsonPrimitive) ?: JsonNull)
                                put("icon_updated_at", appearance?.atMs?.let(::JsonPrimitive) ?: JsonNull)
                            }
                        } }
                        val payload = withContext(Dispatchers.Default) { buildJsonObject {
                            put("achievement_version",1); put("ledger",WireJson.encodeToJsonElement(ledger))
                            put("as_of_ms",stamp.wall); put("zone",zone.id)
                            put("summary",JsonArray(summaries))
                        }.toString() }
                        val chunks = withContext(Dispatchers.Default) { AchievementEngine.snapshotChunks(payload) }
                        val snapshotId = java.util.UUID.randomUUID().toString()
                        chunks.forEachIndexed { index, data ->
                            check(socket.send(buildJsonObject {
                                put("protocol_version",1); put("type","achievement_snapshot")
                                put("achievement_version",1); put("snapshot_id",snapshotId)
                                put("index",index); put("count",chunks.size); put("data",data)
                            }.toString())) { "Achievement snapshot connection lost" }
                            // Bounded in-flight data even when the retained ledger exceeds OkHttp's queue limit.
                            val ack = withTimeout(10_000) { snapshotAcks.receive() }
                            require(ack == (snapshotId to index)) { "Unexpected achievement snapshot acknowledgement" }
                        }
                        lastAchievementRevision = ledger.revision
                    }
                    repo.stored.value.let { stored ->
                        if (stored.deletions.revision.isNotEmpty() && stored.deletions.revision != stored.deletionDelivered) {
                            socket.send(buildJsonObject {
                                put("protocol_version", 1); put("type", "history_delete"); put("sync_id", stored.deletions.revision)
                                put("deletions", WireJson.encodeToJsonElement(stored.deletions))
                            }.toString())
                        }
                        stored.reports.filter { it.key !in stored.delivered }.take(4).forEach { report ->
                            socket.send(buildJsonObject {
                                put("protocol_version", 1); put("type", "report"); put("report_key", report.key)
                                put("report", WireJson.encodeToJsonElement(report))
                            }.toString())
                        }
                    }
                    delay(if (interested && state.second in listOf("active", "paused")) 5_000L else PowerPolicy.syncMs(state.second, visible, state.third.isNotEmpty() || meaning.second.first != meaning.second.second))
                }
            }
        }
        try { closed.await() }
        finally {
            receiver.cancel(); sender.cancel(); messages.close(); snapshotAcks.close(); socket.cancel()
            if (pairing.viewer && repo.stored.value.pairing == pairing) withContext(NonCancellable) { repo.tablet.resetTransfer() }
            http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll()
        }
    }
}
