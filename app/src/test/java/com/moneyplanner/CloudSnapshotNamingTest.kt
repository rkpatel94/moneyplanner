package com.moneyplanner

import com.moneyplanner.data.cloud.CloudSnapshotNaming
import com.moneyplanner.ui.screens.cloud.CloudSignInForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of cloud backup that are pure: what a snapshot is called, and whether a
 * sign-in form is complete enough to send.
 *
 * Everything else in the feature is a network call, but these two decide whether one
 * device can delete another's backups and whether a request is worth making at all.
 */
class CloudSnapshotNamingTest {

    @Test
    fun `a name survives the round trip`() {
        val name = CloudSnapshotNaming.fileName("Pixel 7", 1_757_500_000_000)

        assertEquals("Pixel 7-1757500000000.json", name)
        assertEquals("Pixel 7", CloudSnapshotNaming.deviceOf(name))
        assertEquals(1_757_500_000_000, CloudSnapshotNaming.timestampOf(name))
    }

    /**
     * The reason the separator is stripped from device names.
     *
     * A phone called "Redmi Note" must not match a "Redmi Note-10"'s backups. With a
     * prefix test it would, and pruning would silently delete the other device's history.
     */
    @Test
    fun `one device cannot claim another whose name it starts with`() {
        val short = CloudSnapshotNaming.fileName("Redmi Note", 1_000)
        val long = CloudSnapshotNaming.fileName("Redmi Note-10", 2_000)

        assertEquals("Redmi Note", CloudSnapshotNaming.deviceOf(short))
        assertEquals("Redmi Note 10", CloudSnapshotNaming.deviceOf(long))

        assertTrue(CloudSnapshotNaming.belongsTo(short, "Redmi Note"))
        assertFalse(CloudSnapshotNaming.belongsTo(long, "Redmi Note"))
        assertFalse(CloudSnapshotNaming.belongsTo(short, "Redmi Note-10"))
        assertTrue(CloudSnapshotNaming.belongsTo(long, "Redmi Note-10"))
    }

    @Test
    fun `punctuation in a device name does not reach the path`() {
        assertEquals("OnePlus 9 Pro", CloudSnapshotNaming.sanitiseDevice("OnePlus/9 Pro"))
        assertEquals("a b", CloudSnapshotNaming.sanitiseDevice("  a   b  "))
        assertEquals(CloudSnapshotNaming.UNKNOWN_DEVICE, CloudSnapshotNaming.sanitiseDevice("  "))
        assertEquals(CloudSnapshotNaming.UNKNOWN_DEVICE, CloudSnapshotNaming.sanitiseDevice("///"))
    }

    /** A file somebody else put in the folder is readable without throwing. */
    @Test
    fun `a foreign name is handled rather than trusted`() {
        assertEquals(CloudSnapshotNaming.UNKNOWN_DEVICE, CloudSnapshotNaming.deviceOf("notes.json"))
        assertNull(CloudSnapshotNaming.timestampOf("notes.json"))
        assertNull(CloudSnapshotNaming.timestampOf("Pixel 7-yesterday.json"))
    }

    /** Sorting newest-first is what keeps pruning from dropping a current backup. */
    @Test
    fun `snapshots order by their timestamp`() {
        val names = listOf(
            CloudSnapshotNaming.fileName("Pixel 7", 900_000_000_000),
            CloudSnapshotNaming.fileName("Pixel 7", 1_757_500_000_000),
            CloudSnapshotNaming.fileName("Pixel 7", 1_000_000_000_000)
        )

        val newestFirst = names.sortedByDescending {
            CloudSnapshotNaming.timestampOf(it) ?: Long.MIN_VALUE
        }

        assertEquals(
            listOf(1_757_500_000_000L, 1_000_000_000_000L, 900_000_000_000L),
            newestFirst.map { CloudSnapshotNaming.timestampOf(it) }
        )
    }

    @Test
    fun `the sign-in form refuses what the server would reject anyway`() {
        val empty = CloudSignInForm()
        assertFalse(empty.canSubmit)

        assertFalse(empty.copy(email = "someone", password = "secret1").canSubmit)
        assertFalse(empty.copy(email = "someone@example.com", password = "short").canSubmit)
        assertTrue(empty.copy(email = "someone@example.com", password = "secret1").canSubmit)
    }

    /** A second tap while the first request is in flight would just duplicate it. */
    @Test
    fun `a form in flight cannot be submitted again`() {
        val ready = CloudSignInForm(email = "someone@example.com", password = "secret1")

        assertTrue(ready.canSubmit)
        assertFalse(ready.copy(isBusy = true).canSubmit)
    }
}
