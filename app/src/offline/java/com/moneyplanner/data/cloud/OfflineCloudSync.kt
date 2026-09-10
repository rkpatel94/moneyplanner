package com.moneyplanner.data.cloud

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The offline flavour's answer: there is no cloud, and there never will be in this build.
 *
 * This is the whole reason the app talks to an interface. This source set has no Firebase
 * dependency, so nothing here can drag in Play Services or the INTERNET permission through
 * a transitive, and the guarantee the README makes about the merged manifest stays
 * something anyone can check for themselves rather than something they have to believe.
 *
 * Every call fails with the same explanation instead of throwing, so a caller that reaches
 * one by mistake reports it plainly rather than crashing.
 */
@Singleton
class OfflineCloudSync @Inject constructor() : CloudSync {

    override val isAvailable: Boolean = false

    override val unavailableReason: String =
        "This build has no network access at all. Back up to a folder on your device " +
            "instead, or install the cloud build if you want copies kept online."

    override val account: Flow<CloudAccount?> = flowOf(null)

    private fun <T> refuse(): CloudResult<T> = CloudResult.Failed(unavailableReason)

    override suspend fun signIn(email: String, password: String): CloudResult<Unit> = refuse()

    override suspend fun register(email: String, password: String): CloudResult<Unit> = refuse()

    override suspend fun signOut() = Unit

    override suspend fun upload(json: String, deviceName: String): CloudResult<CloudSnapshot> =
        refuse()

    override suspend fun list(): CloudResult<List<CloudSnapshot>> = refuse()

    override suspend fun download(snapshot: CloudSnapshot): CloudResult<String> = refuse()

    override suspend fun delete(snapshot: CloudSnapshot): CloudResult<Unit> = refuse()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudSyncModule {
    @Binds
    @Singleton
    abstract fun bindCloudSync(impl: OfflineCloudSync): CloudSync
}
