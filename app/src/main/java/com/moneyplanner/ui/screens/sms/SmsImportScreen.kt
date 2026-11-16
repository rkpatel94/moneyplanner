package com.moneyplanner.ui.screens.sms

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.data.sms.SmsScanPeriod
import com.moneyplanner.domain.model.Category
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.theme.MoneyTheme
import kotlinx.coroutines.launch

/**
 * Importing bank alerts, by scanning the inbox or by pasting them.
 *
 * Scanning is the main way in, and the one moment the app asks to read SMS: the request
 * appears when Scan is tapped, never on opening the screen. Pasting sits below it and needs
 * no permission, so somebody who says no still has a way to import, and is told so. Either
 * way the messages land in the same review list, where nothing is saved until the user
 * has seen what each one will become.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsImportScreen(
    onBack: () -> Unit,
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pasted by rememberSaveable { mutableStateOf("") }

    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.scan()
        } else {
            // Once the system stops showing the request, only the settings page can grant it.
            val activity = context.findActivity()
            val canAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.READ_SMS
                )
            viewModel.onPermissionDenied(canAskAgain)
        }
    }

    val startScan: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.scan() else permissionRequest.launch(Manifest.permission.READ_SMS)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Import bank messages") },
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
        bottomBar = {
            if (state.selectedCount > 0) {
                SectionCard {
                    PrimaryActionButton(
                        text = if (state.isImporting) {
                            "Importing…"
                        } else {
                            "Import ${state.selectedCount} " +
                                if (state.selectedCount == 1) "entry" else "entries"
                        },
                        enabled = !state.isImporting,
                        onClick = {
                            viewModel.importSelected { count ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (count == 1) "1 entry added" else "$count entries added"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScanCard(
                    state = state,
                    onPeriod = viewModel::selectPeriod,
                    onScan = startScan,
                    onOpenSettings = { context.openAppSettings() }
                )
            }

            item {
                PasteCard(
                    text = pasted,
                    onTextChange = { pasted = it },
                    state = state,
                    onRead = {
                        viewModel.parsePasted(pasted)
                        pasted = ""
                    }
                )
            }

            if (state.rows.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${state.rows.size} to review",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = viewModel::clearAll) { Text("Start over") }
                    }
                }
            }

            state.importError?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (state.duplicateCount > 0) {
                item {
                    StatusPill(
                        text = "${state.duplicateCount} look like entries you already have",
                        containerColor = MoneyTheme.colors.warningContainer,
                        contentColor = MoneyTheme.colors.onWarningContainer
                    )
                }
            }

            items(state.rows, key = { "sms-${it.key}" }) { row ->
                SmsRowCard(
                    row = row,
                    categories = state.categories,
                    onToggle = { viewModel.toggle(row.key) },
                    onCategory = { viewModel.setCategory(row.key, it) },
                    onRemove = { viewModel.remove(row.key) }
                )
            }
        }
    }
}

@Composable
private fun ScanCard(
    state: SmsImportState,
    onPeriod: (SmsScanPeriod) -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant

    SectionCard(
        title = "Scan your messages",
        subtitle = "Finds bank alerts on this phone. Nothing leaves it."
    ) {
        ChipSelector(
            label = "Look back",
            options = SmsScanPeriod.entries,
            selected = state.period,
            onSelect = onPeriod,
            optionLabel = { it.label }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            state.periodDescription,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryText
        )
        Spacer(Modifier.height(12.dp))

        if (state.isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Reading your messages…", style = MaterialTheme.typography.bodyMedium)
        } else {
            PrimaryActionButton(text = "Scan messages", onClick = onScan)
        }

        state.scanSummary?.let { summary ->
            Spacer(Modifier.height(8.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }

        state.scanError?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (state.permissionDenied) {
            Spacer(Modifier.height(8.dp))
            // Says what still works, so a "no" is a choice rather than a dead end.
            Text(
                "Without permission to read SMS the app cannot scan. Pasting a message " +
                    "below still works, and needs no permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            if (!state.canAskPermissionAgain) {
                TextButton(onClick = onOpenSettings) { Text("Allow it in settings") }
            }
        } else if (!state.hasScanned) {
            Spacer(Modifier.height(8.dp))
            Text(
                "The app asks to read SMS only when you tap Scan. It looks at messages " +
                    "from banks, never from people, and saves nothing until you have " +
                    "checked it.",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText
            )
        }
    }
}

@Composable
private fun PasteCard(
    text: String,
    onTextChange: (String) -> Unit,
    state: SmsImportState,
    onRead: () -> Unit
) {
    SectionCard(
        title = "Or paste a message",
        subtitle = "Several at once is fine, with a blank line between them"
    ) {
        LabelledTextField(
            value = text,
            onValueChange = onTextChange,
            label = "Message text",
            singleLine = false,
            imeAction = ImeAction.Default
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRead,
            enabled = text.isNotBlank(),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Read pasted text")
        }

        state.pastedError?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        state.skippedNote?.let { note ->
            Spacer(Modifier.height(8.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmsRowCard(
    row: SmsImportRow,
    categories: List<Category>,
    onToggle: () -> Unit,
    onCategory: (Long) -> Unit,
    onRemove: () -> Unit
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
                    "${DateUtil.formatDayMonth(row.date)} · ${row.candidate.sender}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MoneyText(
                money = parsed.amount ?: Money.ZERO,
                style = MaterialTheme.typography.titleMedium,
                color = if (parsed.isDebit) colors.negative else colors.positive
            )
        }

        // What importing this row will write, before the user commits to it.
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                row.plan.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                row.plan.summary(row.date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Says how sure it is rather than a flat "duplicate", because the answer ranges
        // from certain to a hunch and the user is the one deciding.
        val warning = row.duplicate.label ?: row.plan.caution
        warning?.let { label ->
            Spacer(Modifier.height(8.dp))
            StatusPill(
                text = label,
                containerColor = colors.warningContainer,
                contentColor = colors.onWarningContainer
            )
        }

        if (row.plan.writesExpense && categories.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ChipSelector(
                label = "Category",
                options = categories.map { it.id },
                selected = row.suggestedCategoryId,
                onSelect = onCategory,
                optionLabel = { id -> categories.firstOrNull { it.id == id }?.name.orEmpty() }
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { showRaw = !showRaw }) {
                Text(if (showRaw) "Hide message" else "Show message")
            }
            TextButton(onClick = onRemove) { Text("Remove") }
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

private fun SmsImportPlan.icon(): ImageVector = when (this) {
    is SmsImportPlan.CardSpend, is SmsImportPlan.CardBillPayment -> Icons.Default.CreditCard
    is SmsImportPlan.CashWithdrawal -> Icons.Default.SwapHoriz
    is SmsImportPlan.BankDebit ->
        if (isCashWithdrawal) Icons.Default.Payments else Icons.Default.AccountBalance
    is SmsImportPlan.ScheduledIncome -> Icons.Default.EventRepeat
    is SmsImportPlan.OtherIncome -> Icons.Default.AccountBalance
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The app's own page in system settings, where a permission refused for good can be granted. */
private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
