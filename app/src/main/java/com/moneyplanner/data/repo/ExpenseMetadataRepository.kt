package com.moneyplanner.data.repo

import com.moneyplanner.data.db.dao.ExpenseMetadataDao
import com.moneyplanner.data.db.entity.ExpenseAttachmentEntity
import com.moneyplanner.data.db.entity.ExpenseTagEntity
import com.moneyplanner.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseMetadataRepository @Inject constructor(private val dao: ExpenseMetadataDao) {
    fun attachments(expenseId: Long): Flow<List<ExpenseAttachmentEntity>> = dao.observeAttachments(expenseId)
    suspend fun tagsForExpense(expenseId: Long): List<String> = dao.tagNamesForExpense(expenseId)

    suspend fun replaceTags(expenseId: Long, rawNames: Collection<String>) {
        val names = rawNames.asSequence().map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }.distinctBy { it.lowercase() }.take(8).toList()
        dao.clearTags(expenseId)
        names.forEach { name ->
            val tag = dao.findTag(name) ?: run {
                val id = dao.insertTag(TagEntity(name = name))
                if (id > 0) TagEntity(id, name) else dao.findTag(name)
            }
            tag?.let { dao.insertExpenseTag(ExpenseTagEntity(expenseId, it.id)) }
        }
    }

    suspend fun addAttachment(expenseId: Long, uri: String, displayName: String, mimeType: String) =
        dao.insertAttachment(ExpenseAttachmentEntity(expenseId = expenseId, uri = uri, displayName = displayName, mimeType = mimeType, addedAtEpochDay = LocalDate.now().toEpochDay()))

    suspend fun deleteAttachment(id: Long) = dao.deleteAttachment(id)
}
