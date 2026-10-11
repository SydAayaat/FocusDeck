package com.hax.focusdeck

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.time.ZoneId

/** Layout-only controls never change timer, scoring or achievement measurements. */
@Composable
internal fun AchievementCards(store: AchievementStore, visible: List<AchievementDefinition>, now: Long, zone: ZoneId,
    editing: Boolean, category: String, onRecordViewed: suspend (String) -> Unit,
    onOpen: (String) -> Unit, onEdit: () -> Unit, onLayout: (String, String?, String?) -> Unit,
    onManage: (String, String) -> Unit, statsById: Map<String, AchievementStats>, readOnly: Boolean = false, header: @Composable () -> Unit = {}) {
    val layout=LocalDashboardLayout.current ?: rememberDashboardLayout("achievements")
    LaunchedEffect(editing) { layout.editing=editing }
    var menu by remember { mutableStateOf<String?>(null) }
    var deletion by remember { mutableStateOf<String?>(null) }
    val compactSpan=if(LocalConfiguration.current.screenWidthDp>=372 && LocalDensity.current.fontScale<=1.25f) 4 else 6
    val cards=visible.mapNotNull { item -> statsById[item.id]?.let { stats -> DashboardCard("achievement-${item.id}",when(store.cardSizes[item.id]) { "full"->12;"wide"->if(compactSpan==4) 8 else 12;else->if(item.id in store.indexes && compactSpan==4) 8 else compactSpan },220,minimumWidth=104) {
        Box {
            AchievementTile(stats,store,onRecordViewed,Modifier.fillMaxWidth(),size=store.cardSizes[item.id] ?: if(item.id in store.indexes) "wide" else "compact",options={menu=item.id},longPress=if(editing) null else ({menu=item.id})) {
                if(editing) menu=item.id else onOpen(item.id)
            }
            DropdownMenu(expanded=menu==item.id,onDismissRequest={menu=null}) {
                DropdownMenuItem(text={Text("Edit layout")},onClick={menu=null;onEdit()})
                if(!readOnly) { listOf("Compact" to "compact","Wide" to "wide","Full-width" to "full").forEach { (label,preset) -> DropdownMenuItem(text={Text(label)},onClick={menu=null;layout.value=layout.value.copy(cards=layout.value.cards-("achievement-${item.id}"));layout.persist();onLayout(item.id,preset,null)}) } }
                val position=visible.indexOf(item)
                DropdownMenuItem(text={Text("Move earlier")},enabled=!readOnly && position>0,onClick={menu=null;onLayout(item.id,null,visible[position-1].id)})
                DropdownMenuItem(text={Text("Move later")},enabled=!readOnly && position<visible.lastIndex,onClick={menu=null;onLayout(item.id,null,visible[position+1].id)})
                if(!readOnly) DropdownMenuItem(text={Text("Archive · keep history")},onClick={menu=null;onManage(item.id,"archive")})
                if(!readOnly) DropdownMenuItem(text={Text("Delete permanently")},onClick={menu=null;deletion=item.id})
            }
        }
    } } }
    DashboardGrid(cards,layout,Modifier.fillMaxSize(),scrollTag="Achievement cards $category",header={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { header();if(visible.isEmpty()) Text("No ${category.lowercase()} achievements yet. GPT can create them through your desktop.",color=AGrey) }
    },allowReorder=!readOnly,onReorder={id,target->onLayout(id.removePrefix("achievement-"),null,target.removePrefix("achievement-"))})
    deletion?.let { id -> DeckAlertDialog(onDismissRequest = { deletion = null }, title = { Text("Delete this card permanently?") },
        text = { Text("This removes ${store.definitions.find { it.id == id }?.title ?: id} and its achievement history. Archive keeps the history. Study session logs and completed daily contracts remain.") },
        confirmButton = { TextButton(onClick = { deletion = null; onManage(id, "delete") }) { Text("DELETE") } },
        dismissButton = { TextButton(onClick = { deletion = null }) { Text("CANCEL") } }) }
}
