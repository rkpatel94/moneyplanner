package com.moneyplanner.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.DayOfMonthField
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.theme.MoneyTheme

/**
 * First run.
 *
 * Without this, a new user landed on an empty dashboard showing zero and had to find
 * Settings on their own to enter a balance — which is the moment most people would decide
 * the app does not work. Three questions is the smallest set that makes the dashboard say
 * something true and useful straight away: what you have, what comes in, and what goes out.
 *
 * Every step is skippable. Someone who wants to look around first should be able to, and
 * the app already handles empty data honestly everywhere else.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(20.dp)
            ) {
                LinearProgressIndicator(
                    progress = {
                        (state.step + 1) / (OnboardingViewModel.TOTAL_STEPS + 1).toFloat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )

                Spacer(Modifier.height(28.dp))

                when {
                    state.isReviewing -> ReviewStep(state)
                    state.step == 0 -> WelcomeStep(state, viewModel)
                    state.step == 1 -> BalanceStep(state, viewModel)
                    state.step == 2 -> IncomeStep(state, viewModel)
                    else -> CommitmentStep(state, viewModel)
                }
            }

            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(20.dp)
                ) {
                    if (state.isReviewing) {
                        PrimaryActionButton(
                            text = "Go to my dashboard",
                            onClick = { viewModel.complete(onFinished) }
                        )
                        Spacer(Modifier.height(8.dp))
                        // Setup is not marked done until the dashboard button is pressed,
                        // so going back returns to the questions rather than to a
                        // half-finished app.
                        TextButton(
                            onClick = viewModel::backToQuestions,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Change something")
                        }
                    } else {
                        PrimaryActionButton(
                            text = if (state.isLastStep) "See what this means" else "Continue",
                            onClick = { viewModel.next(onFinished) },
                            enabled = !state.isSaving
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.skipAll(onFinished) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving
                        ) {
                            Text(if (state.step == 0) "Skip setup" else "Skip the rest")
                        }
                    }
                }
            }
        }
    }
}

/**
 * What the answers add up to, before the dashboard is reached.
 *
 * This is the moment the app earns its keep. Everything up to here has been typing; this
 * is the first time the user sees the thing they installed it for, which is a projection
 * of what they will actually have rather than a record of what they spent.
 *
 * The figures come from the saved records and the same calculators the dashboard uses, so
 * nothing shown here can disagree with what appears a moment later.
 */
@Composable
private fun ReviewStep(state: OnboardingState) {
    val review = state.review
    val colors = MoneyTheme.colors

    Column {
        Text(
            "Here is what that means",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))

        if (review == null) {
            Text(
                "Working it out.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Text(
            "Based on what you have just entered, for " + review.monthLabel + ".",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        SectionCard {
            Text(
                "Expected at the end of " + review.monthLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            MoneyText(
                money = review.closingBalance,
                style = MaterialTheme.typography.displaySmall,
                color = if (review.isComfortable) colors.positive else colors.negative
            )
            Spacer(Modifier.height(10.dp))
            StatusPill(
                text = if (review.isComfortable) {
                    "This month works out"
                } else {
                    "This month is projected to fall short"
                },
                containerColor = if (review.isComfortable) {
                    colors.positiveContainer
                } else {
                    colors.warningContainer
                },
                contentColor = if (review.isComfortable) {
                    colors.positive
                } else {
                    colors.onWarningContainer
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard {
            SummaryRow("Starting with", review.openingBalance)
            SummaryRow("Expected in", review.expectedIncome)
            SummaryRow("Already committed", review.committedOutflow)
        }

        Spacer(Modifier.height(12.dp))

        Text(
            if (review.itemsAhead > 0) {
                review.itemsAhead.toString() + " dated payments are already in your " +
                    "calendar. Everything here is editable, and the projection sharpens " +
                    "as you record what you actually spend."
            } else {
                "Add a few expenses and the projection will start using your own spending " +
                    "rather than only the commitments you named."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WelcomeStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Column {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Let us set you up", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Three quick questions and this app can tell you what you will actually have " +
                "left at the end of each month — not just what you have spent.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        LabelledTextField(
            value = state.name,
            onValueChange = viewModel::updateName,
            label = "What should we call you?",
            supportingText = "Optional",
            imeAction = ImeAction.Done
        )
        Spacer(Modifier.height(20.dp))
        SectionCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                "Everything stays on this phone. There is no account, no sync, and the app " +
                    "has no internet permission at all.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun BalanceStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Column {
        Text("How much do you have?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add up your bank balance and any cash. It does not need to be exact — you can " +
                "correct it any time, and every change is recorded rather than overwritten.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        AmountField(
            value = state.balanceText,
            onValueChange = viewModel::updateBalance,
            label = "Money available now",
            imeAction = ImeAction.Done
        )
    }
}

@Composable
private fun IncomeStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Column {
        Text("What comes in?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your salary or main monthly income. This is what lets the app forecast the " +
                "months ahead instead of only recording the past.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        AmountField(
            value = state.salaryText,
            onValueChange = viewModel::updateSalary,
            label = "Monthly income"
        )
        AnimatedVisibility(visible = state.salaryText.isNotBlank()) {
            Column {
                Spacer(Modifier.height(16.dp))
                DayOfMonthField(
                    day = state.salaryDay,
                    onDayChange = viewModel::updateSalaryDay,
                    label = "Day it usually arrives"
                )
            }
        }
    }
}

@Composable
private fun CommitmentStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Column {
        Text("What goes out every month?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your biggest fixed payments. You can add the rest later, and the more the app " +
                "knows the more useful the forecast becomes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        AmountField(
            value = state.rentText,
            onValueChange = viewModel::updateRent,
            label = "Rent or housing"
        )
        Spacer(Modifier.height(16.dp))
        AmountField(
            value = state.emiText,
            onValueChange = viewModel::updateEmi,
            label = "Loan installments (total)",
            imeAction = ImeAction.Done
        )

        AnimatedVisibility(visible = state.previewAvailable) {
            Column {
                Spacer(Modifier.height(24.dp))
                SectionCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                "That leaves about " +
                                    IndianFormat.format(state.roughMonthlyLeft) + " a month",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "before everyday spending. The app will refine this as you " +
                                    "record what you actually spend.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
