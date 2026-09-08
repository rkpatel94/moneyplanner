package com.moneyplanner.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Wipes every table as part of a restore.
 *
 * Room's own clearAllTables opens a transaction of its own and refuses to run inside one,
 * so it cannot be used here: the restore has to clear and repopulate atomically, or a
 * failed import would leave the user with nothing.
 *
 * The order matters. Expenses reference categories with a restricting foreign key, so the
 * expenses have to go first; everything else is either a child row or cascades from its
 * parent. Getting this order wrong surfaces as a constraint failure rather than as silent
 * damage, but it is spelled out explicitly so it stays correct as tables are added.
 */
@Dao
interface MaintenanceDao {

    @Query("DELETE FROM savings_contributions") suspend fun clearSavingsContributions()
    @Query("DELETE FROM savings_goals") suspend fun clearSavingsGoals()
    @Query("DELETE FROM annual_expense_payments") suspend fun clearAnnualPayments()
    @Query("DELETE FROM annual_expenses") suspend fun clearAnnualExpenses()
    @Query("DELETE FROM bill_payments") suspend fun clearBillPayments()
    @Query("DELETE FROM recurring_bills") suspend fun clearBills()
    @Query("DELETE FROM credit_card_payments") suspend fun clearCardPayments()
    @Query("DELETE FROM credit_cards") suspend fun clearCards()
    @Query("DELETE FROM emi_payments") suspend fun clearEmiPayments()
    @Query("DELETE FROM emis") suspend fun clearEmis()
    @Query("DELETE FROM shared_expense_shares") suspend fun clearShares()
    @Query("DELETE FROM shared_expenses") suspend fun clearSharedExpenses()
    @Query("DELETE FROM settlements") suspend fun clearSettlements()
    @Query("DELETE FROM person_ledger_entries") suspend fun clearLedgerEntries()
    @Query("DELETE FROM expenses") suspend fun clearExpenses()
    @Query("DELETE FROM income_transactions") suspend fun clearIncomeTransactions()
    @Query("DELETE FROM income_sources") suspend fun clearIncomeSources()
    @Query("DELETE FROM balance_adjustments") suspend fun clearAdjustments()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM vehicles") suspend fun clearVehicles()
    @Query("DELETE FROM family_members") suspend fun clearFamilyMembers()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM user_profile") suspend fun clearProfile()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM account_transfers") suspend fun clearTransfers()

    /**
     * Removes everything.
     *
     * Safe to call from inside the restore's own transaction: SQLite nests these as
     * savepoints, so this one joins the enclosing transaction rather than competing with
     * it, and a failed import still rolls the whole thing back. That is the difference
     * from Room's own clearAllTables, which insists on being the outermost transaction
     * and so cannot be used here at all.
     */
    @Transaction
    suspend fun clearEverything() {
        clearBudgets()
        // Transfers reference two accounts each, so they go before the accounts do.
        clearTransfers()
        clearSavingsContributions()
        clearSavingsGoals()
        clearAnnualPayments()
        clearAnnualExpenses()
        clearBillPayments()
        clearBills()
        clearCardPayments()
        clearCards()
        clearEmiPayments()
        clearEmis()
        clearShares()
        clearSharedExpenses()
        clearSettlements()
        clearLedgerEntries()
        // Expenses hold a restricting reference to categories, so they must go first.
        clearExpenses()
        clearIncomeTransactions()
        clearIncomeSources()
        clearAdjustments()
        clearAccounts()
        clearVehicles()
        clearFamilyMembers()
        clearPeople()
        clearCategories()
        clearProfile()
    }
}
