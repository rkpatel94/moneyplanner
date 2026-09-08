package com.moneyplanner

import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.AnnualExpense
import com.moneyplanner.domain.model.BillAmountType
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.CategoryType
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.Emi
import com.moneyplanner.domain.model.EmiPayment
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.domain.model.GoalPriority
import com.moneyplanner.domain.model.IncomeSource
import com.moneyplanner.domain.model.IncomeTransaction
import com.moneyplanner.domain.model.IncomeType
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.Person
import com.moneyplanner.domain.model.PersonLedgerEntry
import com.moneyplanner.domain.model.RecurringBill
import com.moneyplanner.domain.model.Relation
import com.moneyplanner.domain.model.SavingsContribution
import com.moneyplanner.domain.model.SavingsGoal
import com.moneyplanner.domain.model.Settlement
import com.moneyplanner.domain.model.SettlementDirection
import java.time.LocalDate

/**
 * Builders for readable tests.
 *
 * Amounts are written in rupees because that is how the scenarios are described, and
 * converted to paise here so the assertions stay easy to read against the requirements.
 */

fun rupees(amount: Long): Money = Money.ofRupees(amount)

fun date(text: String): LocalDate = LocalDate.parse(text)

fun account(
    id: Long = 1,
    opening: Long = 0,
    openingDate: String = "2026-01-01"
) = Account(
    id = id,
    name = "Bank",
    type = AccountType.BANK,
    openingBalance = rupees(opening),
    openingDate = date(openingDate)
)

fun category(
    id: Long,
    name: String = "Food",
    essential: Boolean = false,
    type: CategoryType = CategoryType.EXPENSE
) = Category(
    id = id,
    name = name,
    type = type,
    iconKey = "other",
    colorHex = "#FF000000",
    isEssential = essential,
    isCustom = false,
    isArchived = false,
    sortOrder = 0
)

fun expense(
    id: Long = 1,
    amount: Long,
    date: String,
    categoryId: Long = 1,
    method: PaymentMethod = PaymentMethod.UPI,
    linkType: ExpenseLinkType = ExpenseLinkType.NONE,
    linkId: Long? = null,
    description: String = "Expense"
) = Expense(
    id = id,
    amount = rupees(amount),
    description = description,
    categoryId = categoryId,
    date = date(date),
    paymentMethod = method,
    linkType = linkType,
    linkId = linkId
)

fun incomeSource(
    id: Long = 1,
    amount: Long = 55_000,
    dayOfMonth: Int = 1,
    startDate: String = "2026-01-01",
    endDate: String? = null,
    frequency: Frequency = Frequency.MONTHLY,
    incrementPercent: Double = 0.0,
    active: Boolean = true
) = IncomeSource(
    id = id,
    name = "Salary",
    type = IncomeType.SALARY,
    amount = rupees(amount),
    dayOfMonth = dayOfMonth,
    frequency = frequency,
    startDate = date(startDate),
    endDate = endDate?.let { date(it) },
    annualIncrementPercent = incrementPercent,
    accountId = null,
    isActive = active,
    notes = ""
)

fun incomeTransaction(
    id: Long = 1,
    sourceId: Long? = null,
    amount: Long,
    date: String,
    periodKey: String? = null
) = IncomeTransaction(
    id = id,
    sourceId = sourceId,
    name = "Salary",
    type = IncomeType.SALARY,
    amount = rupees(amount),
    date = date(date),
    accountId = null,
    periodKey = periodKey,
    notes = ""
)

fun person(id: Long = 1, name: String = "Amit") = Person(
    id = id,
    name = name,
    relation = Relation.FRIEND
)

fun ledgerEntry(
    id: Long = 1,
    personId: Long = 1,
    amount: Long,
    direction: LedgerDirection = LedgerDirection.THEY_OWE_ME,
    date: String = "2026-08-01",
    expectedDate: String? = null,
    description: String = "Loan"
) = PersonLedgerEntry(
    id = id,
    personId = personId,
    amount = rupees(amount),
    direction = direction,
    date = date(date),
    expectedDate = expectedDate?.let { date(it) },
    description = description
)

