package com.moneyplanner.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.moneyplanner.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes backups into a folder the user has chosen.
 *
 * This closes the most dangerous gap in an offline-only app: the records exist in exactly
 * one place, and the device's own cloud backup is switched off precisely because this is
 * financial data. Without a copy somewhere else, a lost phone is a lost financial history.
 *
 * The folder is picked once through the system document picker, which grants a persistent
 * write permission for that folder alone. No storage permission is requested and the app
 * cannot see anything else on the device. If the user later deletes the folder or revokes
 * the grant, the run fails cleanly and reports it rather than crashing.
 */
@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val settingsStore: SettingsStore,
    @com.moneyplanner.di.IoDispatcher private val io: CoroutineDispatcher
) {

    companion object {
        private const val MIME_JSON = "application/json"

        /**
         * The type the unfinished file is created with. Created as JSON, a provider is free
         * to add ".json" to a name that does not already end in it, and the temporary name
         * would no longer be recognisable as one; the generic type keeps the name as given.
         */
        private const val MIME_PARTIAL = "application/octet-stream"
    }

    /**
     * One run at a time. The weekly job and "Back up now" can overlap, and two runs on the
     * same day share a temporary name and a final name, so each would be deleting and
     * renaming the other's file part-way through.
     */
    private val runLock = Mutex()

    /**
     * Takes the folder grant so it survives a reboot. Called when the user picks a folder.
     */
    fun persistFolderAccess(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    /**
     * Gives back the app's access to backup folders. Called by a factory reset, which
     * forgets the folder: keeping the grant after that would leave the app able to write
     * into a folder that nothing in it remembers choosing.
     *
     * Every folder grant goes, not only [currentFolderUri]'s, because choosing a different
     * folder takes a new grant without giving up the old one, and folders are the only kind
     * of grant this app takes. A receipt attached to an expense is a single document rather
     * than a folder, and is left alone, so a backup restored on this phone can still open it.
     * A grant that is already gone is not an error: there is simply nothing to give back.
     */
    fun releaseFolderAccess(currentFolderUri: String?) {
        val resolver = context.contentResolver
        val everyMode = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val folders = runCatching {
            resolver.persistedUriPermissions
                .map { it.uri }
                .filter { DocumentsContract.isTreeUri(it) }
        }.getOrDefault(emptyList()).toMutableSet()
        currentFolderUri?.let { runCatching { Uri.parse(it) }.getOrNull() }?.let(folders::add)

        folders.forEach { uri ->
            runCatching { resolver.releasePersistableUriPermission(uri, everyMode) }
        }
    }

    /** A readable name for the chosen folder, for display in settings. */
    fun folderDisplayName(uriString: String?): String? {
        val uri = uriString?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        return runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
    }

    /**
     * Writes a backup into the chosen folder and prunes older ones.
     *
     * Returns a result rather than throwing, because this runs unattended from a
     * scheduled job where a silent exception would mean backups quietly stopping.
     *
     * The outcome is also written to the settings, because the scheduled job has nobody to
     * tell: a failure is kept, with the day and the reason, until a later run succeeds, so
     * the settings screen can say the weekly copy has stopped rather than leaving the user
     * to find out on the day they need it. "Back up now" goes through here too, so a folder
     * that works again clears the failure the moment the user proves it does.
     */
    suspend fun runBackup(today: LocalDate): AutoBackupResult = withContext(io) {
        runLock.withLock {
            val result = writeBackup(today)
            // The file is either in the folder or it is not; a settings write that fails
            // afterwards must not turn a backup that worked into one reported as failed.
            runCatching {
                when (result) {
                    is AutoBackupResult.Success ->
                        settingsStore.recordAutoBackupSuccess(today.toEpochDay())
                    // Nothing was attempted, so there is nothing to report.
                    AutoBackupResult.NoFolderChosen -> Unit
                    AutoBackupResult.FolderUnavailable, AutoBackupResult.WriteFailed ->
                        settingsStore.recordAutoBackupFailure(
                            today.toEpochDay(),
                            result.failureReason.orEmpty()
                        )
                }
            }
            result
        }
    }

    private suspend fun writeBackup(today: LocalDate): AutoBackupResult {
        val settings = settingsStore.settings.first()
        val folderUri = settings.backupFolderUri ?: return AutoBackupResult.NoFolderChosen

        val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) }
            .getOrNull()
            ?: return AutoBackupResult.FolderUnavailable

        if (!tree.exists() || !tree.canWrite()) {
            return AutoBackupResult.FolderUnavailable
        }

        val fileName = BackupFolderRules.fileNameFor(today)
        val written = runCatching { writeVerified(tree, fileName, today) }.getOrNull()
            ?: return AutoBackupResult.WriteFailed

        // Only after a complete copy is in place. A run that failed has proved nothing
        // about which of the existing files are worth keeping, so it leaves them all.
        runCatching { prune(tree, settings.backupKeepCount) }
        return AutoBackupResult.Success(written)
    }

    /**
     * Puts today's backup in the folder without ever leaving the folder worse off.
     *
     * The backup used to replace the same day's file by deleting it and creating the new
     * one empty before a byte of the export was written, so a failed write left a 0-byte
     * file where a good backup had been. Now the export is written under a temporary name
     * and its size checked first. Only once a complete copy is sitting in the folder is the
     * earlier copy from the same day removed and the new one renamed into its place. Any
     * failure along the way deletes the temporary file and leaves everything else as it was.
     *
     * Returns the name the backup ended up with, or null when it could not be written.
     */
    private suspend fun writeVerified(
        tree: DocumentFile,
        fileName: String,
        today: LocalDate
    ): String? {
        val source = backupManager.exportJson(today)
        val expected = source.length()
        if (expected <= 0L) return null

        // Left by a run that died part-way, when the app was killed in the middle of a
        // write. With runs serialised, none can belong to a run still in progress.
        deletePartials(tree)

        val partial = tree.createFile(MIME_PARTIAL, BackupFolderRules.partialNameFor(today))
            ?: return null
        if (!copyInto(partial, source, expected)) {
            partial.delete()
            return null
        }

        // Out of the way first: renaming onto a name that is taken makes the provider pick
        // a different one, "(1)" and so on, rather than replacing it.
        tree.findFile(fileName)?.delete()
        if (partial.renameTo(fileName)) {
            return partial.name ?: fileName
        }

        // Not every provider can rename a document. Without this a folder on one of them
        // would never receive a backup at all, so the verified export is written a second
        // time, straight under its own name, and checked the same way.
        val target = tree.createFile(MIME_JSON, fileName)
        val copied = target != null && copyInto(target, source, expected)
        partial.delete()
        if (!copied) {
            target?.delete()
            return null
        }
        return target?.name ?: fileName
    }

    /**
     * Copies [source] into [document] and confirms that it arrived.
     *
     * Every byte has to have gone through the stream, and the provider has to report a file
     * that is not empty once the stream is closed. An empty file is exactly what a write
     * that failed silently leaves behind, and treating one as a backup is how good backups
     * were being pruned in favour of nothing.
     */
    private fun copyInto(document: DocumentFile, source: File, expected: Long): Boolean {
        val copied = runCatching {
            context.contentResolver.openOutputStream(document.uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            }
        }.getOrNull() ?: return false
        return copied == expected && document.length() > 0L
    }

    private fun deletePartials(tree: DocumentFile) {
        runCatching {
            tree.listFiles()
                .filter { BackupFolderRules.isPartialName(it.name) }
                .forEach { it.delete() }
        }
    }

    /** Keeps the newest complete backups and clears away what earlier failures left. */
    private fun prune(tree: DocumentFile, keepCount: Int) {
        BackupFolderRules.filesToDelete(
            files = tree.listFiles().toList(),
            keepCount = keepCount,
            nameOf = { it.name },
            sizeOf = { it.length() }
        ).forEach { it.delete() }
    }
}

