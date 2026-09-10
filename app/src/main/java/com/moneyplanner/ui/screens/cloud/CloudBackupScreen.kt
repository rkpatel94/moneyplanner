package com.moneyplanner.ui.screens.cloud

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.BuildConfig
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.data.backup.BackupPreview
import com.moneyplanner.data.cloud.CloudSnapshot
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.theme.MoneyTheme
import java.time.ZoneId

/**
 * Copies of the records kept online, so losing the phone is not losing the history.
 *
 * The screen is deliberately a list of dated snapshots rather than a sync toggle. Nothing
 * uploads on its own and nothing comes down on its own: both directions are something the
 * user asks for and can see the result of. That is the honest shape for a ledger, where a
 * silent merge would lose entries nobody would notice were gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    viewModel: CloudBackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<CloudSnapshot?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }

    // The device name labels the snapshot, so a restore list reads as "the old phone"
    // rather than as a column of identical timestamps.
    val deviceName = remember { Build.MODEL?.takeIf { it.isNotBlank() } ?: "This device" }

    LaunchedEffect(account?.userId) {
        if (account != null) viewModel.refresh()
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isBusy) {
                item(key = "progress") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (!state.isAvailable) {
                item(key = "unavailable") {
                    UnavailableCard(reason = state.unavailableReason)
                }
                return@LazyColumn
            }

            val signedIn = account
            if (signedIn == null) {
                item(key = "sign-in") {
                    SignInCard(form = form, viewModel = viewModel)
                }
                item(key = "what-is-kept") { WhatIsKeptCard() }
            } else {
                item(key = "account") {
                    SectionCard(title = "Signed in") {
                        Text(
                            signedIn.email ?: "Your account",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryActionButton(
                            text = "Back up now",
                            onClick = { viewModel.uploadNow(deviceName) },
                            enabled = !state.isBusy
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { confirmSignOut = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Sign out") }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Signing out leaves your backups online and your records on " +
                                "this phone. Nothing is deleted either way.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.snapshots.isEmpty()) {
                    item(key = "no-snapshots") {
                        EmptyState(
                            icon = Icons.Filled.CloudUpload,
                            title = "No backups yet",
                            message = "Back up now to keep a copy of your records that " +
                                "survives losing this phone."
                        )
                    }
                } else {
                    item(key = "snapshots-header") {
                        Text("Your backups", style = MaterialTheme.typography.titleMedium)
                    }
                    items(
                        items = state.snapshots,
                        // Namespaced because several lists share this column and a bare
                        // path could collide with another item's key.
                        key = { "snapshot-" + it.path }
                    ) { snapshot ->
                        SnapshotRow(
                            snapshot = snapshot,
                            enabled = !state.isBusy,
                            onRestore = { viewModel.previewRestore(snapshot) },
                            onDelete = { pendingDelete = snapshot }
                        )
                    }
                }

                item(key = "what-is-kept") { WhatIsKeptCard() }
            }
        }
    }

    pendingRestore?.let { pending ->
        CloudRestoreDialog(
            snapshot = pending.snapshot,
            preview = pending.preview,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::dismissRestore
        )
    }

    pendingDelete?.let { snapshot ->
        ConfirmDialog(
            title = "Delete this backup?",
            message = "The copy taken on " + formatTaken(snapshot) + " is removed from " +
                "your account. Your records on this phone are not touched.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete(snapshot) },
            onDismiss = { pendingDelete = null }
        )
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            message = "Your backups stay online and your records stay on this phone. " +
                "You will need to sign in again to back up or restore.",
            confirmLabel = "Sign out",
            onConfirm = viewModel::signOut,
            onDismiss = { confirmSignOut = false }
        )
    }
}

/**
 * Why there is nothing to do here.
 *
 * In the offline build this is the permanent answer, and saying so plainly beats hiding the
 * screen: somebody who went looking for cloud backup deserves to be told which build they
 * are running rather than left wondering where the feature went.
 */
