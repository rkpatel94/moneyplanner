package com.moneyplanner.data.mapper

import com.moneyplanner.core.money.Money
import com.moneyplanner.data.db.entity.AccountEntity
import com.moneyplanner.data.db.entity.AccountTransferEntity
import com.moneyplanner.data.db.entity.AnnualExpenseEntity
import com.moneyplanner.data.db.entity.AnnualExpensePaymentEntity
import com.moneyplanner.data.db.entity.BalanceAdjustmentEntity
import com.moneyplanner.data.db.entity.BudgetEntity
import com.moneyplanner.data.db.entity.BillPaymentEntity
import com.moneyplanner.data.db.entity.CategoryEntity
import com.moneyplanner.data.db.entity.CreditCardEntity
import com.moneyplanner.data.db.entity.CreditCardPaymentEntity
import com.moneyplanner.data.db.entity.EmiEntity
import com.moneyplanner.data.db.entity.EmiPaymentEntity
import com.moneyplanner.data.db.entity.ExpenseEntity
import com.moneyplanner.data.db.entity.FamilyMemberEntity
import com.moneyplanner.data.db.entity.IncomeSourceEntity
import com.moneyplanner.data.db.entity.IncomeTransactionEntity
import com.moneyplanner.data.db.entity.PersonEntity
import com.moneyplanner.data.db.entity.PersonLedgerEntryEntity
import com.moneyplanner.data.db.entity.RecurringBillEntity
import com.moneyplanner.data.db.entity.SavingsContributionEntity
import com.moneyplanner.data.db.entity.SavingsGoalEntity
import com.moneyplanner.data.db.entity.SettlementEntity
import com.moneyplanner.data.db.entity.SharedExpenseEntity
import com.moneyplanner.data.db.entity.SharedExpenseShareEntity
import com.moneyplanner.data.db.entity.UserProfileEntity
import com.moneyplanner.data.db.entity.VehicleEntity
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountTransfer
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.AnnualExpense
import com.moneyplanner.domain.model.AnnualExpensePayment
import com.moneyplanner.domain.model.BalanceAdjustment
import com.moneyplanner.domain.model.BillAmountType
import com.moneyplanner.domain.model.BillPayment
import com.moneyplanner.domain.model.Budget
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.CategoryType
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.CreditCardPayment
import com.moneyplanner.domain.model.Emi
import com.moneyplanner.domain.model.EmiPayment
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.FamilyMember
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.domain.model.GoalPriority
import com.moneyplanner.domain.model.IncomeSource
import com.moneyplanner.domain.model.IncomeTransaction
import com.moneyplanner.domain.model.IncomeType
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.model.LedgerSourceType
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.Person
import com.moneyplanner.domain.model.PersonLedgerEntry
import com.moneyplanner.domain.model.RecurringBill
import com.moneyplanner.domain.model.Relation
import com.moneyplanner.domain.model.SavingsContribution
import com.moneyplanner.domain.model.SavingsGoal
import com.moneyplanner.domain.model.Settlement
import com.moneyplanner.domain.model.SettlementDirection
import com.moneyplanner.domain.model.SharedExpense
import com.moneyplanner.domain.model.SharedExpenseShare
import com.moneyplanner.domain.model.SplitType
import com.moneyplanner.domain.model.UserProfile
import com.moneyplanner.domain.model.Vehicle
import com.moneyplanner.domain.model.VehicleType
import java.time.LocalDate

/**
 * Conversions between stored rows and domain models.
 *
 * Paise become [Money] and epoch days become [LocalDate] exactly here and nowhere else,
 * so no calculator or screen ever has to remember which representation it is holding.
 * Enum values are read by name with a safe fallback, so a row written by a newer version
 * of the app degrades to a sensible default instead of crashing.
 */

