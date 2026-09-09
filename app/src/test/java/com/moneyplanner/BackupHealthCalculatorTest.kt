package com.moneyplanner

import com.moneyplanner.domain.calc.BackupHealthCalculator
import com.moneyplanner.domain.calc.BackupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How loudly to say that the records exist in only one place.
 *
 * The judgement being tested is when to stay quiet. Warning somebody with four expenses
 * that their data is at risk teaches them to ignore the warning by the time it matters,
 * so the threshold is about how much there is to lose as well as how long it has been.
 */
class BackupHealthCalculatorTest {

    private val today = date("2026-09-09")

    private fun assess(
        lastBackup: String? = null,
        configured: Boolean = false,
        records: Int = 100
    ) = BackupHealthCalculator.assess(
        lastBackupEpochDay = lastBackup?.let { date(it).toEpochDay() },
        autoBackupConfigured = configured,
        recordCount = records,
        today = today
    )

    @Test
    fun `a nearly empty install is not nagged about backups`() {
        val health = assess(records = 3)

        assertEquals(BackupState.NOTHING_TO_LOSE, health.state)
        assertFalse(health.needsAttention)
        assertNull(health.detail())
    }

    @Test
    fun `once there is real history, never having backed up is serious`() {
        val health = assess(records = 100)

        assertEquals(BackupState.NEVER, health.state)
        assertTrue(health.isSerious)
        assertEquals("You have never backed up", health.headline())
    }

    @Test
    fun `a backup from today reads as recent`() {
        val health = assess(lastBackup = "2026-09-09")

        assertEquals(BackupState.RECENT, health.state)
        assertEquals(0L, health.daysSinceBackup)
        assertEquals("Backed up today", health.headline())
        assertFalse(health.needsAttention)
    }

    @Test
    fun `yesterday is named rather than counted`() {
        assertEquals("Backed up yesterday", assess(lastBackup = "2026-09-08").headline())
    }

    @Test
    fun `a fortnight without a backup is worth mentioning`() {
        val health = assess(lastBackup = "2026-08-26")

        assertEquals(BackupState.STALE, health.state)
        assertEquals(14L, health.daysSinceBackup)
        assertTrue(health.needsAttention)
        // Worth mentioning, not yet worth alarming about.
        assertFalse(health.isSerious)
    }

    @Test
    fun `a month without a backup is said plainly`() {
        val health = assess(lastBackup = "2026-08-10")

        assertEquals(BackupState.OVERDUE, health.state)
        assertEquals(30L, health.daysSinceBackup)
        assertTrue(health.isSerious)
    }

    @Test
    fun `the day before a threshold has not crossed it`() {
        assertEquals(BackupState.RECENT, assess(lastBackup = "2026-08-27").state)
        assertEquals(BackupState.STALE, assess(lastBackup = "2026-08-11").state)
    }

    @Test
    fun `a healthy automatic backup needs no advice`() {
        val health = assess(lastBackup = "2026-09-08", configured = true)

        assertNull(health.detail())
    }

    @Test
    fun `a recent manual backup is told how to make it automatic`() {
        val health = assess(lastBackup = "2026-09-08", configured = false)

        assertNotNull(health.detail())
        assertTrue(health.detail()!!.contains("folder"))
    }

    @Test
    fun `a configured backup that has stopped running says so, not to set one up`() {
        // The advice has to change: telling someone to choose a folder when they already
        // have would send them to fix something that is not broken.
        val health = assess(lastBackup = "2026-08-01", configured = true)

        assertTrue(health.detail()!!.contains("has not run recently"))
    }

    @Test
    fun `a clock that moved backwards does not report a negative age`() {
        val health = BackupHealthCalculator.assess(
            lastBackupEpochDay = date("2026-09-20").toEpochDay(),
            autoBackupConfigured = true,
            recordCount = 100,
            today = today
        )

        assertEquals(0L, health.daysSinceBackup)
        assertEquals(BackupState.RECENT, health.state)
    }
}
