package com.moneyplanner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A monthly spending limit.
 *
 * A null [categoryId] is the overall budget, covering everything the user spends; any
 * other value limits a single category. The repository enforces one budget per category
 * and one overall budget, because SQLite treats NULLs as distinct and a unique index
 * alone would happily allow several overall budgets.
 *
 * Budgets deliberately measure *spending*, not cash flow, which is why they behave
 * differently from the forecast in two ways: a credit card purchase counts on the day it
 * is made, and an expense created by paying a bill still counts against its category. A
 * limit on groceries means the groceries, however they were paid for.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId"), Index("isActive")]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null means this is the overall budget across every category. */
    val categoryId: Long? = null,
    val amountPaise: Long,
    val isActive: Boolean = true,
    val notes: String = "",
    val createdAtEpochDay: Long = 0L
)
