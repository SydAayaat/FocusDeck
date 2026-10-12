package com.hax.focusdeck

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Preferences and trust use the genuine repository; synthetic records never cross this boundary. */
@Composable internal fun ProductionPreviewHost(repo: DeckRepository,stored: StoredDeck,disable: ()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val connection by repo.connection.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(false) }
    var pairing by rememberSaveable { mutableStateOf(false) }
    var devices by rememberSaveable { mutableStateOf(false) }
    var sounds by rememberSaveable { mutableStateOf(false) }
    fun connect() { context.startForegroundService(Intent(context,FocusService::class.java)) }
    fun disconnect() { context.stopService(Intent(context,FocusService::class.java)) }
    CompositionLocalProvider(LocalPreviewHostActions provides PreviewHostActions({settings=true},{pairing=true},{devices=true},{sounds=true})) {
        SyntheticPreviewLoader(disable)
    }
    if(settings) SettingsDialog(stored,connection,{settings=false},
        save={awake,immersive,sound,dim,motion,theme,rate->scope.launch { repo.preferences(awake,immersive,sound,dim,motion,theme,rate) }},
        connect=::connect,disconnect=::disconnect,developer={enabled->scope.launch { repo.developerMode(enabled) }})
    if(pairing) PairDialog(stored.pairing,{pairing=false},stored.deviceId,
        trustChangeAllowed=stored.state.phase !in listOf("active","paused","ready","between","quality","reward","session_reward")) { next, expected ->
        repo.pair(next,expected);pairing=false;connect()
    }
    if(devices) DeviceManagementDialog(stored.pairing,connection,{devices=false},{devices=false;pairing=true},::connect,::disconnect)
    if(sounds) SoundSettingsDialog(stored,repo) {sounds=false}
}

@Composable internal fun DeviceManagementDialog(pairing: Pairing?,connection: String,dismiss: ()->Unit,pair: ()->Unit,
    connect: ()->Unit,disconnect: ()->Unit,connectionControls: Boolean=true) {
    DeckAlertDialog(onDismissRequest=dismiss,title={Text("Devices")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(pairing?.let { "Desktop ${it.desktopId}\n${it.host}:${it.port}\nRole: ${it.role}" } ?: "No desktop paired")
            Text(connection)
            TextButton(pair) {Text(if(pairing==null) "Pair desktop" else "Change paired desktop")}
            if(pairing!=null && connectionControls) {TextButton(connect) {Text("CONNECT DESKTOP")};TextButton(disconnect) {Text("DISCONNECT DESKTOP")}}
        }
    },confirmButton={TextButton(dismiss) {Text("Close")}})
}