@Composable
private fun UnavailableCard(reason: String?) {
    val colors = MoneyTheme.colors

    // Two different reasons reach this card, and naming the wrong one sends somebody
    // looking in the wrong place. A cloud build missing its project is not "unavailable"
    // — it is one file away from working. An offline build is unavailable, by design.
    val configurable = BuildConfig.CLOUD_ENABLED

    SectionCard(
        title = if (configurable) "Not set up yet" else "Not available in this build"
    ) {
        StatusPill(
            text = if (configurable) "Firebase not connected" else "Offline build",
            leadingIcon = Icons.Filled.CloudOff,
            containerColor = colors.warningContainer,
            contentColor = colors.onWarningContainer
        )
        Spacer(Modifier.height(10.dp))
        Text(
            reason ?: "Cloud backup is not set up in this build.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SignInCard(form: CloudSignInForm, viewModel: CloudBackupViewModel) {
    SectionCard(title = if (form.isRegistering) "Create an account" else "Sign in") {
        Text(
            "An email and a password is all this uses. There is no profile, no contacts " +
                "and no analytics.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        LabelledTextField(
            value = form.email,
            onValueChange = viewModel::updateEmail,
            label = "Email",
            keyboardType = KeyboardType.Email,
            enabled = !form.isBusy
        )
        Spacer(Modifier.height(10.dp))

        LabelledTextField(
            value = form.password,
            onValueChange = viewModel::updatePassword,
            label = "Password",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            enabled = !form.isBusy,
            supportingText = if (form.isRegistering) "At least six characters." else null,
            visualTransformation = PasswordVisualTransformation()
        )

        form.error?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(14.dp))
        PrimaryActionButton(
            text = if (form.isRegistering) "Create account" else "Sign in",
            onClick = viewModel::submit,
            enabled = form.canSubmit
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = viewModel::toggleRegistering,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (form.isRegistering) {
                    "I already have an account"
                } else {
                    "I need to create an account"
                }
            )
        }
    }
}

/** The plain statement of what leaves the phone, next to the button that sends it. */
@Composable
private fun WhatIsKeptCard() {
    SectionCard(title = "What is kept online") {
        Text(
            "A backup holds every expense, income, person, loan, bill, goal and account " +
                "in the app: the same file the local backup writes. It is stored under " +
                "your account, where nobody else can read it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Nothing uploads on its own. Each backup is a copy taken the moment you asked " +
                "for it, and restoring replaces what is on this phone only after you have " +
                "seen what is in the file.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SnapshotRow(
    snapshot: CloudSnapshot,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatTaken(snapshot), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    snapshot.deviceName + " · " + formatSize(snapshot.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRestore, enabled = enabled) {
                Icon(Icons.Filled.Restore, contentDescription = "Restore this backup")
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete this backup",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * What a downloaded backup holds, shown before it replaces anything.
 *
 * The same reasoning as the local restore dialog, with one addition: this file came off a
 * network and may have been taken on a different phone, so the dialog names which one and
 * when, not only what is inside.
 */
@Composable
private fun CloudRestoreDialog(
    snapshot: CloudSnapshot,
    preview: BackupPreview.Readable,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MoneyTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "From " + snapshot.deviceName + ", taken on " + formatTaken(snapshot) + ".",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (preview.isEmpty) {
                    StatusPill(
                        text = "This backup is empty. Restoring it would leave you with nothing.",
                        containerColor = colors.warningContainer,
                        contentColor = colors.onWarningContainer
                    )
                } else {
                    Text("It contains:", style = MaterialTheme.typography.bodyMedium)
                    CountRow("Expenses", preview.expenses)
                    CountRow("Income receipts", preview.incomeTransactions)
                    CountRow("People", preview.people)
                    CountRow("Loans", preview.emis)
                    CountRow("Bills", preview.bills)
                    CountRow("Goals", preview.goals)
                    CountRow("Accounts", preview.accounts)
                }

                Text(
                    "Everything currently in the app is replaced. This cannot be undone, " +
                        "so back up first if you are not sure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.negative
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Replace everything") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CountRow(label: String, count: Int) {
    if (count == 0) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(count.toString(), style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatTaken(snapshot: CloudSnapshot): String {
    val local = snapshot.uploadedAt.atZone(ZoneId.systemDefault())
    val time = "%02d:%02d".format(local.hour, local.minute)
    return DateUtil.formatDate(local.toLocalDate()) + " at " + time
}

/**
 * Rounded to whole units on purpose. The size is here to tell somebody whether a backup
 * looks complete or suspiciously small, and a decimal place does not help with that.
 */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> (bytes / (1024L * 1024L)).toString() + " MB"
    bytes >= 1024L -> (bytes / 1024L).toString() + " KB"
    else -> bytes.toString() + " bytes"
}
