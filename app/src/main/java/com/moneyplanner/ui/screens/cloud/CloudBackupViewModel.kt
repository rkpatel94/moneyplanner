package com.moneyplanner.ui.screens.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.data.backup.BackupManager
import com.moneyplanner.data.backup.BackupPreview
import com.moneyplanner.data.backup.RestoreResult
import com.moneyplanner.data.cloud.CloudAccount
import com.moneyplanner.data.cloud.CloudResult
import com.moneyplanner.data.cloud.CloudSnapshot
import com.moneyplanner.data.cloud.CloudSync
import com.moneyplanner.data.prefs.SettingsStore
import com.moneyplanner.data.repo.TodayProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeping a copy of the records somewhere that survives losing the phone.
 *
 * This talks only to [CloudSync], never to Firebase. In the offline flavour that binding
 * refuses everything and the screen says why, which is how one view model serves a build
 * that can reach a network and one that provably cannot.
 *
 * Restoring goes through the same preview the local restore uses. Replacing every record in
 * the app is the one action with nothing behind it, and a file that arrived over a network
 * deserves more scrutiny than one the user picked themselves, not less.
 */
@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    private val cloudSync: CloudSync,
    private val backupManager: BackupManager,
    private val settingsStore: SettingsStore,
    private val today: TodayProvider
) : ViewModel() {

    val account: StateFlow<CloudAccount?> = cloudSync.account.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    private val _state = MutableStateFlow(
        CloudBackupState(
            isAvailable = cloudSync.isAvailable,
            unavailableReason = cloudSync.unavailableReason
        )
    )
    val state: StateFlow<CloudBackupState> = _state.asStateFlow()

    private val _form = MutableStateFlow(CloudSignInForm())
    val form: StateFlow<CloudSignInForm> = _form.asStateFlow()

    private val _pendingRestore = MutableStateFlow<PendingCloudRestore?>(null)
    val pendingRestore: StateFlow<PendingCloudRestore?> = _pendingRestore.asStateFlow()

    // ---- Sign in -------------------------------------------------------------------

    fun updateEmail(value: String) = _form.update { it.copy(email = value, error = null) }
    fun updatePassword(value: String) = _form.update { it.copy(password = value, error = null) }
    fun toggleRegistering() = _form.update { it.copy(isRegistering = !it.isRegistering, error = null) }

    fun submit() {
        val current = _form.value
        if (!current.canSubmit) return

        _form.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val result = if (current.isRegistering) {
                cloudSync.register(current.email, current.password)
            } else {
                cloudSync.signIn(current.email, current.password)
            }
            when (result) {
                is CloudResult.Ok -> {
                    // The password is dropped the moment it is no longer needed; nothing
                    // here has any reason to keep holding it.
                    _form.value = CloudSignInForm()
                    refresh()
                }
                is CloudResult.Failed ->
                    _form.update { it.copy(isBusy = false, error = result.message) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            cloudSync.signOut()
            _state.update { it.copy(snapshots = emptyList(), message = null) }
        }
    }

    // ---- Snapshots -----------------------------------------------------------------

    fun refresh() {
        if (!cloudSync.isAvailable) return
        _state.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            when (val result = cloudSync.list()) {
                is CloudResult.Ok ->
                    _state.update { it.copy(snapshots = result.value, isBusy = false) }
                is CloudResult.Failed ->
                    _state.update { it.copy(isBusy = false, message = result.message) }
            }
        }
    }

    /**
     * Uploads the records as they stand.
     *
     * The upload counts as a backup for the health status on the settings screen, for the
     * same reason a local one does: a copy was made. Whether the network kept it is the
     * one thing a success response is actually evidence of.
     */
    fun uploadNow(deviceName: String) {
        _state.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            val json = backupManager.buildJson(today.today())
            when (val result = cloudSync.upload(json, deviceName)) {
                is CloudResult.Ok -> {
                    settingsStore.recordBackupRun(today.today().toEpochDay())
                    _state.update { it.copy(isBusy = false, message = "Backed up to your account.") }
                    refresh()
                }
                is CloudResult.Failed ->
                    _state.update { it.copy(isBusy = false, message = result.message) }
            }
        }
    }

    // ---- Restore -------------------------------------------------------------------

    /** Downloads and inspects, without touching anything yet. */
    fun previewRestore(snapshot: CloudSnapshot) {
        _state.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            when (val downloaded = cloudSync.download(snapshot)) {
                is CloudResult.Failed ->
                    _state.update { it.copy(isBusy = false, message = downloaded.message) }
                is CloudResult.Ok -> {
                    when (val preview = backupManager.previewText(downloaded.value)) {
                        is BackupPreview.Readable -> {
                            _pendingRestore.value = PendingCloudRestore(
                                snapshot = snapshot,
                                json = downloaded.value,
                                preview = preview
                            )
                            _state.update { it.copy(isBusy = false) }
                        }
                        is BackupPreview.Unreadable ->
                            _state.update { it.copy(isBusy = false, message = preview.message) }
                    }
                }
            }
        }
    }

    fun dismissRestore() {
        _pendingRestore.value = null
    }

    fun confirmRestore() {
        val pending = _pendingRestore.value ?: return
        _pendingRestore.value = null
        _state.update { it.copy(isBusy = true) }

        viewModelScope.launch {
            val result = backupManager.importText(pending.json)
            _state.update {
                it.copy(
                    isBusy = false,
                    message = when (result) {
                        is RestoreResult.Success -> "Your records have been replaced."
                        is RestoreResult.Failure -> result.message
                    }
                )
            }
        }
    }

    fun delete(snapshot: CloudSnapshot) {
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (val result = cloudSync.delete(snapshot)) {
                is CloudResult.Ok -> refresh()
                is CloudResult.Failed ->
                    _state.update { it.copy(isBusy = false, message = result.message) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}

data class CloudBackupState(
    val isAvailable: Boolean = false,
    val unavailableReason: String? = null,
    val snapshots: List<CloudSnapshot> = emptyList(),
    val isBusy: Boolean = false,
    val message: String? = null
)

data class CloudSignInForm(
    val email: String = "",
    val password: String = "",
    /** Registering rather than signing in. Same two fields, different call. */
    val isRegistering: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null
) {
    val canSubmit: Boolean
        get() = !isBusy && email.contains("@") && password.length >= 6
}

/** A snapshot downloaded and read, waiting for the user to say whether to apply it. */
data class PendingCloudRestore(
    val snapshot: CloudSnapshot,
    val json: String,
    val preview: BackupPreview.Readable
)
