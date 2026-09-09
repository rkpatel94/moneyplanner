package com.moneyplanner.domain.model

import com.moneyplanner.core.money.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * Domain models used by the calculators and the UI.
 *
 * They differ from the database entities in two deliberate ways: amounts are [Money]
 * rather than raw paise, and dates are [LocalDate] rather than epoch days. Every
 * conversion happens once, in the mapping layer, so no calculator ever has to remember
 * which unit it is holding.
 */

data class UserProfile(
    val displayName: String = "",
    val emergencyFundMonths: Int = 6,
    val monthStartDay: Int = 1,
    val onboardingCompleted: Boolean = false
)

data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val openingBalance: Money,
    val openingDate: LocalDate,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0
)

/**
 * Money moved between two of the user's own accounts. Nothing earned, nothing spent.
 */
data class AccountTransfer(
    val id: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Money,
    val date: LocalDate,
    val notes: String = ""
)

data class BalanceAdjustment(
    val id: Long,
    val accountId: Long?,
    val delta: Money,
    val date: LocalDate,
    val reason: String
)

data class Category(
    val id: Long,
    val name: String,
    val type: CategoryType,
    val iconKey: String,
    val colorHex: String,
    val isEssential: Boolean,
    val isCustom: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int
)

data class Person(
    val id: Long,
    val name: String,
    val relation: Relation,
    val phone: String? = null,
    val notes: String = "",
    val isArchived: Boolean = false
)

data class FamilyMember(
    val id: Long,
    val name: String,
    val relation: Relation,
    val isSelf: Boolean,
    val isArchived: Boolean = false
)

data class Vehicle(
    val id: Long,
    val name: String,
    val type: VehicleType,
    val registrationNumber: String? = null,
    val purchaseDate: LocalDate? = null,
    val notes: String = "",
    val isArchived: Boolean = false
)

data class IncomeSource(
    val id: Long,
    val name: String,
    val type: IncomeType,
    val amount: Money,
    val dayOfMonth: Int,
    val frequency: Frequency,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val annualIncrementPercent: Double,
    val accountId: Long?,
    val isActive: Boolean,
    val notes: String
)

data class IncomeTransaction(
    val id: Long,
    val sourceId: Long?,
    val name: String,
    val type: IncomeType,
    val amount: Money,
    val date: LocalDate,
    val accountId: Long?,
    val periodKey: String?,
    val notes: String
)

data class Expense(
    val id: Long,
    val amount: Money,
    val description: String,
    val categoryId: Long,
    val date: LocalDate,
    val paymentMethod: PaymentMethod,
    val accountId: Long? = null,
    val creditCardId: Long? = null,
    val personId: Long? = null,
    val vehicleId: Long? = null,
    val familyMemberId: Long? = null,
    val linkType: ExpenseLinkType = ExpenseLinkType.NONE,
    val linkId: Long? = null,
    /** Which occurrence of [linkId] this settled: "2026-09", "2026", or an installment number. */
    val linkPeriodKey: String? = null,
    val notes: String = ""
) {
    /**
     * True when this expense is everyday spending rather than the settlement of an
     * obligation the forecast already knows about. Only these feed spending averages.
     */
    val isDiscretionary: Boolean get() = linkType == ExpenseLinkType.NONE

    /** True when this expense actually moved cash out of an account on its date. */
    val movesCash: Boolean get() = paymentMethod.reducesCashImmediately
}

data class PersonLedgerEntry(
    val id: Long,
    val personId: Long,
    val amount: Money,
    val direction: LedgerDirection,
    val date: LocalDate,
    val expectedDate: LocalDate?,
    val description: String,
    val sourceType: LedgerSourceType = LedgerSourceType.MANUAL,
    val sourceId: Long? = null,
    val notes: String = ""
)

data class Settlement(
    val id: Long,
    val personId: Long,
    val amount: Money,
    val direction: SettlementDirection,
    val date: LocalDate,
    val paymentMethod: PaymentMethod,
    val accountId: Long?,
    val notes: String
)

data class SharedExpense(
    val id: Long,
    val description: String,
    val totalAmount: Money,
    val date: LocalDate,
    val categoryId: Long?,
    val splitType: SplitType,
    val paidByPersonId: Long?,
    val notes: String
)

data class SharedExpenseShare(
    val id: Long,
    val sharedExpenseId: Long,
    val personId: Long?,
    val shareAmount: Money,
    val sharePercent: Double?
)

data class Emi(
    val id: Long,
    val name: String,
    val categoryId: Long?,
    val principal: Money,
    val emiAmount: Money,
    val interestRatePercent: Double?,
    val startDate: LocalDate,
    val firstDueDate: LocalDate,
    val frequency: Frequency,
    val totalInstallments: Int,
    val openingPaidInstallments: Int,
    val vehicleId: Long?,
    val accountReference: String,
    val autoDebit: Boolean,
    val isActive: Boolean,
    val notes: String
)

data class EmiPayment(
    val id: Long,
    val emiId: Long,
    val installmentNumber: Int,
    val amount: Money,
    val dueDate: LocalDate,
    val paidDate: LocalDate,
    val notes: String
)

