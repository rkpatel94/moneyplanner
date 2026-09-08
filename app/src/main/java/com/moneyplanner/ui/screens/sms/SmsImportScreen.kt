package com.moneyplanner.ui.screens.sms

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.theme.Elevation
import com.moneyplanner.ui.theme.MoneyTheme
import kotlinx.coroutines.launch

/**
 * Importing bank alerts.
 *
 * Nothing is written without an explicit tick. Likely duplicates arrive unticked with the
 * reason shown, and the raw message sits under every row so a questionable parse can be
 * checked rather than trusted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsImportScreen(
    onBack: () -> Unit,
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showPaste by remember { mutableStateOf(false) }
    var pastedText by remember { mutableStateOf("") }

    val context = LocalContext.current
    // Android stops showing the dialog after the second refusal. Without noticing that,
    // the button would simply do nothing from then on, which reads as the feature being
    // broken; once it happens the only way back is the app's settings page.
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermission()
        if (granted) {
            permissionPermanentlyDenied = false
            viewModel.scan()
        } else {
            val activity = context.findActivity()
            permissionPermanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.READ_SMS
                )
        }
    }

    // Re-checked every time the screen comes back to the front, so granting the permission
    // in system settings and returning here works without restarting the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refreshPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Import from SMS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.rows.isNotEmpty()) {
                        TextButton(onClick = { viewModel.selectAll(state.selectedCount == 0) }) {
                            Text(if (state.selectedCount == 0) "Select all" else "Clear")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (state.selectedCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = Elevation.level2
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(20.dp)
                    ) {
                        PrimaryActionButton(
                            text = "Import ${state.selectedCount} " +
                                if (state.selectedCount == 1) "entry" else "entries",
                            onClick = {
                                viewModel.importSelected { count ->
                                    scope.launch {
                                        snackbarHost.showSnackbar("$count added.")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.hasPermission) {
                item {
                    SectionCard(title = "Read your bank alerts") {
                        Text(
                            "Your bank texts you for every debit and credit. Reading those " +
                                "alerts saves typing nearly every expense by hand.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Messages are read on your phone, parsed, and thrown away. Only " +
                                "what you tick is saved. Nothing is sent anywhere — this app " +
                                "has no internet permission at all, and only looks at texts " +
                                "from bank shortcodes, never from people.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        if (permissionPermanentlyDenied) {
                            Text(
                                "Android will not ask again once the request has been " +
                                    "turned down twice. It can still be switched on from " +
                                    "this app's permissions screen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                            PrimaryActionButton(
                                text = "Open app settings",
                                onClick = { context.openAppSettings() }
                            )
                        } else {
                            PrimaryActionButton(
                                text = "Allow reading SMS",
                                onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showPaste = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Or paste one message instead") }
                    }
                }
            } else if (!state.hasScanned) {
                item {
                    SectionCard {
                        Text(
                            "Look through the last 30 days of bank alerts for anything not " +
                                "already recorded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryActionButton(text = "Scan messages", onClick = viewModel::scan)
                    }
                }
            }

            if (state.isScanning) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
            }

            state.scanError?.let { message ->
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.Message,
                        title = "Could not read your messages",
                        message = message
                    )
                }
            }

            state.emptyExplanation?.let { explanation ->
                if (!state.isScanning) {
                    item {
                        EmptyState(
                            icon = Icons.AutoMirrored.Filled.Message,
                            title = "Nothing new found",
                            message = explanation
                        )
                    }
                }
            }

            if (state.hasScanned && !state.isScanning) {
                item {
                    OutlinedButton(
                        onClick = viewModel::scan,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Scan again") }
                }
            }

            if (state.duplicateCount > 0) {
                item {
                    StatusPill(
                        text = "${state.duplicateCount} look like entries you already added",
                        containerColor = MoneyTheme.colors.warningContainer,
                        contentColor = MoneyTheme.colors.onWarningContainer
                    )
                }
            }

            items(state.rows, key = { it.candidate.smsId }) { row ->
                SmsRowCard(
                    row = row,
                    categories = state.categories,
                    onToggle = { viewModel.toggle(row.candidate.smsId) },
                    onCategory = { viewModel.setCategory(row.candidate.smsId, it) }
                )
            }
        }
    }

    if (showPaste) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showPaste = false
                viewModel.clearPasted()
            },
            shape = MaterialTheme.shapes.large,
            title = { Text("Paste a bank message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pastedText,
                        onValueChange = {
                            pastedText = it
                            viewModel.parsePasted(it)
                        },
                        label = { Text("Message text") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.pastedError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    state.pastedRow?.let { row ->
                        val parsed = row.candidate.parsed
                        Text(
                            "Read as ${parsed.suggestedDescription()} for " +
                                com.moneyplanner.core.money.IndianFormat.format(
                                    parsed.amount ?: com.moneyplanner.core.money.Money.ZERO
                                ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importPasted { count ->
                            scope.launch { snackbarHost.showSnackbar("$count added.") }
                        }
                        pastedText = ""
                        showPaste = false
                    },
                    enabled = state.pastedRow != null
                ) { Text("Add it") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPaste = false
                    pastedText = ""
                    viewModel.clearPasted()
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SmsRowCard(
    row: SmsImportRow,
    categories: List<com.moneyplanner.domain.model.Category>,
    onToggle: () -> Unit,
    onCategory: (Long) -> Unit
) {
    val colors = MoneyTheme.colors
    val parsed = row.candidate.parsed
    var showRaw by remember { mutableStateOf(false) }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = row.isSelected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    parsed.suggestedDescription(),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${DateUtil.formatDayMonth(parsed.date ?: row.candidate.receivedOn)} · " +
                        row.candidate.sender,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MoneyText(
                money = parsed.amount ?: com.moneyplanner.core.money.Money.ZERO,
                style = MaterialTheme.typography.titleMedium,
                color = if (parsed.isDebit) colors.negative else colors.positive
            )
        }

        if (row.isDuplicate) {
            Spacer(Modifier.height(8.dp))
            StatusPill(
                text = "Same amount and date already recorded",
                containerColor = colors.warningContainer,
                contentColor = colors.onWarningContainer
            )
        }

        if (parsed.isDebit && categories.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ChipSelector(
                label = "Category",
                options = categories.map { it.id },
                selected = row.suggestedCategoryId,
                onSelect = onCategory,
                optionLabel = { id -> categories.first { it.id == id }.name }
            )
        }

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { showRaw = !showRaw }) {
            Text(if (showRaw) "Hide message" else "Show message")
        }
        if (showRaw) {
            Text(
                parsed.originalText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Walks up the context wrappers to the hosting activity.
 *
 * Needed because shouldShowRequestPermissionRationale is an activity call, and the
 * context a composable sees is usually a wrapper around one rather than the activity.
 */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}

/** Opens this app's own entry in system settings, where a denied permission can be granted. */
private fun android.content.Context.openAppSettings() {
    startActivity(
        android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", packageName, null)
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
