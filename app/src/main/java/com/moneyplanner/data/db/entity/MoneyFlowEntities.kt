package com.moneyplanner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recurring income definition, most commonly a salary.
 *
 * This is the rule, not the money. Actual receipts are recorded as
 * [IncomeTransactionEntity] rows. Keeping the two apart is what lets the forecast say
 * "salary is expected on the 1st" while the dashboard says "salary has not arrived yet".
 */
@Entity(tableName = "income_sources", indices = [Index("isActive")])
data class IncomeSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val amountPaise: Long,
    /** Day of month the money is expected; clamped to shorter months when resolved. */
    val dayOfMonth: Int,
    val frequency: String,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long? = null,
    /** Expected yearly raise, applied on each anniversary of the start date. */
    val annualIncrementPercent: Double = 0.0,
    val accountId: Long? = null,
    val isActive: Boolean = true,
    val notes: String = ""
)

/** Money that actually arrived, whether recurring or one-off. */
@Entity(
    tableName = "income_transactions",
    indices = [Index("sourceId"), Index("dateEpochDay"), Index("accountId")]
)
data class IncomeTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long?,
    val name: String,
    val type: String,
    val amountPaise: Long,
    val dateEpochDay: Long,
    val accountId: Long? = null,
    /** Set when this receipt satisfies a scheduled occurrence, for example "2026-09". */
    val periodKey: String? = null,
    val notes: String = ""
)

/**
 * A single expense.
 *
 * [linkType] and [linkId] record whether this expense settled a specific obligation.
 * Linked expenses are excluded from discretionary spending averages, because an EMI or a
 * rent payment is already represented as a scheduled obligation in the forecast.
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = FamilyMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyMemberId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("categoryId"), Index("personId"), Index("vehicleId"),
        Index("familyMemberId"), Index("dateEpochDay"), Index("accountId"),
        Index(value = ["linkType", "linkId"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val description: String,
    val categoryId: Long,
    val dateEpochDay: Long,
    val paymentMethod: String,
    val accountId: Long? = null,
    val creditCardId: Long? = null,
    val personId: Long? = null,
    val vehicleId: Long? = null,
    val familyMemberId: Long? = null,
    val linkType: String = "NONE",
    val linkId: Long? = null,
    /**
     * Which single occurrence of [linkId] this expense settled: "2026-09" for a bill
     * period, "2026" for a yearly commitment, "14" for an EMI installment.
     *
     * [linkId] alone identifies the obligation, not the payment, so undoing one month of
     * rent would otherwise have to delete every month's expense to find the right one.
     */
    val linkPeriodKey: String? = null,
    val notes: String = "",
    val createdAtEpochDay: Long = 0L
)

/**
 * One side of a person-to-person obligation.
 *
 * This table is the only source of truth for who owes whom. Shared expenses write their
 * per-person shares here rather than keeping a parallel set of balances, which is what
 * keeps a split from ever being counted twice.
 */
@Entity(
    tableName = "person_ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("personId"), Index("dateEpochDay"), Index(value = ["sourceType", "sourceId"])]
)
data class PersonLedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    /** Always positive; [direction] carries the sign. */
    val amountPaise: Long,
    val direction: String,
    val dateEpochDay: Long,
    /** When the money is expected to change hands, used by the forecast. */
    val expectedDateEpochDay: Long? = null,
    val description: String,
    val sourceType: String = "MANUAL",
    val sourceId: Long? = null,
    val notes: String = ""
)

/** A payment that reduces an outstanding person balance, in part or in full. */
@Entity(
    tableName = "settlements",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("personId"), Index("dateEpochDay")]
)
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val amountPaise: Long,
    val direction: String,
    val dateEpochDay: Long,
    val paymentMethod: String = "CASH",
    val accountId: Long? = null,
    val notes: String = ""
)

/** The split metadata for an expense shared with other people. */
@Entity(tableName = "shared_expenses", indices = [Index("dateEpochDay"), Index("categoryId")])
data class SharedExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val totalAmountPaise: Long,
    val dateEpochDay: Long,
    val categoryId: Long?,
    val splitType: String,
    /** Null means the user paid the bill; otherwise the person who paid. */
    val paidByPersonId: Long?,
    val notes: String = ""
)

/**
 * One participant's share of a shared expense.
 *
 * A null [personId] is the user's own share. Shares always sum to the total, because the
 * split helpers distribute the rounding remainder rather than dropping it.
 */
@Entity(
    tableName = "shared_expense_shares",
    foreignKeys = [
        ForeignKey(
            entity = SharedExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sharedExpenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        // A share belongs to a person. Their ledger entries already cascade away with
        // them, so without this the shares would be the one trace of a deleted person
        // left behind, pointing at an id that no longer resolves to a name.
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sharedExpenseId"), Index("personId")]
)
data class SharedExpenseShareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sharedExpenseId: Long,
    val personId: Long?,
    val shareAmountPaise: Long,
    val sharePercent: Double? = null
)
