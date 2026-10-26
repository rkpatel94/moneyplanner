package com.moneyplanner.ui.screens.expenses

import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.moneyplanner.core.money.IndianNumberWords
import com.moneyplanner.domain.model.Category
import com.moneyplanner.ui.components.softShadow
import com.moneyplanner.ui.theme.Inter
import com.moneyplanner.ui.theme.PillLabelStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.DropdownField
import com.moneyplanner.ui.components.InitialsAvatar
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.theme.MoneyTheme
import com.moneyplanner.ui.util.categoryIcon

/**
 * Adding or editing a single expense.
 *
 * The amount is the hero, typed on the app's own keypad at the bottom of the screen, so the
 * most common entry is a few taps and a save with no system keyboard covering the form. The
 * figure is spelled out in words beneath it, because an extra zero is the slip that matters
 * and words make it obvious. Everything below the amount is optional, and the save button,
 * which repeats the figure, stays pinned in the thumb zone.
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
    // Up whenever the amount is being typed: from the start for a new expense, and on a tap
    // of the amount when correcting one. A text field taking focus puts it away again.
    var keypadUp by rememberSaveable { mutableStateOf(!form.isEditing) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val colors = MoneyTheme.colors

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

    val hideKeypadOnFocus = Modifier.onFocusChanged { if (it.isFocused) keypadUp = false }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EditorTopBar(
                isEditing = form.isEditing,
                onClose = onBack,
                onVoice = { startVoiceEntry() },
                onDelete = { showDeleteDialog = true }
            )
        },
        bottomBar = {
            // Anchored so the keypad and the primary action never scroll out of the thumb
            // zone, and lifted above the system keyboard when a text field is in use.
            val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .softShadow(shape, elevation = 8.dp)
                    .background(colors.card, shape)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                if (keypadUp) {
                    AmountKeypad(
                        onKey = { key -> viewModel.updateAmount(typedAmountAfter(form.amountText, key)) },
                        onBackspace = { viewModel.updateAmount(form.amountText.dropLast(1)) }
                    )
                }
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
                    PrimaryActionButton(
                        // The figure rides on the button, in the second before it is saved,
                        // which is when a mistyped digit is easiest to catch.
                        text = buildString {
                            append(if (form.isEditing) "Save changes" else "Save expense")
                            form.parsedAmount?.let {
                                append(" · ")
                                append(IndianFormat.format(it))
                            }
                        },
                        onClick = { viewModel.save(onBack) },
                        enabled = form.canSave,
                        trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = colors.positive,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Saved offline on this phone · Nothing is uploaded",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textTertiary
                        )
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                typed = form.amountText,
                parsed = form.parsedAmount,
                error = form.amountError,
                active = keypadUp,
                onActivate = {
                    focusManager.clearFocus()
                    keypadUp = true
                }
            )

            DescriptionField(
                value = form.description,
                onValueChange = viewModel::updateDescription,
                modifier = hideKeypadOnFocus
            )

            DateCard(date = form.date, onDateChange = viewModel::updateDate)

            CategoryPicker(
                categories = options.categories,
                selected = form.categoryId,
                onSelect = viewModel::selectCategory,
                error = form.categoryError
            )

            PaymentCard(
                method = form.paymentMethod,
                accountId = form.accountId,
                creditCardId = form.creditCardId,
                options = options,
                onSelectMethod = viewModel::selectPaymentMethod,
                onSelectAccount = viewModel::selectAccount,
                onSelectCard = viewModel::selectCreditCard
            )

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
                                                .border(2.dp, colors.accent, CircleShape)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    person.name.take(8),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) colors.textPrimary else colors.textSecondary
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
                        imeAction = ImeAction.Done,
                        modifier = hideKeypadOnFocus
                    )
                    Spacer(Modifier.height(12.dp))
                    LabelledTextField(
                        value = form.tagsText,
                        onValueChange = viewModel::updateTags,
                        label = "Tags (separate with commas)",
                        imeAction = ImeAction.Done,
                        modifier = hideKeypadOnFocus
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
 * The amount as typed after one more key, following the rules a rupee figure has: one
 * decimal point, paise to two places, no leading zero, and no more than nine digits of
 * rupees, which is ₹99,99,99,999 and more than any household expense.
 */
internal fun typedAmountAfter(current: String, key: Char): String {
    if (key == '.') {
        return when {
            current.contains('.') -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
    }
    if (!key.isDigit()) return current
    if (current.contains('.')) {
        return if (current.substringAfter('.').length >= 2) current else current + key
    }
    if (current == "0") return key.toString()
    return if (current.length >= 9) current else current + key
}

@Composable
private fun EditorTopBar(
    isEditing: Boolean,
    onClose: () -> Unit,
    onVoice: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MoneyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary)
        }
        Text(
            if (isEditing) "Edit expense" else "Add expense",
            style = MaterialTheme.typography.titleLarge,
            color = colors.display,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        if (isEditing) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete expense", tint = colors.negative)
            }
        } else {
            val shape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(shape)
                    .background(colors.accentSoft)
                    .border(1.dp, colors.accent, shape)
                    .clickable(onClickLabel = "Add with voice", onClick = onVoice)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "Add with voice" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = colors.warning, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("VOICE", style = PillLabelStyle, color = colors.onWarningContainer)
            }
        }
    }
}

