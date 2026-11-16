package com.moneyplanner

import com.moneyplanner.data.sms.ImportedSmsIds
import com.moneyplanner.data.sms.SmsScanPeriod
import com.moneyplanner.data.sms.SmsScanWindow
import com.moneyplanner.data.sms.SmsSenderFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The rules that decide what an inbox scan reads: whose messages, from how far back, and
 * which ones it has already imported.
 *
 * The sender rule carries the privacy promise, so it is tested from both sides. Every bank
 * header shape has to pass, and every shape a person's number can take has to fail,
 * because a conversation that slips through is read for no reason at all.
 */
class SmsInboxRulesTest {

    private fun bank(sender: String?) = SmsSenderFilter.isLikelyBankSender(sender)

    // ---- Senders ----------------------------------------------------------------------

    @Test
    fun `dlt headers from the banks are read`() {
        listOf("VM-HDFCBK", "AD-SBIUPI-S", "JD-ICICIB", "JD-ICICIT-T", "VK-AxisBk", "BP-KOTAKB",
            "VM-PAYTMB", "AX-SBICRD-S", "TX-IDFCFB-T")
            .forEach { assertTrue(it, bank(it)) }
    }

    @Test
    fun `a bare alphanumeric sender id is read`() {
        listOf("HDFCBK", "ICICIB", "SBIINB", "AxisBk", "KOTAKB")
            .forEach { assertTrue(it, bank(it)) }
    }

    @Test
    fun `short numeric codes are read`() {
        listOf("57575", "567676", "1909", "  56767  ")
            .forEach { assertTrue(it, bank(it)) }
    }

    @Test
    fun `phone numbers are never read`() {
        listOf(
            "+919876543210", "9876543210", "09876543210", "919876543210", "+91 98765 43210",
            "98765-43210", "+14155550123", "+12345"
        ).forEach { assertFalse(it, bank(it)) }
    }

    @Test
    fun `a promotional header is not a transaction sender`() {
        // The -P suffix marks marketing; alerts go out as service or transactional.
        assertFalse(bank("AD-HDFCBK-P"))
        assertTrue(bank("AD-HDFCBK-T"))
    }

    @Test
    fun `blank, odd and email senders are not read`() {
        listOf(null, "", "   ", "alerts@hdfcbank.net", "HDFC Bank", "VM_HDFCBK", "AB-CD",
            "VERYLONGSENDERID")
            .forEach { assertFalse("$it", bank(it)) }
    }

    // ---- Scan window ------------------------------------------------------------------

    private val now: Instant = Instant.parse("2026-11-16T10:00:00Z")

    @Test
    fun `the first scan since last scan reads a month`() {
        val start = SmsScanWindow.startFor(SmsScanPeriod.SINCE_LAST_SCAN, lastScanAt = null, now = now)
        assertEquals(now.minus(Duration.ofDays(30)), start)
    }

    @Test
    fun `since last scan starts a little before the last scan`() {
        val last = Instant.parse("2026-11-14T08:30:00Z")
        val start = SmsScanWindow.startFor(SmsScanPeriod.SINCE_LAST_SCAN, last, now)
        // The overlap catches a message that arrived as the last scan ran.
        assertEquals(last.minus(SmsScanWindow.OVERLAP), start)
    }

    @Test
    fun `since last scan never reaches past ninety days`() {
        val last = Instant.parse("2026-01-01T00:00:00Z")
        val start = SmsScanWindow.startFor(SmsScanPeriod.SINCE_LAST_SCAN, last, now)
        assertEquals(now.minus(Duration.ofDays(90)), start)
    }

    @Test
    fun `a last scan in the future is treated as no last scan`() {
        // The clock has been moved back; trusting the stored time would read nothing.
        val start = SmsScanWindow.startFor(
            SmsScanPeriod.SINCE_LAST_SCAN,
            lastScanAt = now.plus(Duration.ofDays(2)),
            now = now
        )
        assertEquals(now.minus(Duration.ofDays(30)), start)
    }

    @Test
    fun `fixed periods ignore the last scan`() {
        val last = Instant.parse("2026-11-15T00:00:00Z")
        assertEquals(now.minus(Duration.ofDays(7)), SmsScanWindow.startFor(SmsScanPeriod.LAST_7_DAYS, last, now))
        assertEquals(now.minus(Duration.ofDays(30)), SmsScanWindow.startFor(SmsScanPeriod.LAST_30_DAYS, last, now))
        assertEquals(now.minus(Duration.ofDays(90)), SmsScanWindow.startFor(SmsScanPeriod.LAST_90_DAYS, last, now))
    }

    // ---- Imported ids -----------------------------------------------------------------

    @Test
    fun `imported ids are merged with what is stored`() {
        val merged = ImportedSmsIds.merge(setOf("10", "11"), listOf(12L, 11L))
        assertEquals(setOf(10L, 11L, 12L), ImportedSmsIds.parse(merged))
    }

    @Test
    fun `only the most recent ids are kept`() {
        val stored = (1L..2_000L).map { it.toString() }.toSet()
        val merged = ImportedSmsIds.merge(stored, listOf(2_001L, 2_002L))
        val kept = ImportedSmsIds.parse(merged)

        assertEquals(ImportedSmsIds.KEEP, kept.size)
        // The inbox numbers messages as they arrive, so the oldest ids are the ones to go.
        assertTrue(2_002L in kept)
        assertTrue(2_001L in kept)
        assertFalse(1L in kept)
        assertFalse(2L in kept)
    }

    @Test
    fun `pasted and unreadable ids are never stored`() {
        val merged = ImportedSmsIds.merge(setOf("abc", "-4", "7"), listOf(-1L, 0L, 9L))
        assertEquals(setOf(7L, 9L), ImportedSmsIds.parse(merged))
        assertEquals(setOf("7", "9"), merged)
    }
}
