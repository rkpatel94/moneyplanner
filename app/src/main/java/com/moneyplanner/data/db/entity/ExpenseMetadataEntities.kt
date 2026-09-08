package com.moneyplanner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A reusable, user-created label for grouping related spending. */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

/** The many-to-many link between an expense and a label. */
@Entity(
    tableName = "expense_tags",
    primaryKeys = ["expenseId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = ExpenseEntity::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId")]
)
data class ExpenseTagEntity(val expenseId: Long, val tagId: Long)

/**
 * A persisted document-picker URI, not a copied file. The app therefore has access only
 * to the specific receipt the user selected and never asks for broad storage access.
 */
@Entity(
    tableName = "expense_attachments",
    foreignKeys = [
        ForeignKey(entity = ExpenseEntity::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("expenseId")]
)
data class ExpenseAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val addedAtEpochDay: Long
)