/**
 * The amount, at display size, with the figure spelled out beneath it. Tapping it brings
 * the keypad back; a marigold caret blinks while the keypad is writing into it.
 */
@Composable
private fun AmountHeroCard(
    typed: String,
    parsed: Money?,
    error: String?,
    active: Boolean,
    onActivate: () -> Unit
) {
    val colors = MoneyTheme.colors
    val caretAlpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "caret-alpha"
    )
    val whole = typed.substringBefore('.').toLongOrNull() ?: 0L
    val shown = if (typed.isEmpty()) "0" else IndianFormat.groupDigits(whole) +
        (if (typed.contains('.')) "." + typed.substringAfter('.') else "")

    SectionCard(contentPadding = 22.dp, onClick = onActivate) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = if (parsed != null && parsed.isPositive) {
                        "Amount, " + IndianNumberWords.rupees(parsed)
                    } else {
                        "Amount, nothing entered yet"
                    }
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 0.85.em)) {
                        append(IndianFormat.RUPEE)
                    }
                    append(shown)
                },
                style = MaterialTheme.typography.displayLarge,
                color = if (typed.isEmpty()) colors.textTertiary else colors.display,
                maxLines = 1
            )
            if (active) {
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .size(width = 3.dp, height = 44.dp)
                        .background(colors.accent.copy(alpha = caretAlpha), RoundedCornerShape(2.dp))
                )
            }
        }
        if (parsed != null && parsed.isPositive) {
            Spacer(Modifier.height(6.dp))
            Text(
                IndianNumberWords.rupees(parsed),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            IndianNumberWords.scale(parsed)?.let { scale ->
                Spacer(Modifier.height(2.dp))
                Text(
                    scale.uppercase(),
                    style = PillLabelStyle,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else if (!active) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap to enter the amount",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
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

/** The app's own number pad: digits, a decimal point and a delete key, each a soft well. */
@Composable
private fun AmountKeypad(onKey: (Char) -> Unit, onBackspace: () -> Unit) {
    val colors = MoneyTheme.colors
    val keys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', BACKSPACE)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val isBackspace = key == BACKSPACE
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isBackspace) colors.cardElevated else colors.sunken)
                            .clickable(role = Role.Button) {
                                if (isBackspace) onBackspace() else onKey(key)
                            }
                            .semantics {
                                contentDescription = when (key) {
                                    BACKSPACE -> "Delete last digit"
                                    '.' -> "Decimal point"
                                    else -> key.toString()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBackspace) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(
                                key.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val BACKSPACE = '\b'

/** What the money was for, in a soft well rather than a boxed field. */
@Composable
private fun DescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MoneyTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done
        ),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Description" },
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.sunken, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Notes,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            "What was it for?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textTertiary
                        )
                    }
                    inner()
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateCard(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val colors = MoneyTheme.colors
    var picking by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(shape)
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.cardBorder, shape)
            .clickable(onClickLabel = "Change the date") { picking = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("EXPENSE DATE", style = PillLabelStyle, color = colors.textTertiary)
            Text(
                DateUtil.formatWeekdayDate(date),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }
        Icon(Icons.Default.ExpandMore, contentDescription = null, tint = colors.textSecondary)
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    picking = false
                }) { Text("Select") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { picking = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * Categories as two rows of chips that scroll sideways together, each with its icon. Two
 * rows show twice as many choices at once without the list taking over the screen.
 */
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selected: Long?,
    onSelect: (Long) -> Unit,
    error: String?
) {
    val colors = MoneyTheme.colors
    // Alternating between the rows keeps the usual order running down the first columns,
    // so the most used categories are all on screen before any scrolling.
    val rows = listOf(
        categories.filterIndexed { index, _ -> index % 2 == 0 },
        categories.filterIndexed { index, _ -> index % 2 == 1 }
    )

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CATEGORY", style = PillLabelStyle, color = colors.textTertiary, modifier = Modifier.weight(1f))
            Text(
                "${categories.size} to choose from",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.forecast
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { category ->
                        val chosen = category.id == selected
                        val shape = RoundedCornerShape(50)
                        Row(
                            modifier = Modifier
                                .clip(shape)
                                .background(if (chosen) colors.accent else colors.card)
                                .then(if (chosen) Modifier else Modifier.border(1.dp, colors.cardBorder, shape))
                                .selectable(selected = chosen, role = Role.RadioButton) { onSelect(category.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                categoryIcon(category.name),
                                contentDescription = null,
                                tint = if (chosen) colors.onAccent else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                category.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (chosen) colors.onAccent else colors.textPrimary
                            )
                        }
                    }
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Where the money came from and how it was paid.
 *
 * The account is only asked for when money actually leaves one. A card purchase touches no
 * account until the card bill is paid, so it asks which card instead and says why the bank
 * balance does not move today.
 */
@Composable
private fun PaymentCard(
    method: PaymentMethod,
    accountId: Long?,
    creditCardId: Long?,
    options: ExpenseEditorOptions,
    onSelectMethod: (PaymentMethod) -> Unit,
    onSelectAccount: (Long?) -> Unit,
    onSelectCard: (Long?) -> Unit
) {
    val colors = MoneyTheme.colors
    val onCard = method == PaymentMethod.CREDIT_CARD

    SectionCard {
        val showsSource = if (onCard) options.creditCards.isNotEmpty() else options.accounts.isNotEmpty()
        if (onCard && options.creditCards.isNotEmpty()) {
            SourceChooser(
                caption = "CHARGED TO",
                icon = Icons.Default.CreditCard,
                title = options.creditCards.firstOrNull { it.id == creditCardId }?.let { card ->
                    card.lastFourDigits?.let { "${card.name} ···· $it" } ?: card.name
                } ?: "Card not specified",
                tag = "CREDIT CARD",
                choices = listOf<Pair<Long?, String>>(null to "Not specified") +
                    options.creditCards.map { card ->
                        card.id to (card.lastFourDigits?.let { "${card.name} ···· $it" } ?: card.name)
                    },
                onChoose = onSelectCard
            )
        } else if (!onCard && options.accounts.isNotEmpty()) {
            val account = options.accounts.firstOrNull { it.id == accountId }
            SourceChooser(
                caption = "PAID FROM",
                icon = Icons.Default.AccountBalanceWallet,
                title = account?.name ?: "Account not specified",
                tag = account?.type?.label?.uppercase(),
                choices = listOf<Pair<Long?, String>>(null to "Not specified") +
                    options.accounts.map { it.id to it.name },
                onChoose = onSelectAccount
            )
        }
        if (showsSource) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.borderSubtle)
            Spacer(Modifier.height(14.dp))
        }

        Text("PAYMENT METHOD", style = PillLabelStyle, color = colors.textTertiary)
        Spacer(Modifier.height(8.dp))
        PaymentSegments(selected = method, onSelect = onSelectMethod)

        if (onCard) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = colors.forecast,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (options.creditCards.isEmpty()) {
                        "No cards added yet. You can record this now and add the card " +
                            "later under More, then Credit cards."
                    } else {
                        "This does not leave your bank balance today. Cash goes out " +
                            "when you pay the card bill."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.forecast
                )
            }
        }
    }
}

@Composable
private fun SourceChooser(
    caption: String,
    icon: ImageVector,
    title: String,
    tag: String?,
    choices: List<Pair<Long?, String>>,
    onChoose: (Long?) -> Unit
) {
    val colors = MoneyTheme.colors
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClickLabel = "Change") { open = true }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.forecastContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.forecast, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(caption, style = PillLabelStyle, color = colors.textTertiary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1
                )
                if (tag != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tag,
                        style = PillLabelStyle,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .background(colors.sunken, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(Icons.Default.SwapHoriz, contentDescription = "Change", tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onChoose(id)
                        open = false
                    }
                )
            }
        }
    }
}

