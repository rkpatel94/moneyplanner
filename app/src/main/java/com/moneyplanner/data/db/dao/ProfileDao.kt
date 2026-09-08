package com.moneyplanner.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.moneyplanner.data.db.entity.AccountEntity
import com.moneyplanner.data.db.entity.AccountTransferEntity
import com.moneyplanner.data.db.entity.BalanceAdjustmentEntity
import com.moneyplanner.data.db.entity.CategoryEntity
import com.moneyplanner.data.db.entity.FamilyMemberEntity
import com.moneyplanner.data.db.entity.UserProfileEntity
import com.moneyplanner.data.db.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY sortOrder, name")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccount(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun accountCount(): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE type = :type")
    suspend fun accountCountOfType(type: String): Int

    @Query("SELECT * FROM account_transfers ORDER BY dateEpochDay DESC, id DESC")
    fun observeTransfers(): Flow<List<AccountTransferEntity>>

    @Query("SELECT * FROM account_transfers")
    suspend fun getAllTransfers(): List<AccountTransferEntity>

    @Insert
    suspend fun insertTransfer(transfer: AccountTransferEntity): Long

    @Query("DELETE FROM account_transfers WHERE id = :id")
    suspend fun deleteTransferById(id: Long)

    @Query("SELECT * FROM balance_adjustments ORDER BY dateEpochDay DESC, id DESC")
    fun observeAdjustments(): Flow<List<BalanceAdjustmentEntity>>

    @Query("SELECT * FROM balance_adjustments")
    suspend fun getAllAdjustments(): List<BalanceAdjustmentEntity>

    @Insert
    suspend fun insertAdjustment(adjustment: BalanceAdjustmentEntity): Long

    @Delete
    suspend fun deleteAdjustment(adjustment: BalanceAdjustmentEntity)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type AND isArchived = 0 ORDER BY sortOrder, name")
    fun observeByType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name AND type = :type LIMIT 1")
    suspend fun findByName(name: String, type: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :id")
    suspend fun expenseCountFor(id: Long): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles WHERE isArchived = 0 ORDER BY name")
    fun observeActive(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles")
    suspend fun getAll(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun observeById(id: Long): Flow<VehicleEntity?>

    @Insert
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface FamilyDao {

    @Query("SELECT * FROM family_members WHERE isArchived = 0 ORDER BY isSelf DESC, name")
    fun observeActive(): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members")
    suspend fun getAll(): List<FamilyMemberEntity>

    @Insert
    suspend fun insert(member: FamilyMemberEntity): Long

    @Update
    suspend fun update(member: FamilyMemberEntity)

    @Query("DELETE FROM family_members WHERE id = :id")
    suspend fun deleteById(id: Long)
}
