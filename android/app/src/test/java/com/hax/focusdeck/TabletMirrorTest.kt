package com.hax.focusdeck

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TabletMirrorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val pairing = Pairing(1, "127.0.0.1", 8765, "t".repeat(40), "a".repeat(64), "desktop", "viewer")
    private fun status(authority: String? = "phone") = buildJsonObject {
        put("type", "viewer_status"); put("desktop_id", "desktop")
        put("authority_device_id", authority?.let(::JsonPrimitive) ?: JsonNull)
        put("phone_connected", true); put("phone_supports_viewer", true)
    }
    private fun state() = ViewerState("desktop", "phone", DeckState(phase = "idle", revision = 2), ClockNow(10000, 200, 1))
    private suspend fun send(mirror: TabletMirror, kind: String, raw: String) {
        val chunks = raw.chunked(32_000)
        chunks.forEachIndexed { index, data ->
            val ack = mirror.receive(buildJsonObject {
                put("type", "viewer_chunk"); put("transfer_id", "$kind-transfer"); put("document", kind)
                put("index", index); put("count", chunks.size); put("data", data)
            }, pairing)!!
            assertEquals(index, ack.getValue("index").jsonPrimitive.int)
        }
    }
    private fun report(): Report = WireJson.decodeFromString(File("src/test/resources/mixed-mode-report.json").readText())
    private fun batch(kind: String, sync: String = "sync") = buildJsonObject {
        put("type", kind); put("sync_id", sync); if (kind == "viewer_reports_end") put("as_of_ms", 20000); put("desktop_id", "desktop"); put("authority_device_id", "phone")
    }

    @Test fun backwardPairingDefaultsToPhoneAndViewerCacheDoesNotBecomeTimerState() = runBlocking {
        val legacy = WireJson.encodeToJsonElement(pairing).jsonObject.toMutableMap().also { it.remove("role") }
        assertFalse(WireJson.decodeFromJsonElement<Pairing>(JsonObject(legacy)).viewer)
        val directory = temporary.newFolder(); val mirror = TabletMirror(directory)
        mirror.load(pairing); mirror.receive(status(), pairing); send(mirror, "state", WireJson.encodeToString(state()))
        mirror.resetTransfer()
        assertEquals(state(), mirror.snapshot.value.state); assertFalse(mirror.snapshot.value.phoneConnected)
        val restored = TabletMirror(directory); restored.load(pairing)
        assertEquals(state(), restored.snapshot.value.state)
        assertFalse(restored.snapshot.value.phoneConnected)
        restored.load(pairing.copy(role = "phone")); assertNull(restored.snapshot.value.state)
    }

    @Test fun incompleteReportBatchKeepsPreviousArchiveAndReconnectPublishesDeletion() = runBlocking {
        val directory = temporary.newFolder(); val mirror = TabletMirror(directory)
        mirror.load(pairing); mirror.receive(status(), pairing); send(mirror, "state", WireJson.encodeToString(state()))
        mirror.receive(batch("viewer_reports_begin"), pairing)
        send(mirror, "report", buildJsonObject { put("sync_id", "sync"); put("report", WireJson.encodeToJsonElement(report())) }.toString())
        assertTrue(mirror.snapshot.value.reports.isEmpty())
        mirror.receive(batch("viewer_reports_end"), pairing)
        assertEquals(listOf(report()), mirror.snapshot.value.reports)
        mirror.receive(batch("viewer_reports_begin", "unfinished"), pairing); mirror.resetTransfer()
        val restored = TabletMirror(directory); restored.load(pairing)
        assertEquals(listOf(report()), restored.snapshot.value.reports)
        restored.receive(status(), pairing); restored.receive(batch("viewer_reports_begin", "delete"), pairing)
        restored.receive(batch("viewer_reports_end", "delete"), pairing)
        assertTrue(restored.snapshot.value.reports.isEmpty())
        mirror.load(pairing); assertTrue(mirror.snapshot.value.reports.isEmpty())
    }

    @Test fun forgottenAuthorityCannotReappearFromOldCacheOnRestart() = runBlocking {
        val directory = temporary.newFolder(); val mirror = TabletMirror(directory)
        mirror.load(pairing); mirror.receive(status(), pairing); send(mirror, "state", WireJson.encodeToString(state()))
        mirror.receive(status(null), pairing)
        val restored = TabletMirror(directory); restored.load(pairing)
        assertNull(restored.snapshot.value.authorityId); assertNull(restored.snapshot.value.state)
        restored.load(pairing.copy(desktopId = "other-desktop")); assertNull(restored.snapshot.value.state)
    }

    @Test fun writableFramesAndWrongSourceCannotReplaceCommittedSnapshot() = runBlocking {
        val mirror = TabletMirror(temporary.newFolder()); mirror.load(pairing); mirror.receive(status(), pairing)
        send(mirror, "state", WireJson.encodeToString(state()))
        for (kind in listOf("command", "history_delete", "report", "achievement_snapshot")) {
            assertTrue(runCatching { mirror.receive(buildJsonObject { put("type", kind) }, pairing) }.isFailure)
            assertEquals(state(), mirror.snapshot.value.state)
        }
        assertTrue(runCatching { send(mirror, "state", WireJson.encodeToString(state().copy(authorityId = "wrong-phone"))) }.isFailure)
        assertEquals(state(), mirror.snapshot.value.state)
        mirror.resetTransfer()
    }

    @Test fun partialChunkDoesNotPublishAndOutOfOrderFailsWithoutReplacingCache() = runBlocking {
        val directory = temporary.newFolder(); val mirror = TabletMirror(directory)
        mirror.load(pairing); mirror.receive(status(), pairing); send(mirror, "state", WireJson.encodeToString(state()))
        val first = buildJsonObject { put("type", "viewer_chunk"); put("transfer_id", "partial"); put("document", "state"); put("index", 0); put("count", 2); put("data", "{") }
        mirror.receive(first, pairing)
        assertTrue(runCatching { mirror.receive(first, pairing) }.isFailure)
        mirror.resetTransfer(); mirror.load(pairing)
        assertEquals(state(), mirror.snapshot.value.state)
    }
}
