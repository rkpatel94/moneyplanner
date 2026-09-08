package com.moneyplanner.data.repo

import androidx.room.withTransaction
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.periodKey
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.db.dao.AnnualExpenseDao
import com.moneyplanner.data.db.dao.BillDao
import com.moneyplanner.data.db.dao.CreditCardDao
import com.moneyplanner.data.db.dao.EmiDao
import com.moneyplanner.data.db.dao.ExpenseDao
import com.moneyplanner.data.db.dao.SavingsDao
import com.moneyplanner.data.db.entity.AnnualExpensePaymentEntity
import com.moneyplanner.data.db.entity.BillPaymentEntity
import com.moneyplanner.data.db.entity.CreditCardPaymentEntity
import com.moneyplanner.data.db.entity.EmiPaymentEntity
import com.moneyplanner.data.db.entity.SavingsContributionEntity
import com.moneyplanner.data.mapper.toDomain
import com.moneyplanner.data.mapper.toEntity
import com.moneyplanner.domain.calc.EmiCalculator
import com.moneyplanner.domain.model.AnnualExpense
import com.moneyplanner.domain.model.AnnualExpensePayment
import com.moneyplanner.domain.model.BillPayment
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.CreditCardPayment
import com.moneyplanner.domain.model.Emi
import com.moneyplanner.domain.model.EmiPayment
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.RecurringBill
import com.moneyplanner.domain.model.SavingsContribution
import com.moneyplanner.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmiRepository @Inject constructor(
    private val dao: EmiDao,
    private val expenseDao: ExpenseDao,
    private val database: AppDatabase,
    private val today: TodayProvider
) {
    val all: Flow<List<Emi>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val payments: Flow<List<EmiPayment>> =
        dao.observeAllPayments().map { list -> list.map { it.toDomain() } }

    fun byId(id: Long): Flow<Emi?> = dao.observeById(id).map { it?.toDomain() }

    fun paymentsFor(emiId: Long): Flow<List<EmiPayment>> =
        dao.observePaymentsFor(emiId).map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<Emi>> =
        dao.search(query).map { list -> list.map { it.toDomain() } }

    suspend fun add(emi: Emi): Long = dao.insert(emi.toEntity())
    suspend fun update(emi: Emi) = dao.update(emi.toEntity())
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun getById(id: Long) = dao.getById(id)?.toDomain()

    /**
     * Records an installment payment and the expense that goes with it.
     *
     * The expense is linked back to the loan, which is what keeps the money out of the
     * everyday spending average while still showing up in the month's real expenses. The
     * loan is closed automatically once the final installment is recorded.
     */
    suspend fun payInstallment(
        emi: Emi,
        installmentNumber: Int,
        amount: Money,
        paidOn: LocalDate = today.today(),
        paymentMethod: PaymentMethod = PaymentMethod.AUTO_DEBIT,
        categoryId: Long?,
        notes: String = ""
    ) = database.withTransaction {
        dao.insertPayment(
            EmiPaymentEntity(
                emiId = emi.id,
                installmentNumber = installmentNumber,
                amountPaise = amount.paise,
                dueDateEpochDay = EmiCalculator.dueDateFor(emi, installmentNumber).toEpochDay(),
                paidDateEpochDay = paidOn.toEpochDay(),
                notes = notes
            )
        )

        if (categoryId != null) {
            expenseDao.insert(
                Expense(
                    id = 0,
                    amount = amount,
                    description = "${emi.name} installment $installmentNumber",
                    categoryId = categoryId,
                    date = paidOn,
                    paymentMethod = paymentMethod,
                    linkType = ExpenseLinkType.EMI,
                    linkId = emi.id,
                    linkPeriodKey = installmentNumber.toString(),
                    vehicleId = emi.vehicleId
                ).toEntity(paidOn)
            )
        }

        val paidCount = EmiCalculator.paidInstallments(emi, dao.getPaymentsFor(emi.id).map { it.toDomain() })
        if (paidCount >= emi.totalInstallments) {
            dao.update(emi.copy(isActive = false).toEntity())
        }
    }

    /**
     * Undoes an installment payment, reopening the loan if it had been closed.
     *
     * The expense recorded alongside the payment goes with it. Leaving it behind would
     * count the installment twice: once as an expense still reducing the balance, and
     * again as an unpaid installment returning to the forecast.
     */
    suspend fun undoInstallment(emi: Emi, installmentNumber: Int) = database.withTransaction {
        val payment = dao.getPaymentsFor(emi.id)
            .firstOrNull { it.installmentNumber == installmentNumber }
        dao.deletePayment(emi.id, installmentNumber)
        expenseDao.deleteLinkedPeriod(
            linkType = ExpenseLinkType.EMI.name,
            linkId = emi.id,
            periodKey = installmentNumber.toString(),
            paidDateEpochDay = payment?.paidDateEpochDay ?: Long.MIN_VALUE
        )
        if (!emi.isActive) dao.update(emi.copy(isActive = true).toEntity())
    }
}

