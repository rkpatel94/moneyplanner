package com.moneyplanner.data.repo

import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountTransfer
import com.moneyplanner.domain.model.AnnualExpense
import com.moneyplanner.domain.model.AnnualExpensePayment
import com.moneyplanner.domain.model.BalanceAdjustment
import com.moneyplanner.domain.model.BillPayment
import com.moneyplanner.domain.model.Budget
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.CreditCardPayment
import com.moneyplanner.domain.model.Emi
import com.moneyplanner.domain.model.EmiPayment
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.IncomeSource
import com.moneyplanner.domain.model.IncomeTransaction
import com.moneyplanner.domain.model.Person
import com.moneyplanner.domain.model.PersonLedgerEntry
import com.moneyplanner.domain.model.RecurringBill
import com.moneyplanner.domain.model.SavingsContribution
import com.moneyplanner.domain.model.SavingsGoal
import com.moneyplanner.domain.model.Settlement
import com.moneyplanner.domain.model.UserProfile
import com.moneyplanner.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles the complete financial picture that the calculators run against.
 *
 * Everything is combined into one snapshot so that a screen never sees a half-updated
 * position: adding an EMI payment, for example, changes the balance, the loan tenure and
 * every future month at the same moment rather than in a visible sequence of steps.
 *
 * The snapshot is rebuilt whenever any underlying table changes, and the calculators are
 * pure functions of it, so what the user sees is always a consistent view of their data.
 */
@Singleton
class SnapshotRepository @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val categoryRepository: CategoryRepository,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val peopleRepository: PeopleRepository,
    private val emiRepository: EmiRepository,
    private val creditCardRepository: CreditCardRepository,
    private val billRepository: BillRepository,
    private val annualExpenseRepository: AnnualExpenseRepository,
    private val savingsRepository: SavingsRepository,
    private val budgetRepository: BudgetRepository,
    private val today: TodayProvider,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) {

    private data class ProfileBundle(
        val profile: UserProfile,
        val accounts: List<Account>,
        val adjustments: List<BalanceAdjustment>,
        val categories: List<Category>,
        val transfers: List<AccountTransfer>
    )

    private data class FlowBundle(
        val incomeSources: List<IncomeSource>,
        val incomeTransactions: List<IncomeTransaction>,
        val expenses: List<Expense>
    )

    private data class PeopleBundle(
        val people: List<Person>,
        val entries: List<PersonLedgerEntry>,
        val settlements: List<Settlement>
    )

    private data class ObligationBundle(
        val emis: List<Emi>,
        val emiPayments: List<EmiPayment>,
        val cards: List<CreditCard>,
        val cardPayments: List<CreditCardPayment>
    )

    private data class CommitmentBundle(
        val bills: List<RecurringBill>,
        val billPayments: List<BillPayment>,
        val annualExpenses: List<AnnualExpense>,
        val annualPayments: List<AnnualExpensePayment>,
        val goals: List<SavingsGoal>,
        val contributions: List<SavingsContribution>,
        val budgets: List<Budget>
    )

    private val profileBundle = combine(
        profileRepository.profile,
        profileRepository.accounts,
        profileRepository.adjustments,
        categoryRepository.everything,
        profileRepository.transfers,
        ::ProfileBundle
    )

    private val flowBundle = combine(
        incomeRepository.sources,
        incomeRepository.transactions,
        expenseRepository.all,
        ::FlowBundle
    )

    private val peopleBundle = combine(
        peopleRepository.people,
        peopleRepository.ledgerEntries,
        peopleRepository.settlements,
        ::PeopleBundle
    )

    private val obligationBundle = combine(
        emiRepository.all,
        emiRepository.payments,
        creditCardRepository.all,
        creditCardRepository.payments,
        ::ObligationBundle
    )

    private val commitmentBundle = combine(
        billRepository.all,
        billRepository.payments,
        annualExpenseRepository.all,
        annualExpenseRepository.payments,
        savingsRepository.goals,
        savingsRepository.contributions,
        budgetRepository.all
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        CommitmentBundle(
            bills = values[0] as List<RecurringBill>,
            billPayments = values[1] as List<BillPayment>,
            annualExpenses = values[2] as List<AnnualExpense>,
            annualPayments = values[3] as List<AnnualExpensePayment>,
            goals = values[4] as List<SavingsGoal>,
            contributions = values[5] as List<SavingsContribution>,
            budgets = values[6] as List<Budget>
        )
    }

    /** The full picture, refreshed whenever anything the user has recorded changes. */
    val snapshot: Flow<FinancialSnapshot> = combine(
        profileBundle,
        flowBundle,
        peopleBundle,
        obligationBundle,
        commitmentBundle,
        // The date is an input like any other. Without it the snapshot only rebuilds when
        // a record changes, so an app left open overnight keeps yesterday's idea of what
        // is overdue and how much of the month is left to project.
        today.todayFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val profileData = values[0] as ProfileBundle
        @Suppress("UNCHECKED_CAST")
        val flowData = values[1] as FlowBundle
        @Suppress("UNCHECKED_CAST")
        val peopleData = values[2] as PeopleBundle
        @Suppress("UNCHECKED_CAST")
        val obligations = values[3] as ObligationBundle
        @Suppress("UNCHECKED_CAST")
        val commitments = values[4] as CommitmentBundle
        val currentDate = values[5] as java.time.LocalDate

        FinancialSnapshot(
            today = currentDate,
            profile = profileData.profile,
            accounts = profileData.accounts,
            adjustments = profileData.adjustments,
            transfers = profileData.transfers,
            categories = profileData.categories,
            incomeSources = flowData.incomeSources,
            incomeTransactions = flowData.incomeTransactions,
            expenses = flowData.expenses,
            people = peopleData.people,
            ledgerEntries = peopleData.entries,
            settlements = peopleData.settlements,
            emis = obligations.emis,
            emiPayments = obligations.emiPayments,
            creditCards = obligations.cards,
            creditCardPayments = obligations.cardPayments,
            bills = commitments.bills,
            billPayments = commitments.billPayments,
            annualExpenses = commitments.annualExpenses,
            annualExpensePayments = commitments.annualPayments,
            goals = commitments.goals,
            contributions = commitments.contributions,
            budgets = commitments.budgets
        )
    }
        // Everything downstream of here — assembling the snapshot and every calculator a
        // screen runs over it — is arithmetic over the user's whole history, and a
        // twelve month projection walks every schedule day by day. Without this it would
        // all run on whichever thread collects, which for a ViewModel is the main one.
        .flowOn(computation)
}
