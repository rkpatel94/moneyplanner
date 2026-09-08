package com.moneyplanner.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneyplanner.data.db.entity.AnnualExpenseEntity
import com.moneyplanner.data.db.entity.AnnualExpensePaymentEntity
import com.moneyplanner.data.db.entity.BillPaymentEntity
import com.moneyplanner.data.db.entity.CreditCardEntity
import com.moneyplanner.data.db.entity.CreditCardPaymentEntity
import com.moneyplanner.data.db.entity.EmiEntity
import com.moneyplanner.data.db.entity.EmiPaymentEntity
import com.moneyplanner.data.db.entity.RecurringBillEntity
import com.moneyplanner.data.db.entity.SavingsContributionEntity
import com.moneyplanner.data.db.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmiDao {

    @Query("SELECT * FROM emis ORDER BY isActive DESC, name")
    fun observeAll(): Flow<List<EmiEntity>>

    @Query("SELECT * FROM emis WHERE isActive = 1")
    fun observeActive(): Flow<List<EmiEntity>>

    @Query("SELECT * FROM emis")
    suspend fun getAll(): List<EmiEntity>

    @Query("SELECT * FROM emis WHERE id = :id")
    fun observeById(id: Long): Flow<EmiEntity?>

    @Query("SELECT * FROM emis WHERE id = :id")
    suspend fun getById(id: Long): EmiEntity?

    @Query("SELECT * FROM emis WHERE name LIKE '%' || :query || '%' LIMIT 50")
    fun search(query: String): Flow<List<EmiEntity>>

    @Insert
    suspend fun insert(emi: EmiEntity): Long

    @Update
    suspend fun update(emi: EmiEntity)

    @Query("DELETE FROM emis WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM emi_payments ORDER BY dueDateEpochDay")
    fun observeAllPayments(): Flow<List<EmiPaymentEntity>>

    @Query("SELECT * FROM emi_payments")
    suspend fun getAllPayments(): List<EmiPaymentEntity>

    @Query("SELECT * FROM emi_payments WHERE emiId = :emiId ORDER BY installmentNumber")
    fun observePaymentsFor(emiId: Long): Flow<List<EmiPaymentEntity>>

    @Query("SELECT * FROM emi_payments WHERE emiId = :emiId ORDER BY installmentNumber")
    suspend fun getPaymentsFor(emiId: Long): List<EmiPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: EmiPaymentEntity): Long

    @Delete
    suspend fun deletePayment(payment: EmiPaymentEntity)

    @Query(
        "UPDATE emi_payments SET amountPaise = :amountPaise, paidDateEpochDay = :paidDateEpochDay " +
            "WHERE emiId = :emiId AND installmentNumber = :number"
    )
    suspend fun updatePaymentAmount(
        emiId: Long,
        number: Int,
        amountPaise: Long,
        paidDateEpochDay: Long
    )

    @Query("DELETE FROM emi_payments WHERE emiId = :emiId AND installmentNumber = :number")
    suspend fun deletePayment(emiId: Long, number: Int)
}

@Dao
interface CreditCardDao {

    @Query("SELECT * FROM credit_cards ORDER BY isActive DESC, name")
    fun observeAll(): Flow<List<CreditCardEntity>>

    @Query("SELECT * FROM credit_cards WHERE isActive = 1")
    fun observeActive(): Flow<List<CreditCardEntity>>

    @Query("SELECT * FROM credit_cards")
    suspend fun getAll(): List<CreditCardEntity>

    @Query("SELECT * FROM credit_cards WHERE id = :id")
    fun observeById(id: Long): Flow<CreditCardEntity?>

    @Query("SELECT * FROM credit_cards WHERE id = :id")
    suspend fun getById(id: Long): CreditCardEntity?

    @Insert
    suspend fun insert(card: CreditCardEntity): Long

    @Update
    suspend fun update(card: CreditCardEntity)

    @Query("DELETE FROM credit_cards WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM credit_card_payments ORDER BY paidDateEpochDay DESC")
    fun observeAllPayments(): Flow<List<CreditCardPaymentEntity>>

    @Query("SELECT * FROM credit_card_payments WHERE cardId = :cardId ORDER BY paidDateEpochDay DESC")
    fun observePaymentsFor(cardId: Long): Flow<List<CreditCardPaymentEntity>>

    @Query("SELECT * FROM credit_card_payments")
    suspend fun getAllPayments(): List<CreditCardPaymentEntity>

    @Insert
    suspend fun insertPayment(payment: CreditCardPaymentEntity): Long

    @Delete
    suspend fun deletePayment(payment: CreditCardPaymentEntity)
}

@Dao
interface BillDao {

    @Query("SELECT * FROM recurring_bills ORDER BY isActive DESC, dueDayOfMonth")
    fun observeAll(): Flow<List<RecurringBillEntity>>

    @Query("SELECT * FROM recurring_bills WHERE isActive = 1")
    fun observeActive(): Flow<List<RecurringBillEntity>>

    @Query("SELECT * FROM recurring_bills")
    suspend fun getAll(): List<RecurringBillEntity>

    @Query("SELECT * FROM recurring_bills WHERE id = :id")
    fun observeById(id: Long): Flow<RecurringBillEntity?>

    @Query("SELECT * FROM recurring_bills WHERE id = :id")
    suspend fun getById(id: Long): RecurringBillEntity?

