package com.moneyplanner.data.cloud

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Copying the user's records somewhere that survives losing the phone.
 *
 * The whole app talks to this interface and never to Firebase. Each build flavour supplies
 * its own binding: the offline flavour a no-op that reports itself unavailable, the cloud
 * flavour one backed by Firebase Auth and Storage. That is what lets the offline build keep
 * a merged manifest with no INTERNET permission in it, verifiable rather than promised.
 *
 * What this deliberately is **not** is live multi-device merge. Each device uploads whole
 * snapshots under its own name, and restoring is an explicit act. Two devices editing at
 * once therefore produce two snapshots rather than one silently winning, because a
 * last-write-wins merge on a financial ledger loses records without telling anybody, and
 * this app exists to not do that.
 */
interface CloudSync {

    /**
     * False in the offline flavour, and in a cloud build with no Firebase project
     * configured. The UI hides itself rather than offering something that cannot work.
     */
    val isAvailable: Boolean

    /** Why it is unavailable, when it is, so the screen can say something useful. */
    val unavailableReason: String?

    val account: Flow<CloudAccount?>

    suspend fun signIn(email: String, password: String): CloudResult<Unit>

    suspend fun register(email: String, password: String): CloudResult<Unit>

    suspend fun signOut()

    /**
     * Uploads the current records as a new snapshot.
     *
     * Always a new object rather than an overwrite: the point of a backup is the copy you
     * did not know you would need, and a rolling single file cannot give you that.
     */
    suspend fun upload(json: String, deviceName: String): CloudResult<CloudSnapshot>

    /** Every snapshot in the account, newest first. */
    suspend fun list(): CloudResult<List<CloudSnapshot>>

    /** The backup JSON behind one snapshot, ready to be previewed before it is applied. */
    suspend fun download(snapshot: CloudSnapshot): CloudResult<String>

    suspend fun delete(snapshot: CloudSnapshot): CloudResult<Unit>
}

/** Who is signed in. Nothing here is a secret; the token stays inside the SDK. */
data class CloudAccount(
    val userId: String,
    val email: String?
)

/**
 * One uploaded copy of the records.
 *
 * [deviceName] is carried so a restore list reads as "the phone" and "the tablet" rather
 * than as a column of timestamps, which is the difference between choosing confidently and
 * guessing.
 */
data class CloudSnapshot(
    val path: String,
    val deviceName: String,
    val uploadedAt: Instant,
    val sizeBytes: Long
)

/**
 * Success or a message a person can act on.
 *
 * Every failure here is one the user might be able to do something about — no signal, wrong
 * password, project not set up — so the message is written for them rather than being a
 * stack trace forwarded to the screen.
 */
sealed interface CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>
    data class Failed(val message: String) : CloudResult<Nothing>
}

inline fun <T> CloudResult<T>.onOk(action: (T) -> Unit): CloudResult<T> {
    if (this is CloudResult.Ok) action(value)
    return this
}

inline fun <T> CloudResult<T>.onFailed(action: (String) -> Unit): CloudResult<T> {
    if (this is CloudResult.Failed) action(message)
    return this
}
