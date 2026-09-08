package com.moneyplanner.ui.screens.goals

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.GoalPriority
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.GoalProgressBar
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.LoadingState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.OptionalDateField
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.theme.MoneyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onBack: (() -> Unit)?,
    onAddGoal: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MoneyTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Savings goals") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddGoal,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add goal") }
            )
        }
    ) { padding ->
        if (state.goals.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Savings,
                title = "No goals yet",
                message = "Set a target for the things you are saving towards and the app " +
                    "will work out what each one needs every month.",
                actionLabel = "Add a goal",
                onAction = onAddGoal,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "All goals") {
                    SummaryRow("Saved so far", state.totalSaved)
                    SummaryRow("Still to save", state.totalRemaining)
                    SummaryRow(
                        label = "Needed each month",
                        amount = state.totalMonthlyRequired,
                        supporting = "To reach every goal by its target date",
                        emphasise = true
                    )
                }
            }

            items(state.goals, key = { it.goal.id }) { progress ->
                SectionCard(modifier = Modifier.clickable { onOpenGoal(progress.goal.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(progress.goal.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append(progress.goal.priority.label)
                                    append(" priority")
                                    progress.goal.targetDate?.let {
                                        append(" · by ${DateUtil.formatDate(it)}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (progress.goal.isEmergencyFund) StatusPill(text = "Emergency fund")
                    }

                    Spacer(Modifier.height(10.dp))
                    GoalProgressBar(
                        fraction = progress.progressFraction,
                        color = if (progress.isAchieved) colors.positive else MaterialTheme.colorScheme.primary,
                        label = "${progress.goal.name} is ${progress.progressPercent} percent funded"
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MoneyText(progress.saved, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "of ${IndianFormat.format(progress.goal.targetAmount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    when {
                        progress.isAchieved -> StatusPill(
                            text = "Goal reached",
                            containerColor = colors.positiveContainer,
                            contentColor = colors.positive
                        )
                        progress.isBehindSchedule -> StatusPill(
                            text = "Target date has passed",
                            containerColor = colors.warningContainer,
                            contentColor = colors.onWarningContainer
                        )
                        progress.requiredMonthlySaving.isPositive -> Text(
                            "${IndianFormat.format(progress.requiredMonthlySaving)} a month for " +
                                "${progress.monthsRemaining} more months",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Text(
                            "${IndianFormat.format(progress.remaining)} to go",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    viewModel: GoalDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contributionText by viewModel.contributionText.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    val colors = MoneyTheme.colors

    if (state.isLoading) {
        LoadingState()
        return
    }
    val progress = state.progress ?: run {
        EmptyState(
            icon = Icons.Default.Savings,
            title = "Goal not found",
            message = "This goal may have been deleted."
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(progress.goal.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete goal")
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
                    MoneyText(progress.saved, style = MaterialTheme.typography.displaySmall)
                    Text(
                        "of ${IndianFormat.format(progress.goal.targetAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    GoalProgressBar(
                        fraction = progress.progressFraction,
                        color = if (progress.isAchieved) colors.positive else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    SummaryRow("Still to save", progress.remaining)
                    if (progress.requiredMonthlySaving.isPositive) {
                        SummaryRow(
                            label = "Needed each month",
                            amount = progress.requiredMonthlySaving,
                            supporting = progress.monthsRemaining?.let { "Over $it months" },
                            emphasise = true
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Add to this goal") {
                    AmountField(
                        value = contributionText,
                        onValueChange = viewModel::updateContributionText,
                        label = "Amount",
                        imeAction = ImeAction.Done
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryActionButton(
                            text = "Add",
                            onClick = viewModel::contribute,
                            enabled = Money.parseOrNull(contributionText)?.isPositive == true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = viewModel::withdraw,
                            enabled = Money.parseOrNull(contributionText)?.isPositive == true,
                            modifier = Modifier.weight(1f)
                        ) { Text("Take out") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Money set aside stays in your balance and is shown as earmarked, " +
                            "so nothing is double counted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.contributions.isNotEmpty()) {
                item {
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(state.contributions, key = { it.id }) { contribution ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (contribution.amount.isNegative) "Taken out" else "Added",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                DateUtil.formatDate(contribution.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MoneyText(contribution.amount, colorBySign = true, showSign = true)
                        IconButton(onClick = { viewModel.deleteContribution(contribution.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete entry")
                        }
                    }
                }
            }
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete ${progress.goal.name}?",
            message = "The goal and its contribution history will be removed.",
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { showDelete = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorScreen(
    onBack: () -> Unit,
    viewModel: GoalEditorViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add goal") },
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
                label = "What are you saving for?",
                supportingText = "For example, New phone, Bike, or Vacation",
                isError = form.nameError != null,
                errorMessage = form.nameError
            )

            ChipSelector(
                label = "Common goals",
                options = GoalEditorViewModel.SUGGESTIONS,
                selected = null,
                onSelect = viewModel::updateName,
                optionLabel = { it }
            )

            AmountField(
                value = form.targetText,
                onValueChange = viewModel::updateTarget,
                label = "Target amount",
                isError = form.targetError != null,
                errorMessage = form.targetError
            )

            OptionalDateField(
                date = form.targetDate,
                onDateChange = viewModel::updateTargetDate,
                label = "Has a target date",
                defaultWhenEnabled = form.suggestedTargetDate
            )

            if (form.requiredMonthly.isPositive) {
                SectionCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "You would need ${IndianFormat.format(form.requiredMonthly)} a month",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            ChipSelector(
                label = "Priority",
                options = GoalPriority.entries,
                selected = form.priority,
                onSelect = viewModel::updatePriority,
                optionLabel = { it.label }
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
                text = "Add goal",
                onClick = { viewModel.save(onBack) },
                enabled = form.canSave
            )
        }
    }
}
