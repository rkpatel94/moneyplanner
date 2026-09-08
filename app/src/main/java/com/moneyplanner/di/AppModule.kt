package com.moneyplanner.di

import android.content.Context
import androidx.room.Room
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.db.dao.AnnualExpenseDao
import com.moneyplanner.data.db.dao.BillDao
import com.moneyplanner.data.db.dao.BudgetDao
import com.moneyplanner.data.db.dao.CategoryDao
import com.moneyplanner.data.db.dao.CreditCardDao
import com.moneyplanner.data.db.dao.EmiDao
import com.moneyplanner.data.db.dao.ExpenseDao
import com.moneyplanner.data.db.dao.FamilyDao
import com.moneyplanner.data.db.dao.IncomeDao
import com.moneyplanner.data.db.dao.PeopleDao
import com.moneyplanner.data.db.dao.ProfileDao
import com.moneyplanner.data.db.dao.SavingsDao
import com.moneyplanner.data.db.dao.VehicleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Foreign keys are enforced so an expense can never point at a deleted
            // category and a ledger entry can never outlive the person it belongs to.
            //
            // No destructive fallback on purpose: if a migration is ever missing the app
            // must fail loudly rather than silently wipe records the user cannot recover.
            .addMigrations(*com.moneyplanner.data.db.Migrations.ALL)
            .build()

    @Provides fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideVehicleDao(db: AppDatabase): VehicleDao = db.vehicleDao()
    @Provides fun provideFamilyDao(db: AppDatabase): FamilyDao = db.familyDao()
    @Provides fun provideIncomeDao(db: AppDatabase): IncomeDao = db.incomeDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides fun providePeopleDao(db: AppDatabase): PeopleDao = db.peopleDao()
    @Provides fun provideEmiDao(db: AppDatabase): EmiDao = db.emiDao()
    @Provides fun provideCreditCardDao(db: AppDatabase): CreditCardDao = db.creditCardDao()
    @Provides fun provideBillDao(db: AppDatabase): BillDao = db.billDao()
    @Provides fun provideAnnualExpenseDao(db: AppDatabase): AnnualExpenseDao = db.annualExpenseDao()
    @Provides fun provideSavingsDao(db: AppDatabase): SavingsDao = db.savingsDao()
    @Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@IoDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}