data class CreditCard(
    val id: Long,
    val name: String,
    val bank: String,
    val lastFourDigits: String?,
    val creditLimit: Money,
    val currentOutstanding: Money,
    val minimumDue: Money,
    val statementDayOfMonth: Int,
    val dueDayOfMonth: Int,
    val isActive: Boolean,
    val notes: String,
    val lastUpdated: LocalDate?
) {
    val availableLimit: Money get() = (creditLimit - currentOutstanding).coerceAtLeastZero()

    /** Utilisation as a fraction between 0 and 1; zero when no limit has been recorded. */
    val utilisation: Float
        get() = if (creditLimit.paise <= 0) 0f
        else (currentOutstanding.paise.toDouble() / creditLimit.paise).toFloat().coerceIn(0f, 1f)
}

data class CreditCardPayment(
    val id: Long,
    val cardId: Long,
    val amount: Money,
    val paidDate: LocalDate,
    val accountId: Long?,
    val notes: String
)

data class RecurringBill(
    val id: Long,
    val name: String,
    val categoryId: Long?,
    val amountType: BillAmountType,
    val amount: Money,
    val minAmount: Money?,
    val maxAmount: Money?,
    val dueDayOfMonth: Int,
    val frequency: Frequency,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val paymentMethod: PaymentMethod,
    val autoDebit: Boolean,
    val isEssential: Boolean,
    val isActive: Boolean,
    val notes: String
)

data class BillPayment(
    val id: Long,
    val billId: Long,
    val periodKey: String,
    val amount: Money,
    val dueDate: LocalDate,
    val paidDate: LocalDate,
    val notes: String
)

data class AnnualExpense(
    val id: Long,
    val name: String,
    val categoryId: Long?,
    val amount: Money,
    val dueMonth: Int,
    val dueDayOfMonth: Int,
    val vehicleId: Long?,
    val isActive: Boolean,
    val notes: String
) {
    /** What should be put aside every month so the bill does not arrive as a shock. */
    val monthlyReserve: Money get() = amount.divideRounded(12)
}

data class AnnualExpensePayment(
    val id: Long,
    val annualExpenseId: Long,
    val year: Int,
    val amount: Money,
    val paidDate: LocalDate
)

data class SavingsGoal(
    val id: Long,
    val name: String,
    val targetAmount: Money,
    val targetDate: LocalDate?,
    val priority: GoalPriority,
    val isEmergencyFund: Boolean,
    val isAchieved: Boolean,
    val notes: String
)

/**
 * A monthly spending limit. A null [categoryId] is the overall budget.
 */
data class Budget(
    val id: Long,
    val categoryId: Long?,
    val amount: Money,
    val isActive: Boolean = true,
    val notes: String = "",
    /**
     * Whether money left unspent in a month raises the next month's limit.
     *
     * Only unspent money carries. An overspend is not carried as a debt, because that
     * turns one bad month into a limit the following month cannot meet either, and a
     * budget nobody can hit is one they stop reading.
     */
    val rolloverEnabled: Boolean = false,
    /** How far through the limit counts as close to it. */
    val alertThresholdPercent: Int = DEFAULT_ALERT_THRESHOLD,
    /** Set when the budget was created, so rollover cannot reach back before it existed. */
    val createdAt: LocalDate? = null
) {
    val isOverall: Boolean get() = categoryId == null

    val alertFraction: Float get() = alertThresholdPercent.coerceIn(1, 100) / 100f

    companion object {
        const val DEFAULT_ALERT_THRESHOLD = 80
    }
}

data class SavingsContribution(
    val id: Long,
    val goalId: Long,
    val amount: Money,
    val date: LocalDate,
    val accountId: Long?,
    val notes: String
)

/**
 * Everything the calculators need, gathered once.
 *
 * Passing a snapshot rather than a set of repositories keeps every calculator a pure
 * function: the same snapshot always produces the same numbers, which is what makes the
 * forecast reproducible and straightforward to unit test.
 */
data class FinancialSnapshot(
    val today: LocalDate,
    val profile: UserProfile = UserProfile(),
    val accounts: List<Account> = emptyList(),
    val adjustments: List<BalanceAdjustment> = emptyList(),
    val transfers: List<AccountTransfer> = emptyList(),
    val categories: List<Category> = emptyList(),
    val incomeSources: List<IncomeSource> = emptyList(),
    val incomeTransactions: List<IncomeTransaction> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val ledgerEntries: List<PersonLedgerEntry> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
    val people: List<Person> = emptyList(),
    val emis: List<Emi> = emptyList(),
    val emiPayments: List<EmiPayment> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val creditCardPayments: List<CreditCardPayment> = emptyList(),
    val bills: List<RecurringBill> = emptyList(),
    val billPayments: List<BillPayment> = emptyList(),
    val annualExpenses: List<AnnualExpense> = emptyList(),
    val annualExpensePayments: List<AnnualExpensePayment> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val contributions: List<SavingsContribution> = emptyList(),
    val budgets: List<Budget> = emptyList()
) {
    val currentMonth: YearMonth get() = YearMonth.from(today)

    /**
     * Built once per snapshot rather than on every read.
     *
     * These are looked up inside loops — once per budget, once per expense row in a
     * breakdown — and as plain getters each of those lookups rebuilt the whole map. A
     * snapshot never changes after it is constructed, so caching it is safe.
     */
    val categoriesById: Map<Long, Category> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        categories.associateBy { it.id }
    }
    val peopleById: Map<Long, Person> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        people.associateBy { it.id }
    }
}