private fun Long.toDate(): LocalDate = LocalDate.ofEpochDay(this)
private fun Long?.toDateOrNull(): LocalDate? = this?.let { LocalDate.ofEpochDay(it) }
private fun LocalDate.toEpoch(): Long = toEpochDay()
private fun LocalDate?.toEpochOrNull(): Long? = this?.toEpochDay()

// ---- Profile, accounts, categories ---------------------------------------------

fun UserProfileEntity.toDomain() = UserProfile(
    displayName = displayName,
    emergencyFundMonths = emergencyFundMonths,
    monthStartDay = monthStartDay,
    onboardingCompleted = onboardingCompleted
)

fun UserProfile.toEntity(createdAt: LocalDate) = UserProfileEntity(
    displayName = displayName,
    emergencyFundMonths = emergencyFundMonths,
    monthStartDay = monthStartDay,
    onboardingCompleted = onboardingCompleted,
    createdAtEpochDay = createdAt.toEpoch()
)

fun AccountTransferEntity.toDomain() = AccountTransfer(
    id = id,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    amount = Money(amountPaise),
    date = dateEpochDay.toDate(),
    notes = notes
)

fun AccountTransfer.toEntity() = AccountTransferEntity(
    id = id,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    amountPaise = amount.paise,
    dateEpochDay = date.toEpoch(),
    notes = notes
)

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    type = AccountType.fromName(type),
    openingBalance = Money(openingBalancePaise),
    openingDate = openingDateEpochDay.toDate(),
    isArchived = isArchived,
    sortOrder = sortOrder
)

fun Account.toEntity() = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    openingBalancePaise = openingBalance.paise,
    openingDateEpochDay = openingDate.toEpoch(),
    isArchived = isArchived,
    sortOrder = sortOrder
)

fun BalanceAdjustmentEntity.toDomain() = BalanceAdjustment(
    id = id,
    accountId = accountId,
    delta = Money(deltaPaise),
    date = dateEpochDay.toDate(),
    reason = reason
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    type = if (type == CategoryType.INCOME.name) CategoryType.INCOME else CategoryType.EXPENSE,
    iconKey = iconKey,
    colorHex = colorHex,
    isEssential = isEssential,
    isCustom = isCustom,
    isArchived = isArchived,
    sortOrder = sortOrder
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    type = type.name,
    iconKey = iconKey,
    colorHex = colorHex,
    isEssential = isEssential,
    isCustom = isCustom,
    isArchived = isArchived,
    sortOrder = sortOrder
)

fun PersonEntity.toDomain() = Person(
    id = id,
    name = name,
    relation = Relation.fromName(relation),
    phone = phone,
    notes = notes,
    isArchived = isArchived
)

fun FamilyMemberEntity.toDomain() = FamilyMember(
    id = id,
    name = name,
    relation = Relation.fromName(relation),
    isSelf = isSelf,
    isArchived = isArchived
)

fun VehicleEntity.toDomain() = Vehicle(
    id = id,
    name = name,
    type = VehicleType.fromName(type),
    registrationNumber = registrationNumber,
    purchaseDate = purchaseDateEpochDay.toDateOrNull(),
    notes = notes,
    isArchived = isArchived
)

fun Vehicle.toEntity() = VehicleEntity(
    id = id,
    name = name,
    type = type.name,
    registrationNumber = registrationNumber,
    purchaseDateEpochDay = purchaseDate.toEpochOrNull(),
    notes = notes,
    isArchived = isArchived
)

// ---- Income and expenses --------------------------------------------------------

fun IncomeSourceEntity.toDomain() = IncomeSource(
    id = id,
    name = name,
    type = IncomeType.fromName(type),
    amount = Money(amountPaise),
    dayOfMonth = dayOfMonth,
    frequency = Frequency.fromName(frequency),
    startDate = startDateEpochDay.toDate(),
    endDate = endDateEpochDay.toDateOrNull(),
    annualIncrementPercent = annualIncrementPercent,
    accountId = accountId,
    isActive = isActive,
    notes = notes
)

