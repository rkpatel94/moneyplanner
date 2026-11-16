package com.moneyplanner

import com.moneyplanner.data.backup.finiteOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The doubles a backup writes.
 *
 * Android's JSONObject throws from put for NaN and either infinity, so one EMI rate or
 * salary increment that was not a real number used to stop every backup, the weekly one
 * included. The writer now passes each through [finiteOrNull] and writes null for anything
 * that is not a real number, which the reader already restores as no rate and no increment.
 * JSONObject itself is only a stub in these tests, so it is the filter that is checked here.
 */
class BackupNumbersTest {

    @Test
    fun `a real number is written as it is`() {
        assertEquals(10.5, 10.5.finiteOrNull()!!, 0.0)
        assertEquals(0.0, 0.0.finiteOrNull()!!, 0.0)
        assertEquals(-2.25, (-2.25).finiteOrNull()!!, 0.0)
    }

    @Test
    fun `values JSON cannot hold become null rather than failing the backup`() {
        assertNull(Double.NaN.finiteOrNull())
        assertNull(Double.POSITIVE_INFINITY.finiteOrNull())
        assertNull(Double.NEGATIVE_INFINITY.finiteOrNull())
    }

    @Test
    fun `a missing value stays missing`() {
        assertNull((null as Double?).finiteOrNull())
    }
}
