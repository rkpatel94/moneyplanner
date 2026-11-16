package com.moneyplanner.data.repo

import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.ExpenseLinkType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes an expense together with whatever it was half of.
 *
 * An expense created by marking a bill, an EMI or a yearly expense paid is one half of a
 * pair, and removing it on its own leaves the payment record behind: the forecast still
 * believes the bill is settled while the money returns to the balance, which is the double
 * count the whole app is built to avoid. Recent activity always routed its deletes through
 * the obligation; the expense editor did not, and it is what opens when a row is tapped
 * in Recent activity, Transactions or Search. Both now come through here, so there is one
 * rule and one place it is kept.
 */
@Singleton
class RecordRemover @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val billRepository: BillRepository,
    private val emiRepository: EmiRepository,
    private val annualExpenseRepository: AnnualExpenseRepository,
    private val peopleRepository: PeopleRepository
) {

    suspend fun deleteExpense(expense: Expense) =
        deleteExpense(expense.id, expense.linkType, expense.linkId, expense.linkPeriodKey)

    suspend fun deleteExpense(
        expenseId: Long,
        linkType: ExpenseLinkType,
        linkId: Long?,
        periodKey: String?
    ) {
        when (linkType) {
            ExpenseLinkType.RECURRING_BILL ->
                if (linkId != null && periodKey != null) {
                    billRepository.undoPayment(linkId, periodKey)
                } else {
                    expenseRepository.delete(expenseId)
                }

            ExpenseLinkType.EMI -> {
                val installment = periodKey?.toIntOrNull()
                val emi = linkId?.let { emiRepository.getById(it) }
                if (emi != null && installment != null) {
                    emiRepository.undoInstallment(emi, installment)
                } else {
                    expenseRepository.delete(expenseId)
                }
            }

            ExpenseLinkType.ANNUAL_EXPENSE -> {
                val year = periodKey?.toIntOrNull()
                if (linkId != null && year != null) {
                    annualExpenseRepository.undoPayment(linkId, year)
                } else {
                    expenseRepository.delete(expenseId)
                }
            }

            // The out-of-pocket payment for a shared bill. Deleting it alone would leave
            // the split and everybody's share behind, so the shared expense itself goes,
            // which takes the balances it created with it.
            ExpenseLinkType.SHARED_EXPENSE ->
                if (linkId != null) {
                    peopleRepository.deleteSharedExpense(linkId)
                } else {
                    expenseRepository.delete(expenseId)
                }

            else -> expenseRepository.delete(expenseId)
        }
    }

    companion object {
        /** What else a delete takes with it, said before anything is removed. */
        fun consequenceOf(linkType: ExpenseLinkType): String? = when (linkType) {
            ExpenseLinkType.RECURRING_BILL -> "The bill goes back to unpaid for that period."
            ExpenseLinkType.EMI -> "The installment goes back to unpaid."
            ExpenseLinkType.ANNUAL_EXPENSE -> "The yearly expense goes back to unpaid for that year."
            ExpenseLinkType.SHARED_EXPENSE -> "The split and everyone's share of it go too."
            else -> null
        }
    }
}
