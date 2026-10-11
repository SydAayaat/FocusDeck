package com.hax.focusdeck

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.SoundPool
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

@Serializable
data class SoundPreferences(
    val timer: String = "off", val stopwatch: String = "off", val ui: String = "off",
    val completion: String = "chime", val volume: Float = .35f,
    val customFiles: Map<String, String> = emptyMap()
) {
    fun choice(slot: String): String = when(slot) { "timer" -> timer; "stopwatch" -> stopwatch; "ui" -> ui; else -> completion }
    fun withChoice(slot: String, value: String): SoundPreferences = when(slot) {
        "timer" -> copy(timer=value); "stopwatch" -> copy(stopwatch=value); "ui" -> copy(ui=value); else -> copy(completion=value)
    }
    fun validate() {
        require(volume.isFinite() && volume in 0f..1f)
        SoundPolicy.slots.forEach { slot ->
            require(choice(slot) in SoundPolicy.options(slot).map { it.first })
            if(choice(slot)=="custom") require(customFiles[slot]?.matches(Regex("[a-f0-9-]{36}\\.audio")) == true) { "Choose an audio file first" }
        }
    }
}
internal object SoundPolicy {
    val slots = listOf("timer", "stopwatch", "ui", "completion")
    fun options(slot: String) = if(slot in listOf("timer","stopwatch")) listOf("off" to "Off", "soft_tick" to "Soft clock", "mechanical_tick" to "Mechanical clock", "wood_tick" to "Wood clock", "digital_tick" to "Digital tick", "custom" to "Custom")
        else listOf("off" to "Off", "click" to "Soft click", "chime" to "Chime", "bell" to "Bell", "custom" to "Custom")
    fun tickSlot(state: DeckState): String? = if(state.phase != "active") null else state.session?.blocks?.getOrNull(state.index)?.let { if(it.stopwatch) "stopwatch" else "timer" }
}

/** Short predecoded samples. No per-frame audio, timer mutations or disk writes. */
internal class DeckSounds(private val context: Context, private val repo: DeckRepository) {
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val samples = mutableMapOf<String,Int>()
    private val ready = mutableSetOf<Int>()
    private val pending = mutableMapOf<Int,Float>()
    private val loading = mutableMapOf<Int,CompletableDeferred<Unit>>()
    private var clockStream = 0
    private val folder get() = File(context.filesDir,"sounds").apply { mkdirs() }
    init { pool.setOnLoadCompleteListener { _, id, status -> synchronized(ready) { if(status==0) { ready.add(id); loading.remove(id)?.complete(Unit); pending.remove(id)?.let { volume -> pool.play(id,volume,volume,1,0,1f) } } else { pending.remove(id); loading.remove(id)?.completeExceptionally(IllegalArgumentException("Audio format could not be decoded")) } } }
        mapOf("soft_tick" to R.raw.soft_tick,"mechanical_tick" to R.raw.mechanical_tick,"wood_tick" to R.raw.wood_tick,
            "digital_tick" to R.raw.digital_tick,"click" to R.raw.ui_click,"chime" to R.raw.chime,"bell" to R.raw.bell).forEach { (name,res) -> samples[name] = pool.load(context,res,1) }
    }
    fun play(slot: String, preferences: SoundPreferences = repo.stored.value.audio, force: Boolean = false) {
        if(slot=="completion" && !force && !repo.stored.value.sound) return
        val choice = preferences.choice(slot)
        if(choice=="off" || preferences.volume==0f) return
        val key = if(choice=="custom") preferences.customFiles[slot] ?: return else choice
        var id = samples[key]
        if(id==null && choice=="custom" && key.matches(Regex("[a-f0-9-]{36}\\.audio"))) {
            val file = File(folder,key)
            if(!file.isFile) return
            id = runCatching { pool.load(file.path,1) }.getOrNull() ?: return
            samples[key]=id
        }
        if(id!=null) synchronized(ready) {
            if(id in ready) {
                val stream=pool.play(id,preferences.volume,preferences.volume,1,0,1f)
                if(slot in listOf("timer","stopwatch") && !force) clockStream=stream
            }
            else if(force) pending[id]=preferences.volume
        }
    }
    fun runClock(scope: CoroutineScope) {
        scope.launch {
            repo.stored.map { SoundPolicy.tickSlot(it.state) to it.audio }.distinctUntilChanged().collect { (slot, prefs) ->
                stopClock()
                if(slot!=null && prefs.choice(slot)!="off" && prefs.volume>0f) clock=scope.launch {
                    while(isActive) { delay(1000); if(SoundPolicy.tickSlot(repo.stored.value.state)==slot) play(slot) }
                }
            }
        }
    }
    private var clock: Job? = null
    fun stopClock() { clock?.cancel(); clock=null; if(clockStream!=0) pool.stop(clockStream); clockStream=0 }
    suspend fun pruneImports() {
        val retained=repo.stored.value.audio.customFiles.values.toSet()
        val removed=withContext(Dispatchers.IO) {
            folder.listFiles().orEmpty().filter { it.isFile && it.name !in retained && it.name.matches(Regex("[a-f0-9-]{36}\\.audio")) }.map { file -> file.delete(); file.name }
        }
        withContext(Dispatchers.Main) { removed.forEach { key -> samples.remove(key)?.let { id -> pool.unload(id); synchronized(ready) { ready.remove(id) } } } }
    }
    suspend fun import(uri: Uri, slot: String): String = withContext(Dispatchers.IO) {
        val destination=File(folder,"${UUID.randomUUID()}.audio")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open audio file" }
                destination.outputStream().use { out ->
                    val bytes=ByteArray(8192); var total=0
                    while(true) { val count=input.read(bytes); if(count<0) break; total+=count; require(total<=2*1024*1024) { "Choose an audio clip smaller than 2 MiB" }; out.write(bytes,0,count) }
                }
            }
            val extractor=MediaExtractor()
            try {
                extractor.setDataSource(destination.path)
                val format=(0 until extractor.trackCount).map(extractor::getTrackFormat).firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true } ?: error("File has no supported audio track")
                val duration=format.getLong(MediaFormat.KEY_DURATION)
                val limit=if(slot in listOf("timer","stopwatch")) 1_000_000L else 2_000_000L
                require(duration in 1..limit) { "Choose a clip up to ${limit/1_000_000} second(s); clock clips repeat once per second" }
                require(format.getInteger(MediaFormat.KEY_SAMPLE_RATE)<=48000 && format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)<=2) { "Audio must be mono/stereo and at most 48 kHz" }
            } finally { extractor.release() }
            withContext(Dispatchers.Main) {
                val decoded=CompletableDeferred<Unit>()
                val sample=pool.load(destination.path,1)
                require(sample>0) { "Audio format could not be loaded" }
                loading[sample]=decoded
                try {
                    withTimeout(5000) { decoded.await() }
                    samples[destination.name]=sample
                    // Keep only the latest imported/selected samples in the native cache.
                    val selected=repo.stored.value.audio.customFiles.values.toSet()+destination.name
                    samples.keys.filter { it.endsWith(".audio") && it !in selected }.forEach { key -> samples.remove(key)?.let { pool.unload(it); synchronized(ready) { ready.remove(it) } } }
                    destination.name
                } catch(e:Exception) { loading.remove(sample);pool.unload(sample);synchronized(ready) { ready.remove(sample) };throw e }
            }
        } catch(e: Exception) { destination.delete(); throw e }
    }
}
