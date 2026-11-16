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
import com.moneyplanner.domain.calc.BillPeriod
import com.moneyplanner.domain.calc.CreditCardCalculator
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
    private val today: TodayProvider,
    private val defaults: PaymentDefaults
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
        categoryId: Long? = null,
        accountId: Long? = null,
        notes: String = ""
    ): Unit = database.withTransaction {
        // A second tap on "Record paid" before the screen has caught up names the same
        // installment again. The payment row would simply be replaced, but a second
        // expense would still be written and the installment taken off the balance twice.
        if (dao.getPaymentsFor(emi.id).any { it.installmentNumber == installmentNumber }) {
            return@withTransaction
        }

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

        // Always written. Without it the installment leaves the forecast, because it is
        // paid, without ever leaving the balance. The loan's own category wins; a loan
        // with none is filed under "EMI".
        expenseDao.insert(
            Expense(
                id = 0,
                amount = amount,
                description = "${emi.name} installment $installmentNumber",
                categoryId = defaults.categoryFor(categoryId ?: emi.categoryId, "EMI", emi.name),
                date = paidOn,
                paymentMethod = paymentMethod,
                accountId = accountId ?: defaults.accountFor(paymentMethod),
                linkType = ExpenseLinkType.EMI,
                linkId = emi.id,
                linkPeriodKey = installmentNumber.toString(),
                vehicleId = emi.vehicleId
            ).toEntity(paidOn)
        )

        val paidCount = EmiCalculator.paidInstallments(emi, dao.getPaymentsFor(emi.id).map { it.toDomain() })
        if (paidCount >= emi.totalInstallments) {
            dao.update(emi.copy(isActive = false).toEntity())
        }
    }

    /**
     * Records an expense already in the ledger as the payment of installment [installment].
     *
     * The expense already took the money off the balance; linking it stops the forecast
     * expecting the installment as well. Returns false when it is already paid.
     */
    suspend fun settleWithExpense(emi: Emi, installment: Int, expense: Expense): Boolean =
        database.withTransaction {
            if (dao.getPaymentsFor(emi.id).any { it.installmentNumber == installment }) {
                return@withTransaction false
            }
            dao.insertPayment(
                EmiPaymentEntity(
                    emiId = emi.id,
                    installmentNumber = installment,
                    amountPaise = expense.amount.paise,
                    dueDateEpochDay = EmiCalculator.dueDateFor(emi, installment).toEpochDay(),
                    paidDateEpochDay = expense.date.toEpochDay(),
                    notes = ""
                )
            )
            expenseDao.update(
                expense.copy(
                    linkType = ExpenseLinkType.EMI,
                    linkId = emi.id,
                    linkPeriodKey = installment.toString()
                ).toEntity()
            )
            val paidCount = EmiCalculator.paidInstallments(emi, dao.getPaymentsFor(emi.id).map { it.toDomain() })
            if (paidCount >= emi.totalInstallments) dao.update(emi.copy(isActive = false).toEntity())
            true
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
    private val today: TodayProvider,
    private val defaults: PaymentDefaults,
    private val expenseDao: ExpenseDao
) {
    val all: Flow<List<CreditCard>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val payments: Flow<List<CreditCardPayment>> =
        dao.observeAllPayments().map { list -> list.map { it.toDomain() } }

    fun byId(id: Long): Flow<CreditCard?> = dao.observeById(id).map { it?.toDomain() }

    fun paymentsFor(cardId: Long): Flow<List<CreditCardPayment>> =
        dao.observePaymentsFor(cardId).map { list -> list.map { it.toDomain() } }

    suspend fun add(card: CreditCard): Long =
        dao.insert(card.copy(lastUpdated = card.lastUpdated ?: today.today()).toEntity())

    /**
     * Saves the card's own details: its name, limit, billing days and notes.
     *
     * The statement figure is left exactly as it was. It changes through [updateStatement]
     * and through payments, never here, because saving a new name used to stamp the card
     * as freshly updated, and every purchase made before that day then dropped out of what
     * the card was said to owe.
     */
    suspend fun update(card: CreditCard) = database.withTransaction {
        val existing = dao.getById(card.id)?.toDomain()
        dao.update(
            if (existing == null) {
                card.toEntity()
            } else {
                card.copy(
                    currentOutstanding = existing.currentOutstanding,
                    minimumDue = existing.minimumDue,
                    lastUpdated = existing.lastUpdated,
                    statementDate = existing.statementDate
                ).toEntity()
            }
        )
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun getById(id: Long) = dao.getById(id)?.toDomain()

    suspend fun getPayment(id: Long): CreditCardPayment? = dao.getPayment(id)?.toDomain()

    /**
     * Records a payment towards a card bill, and how and from where it was paid.
     *
     * No expense row is created: the cash movement is the card payment itself, and adding
     * an expense as well would subtract it twice. The outstanding comes down by the amount,
     * and may go below zero: paying the statement and the spending since in one go leaves
     * the card in credit against the purchases still to be billed, rather than losing the
     * overpayment as it used to when the figure was floored at zero.
     *
     * The date the card was last updated is no longer moved by a payment. It marks where
     * the statement figure ends, and moving it made every purchase between the statement
     * and the payment disappear from the card.
     */
    suspend fun payBill(
        card: CreditCard,
        amount: Money,
        paidOn: LocalDate = today.today(),
        paymentMethod: PaymentMethod = PaymentMethod.BANK_TRANSFER,
        accountId: Long? = null,
        notes: String = ""
    ): Long = database.withTransaction {
        val method = if (paymentMethod == PaymentMethod.CREDIT_CARD) PaymentMethod.BANK_TRANSFER else paymentMethod
        val id = dao.insertPayment(
            CreditCardPaymentEntity(
                cardId = card.id,
                amountPaise = amount.paise,
                paidDateEpochDay = paidOn.toEpochDay(),
                accountId = accountId ?: defaults.accountFor(method),
                notes = notes,
                paymentMethod = method.name
            )
        )
        val fresh = dao.getById(card.id)?.toDomain() ?: card
        if (CreditCardCalculator.paymentReducesFigure(fresh, paidOn)) {
            dao.update(fresh.copy(currentOutstanding = fresh.currentOutstanding - amount).toEntity())
        }
        id
    }

    /**
     * Corrects a payment already recorded, moving the outstanding by the difference.
     *
     * A payment that sits inside the statement on file is already reflected in the bank's
     * figure, so changing or removing it leaves that figure alone.
     */
    suspend fun updatePayment(updated: CreditCardPayment) = database.withTransaction {
        val old = dao.getPayment(updated.id)?.toDomain() ?: return@withTransaction
        val method = if (updated.paymentMethod == PaymentMethod.CREDIT_CARD) {
            PaymentMethod.BANK_TRANSFER
        } else {
            updated.paymentMethod
        }
        dao.updatePayment(updated.copy(cardId = old.cardId, paymentMethod = method).toEntity())
        val card = dao.getById(old.cardId)?.toDomain() ?: return@withTransaction
        var figure = card.currentOutstanding
        if (CreditCardCalculator.paymentReducesFigure(card, old.paidDate)) figure += old.amount
        if (CreditCardCalculator.paymentReducesFigure(card, updated.paidDate)) figure -= updated.amount
        if (figure != card.currentOutstanding) {
            dao.update(card.copy(currentOutstanding = figure).toEntity())
        }
    }

    /**
     * Turns an expense that was really a card bill payment into one.
     *
     * Paying a card is not spending: the purchases were already counted on the card. As an
     * expense it counted them twice, once on the day of each purchase via the card bill and
     * again on the day the bill was paid. The expense goes and a card payment takes its
     * place, with the same amount, date and account, so the bank balance does not move.
     */
    suspend fun settleExpenseAsPayment(card: CreditCard, expense: Expense): Long = database.withTransaction {
        expenseDao.deleteById(expense.id)
        payBill(
            card = card,
            amount = expense.amount,
            paidOn = expense.date,
            paymentMethod = expense.paymentMethod,
            accountId = expense.accountId,
            notes = expense.description
        )
    }

    /** Removes a payment recorded by mistake, putting what it took off the card back on. */
    suspend fun deletePayment(paymentId: Long) = database.withTransaction {
        val payment = dao.getPayment(paymentId)?.toDomain() ?: return@withTransaction
        dao.deletePaymentById(paymentId)
        val card = dao.getById(payment.cardId)?.toDomain() ?: return@withTransaction
        if (CreditCardCalculator.paymentReducesFigure(card, payment.paidDate)) {
            dao.update(card.copy(currentOutstanding = card.currentOutstanding + payment.amount).toEntity())
        }
    }

    /**
     * Copies in a new statement from the bank.
     *
     * [amount] is the bank's figure as of [statementOn], which already reflects every
     * payment made before that date. Payments recorded here since that date have not
     * reached the bank's figure yet, so they come off it; purchases after it are what the
     * next statement will hold.
     */
    suspend fun updateStatement(
        card: CreditCard,
        statementOn: LocalDate,
        amount: Money,
        minimumDue: Money
    ) = database.withTransaction {
        val paidSince = dao.getPaymentsFor(card.id)
            .filter { it.paidDateEpochDay > statementOn.toEpochDay() }
            .sumOf { it.amountPaise }
        val fresh = dao.getById(card.id)?.toDomain() ?: card
        dao.update(
            fresh.copy(
                currentOutstanding = amount - Money(paidSince),
                minimumDue = minimumDue,
                statementDate = statementOn,
                lastUpdated = today.today()
            ).toEntity()
        )
    }
}

@Singleton
class BillRepository @Inject constructor(
    private val dao: BillDao,
    private val expenseDao: ExpenseDao,
    private val database: AppDatabase,
    private val today: TodayProvider,
    private val defaults: PaymentDefaults,
    private val cardDao: CreditCardDao
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
    /**
     * Marks the occurrence of [bill] due on [dueDate] as paid, with its expense.
     *
     * Which occurrence comes from [BillPeriod]: the month for most bills, the due date
     * itself for a weekly one. A second call for an occurrence already paid does nothing,
     * so a double tap cannot take the bill off the balance twice.
     */
    suspend fun markPaid(
        bill: RecurringBill,
        dueDate: LocalDate,
        amount: Money,
        paidOn: LocalDate = today.today(),
        notes: String = "",
        accountId: Long? = null
    ): Unit = database.withTransaction {
        val periodKey = BillPeriod.keyFor(bill, dueDate)
        if (dao.getPayment(bill.id, periodKey) != null) return@withTransaction

        dao.insertPayment(
            BillPaymentEntity(
                billId = bill.id,
                periodKey = periodKey,
                amountPaise = amount.paise,
                dueDateEpochDay = dueDate.toEpochDay(),
                paidDateEpochDay = paidOn.toEpochDay(),
                notes = notes
            )
        )

        // A bill paid on a card goes on the card when there is only one to choose from;
        // with several, it is left for the user to say which, and shows as unlinked.
        val onCard = bill.paymentMethod == PaymentMethod.CREDIT_CARD
        val cardId = if (onCard) cardDao.getAll().filter { it.isActive }.singleOrNull()?.id else null

        // Always written, whatever the bill's category. Without it a bill with "No
        // category", the editor's default, left the forecast without ever leaving the balance.
        expenseDao.insert(
            Expense(
                id = 0,
                amount = amount,
                description = bill.name,
                categoryId = defaults.categoryFor(bill.categoryId, bill.name),
                date = paidOn,
                paymentMethod = bill.paymentMethod,
                accountId = accountId ?: defaults.accountFor(bill.paymentMethod),
                creditCardId = cardId,
                linkType = ExpenseLinkType.RECURRING_BILL,
                linkId = bill.id,
                linkPeriodKey = periodKey
            ).toEntity(paidOn)
        )
    }

    /**
     * Records an expense already in the ledger as the payment of one occurrence of [bill].
     *
     * For a bill paid outside the Bills screen — a bank transfer imported from an SMS, a
     * rent payment typed in as an ordinary expense. The money has already left the balance
     * through that expense; what was missing was the payment record, so the forecast kept
     * expecting the bill and counted it a second time. Returns false when that occurrence
     * already has a payment, in which case nothing changes.
     */
    suspend fun settleWithExpense(bill: RecurringBill, dueDate: LocalDate, expense: Expense): Boolean =
        database.withTransaction {
            val periodKey = BillPeriod.keyFor(bill, dueDate)
            if (dao.getPayment(bill.id, periodKey) != null) return@withTransaction false
            dao.insertPayment(
                BillPaymentEntity(
                    billId = bill.id,
                    periodKey = periodKey,
                    amountPaise = expense.amount.paise,
                    dueDateEpochDay = dueDate.toEpochDay(),
                    paidDateEpochDay = expense.date.toEpochDay(),
                    notes = ""
                )
            )
            expenseDao.update(
                expense.copy(
                    linkType = ExpenseLinkType.RECURRING_BILL,
                    linkId = bill.id,
                    linkPeriodKey = periodKey
                ).toEntity()
            )
            true
        }

    /** Undoes one monthly period of a bill. See the overload taking a period key. */
    suspend fun undoPayment(billId: Long, month: YearMonth) = undoPayment(billId, month.periodKey())

    /**
     * Undoes one occurrence of a bill.
     *
     * Only the expense for that occurrence is removed. [ExpenseLinkType.RECURRING_BILL]
     * plus the bill id names the bill, not the payment, so deleting on those two alone
     * would take every month ever recorded against it.
     */
    suspend fun undoPayment(billId: Long, periodKey: String) = database.withTransaction {
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
    private val today: TodayProvider,
    private val defaults: PaymentDefaults
) {
    val all: Flow<List<AnnualExpense>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val payments: Flow<List<AnnualExpensePayment>> =
        dao.observeAllPayments().map { list -> list.map { it.toDomain() } }

    suspend fun add(expense: AnnualExpense): Long = dao.insert(expense.toEntity())
    suspend fun update(expense: AnnualExpense) = dao.update(expense.toEntity())
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun getById(id: Long) = dao.getById(id)?.toDomain()

    /**
     * Marks one year of a yearly commitment paid, with its expense.
     *
     * The expense is always written, whatever the category, and a second call for a year
     * already paid does nothing, so a double tap cannot count it twice.
     */
    suspend fun markPaid(
        annual: AnnualExpense,
        year: Int,
        amount: Money,
        paidOn: LocalDate = today.today(),
        paymentMethod: PaymentMethod = PaymentMethod.UPI,
        accountId: Long? = null
    ): Unit = database.withTransaction {
        if (dao.getPayment(annual.id, year) != null) return@withTransaction

        dao.insertPayment(
            AnnualExpensePaymentEntity(
                annualExpenseId = annual.id,
                year = year,
                amountPaise = amount.paise,
                paidDateEpochDay = paidOn.toEpochDay()
            )
        )
        expenseDao.insert(
            Expense(
                id = 0,
                amount = amount,
                description = annual.name,
                categoryId = defaults.categoryFor(annual.categoryId, annual.name),
                date = paidOn,
                paymentMethod = paymentMethod,
                accountId = accountId ?: defaults.accountFor(paymentMethod),
                linkType = ExpenseLinkType.ANNUAL_EXPENSE,
                linkId = annual.id,
                linkPeriodKey = year.toString(),
                vehicleId = annual.vehicleId
            ).toEntity(paidOn)
        )
    }

    /** Records an expense already in the ledger as one year's payment of [annual]. */
    suspend fun settleWithExpense(annual: AnnualExpense, year: Int, expense: Expense): Boolean =
        database.withTransaction {
            if (dao.getPayment(annual.id, year) != null) return@withTransaction false
            dao.insertPayment(
                AnnualExpensePaymentEntity(
                    annualExpenseId = annual.id,
                    year = year,
                    amountPaise = expense.amount.paise,
                    paidDateEpochDay = expense.date.toEpochDay()
                )
            )
            expenseDao.update(
                expense.copy(
                    linkType = ExpenseLinkType.ANNUAL_EXPENSE,
                    linkId = annual.id,
                    linkPeriodKey = year.toString()
                ).toEntity()
            )
            true
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

    suspend fun getContribution(id: Long): SavingsContribution? =
        dao.getContribution(id)?.toDomain()

    /**
     * Corrects an entry already recorded against a goal.
     *
     * The sign carries the direction: money taken back out of a goal is stored as a
     * negative contribution, which is what keeps the history complete rather than
     * quietly erasing the deposit it reversed. An edit that flips the direction simply
     * flips the sign, and the goal balance follows, since it is the sum of the entries
     * and never a stored total.
     */
    suspend fun updateContribution(contribution: SavingsContribution) =
        dao.updateContribution(
            SavingsContributionEntity(
                id = contribution.id,
                goalId = contribution.goalId,
                amountPaise = contribution.amount.paise,
                dateEpochDay = contribution.date.toEpochDay(),
                // Carried rather than defaulted: rebuilding the row without it would
                // blank whichever account the entry was filed against.
                accountId = contribution.accountId,
                notes = contribution.notes
            )
        )

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
