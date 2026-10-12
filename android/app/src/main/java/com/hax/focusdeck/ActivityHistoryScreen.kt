package com.hax.focusdeck

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun ActivityHistoryDialog(repo: DeckRepository,dismiss: ()->Unit,openReport: (Report)->Unit,archive: ()->Unit) {
    val reports by repo.history.collectAsStateWithLifecycle()
    val store by repo.achievements.collectAsStateWithLifecycle()
    val zoneId by repo.displayZone.collectAsStateWithLifecycle()
    val clock by repo.now.collectAsStateWithLifecycle()
    Dialog(dismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        DialogNavigationRefresh()
        Surface(Modifier.fillMaxSize(),color=DashboardBackground) { ActivityHistoryScreen(reports,store,ZoneId.of(zoneId),Instant.ofEpochMilli(clock.wall).atZone(ZoneId.of(zoneId)).toLocalDate(),dismiss,openReport,archive) }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ActivityHistoryScreen(reports: List<Report>,store: AchievementStore,zone: ZoneId,today: LocalDate,dismiss: ()->Unit,openReport: (Report)->Unit,archive: ()->Unit,syntheticSource: SyntheticDataset? = null) {
    val compactSynthetic=LocalSyntheticPreview.current && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<500
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var selectedNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var ascending by rememberSaveable { mutableStateOf(false) }
    var fromText by rememberSaveable { mutableStateOf<String?>(null) }
    var toText by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<HistoryActivity?>(null) }
    val categories=selectedNames.mapNotNull { name -> ActivityCategory.entries.find { it.name==name } }.toSet()
    var pageCount by remember(categories,query,fromText,toText,ascending) { mutableIntStateOf(1) }
    val records by produceState<List<HistoryActivity>?>(null,reports,store,zone) { value=if(syntheticSource==null) withContext(Dispatchers.Default) { ActivityHistory.records(reports,store,zone) } else emptyList() }
    val filtered by produceState<List<HistoryActivity>?>(null,records,categories,query,fromText,toText,ascending,pageCount,syntheticSource) {
        value=if(syntheticSource!=null) withContext(Dispatchers.IO) {
            syntheticSource.historyPage(categories,query,fromText?.let(LocalDate::parse),toText?.let(LocalDate::parse),ascending,(pageCount-1)*80)
        } else records?.let { withContext(Dispatchers.Default) { ActivityHistory.filter(it,categories,query,fromText?.let(LocalDate::parse),toText?.let(LocalDate::parse),ascending) } }
    }
    LaunchedEffect(records) { if(syntheticSource==null && detail?.let { d -> records?.none { it.id==d.id } }==true) detail=null }
    Column(Modifier.fillMaxSize().windowInsetsPadding(dashboardViewportInsets()).padding(horizontal=12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(dismiss) { ActivityGlyph("close",Modifier.size(22.dp),DeckColors.muted) }
            Text("History",Modifier.weight(1f),fontSize=28.sp,fontWeight=FontWeight.Bold)
            IconButton({search=!search},Modifier.testTag("history-search").semantics { contentDescription="Search activity" }) { ActivityGlyph("search",Modifier.size(22.dp),DeckColors.muted) }
            IconButton({picking=true},Modifier.testTag("history-date").semantics { contentDescription="Choose activity date range" }) { ActivityGlyph("calendar",Modifier.size(22.dp),DeckColors.muted) }
            IconButton({ascending=!ascending},Modifier.testTag("history-sort").semantics { contentDescription=if(ascending) "Sort newest first" else "Sort oldest first" }) { ActivityGlyph("arrow-up-down",Modifier.size(22.dp),DeckColors.accent) }
        }
        if(!compactSynthetic) Text(if(LocalSyntheticPreview.current) "Synthetic sessions, activity and changes." else "Your recorded sessions, activity and changes.",color=DeckColors.muted,fontSize=12.sp)
        if(search) OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("history-query"),singleLine=true,label={Text("Search recorded activity")})
        val chipColors=FilterChipDefaults.filterChipColors(containerColor=DeckColors.surface,selectedContainerColor=DeckColors.surface,labelColor=DeckColors.muted,selectedLabelColor=DeckColors.accent)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(categories.isEmpty(),{selectedNames=emptyList()},label={Text("All Activity",fontSize=11.sp)},colors=chipColors,border=BorderStroke(1.dp,if(categories.isEmpty()) DeckColors.accent else DeckColors.track))
            ActivityCategory.entries.forEach { category -> FilterChip(category in categories,{
                selectedNames=if(category.name in selectedNames) selectedNames-category.name else selectedNames+category.name
            },label={Text(category.label,fontSize=11.sp)},colors=chipColors,border=BorderStroke(1.dp,if(category in categories) DeckColors.accent else DeckColors.track),modifier=Modifier.testTag("history-filter-${category.name}")) }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(fromText==null && toText==null) if(ascending) "Oldest first" else "Newest first" else "${fromText ?: "Any"} → ${toText ?: "Any"}",Modifier.weight(1f),fontSize=10.sp,color=DeckColors.muted)
            if(fromText!=null || toText!=null) TextButton({fromText=null;toText=null}) { Text("Clear dates",fontSize=10.sp) }
            if(!LocalSyntheticPreview.current) TextButton(archive) { Text("Archive actions",fontSize=11.sp) }
        }
        val rows=filtered
        val list=rememberLazyListState()
        LaunchedEffect(pageCount,query,categories,fromText,toText,ascending) { list.scrollToItem(0) }
        if(syntheticSource!=null) Row(Modifier.height(if(compactSynthetic) 36.dp else 48.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton({pageCount--},enabled=pageCount>1,modifier=Modifier.testTag("history-previous")) {Text("Previous")}
            Text("Page $pageCount",fontSize=11.sp)
            TextButton({pageCount++},enabled=filtered?.size==80,modifier=Modifier.testTag("history-next")) {Text("Next")}
        }
        LazyColumn(Modifier.weight(1f).testTag("activity-history"),state=list,contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(rows==null) item { Text("Loading recorded activity…",color=DeckColors.muted) }
            else if(rows.isEmpty()) item { FDCard { Text("No recorded activity matches these filters.");Text("Past GPT session generation and system changes are unavailable unless durably logged. Retained card commands are receipts, not proof of generation or a state change.",color=DeckColors.muted,fontSize=11.sp) } }
            else {
                rows.forEachIndexed { i,r ->
                    val date=Instant.ofEpochMilli(r.at).atZone(r.zone).toLocalDate()
                    val previousDate=rows.getOrNull(i-1)?.let { Instant.ofEpochMilli(it.at).atZone(it.zone).toLocalDate() }
                    if(date!=previousDate) item(key="date:${r.id}") { Row(Modifier.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f));Text("${when(date) { today -> "TODAY, ";today.minusDays(1) -> "YESTERDAY, ";else -> "" }}${date.format(DateTimeFormatter.ofPattern("d MMM yyyy")).uppercase()}",Modifier.padding(horizontal=10.dp),fontSize=10.sp,color=DeckColors.muted);HorizontalDivider(Modifier.weight(1f))
                    } }
                    item(key=r.id) { HistoryActivityCard(r) {
                        val canonical=reports.find { it.key==r.reportKey }
                        if(canonical!=null) openReport(canonical) else detail=r
                    } }
                }
            }
        }
    }
    if(picking) {
        val picker=rememberDateRangePickerState(initialSelectedStartDateMillis=fromText?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },initialSelectedEndDateMillis=toText?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() })
        DatePickerDialog({picking=false},confirmButton={TextButton({
            fromText=picker.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
            toText=picker.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
            picking=false
        }) { Text("Apply") }},dismissButton={TextButton({picking=false}) { Text("Cancel") }}) { DateRangePicker(picker,Modifier.heightIn(max=540.dp)) }
    }
    detail?.let { d -> DeckAlertDialog(onDismissRequest={detail=null},title={Text(d.title)},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState())) { Text(d.description);Text(d.metrics);d.payload?.let { Text(it,fontSize=11.sp) } }
    },confirmButton={TextButton({detail=null}) { Text("Close") }}) }
}
@Composable private fun HistoryActivityCard(r: HistoryActivity,open: ()->Unit) {
    val color=if(DeckColors.palette.id=="monochrome") DeckColors.accent else when(r.category) {
        ActivityCategory.CARDS -> Color(0xFFB86CE8)
        ActivityCategory.GPT,ActivityCategory.INDEX -> OmssColors.analytical
        ActivityCategory.STATUS,ActivityCategory.BLOCKS -> DeckColors.muted
        else -> DeckColors.accent
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(40.dp).fillMaxHeight(),horizontalAlignment=Alignment.End) { Text(compactClock(r.at,r.zone),fontSize=10.sp,color=DeckColors.muted);Box(Modifier.width(2.dp).weight(1f).background(color.copy(alpha=.35f))) }
        FDCard(Modifier.weight(1f),onClick=open,padding=10.dp) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ActivityGlyph(r.category.icon,Modifier.size(24.dp),color)
                Column(Modifier.weight(1f)) { Text(r.title,fontSize=14.sp,fontWeight=FontWeight.SemiBold);Text(r.description,fontSize=11.sp,color=DeckColors.muted) }
                ActivityGlyph("chevron-right",Modifier.size(16.dp),DeckColors.muted)
            }
            if(r.status.isNotBlank()) Text(r.status,color=if(r.category==ActivityCategory.SESSIONS) statusColor(if(r.status=="Completed") "complete" else "stopped") else color,fontSize=11.sp)
            if(r.metrics.isNotBlank()) { HorizontalDivider(color=DeckColors.track);Text(r.metrics,fontSize=11.sp,color=DeckColors.muted) }
            if(r.category==ActivityCategory.SESSIONS) Row { Text("Start delta ",fontSize=11.sp,color=DeckColors.muted);PunctualityText(r.punctuality?.toDouble()) }
            Text(r.zone.id,fontSize=9.sp,color=DeckColors.muted)
        }
    }
}
