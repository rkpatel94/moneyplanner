package com.moneyplanner.ui.screens.expenses

import android.content.Intent
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.DateField
import com.moneyplanner.ui.components.DropdownField
import com.moneyplanner.ui.components.IconWash
import com.moneyplanner.ui.components.InitialsAvatar
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.theme.Elevation
import com.moneyplanner.ui.theme.MoneyTheme
import com.moneyplanner.ui.util.categoryIcon

/**
 * Adding or editing a single expense.
 *
 * The amount is the hero: it opens focused on a numeric pad at display size, so the most
 * common entry is a few taps and a save. Everything below it is optional. The save button
 * is pinned to the bottom of the screen inside the thumb zone rather than sitting at the
 * end of a scroll, which is what keeps a routine entry under ten seconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(
    onBack: () -> Unit,
    viewModel: ExpenseEditorViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val context = LocalContext.current

    // Speech is handled by the system recogniser through an intent, so this app needs no
    // RECORD_AUDIO permission and no network access of its own.
    val voiceAvailable = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .resolveActivity(context.packageManager) != null
    }
    var voiceUnavailableShown by remember { mutableStateOf(false) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) viewModel.applySpoken(spoken)
    }

    // OpenDocument grants persistent access to one selected receipt, rather than broad
    // photo/storage access. It accepts images and PDFs because both are common receipts.
    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: "Receipt"
        val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
        viewModel.addAttachment(uri.toString(), name, type)
    }

    fun startVoiceEntry() {
        if (!voiceAvailable) {
            voiceUnavailableShown = true
            return
        }
        voiceLauncher.launch(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Say the amount and what it was for"
                )
            }
        )
    }

    LaunchedEffect(form.isEditing) {
        if (!form.isEditing) amountFocus.requestFocus()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditing) "Edit expense" else "Add expense") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (form.isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete expense")
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
            // Anchored so the primary action never scrolls out of the thumb zone.
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
                        text = if (form.isEditing) "Save changes" else "Save expense",
                        onClick = { viewModel.save(onBack) },
                        enabled = form.canSave
                    )
                    if (!form.isEditing) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { startVoiceEntry() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add with voice")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (form.isLinked) {
                StatusPill(text = "Created by a scheduled payment")
            }

            form.heardText?.let { heard ->
                HeardCard(
                    heard = heard,
                    summary = form.heardSummary.orEmpty(),
                    nothingUsable = form.heardNothingUsable,
                    onDismiss = viewModel::dismissHeard
                )
            }

            AmountHeroCard(
                value = form.amountText,
                onValueChange = viewModel::updateAmount,
                error = form.amountError,
                focusRequester = amountFocus
            )

            SectionCard {
                LabelledTextField(
                    value = form.description,
                    onValueChange = viewModel::updateDescription,
                    label = "Description",
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                ChipSelector(
                    options = options.categories.map { it.id },
                    selected = form.categoryId,
                    onSelect = viewModel::selectCategory,
                    optionLabel = { id -> options.categories.first { it.id == id }.name }
                )
                if (form.categoryError != null) {
                    Text(
                        form.categoryError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(14.dp))
                DateField(date = form.date, onDateChange = viewModel::updateDate)

                Spacer(Modifier.height(12.dp))
                DropdownField(
                    value = form.paymentMethod,
                    options = PaymentMethod.entries,
                    onSelect = viewModel::selectPaymentMethod,
                    label = "Paid by",
                    optionLabel = { it.label }
                )
                // Only asked for when the money actually leaves an account. A card
                // purchase does not touch one until the card bill is paid, so offering the
                // choice there would invite an answer the balance must then ignore.
                if (options.accounts.isNotEmpty() &&
                    form.paymentMethod != PaymentMethod.CREDIT_CARD
                ) {
                    Spacer(Modifier.height(12.dp))
                    DropdownField(
                        value = form.accountId,
                        options = listOf<Long?>(null) + options.accounts.map { it.id },
                        onSelect = viewModel::selectAccount,
                        label = "Paid from",
                        optionLabel = { id ->
                            if (id == null) "Not specified"
                            else options.accounts.first { it.id == id }.name
                        }
                    )
                }
                if (form.paymentMethod == PaymentMethod.CREDIT_CARD) {
                    if (options.creditCards.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        DropdownField(
                            value = form.creditCardId,
                            options = listOf<Long?>(null) + options.creditCards.map { it.id },
                            onSelect = viewModel::selectCreditCard,
                            label = "Which card",
                            optionLabel = { id ->
                                if (id == null) {
                                    "Not specified"
                                } else {
                                    val card = options.creditCards.first { it.id == id }
                                    card.lastFourDigits
                                        ?.let { digits -> "${card.name} ···· $digits" }
                                        ?: card.name
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (options.creditCards.isEmpty()) {
                            "No cards added yet. You can record this now and add the card " +
                                "later under More, then Credit cards."
                        } else {
                            "This does not leave your bank balance today. Cash goes out " +
                                "when you pay the card bill."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (options.people.isNotEmpty()) {
                SectionCard(title = "Person involved") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        options.people.take(4).forEach { person ->
                            val selected = form.personId == person.id
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    viewModel.selectPerson(if (selected) null else person.id)
                                }
                            ) {
                                Box {
                                    InitialsAvatar(person.name, size = 44.dp)
                                    if (selected) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    person.name.take(8),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Text(
                if (showMore) "Fewer details" else "More details",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { showMore = !showMore }
                    .padding(vertical = 4.dp)
            )

            if (showMore) {
                SectionCard {
                    if (options.familyMembers.isNotEmpty()) {
                        DropdownField(
                            value = form.familyMemberId,
                            options = listOf<Long?>(null) + options.familyMembers.map { it.id },
                            onSelect = viewModel::selectFamilyMember,
                            label = "Spent on",
                            optionLabel = { id ->
                                if (id == null) "Not specified"
                                else options.familyMembers.first { it.id == id }.name
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (options.vehicles.isNotEmpty()) {
                        DropdownField(
                            value = form.vehicleId,
                            options = listOf<Long?>(null) + options.vehicles.map { it.id },
                            onSelect = viewModel::selectVehicle,
                            label = "Vehicle",
                            optionLabel = { id ->
                                if (id == null) "Not related to a vehicle"
                                else options.vehicles.first { it.id == id }.name
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    LabelledTextField(
                        value = form.notes,
                        onValueChange = viewModel::updateNotes,
                        label = "Notes",
                        singleLine = false,
                        imeAction = ImeAction.Done
                    )
                    Spacer(Modifier.height(12.dp))
                    LabelledTextField(
                        value = form.tagsText,
                        onValueChange = viewModel::updateTags,
                        label = "Tags (separate with commas)",
                        imeAction = ImeAction.Done
                    )
                    if (form.isEditing) {
                        Spacer(Modifier.height(16.dp))
                        Text("Receipt attachments", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Attach a photo or PDF. The original stays where you selected it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { attachmentLauncher.launch(arrayOf("image/*", "application/pdf")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Attach receipt")
                        }
                        attachments.forEach { attachment ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(attachment.displayName, modifier = Modifier.weight(1f), maxLines = 1)
                                IconButton(onClick = { viewModel.deleteAttachment(attachment.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove attachment")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (voiceUnavailableShown) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { voiceUnavailableShown = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("Voice input is not available") },
            text = {
                Text(
                    "This phone has no speech recogniser installed, so voice entry cannot " +
                        "be used. You can still type the expense in normally."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { voiceUnavailableShown = false }
                ) { Text("Got it") }
            }
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete this expense?",
            message = "It will be removed from your records and your balance will be " +
                "recalculated without it.",
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

/**
 * The amount, entered at display size.
 *
 * The rupee sign sits alongside the field at a lighter weight so the eye lands on the
 * number rather than the currency marker, which is the framework's rule for money.
 */
