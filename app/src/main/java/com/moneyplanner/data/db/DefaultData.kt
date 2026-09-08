package com.moneyplanner.data.db

import com.moneyplanner.data.db.entity.AccountEntity
import com.moneyplanner.data.db.entity.CategoryEntity
import com.moneyplanner.data.db.entity.FamilyMemberEntity
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.CategoryType
import com.moneyplanner.domain.model.Relation
import java.time.LocalDate

/**
 * The starting set of categories and accounts created the first time the app runs.
 *
 * This is structure, not sample data: no amounts, no transactions and no imaginary
 * people are ever inserted, so a new install starts at a genuine zero and every figure
 * the app later shows comes from something the user actually entered.
 *
 * The essential flag marks the categories that a household cannot simply stop paying.
 * The emergency fund target is built from exactly these categories.
 */
object DefaultData {

    fun categories(): List<CategoryEntity> {
        var order = 0
        fun expense(
            name: String,
            icon: String,
            color: String,
            essential: Boolean
        ) = CategoryEntity(
            name = name,
            type = CategoryType.EXPENSE.name,
            iconKey = icon,
            colorHex = color,
            isEssential = essential,
            isCustom = false,
            sortOrder = order++
        )

        val expenses = listOf(
            expense("Food", "food", "#FFE8833A", false),
            expense("Grocery", "grocery", "#FF3F9E5A", true),
            expense("Travel", "travel", "#FF3B7DD8", false),
            expense("Fuel", "fuel", "#FFD9534F", true),
            expense("Shopping", "shopping", "#FFB05FD6", false),
            expense("Rent", "rent", "#FF2E7D6B", true),
            expense("Utilities", "utilities", "#FF4C8DAE", true),
            expense("Education", "education", "#FF5C6BC0", true),
            expense("Medical", "medical", "#FFE05252", true),
            expense("Entertainment", "entertainment", "#FFEC6FA1", false),
            expense("Family", "family", "#FF8D6E63", false),
            expense("EMI", "emi", "#FF7A5AF8", true),
            expense("Insurance", "insurance", "#FF1E88A8", true),
            expense("Investment", "investment", "#FF2FA36B", false),
            expense("Vehicle", "vehicle", "#FF6D7A8C", false),
            expense("Gifts", "gifts", "#FFD1913C", false),
            expense("Other", "other", "#FF6E7A8A", false)
        )

        val incomes = listOf(
            CategoryEntity(
                name = "Salary",
                type = CategoryType.INCOME.name,
                iconKey = "salary",
                colorHex = "#FF2E7D6B",
                sortOrder = order++
            ),
            CategoryEntity(
                name = "Freelance",
                type = CategoryType.INCOME.name,
                iconKey = "freelance",
                colorHex = "#FF3B7DD8",
                sortOrder = order++
            ),
            CategoryEntity(
                name = "Business",
                type = CategoryType.INCOME.name,
                iconKey = "business",
                colorHex = "#FF7A5AF8",
                sortOrder = order++
            ),
            CategoryEntity(
                name = "Other income",
                type = CategoryType.INCOME.name,
                iconKey = "other",
                colorHex = "#FF6E7A8A",
                sortOrder = order++
            )
        )

        return expenses + incomes
    }

    /**
     * The bank account, with a zero opening balance. The user sets their real balance
     * during onboarding, which is recorded as a reconciliation entry rather than by
     * overwriting this row.
     */
    fun defaultAccount(today: LocalDate) = AccountEntity(
        name = "Bank account",
        type = AccountType.BANK.name,
        openingBalancePaise = 0L,
        openingDateEpochDay = today.toEpochDay(),
        sortOrder = 0
    )

    /**
     * Cash in hand, kept as an account of its own rather than folded into the bank.
     *
     * Money withdrawn from an ATM has not been spent, it has only moved, and a single
     * combined account cannot express that: the withdrawal either has to be ignored,
     * which loses track of the cash, or recorded as an expense, which understates the
     * balance until the cash is actually spent. Two accounts let a withdrawal be what it
     * really is, a transfer, and let a cash purchase say which pocket it came out of.
     */
    fun defaultCashAccount(today: LocalDate) = AccountEntity(
        name = "Cash",
        type = AccountType.CASH.name,
        openingBalancePaise = 0L,
        openingDateEpochDay = today.toEpochDay(),
        sortOrder = 1
    )

    fun defaultFamilyMember() = FamilyMemberEntity(
        name = "Self",
        relation = Relation.SELF.name,
        isSelf = true
    )
}
