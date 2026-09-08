package com.moneyplanner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The single user profile row. Kept as a table rather than a preference so that it can
 * be joined, exported and restored together with the rest of the financial data.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val displayName: String = "",
    /** How many months of essential expenses the emergency fund should cover. */
    val emergencyFundMonths: Int = 6,
    /** The day the user considers their financial month to begin, usually salary day. */
    val monthStartDay: Int = 1,
    val onboardingCompleted: Boolean = false,
    val createdAtEpochDay: Long = 0L
) {
    companion object { const val SINGLETON_ID = 1L }
}

/**
 * A place money sits: a bank account, cash in hand, or a wallet.
 *
 * The current balance is never stored as a mutable number. It is always derived from the
 * opening balance plus every recorded movement, so the figure on the dashboard can
 * always be explained by the records behind it.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val openingBalancePaise: Long,
    val openingDateEpochDay: Long,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0
)

/**
 * A reconciliation entry.
 *
 * When the user says "my actual balance today is 42,500", we do not overwrite history:
 * we record the difference between the computed balance and the stated balance as an
 * explicit adjustment. The balance stays fully derivable and the correction is visible.
 */
@Entity(
    tableName = "balance_adjustments",
    indices = [Index("accountId"), Index("dateEpochDay")]
)
data class BalanceAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long?,
    val deltaPaise: Long,
    val dateEpochDay: Long,
    val reason: String = "",
    val createdAtEpochDay: Long = 0L
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name", "type"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val iconKey: String = "other",
    val colorHex: String = "#FF6E7A8A",
    /**
     * Essential categories feed the emergency fund calculation: rent, groceries,
     * utilities, medical and school fees are essential; entertainment is not.
     */
    val isEssential: Boolean = false,
    val isCustom: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "people", indices = [Index("name")])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relation: String,
    val phone: String? = null,
    val notes: String = "",
    val isArchived: Boolean = false,
    val createdAtEpochDay: Long = 0L
)

@Entity(tableName = "family_members", indices = [Index("name")])
data class FamilyMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relation: String,
    val isSelf: Boolean = false,
    val isArchived: Boolean = false
)

@Entity(tableName = "vehicles", indices = [Index("name")])
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val registrationNumber: String? = null,
    val purchaseDateEpochDay: Long? = null,
    val notes: String = "",
    val isArchived: Boolean = false
)

/**
 * Money moved between two of the user's own accounts.
 *
 * Not income and not an expense: nothing was earned or spent, the same rupees simply sit
 * somewhere else. Recording it as a pair of ordinary transactions would inflate both the
 * month's income and its spending, so it is its own kind of record and the only one that
 * touches two accounts at once.
 *
 * It nets to zero across the whole balance, which is exactly why per-account balances
 * cannot be kept honest without it.
 */
@Entity(
    tableName = "account_transfers",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromAccountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("fromAccountId"), Index("toAccountId"), Index("dateEpochDay")]
)
data class AccountTransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountPaise: Long,
    val dateEpochDay: Long,
    val notes: String = ""
)
