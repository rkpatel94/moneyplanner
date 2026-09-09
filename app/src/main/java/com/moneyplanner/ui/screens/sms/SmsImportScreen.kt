package com.moneyplanner.ui.screens.sms

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.theme.MoneyTheme
import kotlinx.coroutines.launch

/**
 * Importing bank alerts by pasting them.
 *
 * The app does not read the message inbox and asks for no SMS permission. Pasting costs a
 * copy and a tap, works on any phone, and keeps the promise that an app holding this much
 * financial detail never asks to read your messages.
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
    var pasted by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Import from a message") },
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
                        text = "Import ${state.selectedCount} " +
                            if (state.selectedCount == 1) "entry" else "entries",
                        onClick = {
                            viewModel.importSelected { count ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (count == 1) "1 entry added"
                                        else "$count entries added"
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
                SectionCard(
                    title = "Paste a bank message",
                    subtitle = "Several at once is fine, with a blank line between them"
                ) {
                    LabelledTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        label = "Message text",
                        singleLine = false,
                        imeAction = ImeAction.Default
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryActionButton(
                            text = "Read it",
                            onClick = {
                                viewModel.parsePasted(pasted)
                                pasted = ""
                            },
                            enabled = pasted.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        )
                        if (state.rows.isNotEmpty()) {
                            TextButton(onClick = viewModel::clearAll) { Text("Start over") }
                        }
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

                    if (!state.hasParsed) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Nothing is saved until you have looked at it. The app reads " +
                                "the amount, the date and where the money went, and asks " +
                                "you to confirm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

            items(state.rows, key = { "sms-${it.candidate.smsId}" }) { row ->
                SmsRowCard(
                    row = row,
                    categories = state.categories,
                    onToggle = { viewModel.toggle(row.candidate.smsId) },
                    onCategory = { viewModel.setCategory(row.candidate.smsId, it) },
                    onRemove = { viewModel.remove(row.candidate.smsId) }
                )
            }
        }
    }
}

@Composable
private fun SmsRowCard(
    row: SmsImportRow,
    categories: List<com.moneyplanner.domain.model.Category>,
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
                    DateUtil.formatDayMonth(parsed.date ?: row.candidate.receivedOn),
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

        // Says how sure it is rather than a flat "duplicate", because the answer ranges
        // from certain to a hunch and the user is the one deciding.
        row.duplicate.label?.let { label ->
            Spacer(Modifier.height(8.dp))
            StatusPill(
                text = label,
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
