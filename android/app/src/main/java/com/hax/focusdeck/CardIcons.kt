package com.hax.focusdeck

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import java.security.MessageDigest

internal fun validateIconBitmap(icon: CardIcon): ImageBitmap {
    CardIconPolicy.validate(icon)
    val bytes = Base64.getDecoder().decode(requireNotNull(icon.dataBase64))
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth in 1..256 && bounds.outHeight in 1..256 && bounds.outMimeType == "image/png") { "Invalid PNG image" }
    return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Cannot decode PNG icon" }.asImageBitmap()
}
private object IconBitmaps {
    // Bound by pixels, not icon count. Decode only on demand and outside the UI thread.
    val cache = object : LruCache<String, ImageBitmap>(2*1024*1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width*value.height*4
    }
    fun key(data: String): String = MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
}
@Composable internal fun CardIconView(def: AchievementDefinition, store: AchievementStore, modifier: Modifier, tint: Color) {
    val assignment = store.cardIcons[def.id]
    val icon = assignment?.icon
    val default = when (def.id) {
        "porn_free" -> "ban"; "daily_objectives" -> "trophy"; "gym" -> "dumbbell"; "cardio" -> "heart"
        "german" -> "book-open"; "guitar" -> "guitar"; "custom_compute" -> "laptop"
        else -> when (def.category) { "focus" -> "timer"; "college" -> "graduation-cap"; "projects" -> "laptop"; "health" -> "activity"; "learning" -> "book-open"; else -> "shield-check" }
    }
    if (icon?.kind == "image") {
        val bitmap by produceState<ImageBitmap?>(null, icon.dataBase64) {
            value = withContext(Dispatchers.Default) {
                runCatching {
                    val key = IconBitmaps.key(requireNotNull(icon.dataBase64))
                    IconBitmaps.cache.get(key) ?: validateIconBitmap(icon).also { IconBitmaps.cache.put(key,it) }
                }.getOrNull()
            }
        }
        bitmap?.let { Image(it, contentDescription = "${def.title} custom icon", modifier = modifier, contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tint)) }
            ?: BundledCardIcon(default, modifier, tint)
    } else BundledCardIcon(if (icon?.kind == "builtin") icon.name ?: default else default, modifier, tint)
}
@Composable internal fun BundledCardIcon(name: String, modifier: Modifier, tint: Color) {
    val resource = when (name) {
        "activity" -> R.drawable.lucide_activity
        "ban" -> R.drawable.lucide_ban
        "book-open" -> R.drawable.lucide_book_open
        "brain" -> R.drawable.lucide_brain
        "chart-no-axes-column" -> R.drawable.lucide_chart_no_axes_column
        "check" -> R.drawable.lucide_check
        "chevron-left" -> R.drawable.lucide_chevron_left
        "chevron-right" -> R.drawable.lucide_chevron_right
        "circuit-board" -> R.drawable.lucide_circuit_board
        "code" -> R.drawable.lucide_code
        "coffee" -> R.drawable.lucide_coffee
        "cpu" -> R.drawable.lucide_cpu
        "dumbbell" -> R.drawable.lucide_dumbbell
        "flag" -> R.drawable.lucide_flag
        "flame" -> R.drawable.lucide_flame
        "folder" -> R.drawable.lucide_folder
        "graduation-cap" -> R.drawable.lucide_graduation_cap
        "guitar" -> R.drawable.lucide_guitar
        "heart" -> R.drawable.lucide_heart
        "history" -> R.drawable.lucide_history
        "house" -> R.drawable.lucide_house
        "image" -> R.drawable.lucide_image
        "laptop" -> R.drawable.lucide_laptop
        "leaf" -> R.drawable.lucide_leaf
        "mountain" -> R.drawable.lucide_mountain
        "music" -> R.drawable.lucide_music
        "palette" -> R.drawable.lucide_palette
        "rocket" -> R.drawable.lucide_rocket
        "settings" -> R.drawable.lucide_settings
        "shield-check" -> R.drawable.lucide_shield_check
        "sparkles" -> R.drawable.lucide_sparkles
        "star" -> R.drawable.lucide_star
        "target" -> R.drawable.lucide_target
        "timer" -> R.drawable.lucide_timer
        "trophy" -> R.drawable.lucide_trophy
        "wrench" -> R.drawable.lucide_wrench
        "zap" -> R.drawable.lucide_zap
        else -> R.drawable.lucide_shield_check
    }
    Icon(painterResource(resource), contentDescription = null, modifier = modifier, tint = tint)
}
