package com.hax.focusdeck

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

internal val LocalDateSaver=Saver<LocalDate,String>(save={it.toString()},restore={LocalDate.parse(it)})
internal val DrillStackSaver=Saver<List<Pair<HistoryPeriod,LocalDate>>,ArrayList<String>>(
    save={ArrayList(it.map { (period,date)->"${period.name}|$date" })},
    restore={it.map { val parts=it.split('|');HistoryPeriod.valueOf(parts[0]) to LocalDate.parse(parts[1]) }})

/** Registered before child screens, so details and dialogs retain dispatcher priority. */
@Composable internal fun DestinationBackHandler(home: Boolean,enabled: Boolean=true,goHome: ()->Unit) {
    val context=LocalContext.current
    var firstBack by remember { mutableLongStateOf(0L) }
    LaunchedEffect(home) { firstBack=0L }
    BackHandler(enabled) {
        if(!home) goHome() else {
            val now=SystemClock.elapsedRealtime()
            if(firstBack!=0L && now-firstBack<=2000) (context as? Activity)?.finish()
            else {firstBack=now;Toast.makeText(context,"Swipe again to exit the app",Toast.LENGTH_SHORT).show()}
        }
    }
}
