package com.hax.focusdeck

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.MessageDigest

/** Final reports are immutable; CSV is regenerated only when history changes. */
class ReportArchive(private val context: Context) {
    private val root = File(context.filesDir, "reports")
    private val exports = File(context.filesDir, "exports")
    val savedKeys: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun name(report: Report): String = MessageDigest.getInstance("SHA-256")
        .digest(report.key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(24) + ".json"

    fun load(): Pair<List<Report>, List<String>> {
        root.mkdirs()
        val reports = mutableListOf<Report>()
        val errors = mutableListOf<String>()
        root.listFiles()?.filter { it.name.endsWith(".json") }?.forEach { file ->
            try {
                require(file.length() <= 1_048_576) { "Report is too large" }
                val report = WireJson.decodeFromString<Report>(AtomicFile(file).readFully().toString(Charsets.UTF_8))
                report.validateHistory()
                require(file.name == name(report)) { "Archive identity mismatch" }
                reports += report
                savedKeys += report.key
            } catch (e: Exception) { errors += "${file.name}: ${e.message}" }
        }
        return reports to errors
    }

    private fun atomic(file: File, text: String) {
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory) { "Cannot create history directory" }
        val target = AtomicFile(file)
        val output = target.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            target.finishWrite(output)
        } catch (e: Exception) { target.failWrite(output); throw e }
    }

    fun save(report: Report) {
        report.validateHistory()
        val file = File(root, name(report))
        if (file.exists()) {
            require(file.length() <= 1_048_576) { "Existing report is too large; restore it from the PC" }
            val previous = WireJson.decodeFromString<Report>(AtomicFile(file).readFully().toString(Charsets.UTF_8))
            require(previous == report) { "A different archive record already exists for ${report.key}" }
        } else atomic(file, WireJson.encodeToString(report))
        savedKeys += report.key
    }

    fun csv(reports: List<Report>) {
        atomic(File(exports, "all-blocks.csv"), "\uFEFF" + HistoryCsv.encode(reports))
        atomic(File(exports, "all-sessions.csv"), "\uFEFF" + HistoryCsv.encode(reports, sessions = true))
        atomic(File(exports, "all-events.csv"), "\uFEFF" + TimelineCsv.encode(reports))
        atomic(File(exports, "all-intervals.csv"), "\uFEFF" + TimelineCsv.encode(reports, intervals = true))
    }

    /** Only FocusDeck-managed hash-named JSON and master CSV files are touched. */
    fun mirror(treeUri: String, reports: List<Report>) {
        val directory = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: error("History folder is unavailable; select it again")
        require(directory.canWrite()) { "History folder is not writable; select it again" }
        val existing = directory.listFiles().associateBy { it.name }
        reports.forEach { report ->
            val filename = name(report)
            // If a prior mirror was interrupted, retry rather than assuming a partial file is valid.
            val file = existing[filename]
            val expected = WireJson.encodeToString(report)
            val matches = file?.let {
                runCatching { context.contentResolver.openInputStream(it.uri)?.use { input ->
                    val raw = input.readNBytes(1_048_577)
                    raw.size <= 1_048_576 && WireJson.decodeFromString<Report>(raw.toString(Charsets.UTF_8)) == report
                } == true }.getOrDefault(false)
            } ?: false
            if (!matches) writeDocument(directory, file, filename, "application/json", expected)
        }
        writeDocument(directory, existing["all-blocks.csv"], "all-blocks.csv", "text/csv", "\uFEFF" + HistoryCsv.encode(reports))
        writeDocument(directory, existing["all-sessions.csv"], "all-sessions.csv", "text/csv", "\uFEFF" + HistoryCsv.encode(reports, sessions = true))
        writeDocument(directory, existing["all-events.csv"], "all-events.csv", "text/csv", "\uFEFF" + TimelineCsv.encode(reports))
        writeDocument(directory, existing["all-intervals.csv"], "all-intervals.csv", "text/csv", "\uFEFF" + TimelineCsv.encode(reports, intervals = true))
    }

    fun prune(deletions: HistoryDeletion, treeUri: String?, privateFiles: Boolean = true) {
        deletions.validate()
        if (deletions.beforeMs == 0L && deletions.reportKeys.isEmpty()) return
        fun hidden(filename: String, stamp: Long, read: () -> Report?): Boolean {
            if (!filename.matches(Regex("[a-f0-9]{24}\\.json"))) return false
            if (deletions.reportKeys.any { key -> filename == MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(24) + ".json" }) return true
            val report = runCatching { read() }.getOrNull()
            return report?.let { deletions.hides(it) } ?: (deletions.beforeMs > 0 && stamp <= deletions.beforeMs)
        }
        if (privateFiles) {
            val removed = mutableSetOf<String>()
            root.listFiles()?.filter { it.name.endsWith(".json") }?.forEach { file ->
                if (hidden(file.name, file.lastModified()) { if (file.length() > 1048576) null else WireJson.decodeFromString<Report>(AtomicFile(file).readFully().toString(Charsets.UTF_8)) }) {
                    AtomicFile(file).delete(); require(!file.exists()) { "Cannot delete private history file" }; removed += file.name
                }
            }
            savedKeys.removeAll { key -> key in deletions.reportKeys || MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(24) + ".json" in removed }
        }
        if (treeUri != null) {
            val directory = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: error("History folder unavailable for deletion")
            require(directory.canWrite()) { "Restore history folder access to finish deleting its copies" }
            directory.listFiles().forEach { file ->
                val filename = file.name ?: return@forEach
                if (hidden(filename, file.lastModified()) { context.contentResolver.openInputStream(file.uri)?.use { input ->
                    val bytes = input.readNBytes(1048577); if (bytes.size > 1048576) null else WireJson.decodeFromString<Report>(bytes.toString(Charsets.UTF_8))
                } }) require(file.delete()) { "Cannot delete $filename from history folder" }
            }
        }
    }

    private fun writeDocument(directory: DocumentFile, existing: DocumentFile?, name: String, mime: String, text: String) {
        val file = existing ?: directory.createFile(mime, name) ?: error("Cannot create $name in history folder")
        val output = context.contentResolver.openOutputStream(file.uri, "wt") ?: error("Cannot open $name for writing")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}