@Composable
private fun AmountHeroCard(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    focusRequester: FocusRequester
) {
    SectionCard(contentPadding = 24.dp) {
        Text(
            "Amount",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "₹",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1) onValueChange(filtered)
                },
                modifier = Modifier.focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.displayMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "0",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        inner()
                    }
                }
            )
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * What the recogniser heard, and what the app made of it.
 *
 * Speech gets numbers wrong often enough that showing the raw sentence back is the whole
 * safety mechanism here: the user sees "one five hundred" became ₹1,500 and can correct
 * it before anything is written.
 */
@Composable
private fun HeardCard(
    heard: String,
    summary: String,
    nothingUsable: Boolean,
    onDismiss: () -> Unit
) {
    val colors = MoneyTheme.colors
    SectionCard(
        containerColor = if (nothingUsable) {
            colors.warningContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = if (nothingUsable) colors.onWarningContainer
                else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Heard",
                style = MaterialTheme.typography.labelMedium,
                color = if (nothingUsable) colors.onWarningContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = if (nothingUsable) colors.onWarningContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Text(
            "“$heard”",
            style = MaterialTheme.typography.bodyLarge,
            color = if (nothingUsable) colors.onWarningContainer
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (nothingUsable) {
                "No amount was recognised. Say the amount first, for example “500 lunch”."
            } else {
                summary
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (nothingUsable) colors.onWarningContainer
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Check it before saving.",
            style = MaterialTheme.typography.bodySmall,
            color = if (nothingUsable) colors.onWarningContainer
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
