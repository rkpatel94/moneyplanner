package com.moneyplanner.ui.screens.afford

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.domain.calc.AffordabilityCalculator
import com.moneyplanner.domain.calc.AffordabilityResult
import com.moneyplanner.domain.model.AffordabilityVerdict
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.theme.MoneyTheme
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AffordabilityViewModel @Inject constructor(
    snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    private val amountText = MutableStateFlow("")
    private val horizon = MutableStateFlow(AffordabilityCalculator.DEFAULT_HORIZON_MONTHS)

    val input: StateFlow<String> = amountText.asStateFlow()
    val horizonMonths: StateFlow<Int> = horizon.asStateFlow()

    val state: StateFlow<AffordabilityState> = combine(
        snapshotRepository.snapshot,
        amountText,
        horizon
    ) { snapshot, text, months ->
        val amount = Money.parseOrNull(text)
        AffordabilityState(
            result = if (amount != null && amount.isPositive) {
                AffordabilityCalculator.check(snapshot, amount, months)
            } else {
                null
            },
            hasCommitments = hasAnythingToCheckAgainst(snapshot),
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AffordabilityState()
    )

    private fun hasAnythingToCheckAgainst(snapshot: FinancialSnapshot): Boolean =
        snapshot.incomeSources.isNotEmpty() ||
            snapshot.bills.isNotEmpty() ||
            snapshot.emis.isNotEmpty() ||
            snapshot.accounts.isNotEmpty()

    fun updateAmount(value: String) {
        amountText.value = value
    }

    fun updateHorizon(months: Int) {
        horizon.value = months
    }
}

data class AffordabilityState(
    val result: AffordabilityResult? = null,
    val hasCommitments: Boolean = false,
    val isLoading: Boolean = true
)

/**
 * The purchase check.
 *
 * The verdict is only the headline. What matters is the working underneath it, which is
 * built entirely from figures the user can go and verify on other screens. The app does
 * not tell anyone what to do with their money; it shows them the arithmetic and leaves
 * the decision where it belongs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AffordabilityScreen(
    onBack: () -> Unit,
    viewModel: AffordabilityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val amountText by viewModel.input.collectAsStateWithLifecycle()
    val horizon by viewModel.horizonMonths.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Can I afford it?") },
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
                SectionCard(title = "What are you thinking of buying?") {
                    AmountField(
                        value = amountText,
                        onValueChange = viewModel::updateAmount,
                        label = "Purchase amount",
                        imeAction = ImeAction.Done
                    )
                    Spacer(Modifier.height(12.dp))
                    ChipSelector(
                        label = "Check against the next",
                        options = listOf(1, 3, 6),
                        selected = horizon,
                        onSelect = viewModel::updateHorizon,
                        optionLabel = { if (it == 1) "1 month" else "$it months" }
                    )
                }
            }

            val result = state.result
            if (result == null) {
                item {
                    SectionCard {
                        Text(
                            "Enter an amount and this will compare it against the money " +
                                "you have and everything you have told the app is coming.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item { VerdictCard(result) }
                item { WorkingCard(result) }

                if (result.monthsToWaitForComfort != null) {
                    item {
                        SectionCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                "Waiting about ${result.monthsToWaitForComfort} " +
                                    if (result.monthsToWaitForComfort == 1) "month" else "months",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "At your projected monthly surplus, that would leave you " +
                                    "comfortable rather than stretched.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                item {
                    Text(
                        "This is a comparison of your own numbers, not financial advice. " +
                            "Only you know what else is coming that the app has not been told.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VerdictCard(result: AffordabilityResult) {
    val colors = MoneyTheme.colors
    val (container, content, icon) = when (result.verdict) {
        AffordabilityVerdict.SAFE ->
            Triple(colors.positiveContainer, colors.positive, Icons.Default.CheckCircle)
        AffordabilityVerdict.BE_CAREFUL ->
            Triple(colors.warningContainer, colors.onWarningContainer, Icons.Default.WarningAmber)
        AffordabilityVerdict.NOT_RECOMMENDED ->
            Triple(colors.negativeContainer, colors.negative, Icons.Default.ErrorOutline)
    }

    SectionCard(containerColor = container) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                result.verdict.label,
                style = MaterialTheme.typography.headlineSmall,
                color = content
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            result.headline,
            style = MaterialTheme.typography.bodyLarge,
            color = content
        )
        Spacer(Modifier.height(8.dp))
        result.reasons.forEach { reason ->
            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                Text("•  ", color = content)
                Text(reason, style = MaterialTheme.typography.bodyMedium, color = content)
            }
        }
    }
}

@Composable
private fun WorkingCard(result: AffordabilityResult) {
    val colors = MoneyTheme.colors
    SectionCard(
        title = "How this was worked out",
        subtitle = "Over the next ${result.horizonMonths} months"
    ) {
        SummaryRow("Money available now", result.availableNow)
        SummaryRow("Expected to come in", result.expectedIncoming, colorBySign = true, showSign = true)
        SummaryRow(
            label = "Payments already committed",
            amount = -result.upcomingCommitments,
            supporting = "EMIs, bills, card dues and yearly expenses",
            colorBySign = true,
            showSign = true
        )
        if (result.estimatedEverydaySpend.isPositive) {
            SummaryRow(
                label = "Everyday spending",
                amount = -result.estimatedEverydaySpend,
                supporting = "Estimated from your own history",
                colorBySign = true,
                showSign = true
            )
        }
        SummaryRow(
            label = "This purchase",
            amount = -result.purchaseAmount,
            colorBySign = true,
            showSign = true
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SummaryRow(
            label = "Left immediately after buying",
            amount = result.remainingAfterPurchase,
            amountColor = if (result.remainingAfterPurchase.isNegative) colors.negative else null
        )
        SummaryRow(
            label = "Lowest point you would reach",
            amount = result.lowestProjectedBalance,
            supporting = result.lowestPointMonth?.let { "Around $it" }
                ?: "Across the whole period",
            emphasise = true,
            amountColor = if (result.lowestProjectedBalance.isNegative) colors.negative else null
        )

        if (result.monthlyEssentialExpenses.isPositive) {
            Spacer(Modifier.height(6.dp))
            Text(
                "For comparison, one month of your essential expenses is " +
                    "${IndianFormat.format(result.monthlyEssentialExpenses)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
