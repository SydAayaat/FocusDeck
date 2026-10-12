package com.hax.focusdeck

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@Composable internal fun PairDialog(existing: Pairing?,dismiss: ()->Unit,deviceId: String,networkAllowed: Boolean=true,trustChangeAllowed: Boolean=true,
    save: suspend (Pairing,Pairing?)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val trustAtOpen=remember { existing }
    // Credentials and invitation nonces deliberately stay out of saved-state Bundles.
    var input by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf<PairingInvitation?>(null) }
    var manual by remember { mutableStateOf<Pairing?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pairingJob by remember { mutableStateOf<Job?>(null) }
    var interrupted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if(interrupted) { error="Pairing interrupted; scan a fresh invitation or paste JSON again";interrupted=false } }
    fun receive(raw: String) {
        try { invitation=PairingInvitation.parse(raw);interrupted=true;error="" }
        catch(e: IllegalArgumentException) {error=e.message ?: "Invalid or unsupported QR"}
    }
    val scanner=rememberLauncherForActivityResult(ScanContract()) { result ->
        if(result.contents==null) {error="QR scanning interrupted. Manual JSON pairing remains available.";interrupted=false}
        else receive(result.contents)
    }
    fun scan() { interrupted=true;scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the FocusDeck invitation on your trusted desktop").setBeepEnabled(false).setBarcodeImageEnabled(false).setOrientationLocked(false)) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) scan() else error="Camera permission denied. Allow Camera in Android settings or use manual JSON pairing."
    }
    DeckAlertDialog(onDismissRequest={pairingJob?.cancel();dismiss()},title={Text("Pair desktop")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Create an invitation on your trusted desktop’s Devices page. Verify the host and certificate before confirming.",fontSize=13.sp)
            OutlinedButton({if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) scan() else permission.launch(Manifest.permission.CAMERA)},enabled=!busy,modifier=Modifier.testTag("pair-scan")) {Text("Scan QR Code")}
            if(!networkAllowed) Text("Isolated UI test: scanning is local; network pairing and trust storage are disabled.",fontSize=12.sp)
            Text("Manual JSON fallback",fontSize=14.sp)
            Text("Run focusdeck pairing (phone) or focusdeck pairing --viewer (tablet). Keep the permanent token private.",fontSize=12.sp)
            OutlinedTextField(input,{if(it.length<=4096) input=it},label={Text("Pairing JSON")},minLines=4,maxLines=8,enabled=!busy,modifier=Modifier.testTag("pair-json"))
            if(error.isNotEmpty()) Text(error,color=DeckColors.secondary,fontSize=12.sp,modifier=Modifier.testTag("pair-error"))
            if(busy) {CircularProgressIndicator();Text("Authenticating with the pairing host…")}
        }
    },confirmButton={TextButton({
        try {val obj=WireJson.parseToJsonElement(input).jsonObject;val candidate=WireJson.decodeFromJsonElement<Pairing>(obj["pairing"] ?: obj);candidate.validate();manual=candidate;error=""}
        catch(e: Exception) {error="Invalid pairing JSON. Check the desktop’s manual pairing output."}
    },enabled=!busy) {Text("Review JSON")}},dismissButton={TextButton({pairingJob?.cancel();dismiss()}) {Text("Cancel")}})
    val candidate=invitation?.pin() ?: manual
    if(candidate!=null) DeckAlertDialog(onDismissRequest={pairingJob?.cancel();busy=false;invitation=null;manual=null;interrupted=false;error="Pairing interrupted; create a fresh invitation"},title={Text(if(trustAtOpen==null) "Confirm trusted desktop" else "Confirm pairing change")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Desktop ${candidate.desktopId}\n${candidate.host}:${candidate.port}\nRole: ${candidate.role}")
            Text("Certificate SHA-256\n${candidate.certificateSha256}",fontSize=12.sp)
            if(trustAtOpen!=null) Text("Existing desktop: ${trustAtOpen.desktopId}. Confirming replaces the app’s pairing after authentication. Your activity is preserved.")
            if(invitation!=null) Text("Invitation expires in two minutes and can be used once.")
        }
    },confirmButton={TextButton({
        if(!networkAllowed) {error="Pairing is disabled in the isolated visual test";invitation=null;manual=null;interrupted=false}
        else if(invitation!=null && trustAtOpen?.let {it.desktopId==candidate.desktopId && it.role==candidate.role && it.certificateSha256.equals(candidate.certificateSha256,true)}==true) {error="Already paired with this desktop. Use CONNECT DESKTOP in Settings.";invitation=null;interrupted=false}
        else if(!trustChangeAllowed && manual!=trustAtOpen) {error="Finish the current session before changing desktops";invitation=null;manual=null;interrupted=false}
        else {
            busy=true;val selected=invitation;val selectedManual=manual
            pairingJob=scope.launch {
                try {val next=selected?.let {redeemPairingInvitation(it,deviceId)} ?: selectedManual ?: error("Missing pairing");save(next,trustAtOpen);invitation=null;manual=null;interrupted=false}
                catch(e: TimeoutCancellationException) {error="Pairing host unreachable or timed out; create a fresh invitation";invitation=null;manual=null;interrupted=false}
                catch(e: CancellationException) {throw e}
                catch(e: Exception) {error=when(e) {is kotlinx.serialization.SerializationException->"Invalid host response; create a fresh invitation";is IllegalArgumentException->e.message?.take(180) ?: "Pairing invitation rejected";else->"Pairing failed or interrupted; create a fresh invitation"};invitation=null;manual=null;interrupted=false}
                finally {busy=false}
            }
        }
    },enabled=!busy) {Text("Confirm pairing")}},dismissButton={TextButton({pairingJob?.cancel();busy=false;invitation=null;manual=null;interrupted=false}) {Text("Cancel")}})
}
