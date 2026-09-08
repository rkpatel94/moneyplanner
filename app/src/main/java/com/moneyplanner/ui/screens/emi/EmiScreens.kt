package com.moneyplanner.ui.screens.emi

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.DateField
import com.moneyplanner.ui.components.DropdownField
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.GoalProgressBar
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.LoadingState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.components.SwitchRow
import com.moneyplanner.ui.theme.MoneyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmiDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: EmiDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    val colors = MoneyTheme.colors

    if (state.isLoading) {
        LoadingState()
        return
    }
    val emi = state.emi ?: run {
        EmptyState(
            icon = Icons.Default.AccountBalance,
            title = "Loan not found",
            message = "This loan may have been deleted."
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(emi.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(emi.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit loan")
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete loan")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard {
                    Text(
                        "Still to repay",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(state.outstanding, style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(12.dp))
                    GoalProgressBar(
                        fraction = if (emi.totalInstallments == 0) 0f
                        else state.paidInstallments.toFloat() / emi.totalInstallments,
                        label = "${state.paidInstallments} of ${emi.totalInstallments} installments paid"
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.paidInstallments} paid · ${state.remainingInstallments} remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.nextInstallment != null) {
                        Spacer(Modifier.height(12.dp))
                        state.nextDueDate?.let {
                            Text(
                                "Installment ${state.nextInstallment} is due on ${DateUtil.formatDate(it)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        PrimaryActionButton(
                            text = "Record ${IndianFormat.format(emi.emiAmount)} paid",
                            onClick = viewModel::payNextInstallment
                        )
                    } else {
                        Spacer(Modifier.height(12.dp))
                        StatusPill(
                            text = "This loan is fully repaid",
                            containerColor = colors.positiveContainer,
                            contentColor = colors.positive
                        )
                    }
                }
            }

            state.prepayment?.let { prepay ->
                item { PrepaymentCard(prepay, viewModel) }
            }

            item {
                SectionCard(title = "Loan details") {
                    SummaryRow("Installment", emi.emiAmount)
                    if (emi.principal.isPositive) SummaryRow("Amount borrowed", emi.principal)
                    SummaryRow("Total payable", state.totalPayable)
                    if (state.totalInterest.isPositive) {
                        SummaryRow("Interest over the loan", state.totalInterest)
                    }
                    emi.interestRatePercent?.let {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Interest rate", style = MaterialTheme.typography.bodyMedium)
                            Text("$it%", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (emi.accountReference.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Loan account", style = MaterialTheme.typography.bodyMedium)
                            Text(emi.accountReference, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            item {
                Text(
                    "Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(state.schedule, key = { it.installmentNumber }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (row.isPaid) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (row.isPaid) colors.positive else MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Installment ${row.installmentNumber}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            buildString {
                                append("Due ${DateUtil.formatDate(row.dueDate)}")
                                row.paidDate?.let { append(" · paid ${DateUtil.formatDayMonth(it)}") }
                                if (row.wasPaidBeforeTracking) append(" · paid before tracking")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MoneyText(row.amount, style = MaterialTheme.typography.bodyMedium)
                    if (row.isPaid && !row.wasPaidBeforeTracking) {
                        TextButton(onClick = { viewModel.undoInstallment(row.installmentNumber) }) {
                            Text("Undo")
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete ${emi.name}?",
            message = "The loan and every payment recorded against it will be removed.",
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { showDelete = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmiEditorScreen(
    onBack: () -> Unit,
    viewModel: EmiEditorViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditing) "Edit EMI" else "Add EMI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LabelledTextField(
                value = form.name,
                onValueChange = viewModel::updateName,
                label = "Loan name",
                supportingText = "For example, Bike EMI or Home loan",
                isError = form.nameError != null,
                errorMessage = form.nameError
            )

            AmountField(
                value = form.emiAmountText,
                onValueChange = viewModel::updateEmiAmount,
                label = "Installment amount",
                isError = form.amountError != null,
                errorMessage = form.amountError
            )

            LabelledTextField(
                value = form.totalInstallmentsText,
                onValueChange = viewModel::updateTotalInstallments,
                label = "Total installments",
                keyboardType = KeyboardType.Number,
                isError = form.tenureError != null,
                errorMessage = form.tenureError
            )

            LabelledTextField(
                value = form.openingPaidText,
                onValueChange = viewModel::updateOpeningPaid,
                label = "Installments already paid",
                keyboardType = KeyboardType.Number,
                supportingText = "If this loan started before you began using the app"
            )

            DropdownField(
                value = form.frequency,
                options = Frequency.entries.filterNot { it == Frequency.WEEKLY },
                onSelect = viewModel::updateFrequency,
                label = "How often",
                optionLabel = { it.label }
            )

            DateField(
                date = form.firstDueDate,
                onDateChange = viewModel::updateFirstDueDate,
                label = "First installment due on"
            )

            DateField(
                date = form.startDate,
                onDateChange = viewModel::updateStartDate,
                label = "Loan start date"
            )

            SectionCard(
                title = "Interest details",
                subtitle = "Optional, used to work out the total cost"
            ) {
                AmountField(
                    value = form.principalText,
                    onValueChange = viewModel::updatePrincipal,
                    label = "Amount borrowed"
                )
                Spacer(Modifier.height(8.dp))
                LabelledTextField(
                    value = form.interestRateText,
                    onValueChange = viewModel::updateInterestRate,
                    label = "Interest rate (% per year)",
                    keyboardType = KeyboardType.Decimal
                )
                if (form.canSuggestInstallment) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::suggestInstallment,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Work out the installment for me")
                    }
                    Text(
                        "This fills in the installment field. If your bank collects a " +
                            "different amount, type theirs instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (options.categories.isNotEmpty()) {
                DropdownField(
                    value = form.categoryId,
                    options = listOf<Long?>(null) + options.categories.map { it.id },
                    onSelect = viewModel::updateCategory,
                    label = "Record payments under",
                    optionLabel = { id ->
                        if (id == null) "EMI"
                        else options.categories.first { it.id == id }.name
                    }
                )
            }

            if (options.vehicles.isNotEmpty()) {
                DropdownField(
                    value = form.vehicleId,
                    options = listOf<Long?>(null) + options.vehicles.map { it.id },
                    onSelect = viewModel::updateVehicle,
                    label = "Related vehicle",
                    optionLabel = { id ->
                        if (id == null) "Not a vehicle loan"
                        else options.vehicles.first { it.id == id }.name
                    }
                )
            }

            LabelledTextField(
                value = form.accountReference,
                onValueChange = viewModel::updateAccountReference,
                label = "Loan account number (optional)"
            )

            SwitchRow(
                label = "Paid by auto debit",
                checked = form.autoDebit,
                onCheckedChange = viewModel::updateAutoDebit
            )

            SwitchRow(
                label = "Currently active",
                checked = form.isActive,
                onCheckedChange = viewModel::updateActive,
                supporting = "Turn off to leave it out of forecasts without deleting it"
            )

            LabelledTextField(
                value = form.notes,
                onValueChange = viewModel::updateNotes,
                label = "Notes",
                singleLine = false,
                imeAction = ImeAction.Done
            )

            Spacer(Modifier.height(8.dp))
            PrimaryActionButton(
                text = if (form.isEditing) "Save changes" else "Add EMI",
                onClick = { viewModel.save(onBack) },
                enabled = form.canSave
            )
        }
    }
}

/**
 * What paying extra would buy.
 *
 * Placed on the loan itself because that is where the decision is made. The figure is
 * usually much larger than people expect, since every rupee paid early removes interest
 * from every remaining month.
 */
@Composable
private fun PrepaymentCard(
    prepay: com.moneyplanner.domain.calc.PrepaymentResult,
    viewModel: EmiDetailViewModel
) {
    val colors = MoneyTheme.colors
    val extraText by viewModel.extraInput.collectAsStateWithLifecycle()

    SectionCard(
        title = "Pay off sooner",
        subtitle = "See what paying extra each month would save"
    ) {
        if (!prepay.isModelled) {
            Text(
                prepay.reason.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        AmountField(
            value = extraText,
            onValueChange = viewModel::updateExtraPerMonth,
            label = "Extra each month",
            imeAction = ImeAction.Done
        )

        Spacer(Modifier.height(12.dp))
        if (prepay.hasSaving) {
            Text(
                prepay.summary(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.positive
            )
            Spacer(Modifier.height(10.dp))
            SummaryRow("Interest as things stand", prepay.baselineInterest)
            SummaryRow("Interest if you pay extra", prepay.newInterest)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SummaryRow(
                label = "Interest saved",
                amount = prepay.interestSaved,
                supporting = "Finishes in " + prepay.newMonths +
                    " months instead of " + prepay.baselineMonths,
                emphasise = true,
                amountColor = colors.positive
            )
        } else {
            Text(
                "Enter an amount above to see the effect.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SummaryRow("Balance outstanding", prepay.outstandingPrincipal)
            SummaryRow(
                label = "Interest still to pay",
                amount = prepay.baselineInterest,
                supporting = "Over " + prepay.baselineMonths + " remaining months"
            )
        }
    }
}
