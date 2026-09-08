package com.moneyplanner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moneyplanner.data.db.entity.ExpenseAttachmentEntity
import com.moneyplanner.data.db.entity.ExpenseTagEntity
import com.moneyplanner.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseMetadataDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun getAllTags(): List<TagEntity>

    @Query("SELECT * FROM expense_tags")
    suspend fun getAllExpenseTags(): List<ExpenseTagEntity>

    @Query("SELECT * FROM expense_attachments")
    suspend fun getAllAttachments(): List<ExpenseAttachmentEntity>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTag(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT t.name FROM tags t INNER JOIN expense_tags et ON et.tagId = t.id WHERE et.expenseId = :expenseId ORDER BY t.name COLLATE NOCASE")
    suspend fun tagNamesForExpense(expenseId: Long): List<String>

    @Query("DELETE FROM expense_tags WHERE expenseId = :expenseId")
    suspend fun clearTags(expenseId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpenseTag(link: ExpenseTagEntity)

    @Query("SELECT * FROM expense_attachments WHERE expenseId = :expenseId ORDER BY id DESC")
    fun observeAttachments(expenseId: Long): Flow<List<ExpenseAttachmentEntity>>

    @Insert
    suspend fun insertAttachment(attachment: ExpenseAttachmentEntity): Long

    @Query("DELETE FROM expense_attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Long)
}
