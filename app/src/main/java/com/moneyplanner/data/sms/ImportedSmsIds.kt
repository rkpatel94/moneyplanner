package com.moneyplanner.data.sms

/**
 * The inbox ids of messages already imported, as kept in preferences.
 *
 * Ids are what let a second scan say "already imported" about the very message it is
 * looking at, rather than inferring it from a matching amount. The set is capped so it
 * cannot grow for the life of the phone: the inbox numbers messages in the order they
 * arrive, so the largest ids are the most recent, and anything older than the cap has long
 * since passed out of every scan window and is covered by the duplicate check anyway.
 */
object ImportedSmsIds {

    const val KEEP = 2_000

    fun parse(stored: Set<String>): Set<Long> =
        stored.mapNotNullTo(HashSet()) { it.toLongOrNull()?.takeIf { id -> id > 0 } }

    /** [stored] with [added] folded in, keeping only the [keep] most recent. */
    fun merge(stored: Set<String>, added: Collection<Long>, keep: Int = KEEP): Set<String> =
        (parse(stored) + added.filter { it > 0 })
            .sortedDescending()
            .take(keep)
            .mapTo(LinkedHashSet()) { it.toString() }
}