fun IncomeSource.toEntity() = IncomeSourceEntity(
    id = id,
    name = name,
    type = type.name,
    amountPaise = amount.paise,
    dayOfMonth = dayOfMonth,
    frequency = frequency.name,
    startDateEpochDay = startDate.toEpoch(),
    endDateEpochDay = endDate.toEpochOrNull(),
    annualIncrementPercent = annualIncrementPercent,
    accountId = accountId,
    isActive = isActive,
    notes = notes
)

fun IncomeTransactionEntity.toDomain() = IncomeTransaction(
    id = id,
    sourceId = sourceId,
    name = name,
    type = IncomeType.fromName(type),
    amount = Money(amountPaise),
    date = dateEpochDay.toDate(),
    accountId = accountId,
    periodKey = periodKey,
    notes = notes
)

fun IncomeTransaction.toEntity() = IncomeTransactionEntity(
    id = id,
    sourceId = sourceId,
    name = name,
    type = type.name,
    amountPaise = amount.paise,
    dateEpochDay = date.toEpoch(),
    accountId = accountId,
    periodKey = periodKey,
    notes = notes
)

fun ExpenseEntity.toDomain() = Expense(
    id = id,
    amount = Money(amountPaise),
    description = description,
    categoryId = categoryId,
    date = dateEpochDay.toDate(),
    paymentMethod = PaymentMethod.fromName(paymentMethod),
    accountId = accountId,
    creditCardId = creditCardId,
    personId = personId,
    vehicleId = vehicleId,
    familyMemberId = familyMemberId,
    linkType = ExpenseLinkType.fromName(linkType),
    linkId = linkId,
    linkPeriodKey = linkPeriodKey,
    notes = notes
)

fun Expense.toEntity(createdAt: LocalDate = date) = ExpenseEntity(
    id = id,
    amountPaise = amount.paise,
    description = description,
    categoryId = categoryId,
    dateEpochDay = date.toEpoch(),
    paymentMethod = paymentMethod.name,
    accountId = accountId,
    creditCardId = creditCardId,
    personId = personId,
    vehicleId = vehicleId,
    familyMemberId = familyMemberId,
    linkType = linkType.name,
    linkId = linkId,
    linkPeriodKey = linkPeriodKey,
    notes = notes,
    createdAtEpochDay = createdAt.toEpoch()
)

// ---- People ledger --------------------------------------------------------------

fun PersonLedgerEntryEntity.toDomain() = PersonLedgerEntry(
    id = id,
    personId = personId,
    amount = Money(amountPaise),
    direction = LedgerDirection.fromName(direction),
    date = dateEpochDay.toDate(),
    expectedDate = expectedDateEpochDay.toDateOrNull(),
    description = description,
    sourceType = LedgerSourceType.fromName(sourceType),
    sourceId = sourceId,
    notes = notes
)

fun PersonLedgerEntry.toEntity() = PersonLedgerEntryEntity(
    id = id,
    personId = personId,
    amountPaise = amount.paise,
    direction = direction.name,
    dateEpochDay = date.toEpoch(),
    expectedDateEpochDay = expectedDate.toEpochOrNull(),
    description = description,
    sourceType = sourceType.name,
    sourceId = sourceId,
    notes = notes
)

fun SettlementEntity.toDomain() = Settlement(
    id = id,
    personId = personId,
    amount = Money(amountPaise),
    direction = SettlementDirection.fromName(direction),
    date = dateEpochDay.toDate(),
    paymentMethod = PaymentMethod.fromName(paymentMethod),
    accountId = accountId,
    notes = notes
)

fun Settlement.toEntity() = SettlementEntity(
    id = id,
    personId = personId,
    amountPaise = amount.paise,
    direction = direction.name,
    dateEpochDay = date.toEpoch(),
    paymentMethod = paymentMethod.name,
    accountId = accountId,
    notes = notes
)

