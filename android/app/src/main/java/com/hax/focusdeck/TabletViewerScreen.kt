package com.hax.focusdeck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun TabletViewerBanner(repo: DeckRepository) {
    val mirror by repo.tablet.snapshot.collectAsStateWithLifecycle()
    val connection by repo.connection.collectAsStateWithLifecycle()
    val sourceTime = mirror.state?.clock?.wall
    val time = sourceTime?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(mirror.ledger?.zone ?: ZoneId.systemDefault().id))
        .format(DateTimeFormatter.ofPattern("d MMM HH:mm:ss")) } ?: "No session snapshot yet"
    val status = when {
        connection != "Connected" -> "Offline · cached view"
        !mirror.phoneConnected -> "Phone offline · cached view"
        !mirror.phoneSupportsViewer -> "Phone upgrade needed for session status"
        else -> "Connected to phone through PC"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("TABLET VIEWER · READ ONLY", color = DeckColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text("$status · Session observed $time", color = DeckColors.muted, fontSize = 10.sp)
        mirror.reportsAtMs?.let { stamp ->
            val synchronized = Instant.ofEpochMilli(stamp).atZone(ZoneId.of(mirror.ledger?.zone ?: ZoneId.systemDefault().id)).format(DateTimeFormatter.ofPattern("d MMM HH:mm:ss"))
            Text("History synchronized $synchronized (PC)", color = DeckColors.muted, fontSize = 10.sp)
        }
        mirror.ledger?.let { ledger ->
            val observed = Instant.ofEpochMilli(ledger.atMs).atZone(ZoneId.of(ledger.zone)).format(DateTimeFormatter.ofPattern("d MMM HH:mm:ss"))
            Text("Cards observed $observed · ${ledger.zone}", color = DeckColors.muted, fontSize = 10.sp)
        }
    }
}

@Composable fun TabletSessionScreen(repo: DeckRepository) {
    val mirror by repo.tablet.snapshot.collectAsStateWithLifecycle()
    val source = mirror.state
    val session = source?.state?.session
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("PHONE SESSION", color = DeckColors.muted, fontSize = 12.sp)
        if (source == null || session == null) {
            Text("Waiting for the phone", fontSize = 24.sp)
            Text("Pair the phone normally and this tablet with focusdeck pairing --viewer. Cards and history appear after synchronization.", color = DeckColors.muted)
        } else {
            val state = source.state
            val block = session.blocks.getOrNull(state.index)
            val timer = TimerEngine(state)
            Text(session.title, fontSize = 23.sp, fontWeight = FontWeight.Medium)
            block?.let {
                Text(it.title, color = DeckColors.accent, fontSize = 27.sp)
                Text(state.phase.replace('_', ' ').uppercase(), color = DeckColors.muted, fontSize = 13.sp)
                val active = timer.currentActive(source.clock)
                val value = if (it.stopwatch) active else if (timer.isOvertime(source.clock)) timer.overtime(source.clock) else timer.remaining(source.clock)
                Text((if (!it.stopwatch && timer.isOvertime(source.clock)) "+" else "") + humanTime(value),
                    color = if (timer.isOvertime(source.clock)) DeckColors.error else DeckColors.accent, fontSize = 48.sp)
                Text("${if (it.stopwatch) "STOPWATCH" else "COUNTDOWN"} · block ${state.index + 1} / ${session.blocks.size}", color = DeckColors.muted, fontSize = 12.sp)
            }
            Text("${state.results.count { it.outcome == "done" }} done · ${state.results.count { it.outcome == "skipped" }} skipped", color = DeckColors.muted)
            Text("Time is shown at the last phone update. Control the session on the phone; GPT commands always target the phone.", color = DeckColors.muted, fontSize = 12.sp)
        }
    }
}
