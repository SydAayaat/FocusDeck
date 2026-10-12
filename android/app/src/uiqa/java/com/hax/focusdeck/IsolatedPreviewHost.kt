package com.hax.focusdeck

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

/** Shared menu/dialogs, with ephemeral appearance and no repository, service or transport. */
@Composable internal fun IsolatedPreviewHost(initialScreen: String,scenario: String,initialImmersive: Boolean,motion: String,exit: ()->Unit) {
    var theme by rememberSaveable { mutableStateOf("gold") }
    var selectedMotion by rememberSaveable { mutableStateOf(motion) }
    var immersive by rememberSaveable { mutableStateOf(initialImmersive) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var pairing by rememberSaveable { mutableStateOf(false) }
    var devices by rememberSaveable { mutableStateOf(false) }
    val stored=StoredDeck("visualtest-disposable-device",developerMode=true,theme=theme,motion=selectedMotion,immersive=immersive)
    val activity=androidx.compose.ui.platform.LocalContext.current as android.app.Activity
    DisposableEffect(immersive) {
        androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {
            if(immersive) hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()) else show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {}
    }
    DeckTheme(theme,selectedMotion,immersive) {
        CompositionLocalProvider(LocalPreviewHostActions provides PreviewHostActions({settings=true},{pairing=true},{devices=true})) {
            SyntheticPreviewLoader(exit,initialScreen,scenario)
        }
        if(settings) SettingsDialog(stored,"Offline · isolated UI test",{settings=false},
            save={_,im,_,_,mo,th,_->immersive=im;selectedMotion=mo;theme=th},connect={},disconnect={},developer={if(!it) exit()},connectionControls=false)
        if(pairing) PairDialog(null,{pairing=false},stored.deviceId,networkAllowed=false) {_,_->error("Visual test cannot store pairing")}
        if(devices) DeviceManagementDialog(null,"Offline · isolated UI test",{devices=false},{devices=false;pairing=true},{},{},connectionControls=false)
    }
}
