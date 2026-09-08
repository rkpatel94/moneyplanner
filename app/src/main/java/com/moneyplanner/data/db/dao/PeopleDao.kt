package com.moneyplanner.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.moneyplanner.data.db.entity.PersonEntity
import com.moneyplanner.data.db.entity.PersonLedgerEntryEntity
import com.moneyplanner.data.db.entity.SettlementEntity
import com.moneyplanner.data.db.entity.SharedExpenseEntity
import com.moneyplanner.data.db.entity.SharedExpenseShareEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeopleDao {

    @Query("SELECT * FROM people WHERE isArchived = 0 ORDER BY name")
    fun observeActive(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people ORDER BY name")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people")
    suspend fun getAll(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE id = :id")
    fun observeById(id: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getById(id: Long): PersonEntity?

    @Query("SELECT * FROM people WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    fun search(query: String): Flow<List<PersonEntity>>

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- Ledger ----------------------------------------------------------------

    @Query("SELECT * FROM person_ledger_entries ORDER BY dateEpochDay DESC, id DESC")
    fun observeAllEntries(): Flow<List<PersonLedgerEntryEntity>>

    @Query("SELECT * FROM person_ledger_entries")
    suspend fun getAllEntries(): List<PersonLedgerEntryEntity>

    @Query("SELECT * FROM person_ledger_entries WHERE personId = :personId ORDER BY dateEpochDay, id")
    fun observeEntriesFor(personId: Long): Flow<List<PersonLedgerEntryEntity>>

    @Query("SELECT * FROM person_ledger_entries WHERE personId = :personId ORDER BY dateEpochDay, id")
    suspend fun getEntriesFor(personId: Long): List<PersonLedgerEntryEntity>

    @Query("SELECT * FROM person_ledger_entries WHERE id = :id")
    suspend fun getEntry(id: Long): PersonLedgerEntryEntity?

    @Insert
    suspend fun insertEntry(entry: PersonLedgerEntryEntity): Long

    @Insert
    suspend fun insertEntries(entries: List<PersonLedgerEntryEntity>)

    @Update
    suspend fun updateEntry(entry: PersonLedgerEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: PersonLedgerEntryEntity)

    @Query("DELETE FROM person_ledger_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM person_ledger_entries WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun deleteEntriesFromSource(sourceType: String, sourceId: Long)

    // ---- Settlements -----------------------------------------------------------

    @Query("SELECT * FROM settlements ORDER BY dateEpochDay DESC, id DESC")
    fun observeAllSettlements(): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements")
    suspend fun getAllSettlements(): List<SettlementEntity>

    @Query("SELECT * FROM settlements WHERE personId = :personId ORDER BY dateEpochDay, id")
    fun observeSettlementsFor(personId: Long): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements WHERE personId = :personId ORDER BY dateEpochDay, id")
    suspend fun getSettlementsFor(personId: Long): List<SettlementEntity>

    @Insert
    suspend fun insertSettlement(settlement: SettlementEntity): Long

    @Update
    suspend fun updateSettlement(settlement: SettlementEntity)

    @Delete
    suspend fun deleteSettlement(settlement: SettlementEntity)

    @Query("DELETE FROM settlements WHERE id = :id")
    suspend fun deleteSettlementById(id: Long)

    // ---- Shared expenses -------------------------------------------------------

    @Query("SELECT * FROM shared_expenses ORDER BY dateEpochDay DESC, id DESC")
    fun observeSharedExpenses(): Flow<List<SharedExpenseEntity>>

    @Query("SELECT * FROM shared_expenses")
    suspend fun getAllSharedExpenses(): List<SharedExpenseEntity>

    @Query("SELECT * FROM shared_expenses WHERE id = :id")
    suspend fun getSharedExpense(id: Long): SharedExpenseEntity?

    @Query("SELECT * FROM shared_expense_shares WHERE sharedExpenseId = :id")
    suspend fun getShares(id: Long): List<SharedExpenseShareEntity>

    @Query("SELECT * FROM shared_expense_shares")
    suspend fun getAllShares(): List<SharedExpenseShareEntity>

    @Insert
    suspend fun insertSharedExpense(shared: SharedExpenseEntity): Long

    @Update
    suspend fun updateSharedExpense(shared: SharedExpenseEntity)

    @Insert
    suspend fun insertShares(shares: List<SharedExpenseShareEntity>)

    @Query("DELETE FROM shared_expense_shares WHERE sharedExpenseId = :id")
    suspend fun deleteShares(id: Long)

    @Query("DELETE FROM shared_expenses WHERE id = :id")
    suspend fun deleteSharedExpenseById(id: Long)

    /**
     * Removes a shared expense together with every ledger entry it created.
     *
     * Doing this in one transaction is what stops a deleted split from leaving behind
     * a balance that no longer has an expense to explain it.
     */
    @Transaction
    suspend fun deleteSharedExpenseCascade(id: Long) {
        deleteEntriesFromSource("SHARED_EXPENSE", id)
        deleteShares(id)
        deleteSharedExpenseById(id)
    }
}
