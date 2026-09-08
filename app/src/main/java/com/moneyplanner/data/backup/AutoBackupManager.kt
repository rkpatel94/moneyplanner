package com.moneyplanner.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.moneyplanner.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
        private const val PREFIX = "money-planner-backup-"
    }

    /**
     * Takes the folder grant so it survives a reboot. Called when the user picks a folder.
     */
    fun persistFolderAccess(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
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
     */
    suspend fun runBackup(today: LocalDate): AutoBackupResult = withContext(io) {
        val settings = settingsStore.settings.first()
        val folderUri = settings.backupFolderUri
            ?: return@withContext AutoBackupResult.NoFolderChosen

        val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) }
            .getOrNull()
            ?: return@withContext AutoBackupResult.FolderUnavailable

        if (!tree.exists() || !tree.canWrite()) {
            return@withContext AutoBackupResult.FolderUnavailable
        }

        val fileName = "$PREFIX${today}.json"

        return@withContext runCatching {
            // Replace the same day's backup rather than accumulating duplicates.
            tree.findFile(fileName)?.delete()

            val target = tree.createFile(MIME_JSON, fileName)
                ?: return@runCatching AutoBackupResult.WriteFailed

            val source = backupManager.exportJson(today)
            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return@runCatching AutoBackupResult.WriteFailed

            pruneOldBackups(tree, settings.backupKeepCount)
            settingsStore.recordBackupRun(today.toEpochDay())
            AutoBackupResult.Success(target.name ?: fileName)
        }.getOrElse { AutoBackupResult.WriteFailed }
    }

    /**
     * Keeps the newest [keepCount] backups. Sorting is by file name, which works because
     * the date stamp is ISO formatted and therefore sorts chronologically.
     */
    private fun pruneOldBackups(tree: DocumentFile, keepCount: Int) {
        runCatching {
            tree.listFiles()
                .filter { it.name?.startsWith(PREFIX) == true }
                .sortedByDescending { it.name }
                .drop(keepCount.coerceAtLeast(1))
                .forEach { it.delete() }
        }
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
}