@Singleton
class CreditCardRepository @Inject constructor(
    private val dao: CreditCardDao,
    private val database: AppDatabase,
    private val today: TodayProvider
) {
    val all: Flow<List<CreditCard>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val payments: Flow<List<CreditCardPayment>> =
        dao.observeAllPayments().map { list -> list.map { it.toDomain() } }

    fun byId(id: Long): Flow<CreditCard?> = dao.observeById(id).map { it?.toDomain() }

    fun paymentsFor(cardId: Long): Flow<List<CreditCardPayment>> =
        dao.observePaymentsFor(cardId).map { list -> list.map { it.toDomain() } }

    suspend fun add(card: CreditCard): Long = dao.insert(card.toEntity())

    suspend fun update(card: CreditCard) =
        dao.update(card.copy(lastUpdated = today.today()).toEntity())

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun getById(id: Long) = dao.getById(id)?.toDomain()

    /**
     * Records a payment towards a card bill and reduces the outstanding by the same
     * amount, in one transaction. No expense row is created: the cash movement is the
     * card payment itself, and adding an expense as well would subtract it twice.
     */
    suspend fun payBill(
        card: CreditCard,
        amount: Money,
        paidOn: LocalDate = today.today(),
        accountId: Long? = null,
        notes: String = ""
    ) = database.withTransaction {
        dao.insertPayment(
            CreditCardPaymentEntity(
                cardId = card.id,
                amountPaise = amount.paise,
                paidDateEpochDay = paidOn.toEpochDay(),
                accountId = accountId,
                notes = notes
            )
        )
        val reduced = (card.currentOutstanding - amount).coerceAtLeastZero()
        dao.update(
            card.copy(
                currentOutstanding = reduced,
                minimumDue = if (reduced.isZero) Money.ZERO else card.minimumDue,
                lastUpdated = paidOn
            ).toEntity()
        )
    }

    /** Lets the user type in the figure from their latest statement. */
    suspend fun updateOutstanding(card: CreditCard, outstanding: Money, minimumDue: Money) =
        dao.update(
            card.copy(
                currentOutstanding = outstanding,
                minimumDue = minimumDue,
                lastUpdated = today.today()
            ).toEntity()
        )
}

@Singleton
class BillRepository @Inject constructor(
    private val dao: BillDao,
    private val expenseDao: ExpenseDao,
    private val database: AppDatabase,
    private val today: TodayProvider
) {
    val all: Flow<List<RecurringBill>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val payments: Flow<List<BillPayment>> =
        dao.observeAllPayments().map { list -> list.map { it.toDomain() } }

    fun byId(id: Long): Flow<RecurringBill?> = dao.observeById(id).map { it?.toDomain() }

    fun paymentsFor(billId: Long): Flow<List<BillPayment>> =
        dao.observePaymentsFor(billId).map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<RecurringBill>> =
        dao.search(query).map { list -> list.map { it.toDomain() } }

    suspend fun add(bill: RecurringBill): Long = dao.insert(bill.toEntity())
    suspend fun update(bill: RecurringBill) = dao.update(bill.toEntity())
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun getById(id: Long) = dao.getById(id)?.toDomain()

    /**
     * Marks one period of a bill as paid and records the matching expense.
     *
     * Variable bills such as electricity are paid at the amount actually billed, which
     * is why the amount is passed in rather than taken from the estimate.
     */
    suspend fun markPaid(
        bill: RecurringBill,
        month: YearMonth,
        amount: Money,
        dueDate: LocalDate,
        paidOn: LocalDate = today.today(),
        notes: String = ""
    ) = database.withTransaction {
        dao.insertPayment(
            BillPaymentEntity(
                billId = bill.id,
                periodKey = month.periodKey(),
                amountPaise = amount.paise,
                dueDateEpochDay = dueDate.toEpochDay(),
                paidDateEpochDay = paidOn.toEpochDay(),
                notes = notes
            )
        )
        if (bill.categoryId != null) {
            expenseDao.insert(
                Expense(
                    id = 0,
                    amount = amount,
                    description = bill.name,
                    categoryId = bill.categoryId,
                    date = paidOn,
                    paymentMethod = bill.paymentMethod,
                    linkType = ExpenseLinkType.RECURRING_BILL,
                    linkId = bill.id,
                    linkPeriodKey = month.periodKey()
                ).toEntity(paidOn)
            )
        }
    }

    /**
     * Undoes one period of a bill.
     *
     * Only the expense for that period is removed. [ExpenseLinkType.RECURRING_BILL] plus
     * the bill id names the bill, not the payment, so deleting on those two alone would
     * take every month ever recorded against it.
     */
    suspend fun undoPayment(billId: Long, month: YearMonth) = database.withTransaction {
        val periodKey = month.periodKey()
        val payment = dao.getPayment(billId, periodKey)
        dao.deletePayment(billId, periodKey)
        expenseDao.deleteLinkedPeriod(
            linkType = ExpenseLinkType.RECURRING_BILL.name,
            linkId = billId,
            periodKey = periodKey,
            paidDateEpochDay = payment?.paidDateEpochDay ?: Long.MIN_VALUE
        )
    }
}

