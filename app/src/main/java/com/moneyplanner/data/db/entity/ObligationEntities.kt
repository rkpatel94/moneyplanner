package com.moneyplanner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A loan repaid in installments.
 *
 * [openingPaidInstallments] exists because people add an EMI to the app long after they
 * took the loan. It records how many installments were already paid before tracking
 * started, so the remaining tenure is right without inventing payment history.
 */
@Entity(
    tableName = "emis",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("isActive"), Index("vehicleId"), Index("categoryId")]
)
data class EmiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long?,
    val principalPaise: Long,
    val emiAmountPaise: Long,
    val interestRatePercent: Double? = null,
    val startDateEpochDay: Long,
    val firstDueDateEpochDay: Long,
    val frequency: String,
    val totalInstallments: Int,
    val openingPaidInstallments: Int = 0,
    val vehicleId: Long? = null,
    val accountReference: String = "",
    val autoDebit: Boolean = false,
    val isActive: Boolean = true,
    val notes: String = ""
)

/** A single EMI installment that has actually been paid. */
@Entity(
    tableName = "emi_payments",
    foreignKeys = [
        ForeignKey(
            entity = EmiEntity::class,
            parentColumns = ["id"],
            childColumns = ["emiId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("emiId"),
        Index("paidDateEpochDay"),
        Index(value = ["emiId", "installmentNumber"], unique = true)
    ]
)
data class EmiPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emiId: Long,
    val installmentNumber: Int,
    val amountPaise: Long,
    val dueDateEpochDay: Long,
    val paidDateEpochDay: Long,
    val notes: String = ""
)

/**
 * A credit card.
 *
 * The outstanding amount is maintained by the user because V1 deliberately does not
 * connect to any bank. The card due is what leaves the bank account in the forecast;
 * individual purchases made on the card are not counted as cash outflows.
 */
@Entity(tableName = "credit_cards", indices = [Index("isActive")])
data class CreditCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bank: String,
    val lastFourDigits: String? = null,
    val creditLimitPaise: Long,
    val currentOutstandingPaise: Long,
    val minimumDuePaise: Long = 0L,
    val statementDayOfMonth: Int,
    val dueDayOfMonth: Int,
    val isActive: Boolean = true,
    val notes: String = "",
    val lastUpdatedEpochDay: Long = 0L
)

/** A payment made towards a credit card bill. */
@Entity(
    tableName = "credit_card_payments",
    foreignKeys = [
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cardId"), Index("paidDateEpochDay")]
)
data class CreditCardPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val amountPaise: Long,
    val paidDateEpochDay: Long,
    val accountId: Long? = null,
    val notes: String = ""
)

/**
 * A bill that repeats: rent, electricity, internet, school fees and so on.
 *
 * Variable bills such as electricity are stored as an estimate with an optional expected
 * range, so the forecast can show a realistic figure while making clear it is not fixed.
 */
@Entity(
    tableName = "recurring_bills",
    indices = [Index("isActive"), Index("categoryId")]
)
data class RecurringBillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long?,
    val amountType: String,
    val amountPaise: Long,
    val minAmountPaise: Long? = null,
    val maxAmountPaise: Long? = null,
    val dueDayOfMonth: Int,
    val frequency: String,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long? = null,
    val paymentMethod: String = "UPI",
    val autoDebit: Boolean = false,
    val isEssential: Boolean = true,
    val isActive: Boolean = true,
    val notes: String = ""
)

/** Marks one period of a recurring bill as paid, for example September 2026 rent. */
@Entity(
    tableName = "bill_payments",
    foreignKeys = [
        ForeignKey(
            entity = RecurringBillEntity::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("billId"),
        Index("paidDateEpochDay"),
        Index(value = ["billId", "periodKey"], unique = true)
    ]
)
data class BillPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val periodKey: String,
    val amountPaise: Long,
    val dueDateEpochDay: Long,
    val paidDateEpochDay: Long,
    val notes: String = ""
)

/**
 * A large commitment that lands once a year: insurance, property tax, school admission.
 *
 * These are the payments that quietly break a monthly budget, so the app both forecasts
 * them in the month they fall due and shows the monthly amount that should be set aside.
 */
@Entity(tableName = "annual_expenses", indices = [Index("isActive"), Index("dueMonth")])
data class AnnualExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long?,
    val amountPaise: Long,
    val dueMonth: Int,
    val dueDayOfMonth: Int,
    val vehicleId: Long? = null,
    val isActive: Boolean = true,
    val notes: String = ""
)

/** Records that a given year instance of an annual expense has been paid. */
@Entity(
    tableName = "annual_expense_payments",
    foreignKeys = [
        ForeignKey(
            entity = AnnualExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["annualExpenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("annualExpenseId"),
        Index(value = ["annualExpenseId", "year"], unique = true)
    ]
)
data class AnnualExpensePaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val annualExpenseId: Long,
    val year: Int,
    val amountPaise: Long,
    val paidDateEpochDay: Long,
    val notes: String = ""
)

/**
 * A savings target.
 *
 * The balance is derived from contributions rather than stored, so editing or deleting a
 * contribution can never leave the goal showing a total that its history cannot explain.
 */
@Entity(tableName = "savings_goals", indices = [Index("isAchieved")])
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmountPaise: Long,
    val targetDateEpochDay: Long?,
    val priority: String,
    /** Exactly one goal may be the emergency fund; it is linked to essential expenses. */
    val isEmergencyFund: Boolean = false,
    val isAchieved: Boolean = false,
    val notes: String = "",
    val createdAtEpochDay: Long = 0L
)

@Entity(
    tableName = "savings_contributions",
    foreignKeys = [
        ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goalId"), Index("dateEpochDay")]
)
data class SavingsContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    /** Negative amounts represent a withdrawal from the goal. */
    val amountPaise: Long,
    val dateEpochDay: Long,
    val accountId: Long? = null,
    val notes: String = ""
)
