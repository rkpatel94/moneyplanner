package com.moneyplanner.ui.screens.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.GoalProgressBar
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.theme.MoneyTheme

/**
 * The emergency fund, explained rather than merely displayed.
 *
 * The target is built from the user's own essential costs, so the screen shows the
 * components that produced it. A number a person understands is a number they will act on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyFundScreen(
    onBack: () -> Unit,
    viewModel: EmergencyFundViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contributionText by viewModel.contributionText.collectAsStateWithLifecycle()
    val status = state.status
    val colors = MoneyTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency fund") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        "Saved so far",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        IndianFormat.format(status.currentAmount),
                        style = MaterialTheme.typography.displayMedium
                    )
                    Text(
                        "of a ${IndianFormat.format(status.targetAmount)} target",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    GoalProgressBar(
                        fraction = status.progressFraction,
                        color = if (status.isFunded) colors.positive else MaterialTheme.colorScheme.primary,
                        label = "Emergency fund is ${status.progressPercent} percent funded"
                    )
                    Spacer(Modifier.height(10.dp))
                    if (status.isFunded) {
                        StatusPill(
                            text = "Fully funded",
                            containerColor = colors.positiveContainer,
                            contentColor = colors.positive
                        )
                    } else if (status.monthlyEssentialExpenses.isPositive) {
                        Text(
                            "This would currently cover about ${
                                String.format("%.1f", status.monthsCovered)
                            } months of essential expenses.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = "How much cover do you want?",
                    subtitle = "Most households aim for three to six months"
                ) {
                    ChipSelector(
                        options = listOf(3, 6, 12),
                        selected = state.monthsSetting,
                        onSelect = viewModel::setMonths,
                        optionLabel = { "$it months" }
                    )
                }
            }

            item {
                SectionCard(
                    title = "What one month costs you",
                    subtitle = "Built from what you have recorded, not from an average household"
                ) {
                    if (status.essentialBills.isPositive) {
                        SummaryRow("Essential bills", status.essentialBills)
                    }
                    if (status.emiBurden.isPositive) {
                        SummaryRow("Loan installments", status.emiBurden)
                    }
                    if (status.essentialEverydaySpend.isPositive) {
                        SummaryRow(
                            label = "Everyday essentials",
                            amount = status.essentialEverydaySpend,
                            supporting = "Average of your spending in essential categories"
                        )
                    }
                    if (status.annualReserve.isPositive) {
                        SummaryRow(
                            label = "Yearly commitments",
                            amount = status.annualReserve,
                            supporting = "Monthly share of insurance and similar bills"
                        )
                    }

                    if (status.monthlyEssentialExpenses.isZero) {
                        Text(
                            "Add your bills, loans and a little spending history, and this " +
                                "will fill in with your own numbers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SummaryRow(
                            label = "Essential cost each month",
                            amount = status.monthlyEssentialExpenses,
                            emphasise = true
                        )
                        SummaryRow(
                            label = "${status.monthsOfCover} months of cover",
                            amount = status.targetAmount,
                            emphasise = true
                        )
                    }
                }
            }

            if (status.targetAmount.isPositive) {
                item {
                    SectionCard(title = "Add to your fund") {
                        AmountField(
                            value = contributionText,
                            onValueChange = viewModel::updateContributionText,
                            label = "Amount",
                            imeAction = ImeAction.Done
                        )
                        Spacer(Modifier.height(10.dp))
                        PrimaryActionButton(
                            text = "Add to emergency fund",
                            onClick = viewModel::contribute,
                            enabled = Money.parseOrNull(contributionText)?.isPositive == true
                        )
                        if (!status.hasGoal) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "This will create your emergency fund goal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!status.isFunded && status.remainingAmount.isPositive) {
                    item {
                        SectionCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                "${IndianFormat.format(status.remainingAmount)} still to go",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Saving ${
                                    IndianFormat.format(status.remainingAmount.divideRounded(12))
                                } a month would get you there within a year.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
