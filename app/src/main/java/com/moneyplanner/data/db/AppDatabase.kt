package com.moneyplanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.moneyplanner.data.db.dao.AnnualExpenseDao
import com.moneyplanner.data.db.dao.BillDao
import com.moneyplanner.data.db.dao.BudgetDao
import com.moneyplanner.data.db.dao.CategoryDao
import com.moneyplanner.data.db.dao.CreditCardDao
import com.moneyplanner.data.db.dao.EmiDao
import com.moneyplanner.data.db.dao.ExpenseDao
import com.moneyplanner.data.db.dao.FamilyDao
import com.moneyplanner.data.db.dao.IncomeDao
import com.moneyplanner.data.db.dao.MaintenanceDao
import com.moneyplanner.data.db.dao.PeopleDao
import com.moneyplanner.data.db.dao.ProfileDao
import com.moneyplanner.data.db.dao.SavingsDao
import com.moneyplanner.data.db.dao.VehicleDao
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

@Database(
    entities = [
        UserProfileEntity::class,
        AccountEntity::class,
        AccountTransferEntity::class,
        BalanceAdjustmentEntity::class,
        CategoryEntity::class,
        PersonEntity::class,
        FamilyMemberEntity::class,
        VehicleEntity::class,
        IncomeSourceEntity::class,
        IncomeTransactionEntity::class,
        ExpenseEntity::class,
        PersonLedgerEntryEntity::class,
        SettlementEntity::class,
        SharedExpenseEntity::class,
        SharedExpenseShareEntity::class,
        EmiEntity::class,
        EmiPaymentEntity::class,
        CreditCardEntity::class,
        CreditCardPaymentEntity::class,
        RecurringBillEntity::class,
        BillPaymentEntity::class,
        AnnualExpenseEntity::class,
        AnnualExpensePaymentEntity::class,
        SavingsGoalEntity::class,
        SavingsContributionEntity::class,
        BudgetEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun categoryDao(): CategoryDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun familyDao(): FamilyDao
    abstract fun incomeDao(): IncomeDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun peopleDao(): PeopleDao
    abstract fun emiDao(): EmiDao
    abstract fun creditCardDao(): CreditCardDao
    abstract fun billDao(): BillDao
    abstract fun annualExpenseDao(): AnnualExpenseDao
    abstract fun savingsDao(): SavingsDao
    abstract fun budgetDao(): BudgetDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val NAME = "money_planner.db"
    }
}
