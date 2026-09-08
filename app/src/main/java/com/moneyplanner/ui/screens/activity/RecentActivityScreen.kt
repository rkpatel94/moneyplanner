package com.moneyplanner.ui.screens.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.calc.ActivityDirection
import com.moneyplanner.domain.calc.ActivityItem
import com.moneyplanner.domain.calc.ActivityKind
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.IconWash
import com.moneyplanner.ui.components.LoadingState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.theme.MoneyTheme

/**
 * The last ten things that happened, across every kind of record.
 *
 * Its job is correction rather than analysis: History already answers "what did I spend
 * this month". This answers "what did I just enter", which is what someone needs when they
 * have typed a figure wrong and want it back before it quietly distorts a projection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentActivityScreen(
    onBack: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onEditIncome: (Long) -> Unit,
    onEditSettlement: (personId: Long, settlementId: Long) -> Unit,
    onEditTransfer: (Long) -> Unit,
    onEditContribution: (goalId: Long, contributionId: Long) -> Unit,
    viewModel: RecentActivityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Recent activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))

            state.isEmpty -> EmptyState(
                icon = Icons.Default.History,
                title = "Nothing recorded yet",
                message = "Expenses, income, payments to people and money moved between " +
                    "your accounts will all appear here as you enter them.",
                modifier = Modifier.padding(padding)
            )

            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp,
                    vertical = 12.dp
                )
            ) {
                item {
                    Text(
                        if (state.items.size == 1) {
                            "Your most recent money movement. Tap it to change it, or " +
                                "remove it if it should not be there."
                        } else {
                            "The last ${state.items.size} money movements. Tap one to " +
                                "change it, or remove anything that should not be there."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }

                items(state.items, key = { it.key }) { item ->
                    ActivityRow(
                        item = item,
                        today = state.today,
                        onEdit = {
                            when (item.kind) {
                                ActivityKind.EXPENSE -> onEditExpense(item.recordId)
                                ActivityKind.INCOME -> onEditIncome(item.recordId)
                                ActivityKind.SETTLEMENT ->
                                    item.parentId?.let { onEditSettlement(it, item.recordId) }
                                ActivityKind.TRANSFER -> onEditTransfer(item.recordId)
                                ActivityKind.SAVING ->
                                    item.parentId?.let { onEditContribution(it, item.recordId) }
                            }
                        },
                        onDelete = { viewModel.askToDelete(item) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = deleteTitleFor(item),
            message = deleteMessageFor(item),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }
}

@Composable
private fun ActivityRow(
    item: ActivityItem,
    today: java.time.LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MoneyTheme.colors
    val tint = when (item.direction) {
        ActivityDirection.IN -> colors.positive
        ActivityDirection.OUT -> colors.negative
        ActivityDirection.NEUTRAL -> colors.neutral
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (item.isEditable) Modifier.clickable { onEdit() } else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconWash(icon = iconFor(item), tint = tint)
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                "${item.relativeDate(today)} · ${item.subtitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            // Two things the amount alone would misrepresent, so the row says them.
            if (item.isObligationPayment || !item.movesCashNow) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.isObligationPayment) StatusPill(text = "Scheduled")
                    if (!item.movesCashNow) {
                        StatusPill(
                            text = "On card",
                            containerColor = colors.warningContainer,
                            contentColor = colors.onWarningContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A transfer is neither in nor out, so it carries no sign at all.
            if (item.direction != ActivityDirection.NEUTRAL) {
                Text(
                    text = if (item.direction == ActivityDirection.IN) "+" else "−",
                    style = MaterialTheme.typography.titleMedium,
                    color = tint
                )
            }
            MoneyText(
                money = item.amount,
                style = MaterialTheme.typography.titleMedium,
                color = tint
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete ${item.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun iconFor(item: ActivityItem): ImageVector = when (item.kind) {
    ActivityKind.EXPENSE -> Icons.AutoMirrored.Filled.ReceiptLong
    ActivityKind.INCOME -> Icons.Default.ArrowDownward
    ActivityKind.SETTLEMENT -> Icons.Default.ArrowUpward
    ActivityKind.TRANSFER -> Icons.Default.SwapHoriz
    ActivityKind.SAVING -> Icons.Default.Savings
}

private fun deleteTitleFor(item: ActivityItem): String = when (item.kind) {
    ActivityKind.EXPENSE -> "Delete this expense?"
    ActivityKind.INCOME -> "Delete this receipt?"
    ActivityKind.SETTLEMENT -> "Delete this payment?"
    ActivityKind.TRANSFER -> "Delete this transfer?"
    ActivityKind.SAVING -> "Delete this contribution?"
}

/**
 * Says what else goes with it.
 *
 * Deleting one of these records often undoes a second thing the user did not name, and
 * finding that out afterwards is how someone stops trusting an app with their money.
 */
private fun deleteMessageFor(item: ActivityItem): String {
    val amount = com.moneyplanner.core.money.IndianFormat.format(item.amount)
    val on = DateUtil.formatDate(item.date)
    val opening = "$amount on $on."

    return when {
        item.isObligationPayment ->
            "$opening This also marks the commitment unpaid, so it will be expected again " +
                "in the forecast."

        item.linkType == com.moneyplanner.domain.model.ExpenseLinkType.SHARED_EXPENSE ->
            "$opening The shared bill goes with it, including what each person owed you " +
                "for their share."

        item.kind == ActivityKind.SETTLEMENT ->
            "$opening The balance with this person will go back up by the same amount."

        item.kind == ActivityKind.TRANSFER ->
            "$opening The money returns to the account it came from."

        item.kind == ActivityKind.SAVING ->
            "$opening The goal's progress drops by this amount."

        else -> "$opening This cannot be undone."
    }
}