/**
 * The three ways most money is paid, as segments, with the rest one tap away under More.
 * A chosen "more" method takes the fourth segment's place, so the choice is always visible.
 */
@Composable
private fun PaymentSegments(selected: PaymentMethod, onSelect: (PaymentMethod) -> Unit) {
    val colors = MoneyTheme.colors
    val main = listOf(PaymentMethod.UPI, PaymentMethod.CASH, PaymentMethod.CREDIT_CARD)
    val others = PaymentMethod.entries - main.toSet()
    var moreOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.sunken, RoundedCornerShape(50))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        main.forEach { method ->
            PaymentSegment(
                label = if (method == PaymentMethod.CREDIT_CARD) "Card" else method.label,
                icon = paymentIcon(method),
                selected = selected == method,
                onClick = { onSelect(method) },
                modifier = Modifier.weight(1f)
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            val otherChosen = selected in others
            PaymentSegment(
                label = if (otherChosen) selected.label else "More",
                icon = if (otherChosen) paymentIcon(selected) else Icons.Default.ExpandMore,
                selected = otherChosen,
                onClick = { moreOpen = true }
            )
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                others.forEach { method ->
                    DropdownMenuItem(
                        text = { Text(method.label) },
                        leadingIcon = { Icon(paymentIcon(method), contentDescription = null) },
                        onClick = {
                            onSelect(method)
                            moreOpen = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MoneyTheme.colors
    val shape = RoundedCornerShape(50)
    val ink = if (selected) MaterialTheme.colorScheme.primary else colors.textSecondary
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.card else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun paymentIcon(method: PaymentMethod): ImageVector = when (method) {
    PaymentMethod.UPI -> Icons.Default.QrCode2
    PaymentMethod.CASH -> Icons.Default.Payments
    PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD -> Icons.Default.CreditCard
    PaymentMethod.BANK_TRANSFER -> Icons.Default.AccountBalance
    PaymentMethod.AUTO_DEBIT -> Icons.Default.Autorenew
    PaymentMethod.CHEQUE -> Icons.Default.EditNote
    PaymentMethod.WALLET -> Icons.Default.AccountBalanceWallet
    PaymentMethod.OTHER -> Icons.Default.MoreHoriz
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
