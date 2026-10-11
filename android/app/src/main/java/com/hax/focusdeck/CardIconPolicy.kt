package com.hax.focusdeck

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.Base64
import java.nio.ByteBuffer

@Serializable data class CardIcon(val kind: String, val name: String? = null,
    @SerialName("data_base64") val dataBase64: String? = null)
@Serializable data class CardIconAssignment(val icon: CardIcon, val atMs: Long, val commandId: String)
internal object CardIconPolicy {
    const val MAX_BYTES = 65536
    const val MAX_DIMENSION = 256
    val names = setOf("book-open", "laptop", "ban", "trophy", "dumbbell", "heart", "guitar", "timer", "flame", "target",
        "graduation-cap", "shield-check", "code", "cpu", "circuit-board", "rocket", "brain", "activity", "star", "check",
        "settings", "house", "history", "chart-no-axes-column", "coffee", "music", "leaf", "mountain", "zap", "palette",
        "image", "folder", "wrench", "flag", "sparkles", "chevron-right", "chevron-left")
    fun validate(icon: CardIcon) {
        when (icon.kind) {
            "default" -> require(icon.name == null && icon.dataBase64 == null) { "Default icon has no extra fields" }
            "builtin" -> require(icon.name in names && icon.dataBase64 == null) { "Unknown bundled icon" }
            "image" -> {
                require(icon.name == null) { "Image icon cannot have a bundled name" }
                val encoded = requireNotNull(icon.dataBase64) { "PNG image required" }
                require(encoded.length <= 87384) { "Icon exceeds 64 KiB" }
                val bytes = Base64.getDecoder().decode(encoded)
                require(bytes.size in 33..MAX_BYTES && bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) { "Icon must be PNG" }
                require(bytes.copyOfRange(12,16).toString(Charsets.US_ASCII) == "IHDR") { "Invalid PNG header" }
                val width = ByteBuffer.wrap(bytes,16,4).int
                val height = ByteBuffer.wrap(bytes,20,4).int
                require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) { "PNG must be at most 256 × 256" }
            }
            else -> error("Unknown icon kind")
        }
    }
}
