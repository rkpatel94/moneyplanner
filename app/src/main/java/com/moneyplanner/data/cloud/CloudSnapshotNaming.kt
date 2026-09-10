package com.moneyplanner.data.cloud

/**
 * How a snapshot's file name is built and read back.
 *
 * A stored object carries two facts in its name — which device wrote it and when — and
 * three separate operations depend on reading them back: listing labels each backup,
 * pruning keeps the newest few *per device*, and both must agree on where one field ends
 * and the next begins.
 *
 * That agreement is what this exists to guarantee. The device name is stripped of hyphens
 * so the last hyphen is always the one before the timestamp, which makes the split exact
 * rather than a guess. Without it, a phone called "Redmi Note" would match the prefix of
 * every backup written by a "Redmi Note-10", and pruning one device's history would delete
 * another's. This is the kind of quiet, unnoticeable loss the whole app is built to avoid.
 *
 * It lives in the shared source set and touches nothing Android, so both flavours compile
 * against it and it can be tested on the JVM.
 */
object CloudSnapshotNaming {

    private const val SEPARATOR = '-'
    private const val SUFFIX = ".json"

    /** Fallback when a device reports no usable name at all. */
    const val UNKNOWN_DEVICE = "This device"

    /**
     * Reduces a device name to letters, digits, spaces and underscores.
     *
     * Storage paths tolerate more than this, but the point is not what a path allows: it
     * is that the result cannot contain the separator, so reading the name back is exact.
     */
    fun sanitiseDevice(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9 _]"), " ")
            .replace(Regex(" +"), " ")
            .trim()
            .ifBlank { UNKNOWN_DEVICE }

    /** The object name for one upload. [device] is sanitised here, so callers need not. */
    fun fileName(device: String, epochMillis: Long): String =
        sanitiseDevice(device) + SEPARATOR + epochMillis + SUFFIX

    /**
     * The device that wrote a snapshot.
     *
     * Everything before the final separator, which is unambiguous because a sanitised
     * device name has no separator in it.
     */
    fun deviceOf(fileName: String): String {
        val name = fileName.removeSuffix(SUFFIX)
        if (!name.contains(SEPARATOR)) return UNKNOWN_DEVICE
        return name.substringBeforeLast(SEPARATOR).ifBlank { UNKNOWN_DEVICE }
    }

    /** When it was written, or null for a name this app did not produce. */
    fun timestampOf(fileName: String): Long? =
        fileName.removeSuffix(SUFFIX).substringAfterLast(SEPARATOR).toLongOrNull()

    /**
     * Whether a snapshot belongs to a given device.
     *
     * An exact match on the parsed name rather than a prefix test, so no device can prune
     * another whose name it happens to begin with.
     */
    fun belongsTo(fileName: String, device: String): Boolean =
        deviceOf(fileName) == sanitiseDevice(device)
}
