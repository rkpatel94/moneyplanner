package com.moneyplanner

import com.moneyplanner.data.backup.BackupFolderRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which files in the backup folder a successful run keeps.
 *
 * The rule used to be "the newest few by name", and a failed run used to leave an empty
 * file behind under a perfectly good name. So a run of failed days followed by one that
 * worked kept the empty files and deleted every backup that held anything. The folder is
 * also the user's own, so anything the app did not write must survive whatever happens.
 */
class BackupFolderRulesTest {

    private data class FolderFile(val name: String?, val size: Long)

    private fun backup(day: String, size: Long = 2_048) =
        FolderFile(BackupFolderRules.fileNameFor(date(day)), size)

    private fun deleted(files: List<FolderFile>, keep: Int): Set<String?> =
        BackupFolderRules.filesToDelete(files, keep, nameOf = { it.name }, sizeOf = { it.size })
            .map { it.name }
            .toSet()

    @Test
    fun `names carry the ISO date so they sort in the order they were taken`() {
        assertEquals(
            "money-planner-backup-2026-11-16.json",
            BackupFolderRules.fileNameFor(date("2026-11-16"))
        )
        assertEquals(
            "money-planner-backup-2026-11-16.json.tmp",
            BackupFolderRules.partialNameFor(date("2026-11-16"))
        )
        assertTrue(BackupFolderRules.isBackupName("money-planner-backup-2026-01-05.json"))
        assertFalse(BackupFolderRules.isBackupName("money-planner-backup-2026-01-05.json.tmp"))
        assertFalse(BackupFolderRules.isBackupName("money-planner-backup-2026-01-05 (1).json"))
        assertFalse(BackupFolderRules.isBackupName("money-planner-backup-latest.json"))
        assertFalse(BackupFolderRules.isBackupName(null))
    }

    @Test
    fun `the newest complete backups are kept and the oldest go`() {
        val files = listOf(
            backup("2026-10-05"),
            backup("2026-10-12"),
            backup("2026-10-19"),
            backup("2026-10-26")
        )

        assertEquals(
            setOf(BackupFolderRules.fileNameFor(date("2026-10-05"))),
            deleted(files, keep = 3)
        )
    }

    @Test
    fun `empty files left by failed runs never push out a good backup`() {
        // Five good weeks, then four days whose runs each left a 0-byte file, then one
        // that worked. Keeping the newest five by name would have kept the four empty
        // files and today's, and deleted every one of the good weeks.
        val good = listOf("2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26", "2026-11-02")
            .map { backup(it) }
        val empty = listOf("2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12")
            .map { backup(it, size = 0) }
        val today = backup("2026-11-16")

        val gone = deleted(good + empty + today, keep = 5)

        assertEquals(empty.map { it.name }.toSet() + good.first().name, gone)
        assertFalse(today.name in gone)
        assertTrue(good.drop(1).none { it.name in gone })
    }

    @Test
    fun `unfinished temporary files are cleared away`() {
        val files = listOf(
            backup("2026-11-16"),
            FolderFile("money-planner-backup-2026-11-12.json.tmp", 512),
            FolderFile("money-planner-backup-2026-11-13.json (1).tmp", 0)
        )

        assertEquals(
            setOf("money-planner-backup-2026-11-12.json.tmp", "money-planner-backup-2026-11-13.json (1).tmp"),
            deleted(files, keep = 5)
        )
    }

    @Test
    fun `nothing the app did not write is ever touched`() {
        val files = listOf(
            backup("2026-11-16"),
            FolderFile("holiday-photo.jpg", 0),
            FolderFile("notes.txt", 10),
            FolderFile("money-planner-backup-2026-11-12 (1).json", 0),
            FolderFile("Money-Planner-Backup-2026-11-01.json", 0),
            FolderFile(null, 0)
        )

        assertTrue(deleted(files, keep = 1).isEmpty())
    }

    @Test
    fun `at least one backup is always kept`() {
        val files = listOf(backup("2026-11-09"), backup("2026-11-16"))

        assertEquals(
            setOf(BackupFolderRules.fileNameFor(date("2026-11-09"))),
            deleted(files, keep = 0)
        )
    }
}