/**
 * The names a backup run writes, and which of the files carrying them to keep.
 *
 * Kept apart from the folder so the rule can be tested without one. Only files this app
 * writes are ever considered: the folder belongs to the user and can hold anything, so a
 * file that does not match one of these exact names is never deleted, whatever it is.
 */
internal object BackupFolderRules {

    private const val PREFIX = "money-planner-backup-"
    private const val EXTENSION = ".json"

    /** Added to the final name while the file is still being written. */
    private const val PARTIAL_SUFFIX = ".tmp"

    /**
     * A complete backup's name, and nothing else. The date is ISO formatted, so these sort
     * by name in the order they were taken.
     */
    private val BACKUP_NAME = Regex("""money-planner-backup-\d{4}-\d{2}-\d{2}\.json""")

    fun fileNameFor(day: LocalDate): String = "$PREFIX$day$EXTENSION"

    fun partialNameFor(day: LocalDate): String = fileNameFor(day) + PARTIAL_SUFFIX

    fun isBackupName(name: String?): Boolean = name != null && BACKUP_NAME.matches(name)

    /**
     * A file left by a run that never finished. Matched loosely, because a provider that
     * found the name taken may have added " (1)" to it.
     */
    fun isPartialName(name: String?): Boolean =
        name != null && name.startsWith(PREFIX) && name.endsWith(PARTIAL_SUFFIX)

    /**
     * Which of [files] to delete after a successful run.
     *
     * Of the complete, non-empty backups, the newest [keepCount] stay and the rest go. The
     * rule used to be the newest few by name alone, so a week of failed runs, each leaving
     * an empty file behind, followed by one that worked, could keep the empty files and
     * delete every backup that held anything. An empty file now counts for nothing: it is
     * deleted along with any unfinished temporary file, and never takes a good backup's
     * place in the count.
     */
    fun <T> filesToDelete(
        files: List<T>,
        keepCount: Int,
        nameOf: (T) -> String?,
        sizeOf: (T) -> Long
    ): List<T> {
        val partials = files.filter { isPartialName(nameOf(it)) }
        val (complete, empty) = files
            .filter { isBackupName(nameOf(it)) }
            .map { it to sizeOf(it) }
            .partition { (_, size) -> size > 0L }

        val surplus = complete
            .map { (file, _) -> file }
            .sortedByDescending { nameOf(it) }
            .drop(keepCount.coerceAtLeast(1))

        return partials + empty.map { (file, _) -> file } + surplus
    }
}

sealed interface AutoBackupResult {
    data class Success(val fileName: String) : AutoBackupResult
    data object NoFolderChosen : AutoBackupResult
    data object FolderUnavailable : AutoBackupResult
    data object WriteFailed : AutoBackupResult

    fun message(): String = when (this) {
        is Success -> "Backup saved as $fileName"
        NoFolderChosen -> "Choose a folder for backups first."
        FolderUnavailable -> "That backup folder is no longer available. Choose it again."
        WriteFailed -> "The backup could not be written to that folder."
    }

    /**
     * Why a run failed, in a few words, kept with the failure so the settings screen can
     * still say why long after the run itself. Null for the outcomes that are not failures.
     */
    val failureReason: String?
        get() = when (this) {
            FolderUnavailable -> "the folder is no longer available"
            WriteFailed -> "the file could not be written to the folder"
            is Success, NoFolderChosen -> null
        }
}