fun SharedExpenseEntity.toDomain() = SharedExpense(
    id = id,
    description = description,
    totalAmount = Money(totalAmountPaise),
    date = dateEpochDay.toDate(),
    categoryId = categoryId,
    splitType = SplitType.fromName(splitType),
    paidByPersonId = paidByPersonId,
    notes = notes
)

fun SharedExpenseShareEntity.toDomain() = SharedExpenseShare(
    id = id,
    sharedExpenseId = sharedExpenseId,
    personId = personId,
    shareAmount = Money(shareAmountPaise),
    sharePercent = sharePercent
)

// ---- Obligations ----------------------------------------------------------------

fun EmiEntity.toDomain() = Emi(
    id = id,
    name = name,
    categoryId = categoryId,
    principal = Money(principalPaise),
    emiAmount = Money(emiAmountPaise),
    interestRatePercent = interestRatePercent,
    startDate = startDateEpochDay.toDate(),
    firstDueDate = firstDueDateEpochDay.toDate(),
    frequency = Frequency.fromName(frequency),
    totalInstallments = totalInstallments,
    openingPaidInstallments = openingPaidInstallments,
    vehicleId = vehicleId,
    accountReference = accountReference,
    autoDebit = autoDebit,
    isActive = isActive,
    notes = notes
)

fun Emi.toEntity() = EmiEntity(
    id = id,
    name = name,
    categoryId = categoryId,
    principalPaise = principal.paise,
    emiAmountPaise = emiAmount.paise,
    interestRatePercent = interestRatePercent,
    startDateEpochDay = startDate.toEpoch(),
    firstDueDateEpochDay = firstDueDate.toEpoch(),
    frequency = frequency.name,
    totalInstallments = totalInstallments,
    openingPaidInstallments = openingPaidInstallments,
    vehicleId = vehicleId,
    accountReference = accountReference,
    autoDebit = autoDebit,
    isActive = isActive,
    notes = notes
)

fun EmiPaymentEntity.toDomain() = EmiPayment(
    id = id,
    emiId = emiId,
    installmentNumber = installmentNumber,
    amount = Money(amountPaise),
    dueDate = dueDateEpochDay.toDate(),
    paidDate = paidDateEpochDay.toDate(),
    notes = notes
)

fun CreditCardEntity.toDomain() = CreditCard(
    id = id,
    name = name,
    bank = bank,
    lastFourDigits = lastFourDigits,
    creditLimit = Money(creditLimitPaise),
    currentOutstanding = Money(currentOutstandingPaise),
    minimumDue = Money(minimumDuePaise),
    statementDayOfMonth = statementDayOfMonth,
    dueDayOfMonth = dueDayOfMonth,
    isActive = isActive,
    notes = notes,
    lastUpdated = if (lastUpdatedEpochDay > 0) lastUpdatedEpochDay.toDate() else null
)

fun CreditCard.toEntity() = CreditCardEntity(
    id = id,
    name = name,
    bank = bank,
    lastFourDigits = lastFourDigits,
    creditLimitPaise = creditLimit.paise,
    currentOutstandingPaise = currentOutstanding.paise,
    minimumDuePaise = minimumDue.paise,
    statementDayOfMonth = statementDayOfMonth,
    dueDayOfMonth = dueDayOfMonth,
    isActive = isActive,
    notes = notes,
    lastUpdatedEpochDay = lastUpdated.toEpochOrNull() ?: 0L
)

fun CreditCardPaymentEntity.toDomain() = CreditCardPayment(
    id = id,
    cardId = cardId,
    amount = Money(amountPaise),
    paidDate = paidDateEpochDay.toDate(),
    accountId = accountId,
    notes = notes
)

