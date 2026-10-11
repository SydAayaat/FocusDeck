package com.hax.focusdeck

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*

/** Shared matte dashboard primitives. Timer and reward structures remain independent. */
internal val DashboardBackground get() = if (DeckColors.palette.id == "monochrome") Color.Black else OmssColors.canvas
internal val DashboardRaised get() = OmssColors.material(OmssColors.nested)
internal val DashboardInset get() = OmssColors.material(OmssColors.hub)
@Composable internal fun FDCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    padding: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    OmssSurface(modifier.then(if(onClick == null) Modifier else Modifier.clickable(onClick=onClick))) {
        CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontSize=14.sp,fontFeatureSettings="tnum")) {
            Column(Modifier.padding(padding), verticalArrangement=Arrangement.spacedBy(8.dp), content=content)
        }
    }
}
@Composable internal fun FDTitle(title: String, trailing: String? = null,trailingAction: (() -> Unit)? = null,badge: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.Bold)
        trailing?.let { Text(it,Modifier.then(if(trailingAction!=null) Modifier.clickable(onClick=trailingAction) else Modifier),color=DeckColors.muted,fontSize=11.sp) }
        badge?.let { OmssStatusPill(it) }
    }
}
@Composable internal fun FDTrack(fraction: Float?, modifier: Modifier = Modifier, color: Color = DeckColors.accent) {
    OmssProgressTrack(fraction,modifier,color)
}
internal fun dashboardDuration(ms: Long?): String = ms?.let {
    val minutes=it.coerceAtLeast(0)/60000
    if(minutes>=60) "${minutes/60}h ${minutes%60}m" else "${minutes}m"
} ?: "N/A"
@Composable internal fun DashboardNavigation(selected: String, home: () -> Unit, achievements: () -> Unit,
    analytics: () -> Unit, focus: (() -> Unit)? = null) {
    OmssSurface(Modifier.fillMaxWidth().testTag("dashboard-navigation")) {
        Row(Modifier.fillMaxWidth().heightIn(min=62.dp),verticalAlignment=Alignment.CenterVertically) {
            val items=listOf(Triple("home","Home","house"),Triple("achievements","Achievements","trophy")) +
                (if(focus!=null) listOf(Triple("focus","Focus","timer")) else emptyList()) + Triple("analytics","Analytics","chart-no-axes-column")
            items.forEach { (key,label,icon) ->
                val tint=if(selected==key) DeckColors.accent else DeckColors.muted
                Column(Modifier.weight(1f).semantics { this.selected=selected==key;role=Role.Tab }.clickable(onClick={when(key){"home"->home();"achievements"->achievements();"focus"->focus?.invoke();else->analytics()}}).padding(vertical=9.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    BundledCardIcon(icon,Modifier.size(22.dp),tint)
                    Text(label,fontSize=11.sp,color=tint,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Box(Modifier.width(40.dp).height(2.dp).background(if(selected==key) tint else Color.Transparent,RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable internal fun FDBrand() {
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.focusdeck_logo),
        contentDescription="FocusDeck",modifier=Modifier.size(32.dp))
    Spacer(Modifier.width(10.dp))
    Text("Focus",fontSize=19.sp,fontWeight=FontWeight.Bold)
    Text("Deck",fontSize=19.sp,fontWeight=FontWeight.Bold,color=DeckColors.accent)
}

@Composable internal fun DashboardHomeHeader(connection: String,options: @Composable ()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        FDBrand();Spacer(Modifier.weight(1f))
        Text(if(connection=="Connected") "● Connected" else "○ $connection",fontSize=11.sp,
            color=if(connection=="Connected") DeckColors.success else DeckColors.muted)
        options()
    }
}
