package com.moneyplanner.data.repo

import androidx.room.withTransaction
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.db.entity.ExpenseEntity
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.PaymentMethod
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time repairs for records an older version left inconsistent.
 *
 * Until this version, paying a bill, an EMI or a yearly expense that had no category wrote
 * the payment and skipped the expense. The payment told the forecast the period was
 * settled, and with no expense the money never left the balance, so every such payment
 * left the balance too high by its own amount. Deleting a paid bill's expense from the
 * expense editor did the same. The payment record is the evidence that the money moved,
 * so the missing expense is written now, dated the day the payment was made.
 */
@Singleton
class LedgerRepair @Inject constructor(
    private val database: AppDatabase,
    private val defaults: PaymentDefaults
) {

    /** Writes the missing expense for every payment that has none. Returns how many. */
    suspend fun restoreMissingPaymentExpenses(): Int = database.withTransaction {
        val expenses = database.expenseDao().getAll()
        fun hasExpense(type: ExpenseLinkType, linkId: Long, periodKey: String, paidDay: Long) =
            expenses.any {
                it.linkType == type.name && it.linkId == linkId &&
                    (it.linkPeriodKey == periodKey || (it.linkPeriodKey == null && it.dateEpochDay == paidDay))
            }

        var written = 0

        val bills = database.billDao().getAll().associateBy { it.id }
        database.billDao().getAllPayments().forEach { payment ->
            val bill = bills[payment.billId] ?: return@forEach
            if (hasExpense(ExpenseLinkType.RECURRING_BILL, bill.id, payment.periodKey, payment.paidDateEpochDay)) {
                return@forEach
            }
            val method = PaymentMethod.fromName(bill.paymentMethod)
            database.expenseDao().insert(
                ExpenseEntity(
                    amountPaise = payment.amountPaise,
                    description = bill.name,
                    categoryId = defaults.categoryFor(bill.categoryId, bill.name),
                    dateEpochDay = payment.paidDateEpochDay,
                    paymentMethod = method.name,
                    accountId = defaults.accountFor(method),
                    linkType = ExpenseLinkType.RECURRING_BILL.name,
                    linkId = bill.id,
                    linkPeriodKey = payment.periodKey,
                    notes = REPAIR_NOTE,
                    createdAtEpochDay = payment.paidDateEpochDay
                )
            )
            written++
        }

        val emis = database.emiDao().getAll().associateBy { it.id }
        database.emiDao().getAllPayments().forEach { payment ->
            val emi = emis[payment.emiId] ?: return@forEach
            val key = payment.installmentNumber.toString()
            if (hasExpense(ExpenseLinkType.EMI, emi.id, key, payment.paidDateEpochDay)) return@forEach
            database.expenseDao().insert(
                ExpenseEntity(
                    amountPaise = payment.amountPaise,
                    description = "${emi.name} installment ${payment.installmentNumber}",
                    categoryId = defaults.categoryFor(emi.categoryId, "EMI", emi.name),
                    dateEpochDay = payment.paidDateEpochDay,
                    paymentMethod = PaymentMethod.AUTO_DEBIT.name,
                    accountId = defaults.accountFor(PaymentMethod.AUTO_DEBIT),
                    vehicleId = emi.vehicleId,
                    linkType = ExpenseLinkType.EMI.name,
                    linkId = emi.id,
                    linkPeriodKey = key,
                    notes = REPAIR_NOTE,
                    createdAtEpochDay = payment.paidDateEpochDay
                )
            )
            written++
        }

        val annuals = database.annualExpenseDao().getAll().associateBy { it.id }
        database.annualExpenseDao().getAllPayments().forEach { payment ->
            val annual = annuals[payment.annualExpenseId] ?: return@forEach
            val key = payment.year.toString()
            if (hasExpense(ExpenseLinkType.ANNUAL_EXPENSE, annual.id, key, payment.paidDateEpochDay)) {
                return@forEach
            }
            database.expenseDao().insert(
                ExpenseEntity(
                    amountPaise = payment.amountPaise,
                    description = annual.name,
                    categoryId = defaults.categoryFor(annual.categoryId, annual.name),
                    dateEpochDay = payment.paidDateEpochDay,
                    paymentMethod = PaymentMethod.UPI.name,
                    accountId = defaults.accountFor(PaymentMethod.UPI),
                    vehicleId = annual.vehicleId,
                    linkType = ExpenseLinkType.ANNUAL_EXPENSE.name,
                    linkId = annual.id,
                    linkPeriodKey = key,
                    notes = REPAIR_NOTE,
                    createdAtEpochDay = payment.paidDateEpochDay
                )
            )
            written++
        }

        written
    }

    private companion object {
        const val REPAIR_NOTE = "Added by the app: this payment was recorded without taking it off your balance"
    }
}