fun RecurringBillEntity.toDomain() = RecurringBill(
    id = id,
    name = name,
    categoryId = categoryId,
    amountType = BillAmountType.fromName(amountType),
    amount = Money(amountPaise),
    minAmount = minAmountPaise?.let { Money(it) },
    maxAmount = maxAmountPaise?.let { Money(it) },
    dueDayOfMonth = dueDayOfMonth,
    frequency = Frequency.fromName(frequency),
    startDate = startDateEpochDay.toDate(),
    endDate = endDateEpochDay.toDateOrNull(),
    paymentMethod = PaymentMethod.fromName(paymentMethod),
    autoDebit = autoDebit,
    isEssential = isEssential,
    isActive = isActive,
    notes = notes
)

fun RecurringBill.toEntity() = RecurringBillEntity(
    id = id,
    name = name,
    categoryId = categoryId,
    amountType = amountType.name,
    amountPaise = amount.paise,
    minAmountPaise = minAmount?.paise,
    maxAmountPaise = maxAmount?.paise,
    dueDayOfMonth = dueDayOfMonth,
    frequency = frequency.name,
    startDateEpochDay = startDate.toEpoch(),
    endDateEpochDay = endDate.toEpochOrNull(),
    paymentMethod = paymentMethod.name,
    autoDebit = autoDebit,
    isEssential = isEssential,
    isActive = isActive,
    notes = notes
)

fun BillPaymentEntity.toDomain() = BillPayment(
    id = id,
    billId = billId,
    periodKey = periodKey,
    amount = Money(amountPaise),
    dueDate = dueDateEpochDay.toDate(),
    paidDate = paidDateEpochDay.toDate(),
    notes = notes
)

fun AnnualExpenseEntity.toDomain() = AnnualExpense(
    id = id,
    name = name,
    categoryId = categoryId,
    amount = Money(amountPaise),
    dueMonth = dueMonth,
    dueDayOfMonth = dueDayOfMonth,
    vehicleId = vehicleId,
    isActive = isActive,
    notes = notes
)

fun AnnualExpense.toEntity() = AnnualExpenseEntity(
    id = id,
    name = name,
    categoryId = categoryId,
    amountPaise = amount.paise,
    dueMonth = dueMonth,
    dueDayOfMonth = dueDayOfMonth,
    vehicleId = vehicleId,
    isActive = isActive,
    notes = notes
)

fun AnnualExpensePaymentEntity.toDomain() = AnnualExpensePayment(
    id = id,
    annualExpenseId = annualExpenseId,
    year = year,
    amount = Money(amountPaise),
    paidDate = paidDateEpochDay.toDate()
)

fun SavingsGoalEntity.toDomain() = SavingsGoal(
    id = id,
    name = name,
    targetAmount = Money(targetAmountPaise),
    targetDate = targetDateEpochDay.toDateOrNull(),
    priority = GoalPriority.fromName(priority),
    isEmergencyFund = isEmergencyFund,
    isAchieved = isAchieved,
    notes = notes
)

fun SavingsGoal.toEntity(createdAt: LocalDate) = SavingsGoalEntity(
    id = id,
    name = name,
    targetAmountPaise = targetAmount.paise,
    targetDateEpochDay = targetDate.toEpochOrNull(),
    priority = priority.name,
    isEmergencyFund = isEmergencyFund,
    isAchieved = isAchieved,
    notes = notes,
    createdAtEpochDay = createdAt.toEpoch()
)

fun SavingsContributionEntity.toDomain() = SavingsContribution(
    id = id,
    goalId = goalId,
    amount = Money(amountPaise),
    date = dateEpochDay.toDate(),
    accountId = accountId,
    notes = notes
)

fun BudgetEntity.toDomain() = Budget(
    id = id,
    categoryId = categoryId,
    amount = Money(amountPaise),
    isActive = isActive,
    notes = notes
)

fun Budget.toEntity(createdAt: LocalDate) = BudgetEntity(
    id = id,
    categoryId = categoryId,
    amountPaise = amount.paise,
    isActive = isActive,
    notes = notes,
    createdAtEpochDay = createdAt.toEpoch()
)
