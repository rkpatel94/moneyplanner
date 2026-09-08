package com.moneyplanner.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneyplanner.data.db.entity.ExpenseEntity
import com.moneyplanner.data.db.entity.IncomeSourceEntity
import com.moneyplanner.data.db.entity.IncomeTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {

    @Query("SELECT * FROM income_sources ORDER BY isActive DESC, dayOfMonth")
    fun observeSources(): Flow<List<IncomeSourceEntity>>

    @Query("SELECT * FROM income_sources WHERE isActive = 1")
    fun observeActiveSources(): Flow<List<IncomeSourceEntity>>

    @Query("SELECT * FROM income_sources")
    suspend fun getAllSources(): List<IncomeSourceEntity>

    @Query("SELECT * FROM income_sources WHERE id = :id")
    suspend fun getSource(id: Long): IncomeSourceEntity?

    @Insert
    suspend fun insertSource(source: IncomeSourceEntity): Long

    @Update
    suspend fun updateSource(source: IncomeSourceEntity)

    @Query("DELETE FROM income_sources WHERE id = :id")
    suspend fun deleteSourceById(id: Long)

    @Query("SELECT * FROM income_transactions ORDER BY dateEpochDay DESC, id DESC")
    fun observeTransactions(): Flow<List<IncomeTransactionEntity>>

    @Query(
        "SELECT * FROM income_transactions WHERE dateEpochDay BETWEEN :from AND :to " +
            "ORDER BY dateEpochDay DESC, id DESC"
    )
    fun observeTransactionsBetween(from: Long, to: Long): Flow<List<IncomeTransactionEntity>>

    @Query("SELECT * FROM income_transactions")
    suspend fun getAllTransactions(): List<IncomeTransactionEntity>

    @Query("SELECT * FROM income_transactions WHERE id = :id")
    suspend fun getTransaction(id: Long): IncomeTransactionEntity?

    @Query("SELECT * FROM income_transactions WHERE sourceId = :sourceId AND periodKey = :periodKey LIMIT 1")
    suspend fun findForPeriod(sourceId: Long, periodKey: String): IncomeTransactionEntity?

    @Insert
    suspend fun insertTransaction(transaction: IncomeTransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: IncomeTransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: IncomeTransactionEntity)

    @Query("DELETE FROM income_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)
}

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY dateEpochDay DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT * FROM expenses WHERE dateEpochDay BETWEEN :from AND :to " +
            "ORDER BY dateEpochDay DESC, id DESC"
    )
    fun observeBetween(from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE dateEpochDay BETWEEN :from AND :to")
    suspend fun getBetween(from: Long, to: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId ORDER BY dateEpochDay DESC")
    fun observeByCategory(categoryId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE vehicleId = :vehicleId ORDER BY dateEpochDay DESC")
    fun observeByVehicle(vehicleId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE familyMemberId = :memberId ORDER BY dateEpochDay DESC")
    fun observeByFamilyMember(memberId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE personId = :personId ORDER BY dateEpochDay DESC")
    fun observeByPerson(personId: Long): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT * FROM expenses WHERE description LIKE '%' || :query || '%' " +
            "OR notes LIKE '%' || :query || '%' ORDER BY dateEpochDay DESC LIMIT 100"
    )
    fun search(query: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE linkType = :linkType AND linkId = :linkId")
    suspend fun getLinked(linkType: String, linkId: Long): List<ExpenseEntity>

    @Query(
        "SELECT * FROM expenses WHERE linkType = :linkType AND linkId = :linkId " +
            "AND linkPeriodKey = :periodKey"
    )
    suspend fun getLinkedPeriod(
        linkType: String,
        linkId: Long,
        periodKey: String
    ): List<ExpenseEntity>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Removes every expense belonging to an obligation. Only correct when the obligation
     * itself is going away; undoing a single payment must use [deleteLinkedPeriod].
     */
    @Query("DELETE FROM expenses WHERE linkType = :linkType AND linkId = :linkId")
    suspend fun deleteLinkedAll(linkType: String, linkId: Long)

    /**
     * Removes the expense created by one specific payment.
     *
     * Rows written before the period key existed and left unmatched by the backfill are
     * caught by the date fallback, so an old payment can still be undone without taking
     * every other month of the same bill with it.
     */
    @Query(
        "DELETE FROM expenses WHERE linkType = :linkType AND linkId = :linkId " +
            "AND (linkPeriodKey = :periodKey " +
            "OR (linkPeriodKey IS NULL AND dateEpochDay = :paidDateEpochDay))"
    )
    suspend fun deleteLinkedPeriod(
        linkType: String,
        linkId: Long,
        periodKey: String,
        paidDateEpochDay: Long
    )

    @Query("SELECT MIN(dateEpochDay) FROM expenses")
    suspend fun earliestDate(): Long?
}
