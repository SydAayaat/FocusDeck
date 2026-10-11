package com.hax.focusdeck

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable internal fun SoundSettingsDialog(stored: StoredDeck, repo: DeckRepository, dismiss: () -> Unit) {
    val sounds=(LocalContext.current.applicationContext as DeckApplication).sounds
    val scope=rememberCoroutineScope()
    DisposableEffect(sounds) { onDispose { repo.scope.launch { sounds.pruneImports() } } }
    var draft by remember { mutableStateOf(stored.audio) }
    var completionEnabled by remember { mutableStateOf(stored.sound) }
    var slot by remember { mutableStateOf("timer") }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            importing=true
            val destinationSlot=slot
            try {
                val file=sounds.import(uri,destinationSlot)
                draft=draft.withChoice(destinationSlot,"custom").copy(customFiles=draft.customFiles+(destinationSlot to file)); error=""
            } catch(e: Exception) { error=e.message ?: "Could not import sound" }
            finally { importing=false }
        }
    }
    DeckAlertDialog(onDismissRequest={ if(!importing) dismiss() },title={ Text("Sounds") },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Volume ${(draft.volume*100).toInt()}%")
            Slider(draft.volume,{ draft=draft.copy(volume=it) })
            SoundPolicy.slots.forEach { item ->
                Text(when(item) { "timer" -> "Timer clock"; "stopwatch" -> "Stopwatch clock"; "ui" -> "Buttons & navigation"; else -> "Completion & overtime" },color=DeckColors.accent)
                if(item=="completion") Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(completionEnabled,{completionEnabled=it}); Text("Enable boundary sounds") }
                SoundPolicy.options(item).chunked(2).forEach { row -> Row(Modifier.fillMaxWidth()) {
                    row.forEach { (id,title) -> TextButton(enabled=!importing,modifier=Modifier.weight(1f),onClick={
                        if(id=="custom" && draft.customFiles[item]==null) {slot=item;picker.launch(arrayOf("audio/*"))}
                        else draft=draft.withChoice(item,id)
                    }) {Text((if(draft.choice(item)==id) "✓ " else "")+title,color=if(draft.choice(item)==id) DeckColors.accent else DeckColors.muted)} }
                } }
                Row {
                    TextButton(enabled=!importing && draft.choice(item)!="off",onClick={sounds.play(item,draft,force=true)}) {Text("PREVIEW")}
                    TextButton(enabled=!importing,onClick={slot=item;picker.launch(arrayOf("audio/*"))}) {Text("CHOOSE FILE")}
                }
                draft.customFiles[item]?.let { Text("Custom sample copied to app storage.",color=DeckColors.muted) }
                HorizontalDivider()
            }
            Text("Clock samples repeat once per second while running, including overtime; pause stops them. Choose clips up to 1 second for clocks or 2 seconds for feedback (2 MiB, mono/stereo, ≤48 kHz). Uses media volume.",color=DeckColors.muted)
            if(importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error.isNotBlank()) Text(error,color=DeckColors.error)
        }
    },confirmButton={TextButton(enabled=!importing,onClick={ scope.launch {
        try {repo.soundPreferences(draft,completionEnabled);dismiss()} catch(e:Exception){error=e.message.orEmpty()}
    } }) {Text("SAVE")}},dismissButton={TextButton(enabled=!importing,onClick=dismiss){Text("CANCEL")}})
}