fun settlement(
    id: Long = 1,
    personId: Long = 1,
    amount: Long,
    direction: SettlementDirection = SettlementDirection.RECEIVED_FROM_THEM,
    date: String = "2026-08-10"
) = Settlement(
    id = id,
    personId = personId,
    amount = rupees(amount),
    direction = direction,
    date = date(date),
    paymentMethod = PaymentMethod.UPI,
    accountId = null,
    notes = ""
)

fun emi(
    id: Long = 1,
    name: String = "Bike EMI",
    emiAmount: Long = 4_500,
    principal: Long = 150_000,
    firstDueDate: String = "2026-01-05",
    startDate: String = "2026-01-01",
    totalInstallments: Int = 36,
    openingPaid: Int = 0,
    frequency: Frequency = Frequency.MONTHLY,
    active: Boolean = true
) = Emi(
    id = id,
    name = name,
    categoryId = null,
    principal = rupees(principal),
    emiAmount = rupees(emiAmount),
    interestRatePercent = null,
    startDate = date(startDate),
    firstDueDate = date(firstDueDate),
    frequency = frequency,
    totalInstallments = totalInstallments,
    openingPaidInstallments = openingPaid,
    vehicleId = null,
    accountReference = "",
    autoDebit = false,
    isActive = active,
    notes = ""
)

fun emiPayment(
    id: Long = 1,
    emiId: Long = 1,
    installmentNumber: Int,
    amount: Long = 4_500,
    dueDate: String,
    paidDate: String
) = EmiPayment(
    id = id,
    emiId = emiId,
    installmentNumber = installmentNumber,
    amount = rupees(amount),
    dueDate = date(dueDate),
    paidDate = date(paidDate),
    notes = ""
)

fun bill(
    id: Long = 1,
    name: String = "Rent",
    amount: Long = 13_000,
    dueDay: Int = 5,
    startDate: String = "2026-01-01",
    endDate: String? = null,
    frequency: Frequency = Frequency.MONTHLY,
    essential: Boolean = true,
    amountType: BillAmountType = BillAmountType.FIXED,
    active: Boolean = true
) = RecurringBill(
    id = id,
    name = name,
    categoryId = null,
    amountType = amountType,
    amount = rupees(amount),
    minAmount = null,
    maxAmount = null,
    dueDayOfMonth = dueDay,
    frequency = frequency,
    startDate = date(startDate),
    endDate = endDate?.let { date(it) },
    paymentMethod = PaymentMethod.UPI,
    autoDebit = false,
    isEssential = essential,
    isActive = active,
    notes = ""
)

fun creditCard(
    id: Long = 1,
    outstanding: Long = 8_000,
    limit: Long = 100_000,
    dueDay: Int = 15,
    statementDay: Int = 1,
    active: Boolean = true
) = CreditCard(
    id = id,
    name = "HDFC Card",
    bank = "HDFC",
    lastFourDigits = "1234",
    creditLimit = rupees(limit),
    currentOutstanding = rupees(outstanding),
    minimumDue = rupees(outstanding / 20),
    statementDayOfMonth = statementDay,
    dueDayOfMonth = dueDay,
    isActive = active,
    notes = "",
    lastUpdated = null
)

fun annualExpense(
    id: Long = 1,
    name: String = "Vehicle insurance",
    amount: Long = 8_000,
    dueMonth: Int = 1,
    dueDay: Int = 15,
    active: Boolean = true
) = AnnualExpense(
    id = id,
    name = name,
    categoryId = null,
    amount = rupees(amount),
    dueMonth = dueMonth,
    dueDayOfMonth = dueDay,
    vehicleId = null,
    isActive = active,
    notes = ""
)

fun goal(
    id: Long = 1,
    name: String = "New phone",
    target: Long = 50_000,
    targetDate: String? = null,
    isEmergencyFund: Boolean = false
) = SavingsGoal(
    id = id,
    name = name,
    targetAmount = rupees(target),
    targetDate = targetDate?.let { date(it) },
    priority = GoalPriority.MEDIUM,
    isEmergencyFund = isEmergencyFund,
    isAchieved = false,
    notes = ""
)

fun contribution(
    id: Long = 1,
    goalId: Long = 1,
    amount: Long,
    date: String = "2026-08-01"
) = SavingsContribution(
    id = id,
    goalId = goalId,
    amount = rupees(amount),
    date = date(date),
    accountId = null,
    notes = ""
)
