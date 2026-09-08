package com.moneyplanner.domain.model

/**
 * Domain enumerations shared by the database, the calculators and the UI.
 *
 * These are persisted by name rather than by ordinal so that reordering or inserting a
 * value later can never silently re-interpret existing financial records.
 */

enum class CategoryType { INCOME, EXPENSE }

enum class PaymentMethod(val label: String, val reducesCashImmediately: Boolean) {
    CASH("Cash", true),
    UPI("UPI", true),
    BANK_TRANSFER("Bank transfer", true),
    DEBIT_CARD("Debit card", true),
    AUTO_DEBIT("Auto debit", true),
    CHEQUE("Cheque", true),
    WALLET("Wallet", true),

    /**
     * A credit card purchase does not move cash on the day it happens. It increases the
     * card outstanding, and the cash leaves the account when the card bill is paid.
     * Treating it as an immediate cash outflow would count the same rupee twice in the
     * forecast, so this is the one payment method that does not reduce cash.
     */
    CREDIT_CARD("Credit card", false),

    OTHER("Other", true);

    companion object {
        fun fromName(name: String?): PaymentMethod =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

enum class IncomeType(val label: String) {
    SALARY("Salary"),
    FREELANCE("Freelance"),
    BUSINESS("Business"),
    BONUS("Bonus"),
    INCENTIVE("Incentive"),
    RENTAL("Rental"),
    INTEREST("Interest"),
    OTHER("Other");

    companion object {
        fun fromName(name: String?): IncomeType =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * How often an obligation or an income repeats.
 *
 * [monthsPerPeriod] is zero for weekly schedules, which are handled by day arithmetic
 * instead of month arithmetic.
 */
enum class Frequency(val label: String, val monthsPerPeriod: Int) {
    WEEKLY("Weekly", 0),
    MONTHLY("Monthly", 1),
    QUARTERLY("Quarterly", 3),
    HALF_YEARLY("Half yearly", 6),
    YEARLY("Yearly", 12);

    val isWeekly: Boolean get() = this == WEEKLY

    companion object {
        fun fromName(name: String?): Frequency =
            entries.firstOrNull { it.name == name } ?: MONTHLY
    }
}

/** Which way a person-to-person obligation points. */
enum class LedgerDirection(val label: String) {
    THEY_OWE_ME("They owe me"),
    I_OWE_THEM("I owe them");

    fun opposite(): LedgerDirection =
        if (this == THEY_OWE_ME) I_OWE_THEM else THEY_OWE_ME

    companion object {
        fun fromName(name: String?): LedgerDirection =
            entries.firstOrNull { it.name == name } ?: THEY_OWE_ME
    }
}

/** Which way money actually moved when a balance was settled. */
enum class SettlementDirection(val label: String) {
    RECEIVED_FROM_THEM("Received"),
    PAID_TO_THEM("Paid");

    companion object {
        fun fromName(name: String?): SettlementDirection =
            entries.firstOrNull { it.name == name } ?: RECEIVED_FROM_THEM
    }
}

enum class SplitType(val label: String) {
    EQUAL("Split equally"),
    EXACT("Custom amounts"),
    PERCENTAGE("By percentage"),
    FULL_ON_ONE("One person pays all");

    companion object {
        fun fromName(name: String?): SplitType =
            entries.firstOrNull { it.name == name } ?: EQUAL
    }
}

/** Where a person ledger entry came from, so edits can be traced to their origin. */
enum class LedgerSourceType {
    MANUAL,
    SHARED_EXPENSE;

    companion object {
        fun fromName(name: String?): LedgerSourceType =
            entries.firstOrNull { it.name == name } ?: MANUAL
    }
}

/**
 * What an expense record was created for.
 *
 * An expense that settles an EMI installment, a bill or an annual expense is linked to
 * that obligation. The forecast counts a linked obligation once: as a real expense after
 * it is paid, and as a pending obligation before. Discretionary spending averages ignore
 * linked expenses entirely so that a rent payment never inflates the projection of
 * everyday spending.
 */
enum class ExpenseLinkType {
    NONE,
    EMI,
    RECURRING_BILL,
    ANNUAL_EXPENSE,
    CREDIT_CARD_PAYMENT,
    SETTLEMENT,
    SAVINGS_CONTRIBUTION,

    /**
     * The out-of-pocket payment for a bill shared with other people.
     *
     * The whole amount left the account, so it is recorded at full value and the balance
     * is right. It is excluded from everyday spending averages because most of it is
     * coming back: the shares other people owe are tracked in the person ledger, and the
     * expense row alone does not say which part was the user's own.
     */
    SHARED_EXPENSE;

    companion object {
        fun fromName(name: String?): ExpenseLinkType =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

enum class AccountType(val label: String) {
    BANK("Bank account"),
    CASH("Cash"),
    WALLET("Wallet");

    companion object {
        fun fromName(name: String?): AccountType =
            entries.firstOrNull { it.name == name } ?: BANK
    }
}

enum class GoalPriority(val label: String, val weight: Int) {
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1);

    companion object {
        fun fromName(name: String?): GoalPriority =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

enum class VehicleType(val label: String) {
    BIKE("Bike"),
    SCOOTER("Scooter"),
    CAR("Car"),
    OTHER("Other");

    companion object {
        fun fromName(name: String?): VehicleType =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

enum class Relation(val label: String) {
    SELF("Self"),
    WIFE("Wife"),
    HUSBAND("Husband"),
    SON("Son"),
    DAUGHTER("Daughter"),
    FATHER("Father"),
    MOTHER("Mother"),
    BROTHER("Brother"),
    SISTER("Sister"),
    FRIEND("Friend"),
    COLLEAGUE("Colleague"),
    RELATIVE("Relative"),
    OTHER("Other");

    companion object {
        fun fromName(name: String?): Relation =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** Whether a recurring bill has a known amount or only an estimate. */
enum class BillAmountType(val label: String) {
    FIXED("Fixed amount"),
    ESTIMATED("Estimated amount");

    companion object {
        fun fromName(name: String?): BillAmountType =
            entries.firstOrNull { it.name == name } ?: FIXED
    }
}

enum class EmiStatus { ACTIVE, CLOSED }

/** The outcome of the "Can I afford it?" check. */
enum class AffordabilityVerdict(val label: String) {
    SAFE("Safe"),
    BE_CAREFUL("Be careful"),
    NOT_RECOMMENDED("Not recommended")
}
