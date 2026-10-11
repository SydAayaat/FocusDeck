package com.hax.focusdeck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.ZoneId

@Composable internal fun DeveloperPreviewDialog(dismiss:()->Unit) {
    val now=remember {System.currentTimeMillis()}
    val zone=remember {ZoneId.systemDefault()}
    var ledger by remember {mutableStateOf(AchievementStore())}
    var add by remember {mutableStateOf(false)}
    Dialog(dismiss,properties=DialogProperties(usePlatformDefaultWidth=false, decorFitsSystemWindows=false)) {
        DialogNavigationRefresh()
        Surface(Modifier.fillMaxSize(),color=androidx.compose.ui.graphics.Color.Black) {
            Column(Modifier.safeDrawingPadding().padding(12.dp)) {
                Text("DEVELOPER PREVIEW · synthetic data",color=DeckColors.secondary)
                Row {
                    TextButton(onClick={add=true}) {Text("CREATE CARD")}
                    TextButton(onClick={ledger=DeveloperFixtures.samples(now,zone)}) {Text("SAMPLES")}
                    TextButton(onClick={ledger=AchievementStore()}) {Text("CLEAR")}
                    TextButton(onClick=dismiss) {Text("CLOSE")}
                }
                Text("Temporary sandbox. Cards and history disappear when closed; never synced or included in real reports.",color=DeckColors.muted)
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalDashboardPreview provides true) { AchievementPages(ledger,now,zone,{key -> ledger=ledger.copy(seenRecords=ledger.seenRecords+key)},dismiss,
                        onLayout={id,size,target ->
                            if(id=="__reset_order__") ledger=ledger.copy(cardOrder=emptyList()) else {
                            val order=AchievementEngine.filtered(ledger,"All").map {it.id}.toMutableList()
                            if(target!=null && id in order && target in order) {val before=order.indexOf(id)<order.indexOf(target);order.remove(id);order.add((order.indexOf(target)+(if(before) 1 else 0)).coerceIn(0,order.size),id)}
                            ledger=ledger.copy(cardOrder=order,cardSizes=if(size==null) ledger.cardSizes else ledger.cardSizes+(id to size))
                            }
                        },onManage={id,action -> ledger=when(action) {
                            "delete" -> ledger.copy(definitions=ledger.definitions.filterNot {it.id==id},days=ledger.days.mapValues {(_,d)->d.copy(statuses=d.statuses-id,activities=d.activities-id)},progressHistory=ledger.progressHistory.filterNot {it.achievementId==id})
                            else -> ledger.copy(definitions=ledger.definitions.map {if(it.id==id) it.copy(archived=action=="archive") else it})
                        }}) }
                }
            }
        }
        if(add) DeveloperCardDialog({add=false}) {title,category,capability,current,history,value,total ->
            ledger=DeveloperFixtures.add(ledger,now,zone,title,category,capability,current,history,value,total);add=false
        }
    }
}
@Composable private fun DeveloperCardDialog(dismiss:()->Unit,create:(String,String,String,Int,List<Int>,Double,Double)->Unit) {
    var title by remember {mutableStateOf("Preview card")}
    var category by remember {mutableStateOf("Learning")}
    var capability by remember {mutableStateOf("STREAK_AND_PROGRESS")}
    var streak by remember {mutableStateOf("8")}
    var history by remember {mutableStateOf("3, 5, 7")}
    var value by remember {mutableStateOf("50")}
    var total by remember {mutableStateOf("100")}
    var error by remember {mutableStateOf("")}
    DeckAlertDialog(dismiss,title={Text("Create preview card")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(title,{title=it},label={Text("Title")},singleLine=true)
        Text("Category")
        AchievementEngine.categories.drop(1).chunked(3).forEach {row->Row {row.forEach {name->TextButton(onClick={category=name}) {Text((if(category==name) "✓ " else "")+name)}}}}
        listOf("STREAK_ONLY","PROGRESS_ONLY","STREAK_AND_PROGRESS").forEach {type->TextButton(onClick={capability=type}){Text((if(capability==type) "✓ " else "")+type)}}
        if(capability!="PROGRESS_ONLY") {
            OutlinedTextField(streak,{streak=it},label={Text("Current streak in days (0–90)")},singleLine=true)
            OutlinedTextField(history,{history=it},label={Text("Completed streaks: 3, 5, 7")},singleLine=true)
        }
        if(capability!="STREAK_ONLY") {
            OutlinedTextField(value,{value=it},label={Text("Current progress")},singleLine=true)
            OutlinedTextField(total,{total=it},label={Text("Total progress")},singleLine=true)
        }
        if(error.isNotBlank()) Text(error,color=DeckColors.error)
    }},confirmButton={TextButton(onClick={try {
        create(title,category,capability,if(capability=="PROGRESS_ONLY") 0 else streak.toInt(),if(capability=="PROGRESS_ONLY" || history.isBlank()) emptyList() else history.split(',').map{it.trim().toInt()},if(capability=="STREAK_ONLY") 0.0 else value.toDouble(),if(capability=="STREAK_ONLY") 100.0 else total.toDouble())
    }catch(e:Exception){error=e.message ?: "Check the values"}}){Text("CREATE")}},dismissButton={TextButton(onClick=dismiss){Text("CANCEL")}})
}
