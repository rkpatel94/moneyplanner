package com.moneyplanner.data.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup to Firebase Auth and Storage.
 *
 * **Storage rather than Firestore.** The payload is the same backup document the app
 * already writes to a file, which for a few years of records runs past Firestore's one
 * megabyte document limit and has no business being queried field by field anyway. Storage
 * is built for whole files, so the cloud copy is byte-identical to the local one.
 *
 * **Every upload is a new object.** Snapshots are named by device and timestamp, and older
 * ones are pruned rather than overwritten. The point of a backup is the copy you did not
 * know you would need, and a single rolling file cannot give you that.
 *
 * **No silent merge.** Two devices produce two snapshots, and restoring is something the
 * user does deliberately after seeing what is in the file. A last-write-wins merge on a
 * ledger loses records without telling anybody, which is the failure this whole app is
 * built to avoid.
 */
@Singleton
class FirebaseCloudSync @Inject constructor(
    @ApplicationContext private val context: Context
) : CloudSync {

    private companion object {
        /** Snapshots kept per device before the oldest is pruned. */
        const val KEEP_PER_DEVICE = 10
        const val ROOT = "backups"
    }

    /**
     * True only when a Firebase project is actually configured.
     *
     * A cloud build with no google-services.json compiles perfectly well and would then
     * throw on first use. Reporting it as unavailable turns that into a sentence on a
     * settings screen instead of a crash.
     */
    private val configured: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    override val isAvailable: Boolean get() = configured

    override val unavailableReason: String?
        get() = if (configured) {
            null
        } else {
            "This build has no Firebase project attached. Add google-services.json from " +
                "the Firebase console at app/src/cloud/ and rebuild."
        }

    private val auth: FirebaseAuth? by lazy {
        if (configured) FirebaseAuth.getInstance() else null
    }

    private val storage: FirebaseStorage? by lazy {
        if (configured) FirebaseStorage.getInstance() else null
    }

    override val account: Flow<CloudAccount?> = callbackFlow {
        val instance = auth
        if (instance == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { state ->
            trySend(state.currentUser?.let { CloudAccount(it.uid, it.email) })
        }
        instance.addAuthStateListener(listener)
        awaitClose { instance.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String): CloudResult<Unit> {
        val instance = auth ?: return unavailable()
        return runCatching {
            instance.signInWithEmailAndPassword(email.trim(), password).await()
            Unit
        }.fold(
            onSuccess = { CloudResult.Ok(Unit) },
            onFailure = { CloudResult.Failed(explain(it)) }
        )
    }

    override suspend fun register(email: String, password: String): CloudResult<Unit> {
        val instance = auth ?: return unavailable()
        return runCatching {
            instance.createUserWithEmailAndPassword(email.trim(), password).await()
            Unit
        }.fold(
            onSuccess = { CloudResult.Ok(Unit) },
            onFailure = { CloudResult.Failed(explain(it)) }
        )
    }

    override suspend fun signOut() {
        auth?.signOut()
    }

    override suspend fun upload(json: String, deviceName: String): CloudResult<CloudSnapshot> {
        val folder = userFolder() ?: return unavailable()
        val safeDevice = CloudSnapshotNaming.sanitiseDevice(deviceName)
        val name = CloudSnapshotNaming.fileName(safeDevice, Instant.now().toEpochMilli())

        return runCatching {
            val target = folder.child(name)
            target.putBytes(json.toByteArray(Charsets.UTF_8)).await()
            val meta = target.metadata.await()

            pruneOldSnapshots(folder, safeDevice)

            CloudSnapshot(
                path = target.path,
                deviceName = safeDevice,
                uploadedAt = Instant.ofEpochMilli(meta.creationTimeMillis),
                sizeBytes = meta.sizeBytes
            )
        }.fold(
            onSuccess = { CloudResult.Ok(it) },
            onFailure = { CloudResult.Failed(explain(it)) }
        )
    }

    override suspend fun list(): CloudResult<List<CloudSnapshot>> {
        val folder = userFolder() ?: return unavailable()

        return runCatching {
            folder.listAll().await().items
                .map { item ->
                    val meta = item.metadata.await()
                    CloudSnapshot(
                        path = item.path,
                        deviceName = CloudSnapshotNaming.deviceOf(item.name),
                        uploadedAt = Instant.ofEpochMilli(meta.creationTimeMillis),
                        sizeBytes = meta.sizeBytes
                    )
                }
                .sortedByDescending { it.uploadedAt }
        }.fold(
            onSuccess = { CloudResult.Ok(it) },
            onFailure = { CloudResult.Failed(explain(it)) }
        )
    }

    override suspend fun download(snapshot: CloudSnapshot): CloudResult<String> {
        val instance = storage ?: return unavailable()

        return runCatching {
            // Capped so a corrupt or hostile object cannot be pulled into memory whole.
            // A backup of this app is measured in hundreds of kilobytes.
            val bytes = instance.getReference(snapshot.path)
                .getBytes(8L * 1024 * 1024)
                .await()
            bytes.toString(Charsets.UTF_8)
        }.fold(
            onSuccess = { CloudResult.Ok(it) },
            onFailure = { CloudResult.Failed(explain(it)) }
        )
    }

    override suspend fun delete(snapshot: CloudSnapshot): CloudResult<Unit> {
        val instance = storage ?: return unavailable()
        return runCatching {
            instance.getReference(snapshot.path).delete().await()
            Unit
        }.fold(
            onSuccess = { CloudResult.Ok(Unit) },
            onFailure = { CloudResult.Failed(explain(it)) }
        )
    }

    // ---- Internals -----------------------------------------------------------------

    /**
     * Everything a user stores lives under their own id, which is what the storage rules
     * key on. A path that did not include the uid would be one rule mistake away from one
     * person reading another's ledger.
     */
    private fun userFolder(): StorageReference? {
        val uid = auth?.currentUser?.uid ?: return null
        return storage?.reference?.child("$ROOT/$uid")
    }

    /** Keeps the newest few per device, so one phone cannot crowd out another's history. */
    private suspend fun pruneOldSnapshots(folder: StorageReference, deviceName: String) {
        runCatching {
            folder.listAll().await().items
                .filter { CloudSnapshotNaming.belongsTo(it.name, deviceName) }
                // Newest first by the timestamp in the name, not by string order, so a
                // name this app did not write cannot displace a real backup. Anything
                // unparseable sorts last and is pruned first.
                .sortedByDescending { CloudSnapshotNaming.timestampOf(it.name) ?: Long.MIN_VALUE }
                .drop(KEEP_PER_DEVICE)
                .forEach { runCatching { it.delete().await() } }
        }
    }

    private fun <T> unavailable(): CloudResult<T> = CloudResult.Failed(
        unavailableReason ?: "You are not signed in."
    )

    /**
     * Turns an SDK exception into something the user can act on.
     *
     * Every one of these is a situation somebody can do something about, so the message
     * says what to do rather than forwarding a class name to the screen.
     */
    private fun explain(error: Throwable): String = when (error) {
        is FirebaseAuthWeakPasswordException ->
            "That password is too short. Use at least six characters."
        is FirebaseAuthInvalidCredentialsException ->
            "That email or password was not right."
        is FirebaseAuthInvalidUserException ->
            "No account exists for that email."
        is FirebaseAuthUserCollisionException ->
            "An account already exists for that email. Sign in instead."
        is IOException ->
            "Could not reach the network. Your records are safe on this device; try again " +
                "when you have a connection."
        else ->
            error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudSyncModule {
    @Binds
    @Singleton
    abstract fun bindCloudSync(impl: FirebaseCloudSync): CloudSync
}
