package com.moneyplanner.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.data.prefs.ThemeMode
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.CategoryType
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.DateField
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.SwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAccounts: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val excelRange by viewModel.excelRange.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val isResetting by viewModel.isResetting.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    var nameText by remember(state.profile.displayName) {
        mutableStateOf(state.profile.displayName)
    }
    var balanceText by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }
    var showDisableLock by remember { mutableStateOf(false) }
    var resetMode by remember { mutableStateOf<ResetMode?>(null) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::restoreBackup) }

    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importCsv) }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::setBackupFolder) }

    // Sharing a file is an explicit user action: the app never sends data anywhere on
    // its own, it hands the file to the system share sheet and the user chooses.
    LaunchedEffect(event) {
        when (val current = event) {
            is SettingsEvent.Message -> {
                snackbarHost.showSnackbar(current.text)
                viewModel.clearEvent()
            }
            is SettingsEvent.ShareFile -> {
                val uri = viewModel.shareableUri(current.file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = current.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, current.file.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Save or send your data"))
                viewModel.clearEvent()
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "You") {
                    LabelledTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = "Your name",
                        imeAction = ImeAction.Done
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.updateName(nameText) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save name") }
                }
            }

            item {
                SectionCard(
                    title = "Your balance",
                    subtitle = "Computed from everything you have recorded"
                ) {
                    Text(
                        IndianFormat.format(state.currentBalance),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(12.dp))
                    AmountField(
                        value = balanceText,
                        onValueChange = { balanceText = it },
                        label = "What is your actual balance?",
                        imeAction = ImeAction.Done
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.setCurrentBalance(balanceText)
                            balanceText = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = balanceText.isNotBlank()
                    ) { Text("Correct my balance") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Your history is kept. The difference is recorded as a correction " +
                            "so the balance can still be explained.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "Planning") {
                    ChipSelector(
                        label = "How far ahead to forecast",
                        options = listOf(3, 6, 12, 24),
                        selected = state.settings.forecastMonths,
                        onSelect = viewModel::setForecastMonths,
                        optionLabel = { "$it months" }
                    )
                    Spacer(Modifier.height(12.dp))
                    ChipSelector(
                        label = "Emergency fund cover",
                        options = listOf(3, 6, 12),
                        selected = state.profile.emergencyFundMonths,
                        onSelect = viewModel::setEmergencyMonths,
                        optionLabel = { "$it months" }
                    )
                }
            }

            item {
                SectionCard(title = "Appearance") {
                    ChipSelector(
                        options = ThemeMode.entries,
                        selected = state.settings.themeMode,
                        onSelect = viewModel::setThemeMode,
                        optionLabel = { mode ->
                            when (mode) {
                                ThemeMode.SYSTEM -> "Follow system"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            }
                        }
                    )
                }
            }

            item {
                SectionCard(title = "Reminders") {
                    SwitchRow(
                        label = "Remind me about payments",
                        checked = state.settings.remindersEnabled,
                        onCheckedChange = viewModel::setRemindersEnabled
                    )
                    if (state.settings.remindersEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ChipSelector(
                            label = "How early",
                            options = listOf(1, 2, 3, 5, 7),
                            selected = state.settings.remindDaysBefore,
                            onSelect = { viewModel.setReminderOption(daysBefore = it) },
                            optionLabel = { if (it == 1) "1 day" else "$it days" }
                        )
                        Spacer(Modifier.height(10.dp))
                        ChipSelector(
                            label = "Time of day",
                            options = listOf(7, 9, 12, 18, 20),
                            selected = state.settings.reminderHour,
                            onSelect = { viewModel.setReminderOption(hour = it) },
                            optionLabel = { hour ->
                                when {
                                    hour == 12 -> "12 noon"
                                    hour < 12 -> "$hour am"
                                    else -> "${hour - 12} pm"
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SwitchRow(
                            label = "EMIs",
                            checked = state.settings.remindAboutEmis,
                            onCheckedChange = { viewModel.setReminderOption(emis = it) }
                        )
                        SwitchRow(
                            label = "Bills and yearly expenses",
                            checked = state.settings.remindAboutBills,
                            onCheckedChange = { viewModel.setReminderOption(bills = it) }
                        )
                        SwitchRow(
                            label = "Credit card dues",
                            checked = state.settings.remindAboutCards,
                            onCheckedChange = { viewModel.setReminderOption(cards = it) }
                        )
                        SwitchRow(
                            label = "Money owed by people",
                            checked = state.settings.remindAboutPeople,
                            onCheckedChange = { viewModel.setReminderOption(people = it) }
                        )
                        SwitchRow(
                            label = "Weekly summary on Monday",
                            checked = state.settings.weeklySummary,
                            onCheckedChange = { viewModel.setReminderOption(weeklySummary = it) }
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Security") {
                    if (state.settings.hasPin) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(text = "App lock is on")
                            TextButton(onClick = { showDisableLock = true }) { Text("Turn off") }
                        }
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            label = "Unlock with fingerprint or face",
                            checked = state.settings.biometricEnabled,
                            onCheckedChange = viewModel::setBiometricEnabled,
                            supporting = "Your PIN still works as a fallback"
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { showPinDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Change PIN") }
                    } else {
                        Text(
                            "Lock the app with a PIN so your financial records are not open " +
                                "to anyone who picks up your phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        PrimaryActionButton(
                            text = "Set up app lock",
                            onClick = { showPinDialog = true }
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = "Your data",
                    subtitle = "Everything stays on this device unless you share it"
                ) {
                    OutlinedButton(
                        onClick = viewModel::exportBackup,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create a backup file") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::startExcelExport,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export transactions to Excel") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::exportCsv,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export expenses as CSV") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::exportPdf,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export this month to PDF") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Import expenses from CSV") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Restore from a backup") }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Restoring replaces everything currently in the app. Nothing is " +
                            "uploaded anywhere at any point.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(
                    title = "Automatic backup",
                    subtitle = "Because this app is the only copy of your records"
                ) {
                    if (state.settings.backupFolderUri == null) {
                        Text(
                            "Pick a folder and a copy will be written there every week. " +
                                "Without it, losing this phone means losing your history.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        PrimaryActionButton(
                            text = "Choose a backup folder",
                            onClick = { folderLauncher.launch(null) }
                        )
                    } else {
                        SwitchRow(
                            label = "Back up weekly",
                            checked = state.settings.autoBackupEnabled,
                            onCheckedChange = viewModel::setAutoBackupEnabled,
                            supporting = state.backupFolderName
                                ?.let { "Saving to $it" }
                                ?: "Saving to the folder you chose"
                        )
                        state.settings.lastBackupEpochDay?.let { day ->
                            Text(
                                "Last backup " + DateUtil.formatDate(
                                    java.time.LocalDate.ofEpochDay(day)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = viewModel::backupNow,
                                modifier = Modifier.weight(1f)
                            ) { Text("Back up now") }
                            OutlinedButton(
                                onClick = { folderLauncher.launch(null) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Change folder") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The newest " + state.settings.backupKeepCount +
                                " backups are kept; older ones are removed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = "Accounts",
                    modifier = Modifier.clickable(onClick = onOpenAccounts)
                ) {
                    Text(
                        "Your bank accounts, cash and wallets, what each one holds, and " +
                            "moving money between them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(
                    title = "Categories",
                    modifier = Modifier.clickable(onClick = onOpenCategories)
                ) {
                    Text(
                        "Add your own categories or change which ones count as essential.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "Start again") {
                    Text(
                        "Two different things, kept apart on purpose so neither happens by " +
                            "accident.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { resetMode = ResetMode.RECORDS },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Clear all my records") }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Deletes every expense, income, loan, bill, person and goal, then " +
                            "puts the default categories and one account back. Your PIN, " +
                            "theme and reminders are left alone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { resetMode = ResetMode.EVERYTHING },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Reset the app completely") }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Everything above, plus your PIN, theme, reminders and backup " +
                            "folder. The app returns to how it was the day you installed it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "About") {
                    Text(
                        "Personal Money Planner works entirely offline. It has no internet " +
                            "permission, no account, and no analytics. Your financial " +
                            "records never leave this device unless you export them yourself.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                viewModel.setPin(pin)
                showPinDialog = false
            }
        )
    }

    resetMode?.let { mode ->
        ResetDialog(
            mode = mode,
            isWorking = isResetting,
            onBackup = viewModel::exportBackup,
            onConfirm = {
                viewModel.performReset(mode)
                resetMode = null
            },
            onDismiss = { resetMode = null }
        )
    }

    if (showDisableLock) {
        ConfirmDialog(
            title = "Turn off app lock?",
            message = "Anyone with your phone will be able to open your financial records.",
            confirmLabel = "Turn off",
            onConfirm = viewModel::disableAppLock,
            onDismiss = { showDisableLock = false }
        )
    }

    if (excelRange.isOpen) {
        ExcelExportDialog(range = excelRange, viewModel = viewModel)
    }
}

/**
 * Choosing what period to export.
 *
 * The presets carry the common answers, because "last month" is what somebody filing
 * something usually wants and picking two dates by hand to express it is work the app can
 * do for them. The exact dates stay editable underneath for everything else.
 */
@Composable
private fun ExcelExportDialog(
    range: ExcelExportRange,
    viewModel: SettingsViewModel
) {
    AlertDialog(
        onDismissRequest = viewModel::dismissExcelExport,
        shape = MaterialTheme.shapes.large,
        title = { Text("Export to Excel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "One sheet per kind: expenses, income, settlements, transfers, " +
                        "savings and card bills. Amounts come through as numbers and " +
                        "dates as dates, so you can sum and filter them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ChipSelector(
                    label = "Period",
                    options = ExcelRangePreset.entries,
                    selected = null,
                    onSelect = viewModel::useExcelPreset,
                    optionLabel = { it.label }
                )

                DateField(
                    date = range.from,
                    onDateChange = viewModel::updateExcelFrom,
                    label = "From"
                )
                DateField(
                    date = range.to,
                    onDateChange = viewModel::updateExcelTo,
                    label = "To"
                )

                if (!range.isValid) {
                    Text(
                        "The end date is before the start date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::exportExcel, enabled = range.isValid) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissExcelExport) { Text("Cancel") }
        }
    )
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val mismatch = confirm.isNotEmpty() && pin != confirm
    val valid = pin.length >= 4 && pin == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Use at least four digits. The PIN itself is never stored, only a " +
                        "scrambled form of it that cannot be reversed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    )
                )
                androidx.compose.material3.OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    supportingText = if (mismatch) {
                        { Text("The PINs do not match", color = MaterialTheme.colorScheme.error) }
                    } else {
                        null
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = valid) { Text("Set PIN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Which kind of reset the user picked. Kept apart so neither can be reached by accident. */
enum class ResetMode { RECORDS, EVERYTHING }

/**
 * The last stop before data is destroyed.
 *
 * A reset is the only action in the app with nothing behind it: no undo, and the records
 * cannot be rebuilt from anywhere else. So it asks for the word to be typed rather than
 * accepting a tap, and offers to write a backup first, because someone who resets by
 * mistake has lost years of entries and a single confirm button is not enough friction to
 * sit in front of that.
 */
@Composable
private fun ResetDialog(
    mode: ResetMode,
    isWorking: Boolean,
    onBackup: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val phrase = if (mode == ResetMode.RECORDS) "CLEAR" else "RESET"
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                if (mode == ResetMode.RECORDS) "Clear all your records?"
                else "Reset the app completely?"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (mode == ResetMode.RECORDS) {
                        "Every expense, income, loan, bill, person, goal and account " +
                            "movement will be deleted. This cannot be undone."
                    } else {
                        "Every record will be deleted, and your PIN, theme, reminders and " +
                            "backup folder will be forgotten. This cannot be undone."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedButton(
                    onClick = onBackup,
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save a backup first") }

                Text(
                    "Type $phrase to confirm",
                    style = MaterialTheme.typography.labelLarge
                )
                LabelledTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = phrase,
                    enabled = !isWorking,
                    imeAction = ImeAction.Done
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = typed.trim().equals(phrase, ignoreCase = true) && !isWorking
            ) {
                Text(
                    if (mode == ResetMode.RECORDS) "Clear records" else "Reset everything",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isWorking) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add category")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp)
        ) {
            item {
                Text(
                    "Essential categories count towards your emergency fund target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            item { SectionHeader("Spending") }
            items(state.expense, key = { "e-${it.id}" }) { category ->
                CategoryRow(
                    category = category,
                    onToggleEssential = { viewModel.toggleEssential(category) },
                    onDelete = { pendingDelete = category },
                    onRestore = { viewModel.restore(category) }
                )
            }

            item { SectionHeader("Income") }
            items(state.income, key = { "i-${it.id}" }) { category ->
                CategoryRow(
                    category = category,
                    onToggleEssential = { viewModel.toggleEssential(category) },
                    onDelete = { pendingDelete = category },
                    onRestore = { viewModel.restore(category) }
                )
            }
        }
    }

    if (showAdd) {
        AddCategoryDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, type, essential ->
                viewModel.add(name, type, essential)
                showAdd = false
            }
        )
    }

    pendingDelete?.let { category ->
        ConfirmDialog(
            title = "Remove ${category.name}?",
            message = "If any expenses use it, it will be hidden instead of deleted so your " +
                "history keeps its labels.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.remove(category) },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun CategoryRow(
    category: Category,
    onToggleEssential: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (category.isArchived) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (category.isArchived) {
                    StatusPill(text = "Hidden")
                    Spacer(Modifier.width(6.dp))
                }
                if (category.isCustom) {
                    StatusPill(text = "Yours")
                    Spacer(Modifier.width(6.dp))
                }
                if (category.type == CategoryType.EXPENSE) {
                    TextButton(onClick = onToggleEssential) {
                        Text(if (category.isEssential) "Essential" else "Not essential")
                    }
                }
            }
        }
        if (category.isArchived) {
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.Restore, contentDescription = "Restore category")
            }
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove category")
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, CategoryType, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CategoryType.EXPENSE) }
    var essential by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabelledTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name"
                )
                ChipSelector(
                    label = "Type",
                    options = CategoryType.entries,
                    selected = type,
                    onSelect = { type = it },
                    optionLabel = { if (it == CategoryType.EXPENSE) "Spending" else "Income" }
                )
                if (type == CategoryType.EXPENSE) {
                    SwitchRow(
                        label = "Essential",
                        checked = essential,
                        onCheckedChange = { essential = it },
                        supporting = "Counts towards your emergency fund"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, type, essential) },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
