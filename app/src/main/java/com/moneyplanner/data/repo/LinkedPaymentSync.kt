package com.moneyplanner.data.repo

import com.moneyplanner.data.db.dao.AnnualExpenseDao
import com.moneyplanner.data.db.dao.BillDao
import com.moneyplanner.data.db.dao.EmiDao
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.ExpenseLinkType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a payment record in step with the expense that represents it.
 *
 * Marking a bill paid writes two rows: the payment, which tells the forecast to stop
 * expecting that period, and the expense, which takes the money out of the balance. They
 * describe one event, so if the user later corrects the amount on the expense — the
 * electricity bill came in higher than the estimate — the payment has to move with it.
 * Otherwise the same payment is recorded at two different amounts and the month's reports
 * disagree with the bill's own history.
 *
 * Which occurrence to update comes from the expense's own period key, so correcting
 * September never touches August.
 */
@Singleton
class LinkedPaymentSync @Inject constructor(
    private val emiDao: EmiDao,
    private val billDao: BillDao,
    private val annualDao: AnnualExpenseDao
) {

    suspend fun syncFromExpense(expense: Expense) {
        val linkId = expense.linkId ?: return
        val periodKey = expense.linkPeriodKey ?: return
        val paise = expense.amount.paise
        val paidOn = expense.date.toEpochDay()

        when (expense.linkType) {
            ExpenseLinkType.EMI -> {
                val installment = periodKey.toIntOrNull() ?: return
                emiDao.updatePaymentAmount(linkId, installment, paise, paidOn)
            }
            ExpenseLinkType.RECURRING_BILL ->
                billDao.updatePaymentAmount(linkId, periodKey, paise, paidOn)
            ExpenseLinkType.ANNUAL_EXPENSE -> {
                val year = periodKey.toIntOrNull() ?: return
                annualDao.updatePaymentAmount(linkId, year, paise, paidOn)
            }
            // Card payments and settlements keep their own records and are not edited
            // through the expense screen; everything else has nothing to keep in step.
            else -> Unit
        }
    }
}