@Singleton
class AnnualExpenseRepository @Inject constructor(
    private val dao: AnnualExpenseDao,
    private val expenseDao: ExpenseDao,
    private val database: AppDatabase,
    private val today: TodayProvider
) {
    val all: Flow<List<AnnualExpense>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val payments: Flow<List<AnnualExpensePayment>> =
        dao.observeAllPayments().map { list -> list.map { it.toDomain() } }

    suspend fun add(expense: AnnualExpense): Long = dao.insert(expense.toEntity())
    suspend fun update(expense: AnnualExpense) = dao.update(expense.toEntity())
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun getById(id: Long) = dao.getById(id)?.toDomain()

    suspend fun markPaid(
        annual: AnnualExpense,
        year: Int,
        amount: Money,
        paidOn: LocalDate = today.today()
    ) = database.withTransaction {
        dao.insertPayment(
            AnnualExpensePaymentEntity(
                annualExpenseId = annual.id,
                year = year,
                amountPaise = amount.paise,
                paidDateEpochDay = paidOn.toEpochDay()
            )
        )
        if (annual.categoryId != null) {
            expenseDao.insert(
                Expense(
                    id = 0,
                    amount = amount,
                    description = annual.name,
                    categoryId = annual.categoryId,
                    date = paidOn,
                    paymentMethod = PaymentMethod.UPI,
                    linkType = ExpenseLinkType.ANNUAL_EXPENSE,
                    linkId = annual.id,
                    linkPeriodKey = year.toString(),
                    vehicleId = annual.vehicleId
                ).toEntity(paidOn)
            )
        }
    }

    /** Undoes one year of a yearly commitment, leaving every other year untouched. */
    suspend fun undoPayment(id: Long, year: Int) = database.withTransaction {
        val payment = dao.getPayment(id, year)
        dao.deletePayment(id, year)
        expenseDao.deleteLinkedPeriod(
            linkType = ExpenseLinkType.ANNUAL_EXPENSE.name,
            linkId = id,
            periodKey = year.toString(),
            paidDateEpochDay = payment?.paidDateEpochDay ?: Long.MIN_VALUE
        )
    }
}

@Singleton
class SavingsRepository @Inject constructor(
    private val dao: SavingsDao,
    private val today: TodayProvider
) {
    val goals: Flow<List<SavingsGoal>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val contributions: Flow<List<SavingsContribution>> =
        dao.observeAllContributions().map { list -> list.map { it.toDomain() } }

    val emergencyFund: Flow<SavingsGoal?> = dao.observeEmergencyFund().map { it?.toDomain() }

    fun byId(id: Long): Flow<SavingsGoal?> = dao.observeById(id).map { it?.toDomain() }

    fun contributionsFor(goalId: Long): Flow<List<SavingsContribution>> =
        dao.observeContributionsFor(goalId).map { list -> list.map { it.toDomain() } }

    suspend fun add(goal: SavingsGoal): Long = dao.insert(goal.toEntity(today.today()))
    suspend fun update(goal: SavingsGoal) = dao.update(goal.toEntity(today.today()))
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun getById(id: Long) = dao.getById(id)?.toDomain()

    suspend fun contribute(
        goalId: Long,
        amount: Money,
        on: LocalDate = today.today(),
        notes: String = ""
    ): Long = dao.insertContribution(
        SavingsContributionEntity(
            goalId = goalId,
            amountPaise = amount.paise,
            dateEpochDay = on.toEpochDay(),
            notes = notes
        )
    )

    /** A withdrawal is stored as a negative contribution so the history stays complete. */
    suspend fun withdraw(
        goalId: Long,
        amount: Money,
        on: LocalDate = today.today(),
        notes: String = ""
    ): Long = contribute(goalId, -amount.abs(), on, notes)

    suspend fun deleteContribution(id: Long) = dao.deleteContributionById(id)

    /** Creates the emergency fund goal if the user does not have one yet. */
    suspend fun ensureEmergencyFund(target: Money): Long {
        dao.getEmergencyFund()?.let { return it.id }
        return dao.insert(
            SavingsGoal(
                id = 0,
                name = "Emergency fund",
                targetAmount = target,
                targetDate = null,
                priority = com.moneyplanner.domain.model.GoalPriority.HIGH,
                isEmergencyFund = true,
                isAchieved = false,
                notes = ""
            ).toEntity(today.today())
        )
    }
}