    @Query("SELECT * FROM recurring_bills WHERE name LIKE '%' || :query || '%' LIMIT 50")
    fun search(query: String): Flow<List<RecurringBillEntity>>

    @Insert
    suspend fun insert(bill: RecurringBillEntity): Long

    @Update
    suspend fun update(bill: RecurringBillEntity)

    @Query("DELETE FROM recurring_bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM bill_payments")
    fun observeAllPayments(): Flow<List<BillPaymentEntity>>

    @Query("SELECT * FROM bill_payments")
    suspend fun getAllPayments(): List<BillPaymentEntity>

    @Query("SELECT * FROM bill_payments WHERE billId = :billId ORDER BY periodKey DESC")
    fun observePaymentsFor(billId: Long): Flow<List<BillPaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: BillPaymentEntity): Long

    @Query("SELECT * FROM bill_payments WHERE billId = :billId AND periodKey = :periodKey LIMIT 1")
    suspend fun getPayment(billId: Long, periodKey: String): BillPaymentEntity?

    @Query(
        "UPDATE bill_payments SET amountPaise = :amountPaise, paidDateEpochDay = :paidDateEpochDay " +
            "WHERE billId = :billId AND periodKey = :periodKey"
    )
    suspend fun updatePaymentAmount(
        billId: Long,
        periodKey: String,
        amountPaise: Long,
        paidDateEpochDay: Long
    )

    @Query("DELETE FROM bill_payments WHERE billId = :billId AND periodKey = :periodKey")
    suspend fun deletePayment(billId: Long, periodKey: String)
}

@Dao
interface AnnualExpenseDao {

    @Query("SELECT * FROM annual_expenses ORDER BY isActive DESC, dueMonth, dueDayOfMonth")
    fun observeAll(): Flow<List<AnnualExpenseEntity>>

    @Query("SELECT * FROM annual_expenses WHERE isActive = 1")
    fun observeActive(): Flow<List<AnnualExpenseEntity>>

    @Query("SELECT * FROM annual_expenses")
    suspend fun getAll(): List<AnnualExpenseEntity>

    @Query("SELECT * FROM annual_expenses WHERE id = :id")
    suspend fun getById(id: Long): AnnualExpenseEntity?

    @Insert
    suspend fun insert(expense: AnnualExpenseEntity): Long

    @Update
    suspend fun update(expense: AnnualExpenseEntity)

    @Query("DELETE FROM annual_expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM annual_expense_payments")
    fun observeAllPayments(): Flow<List<AnnualExpensePaymentEntity>>

    @Query("SELECT * FROM annual_expense_payments")
    suspend fun getAllPayments(): List<AnnualExpensePaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: AnnualExpensePaymentEntity): Long

    @Query(
        "SELECT * FROM annual_expense_payments WHERE annualExpenseId = :id AND year = :year LIMIT 1"
    )
    suspend fun getPayment(id: Long, year: Int): AnnualExpensePaymentEntity?

    @Query(
        "UPDATE annual_expense_payments SET amountPaise = :amountPaise, " +
            "paidDateEpochDay = :paidDateEpochDay WHERE annualExpenseId = :id AND year = :year"
    )
    suspend fun updatePaymentAmount(
        id: Long,
        year: Int,
        amountPaise: Long,
        paidDateEpochDay: Long
    )

    @Query("DELETE FROM annual_expense_payments WHERE annualExpenseId = :id AND year = :year")
    suspend fun deletePayment(id: Long, year: Int)
}

@Dao
interface SavingsDao {

    /**
     * Unmet goals first, then most important first.
     *
     * Priority is stored by name, so ordering on the column itself would sort it
     * alphabetically and put HIGH last. The CASE maps each name to its real rank.
     */
    @Query(
        "SELECT * FROM savings_goals ORDER BY isAchieved, " +
            "CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, id"
    )
    fun observeAll(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals")
    suspend fun getAll(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    fun observeById(id: Long): Flow<SavingsGoalEntity?>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: Long): SavingsGoalEntity?

    @Query("SELECT * FROM savings_goals WHERE isEmergencyFund = 1 LIMIT 1")
    fun observeEmergencyFund(): Flow<SavingsGoalEntity?>

    @Query("SELECT * FROM savings_goals WHERE isEmergencyFund = 1 LIMIT 1")
    suspend fun getEmergencyFund(): SavingsGoalEntity?

    @Insert
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM savings_contributions ORDER BY dateEpochDay DESC")
    fun observeAllContributions(): Flow<List<SavingsContributionEntity>>

    @Query("SELECT * FROM savings_contributions")
    suspend fun getAllContributions(): List<SavingsContributionEntity>

    @Query("SELECT * FROM savings_contributions WHERE goalId = :goalId ORDER BY dateEpochDay DESC")
    fun observeContributionsFor(goalId: Long): Flow<List<SavingsContributionEntity>>

    @Insert
    suspend fun insertContribution(contribution: SavingsContributionEntity): Long

    @Delete
    suspend fun deleteContribution(contribution: SavingsContributionEntity)

    @Update
    suspend fun updateContribution(contribution: SavingsContributionEntity)

    @Query("SELECT * FROM savings_contributions WHERE id = :id")
    suspend fun getContribution(id: Long): SavingsContributionEntity?

    @Query("DELETE FROM savings_contributions WHERE id = :id")
    suspend fun deleteContributionById(id: Long)
}
