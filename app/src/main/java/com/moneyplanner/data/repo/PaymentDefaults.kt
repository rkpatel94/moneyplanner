package com.moneyplanner.data.repo

import com.moneyplanner.data.db.dao.CategoryDao
import com.moneyplanner.data.db.dao.ProfileDao
import com.moneyplanner.data.db.entity.CategoryEntity
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.CategoryType
import com.moneyplanner.domain.model.PaymentMethod
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The category and account a payment is filed under when nothing more specific is known.
 *
 * Paying a bill, an EMI or a yearly expense writes two rows: the payment, which tells the
 * forecast to stop expecting that period, and an expense, which takes the money out of the
 * balance. The expense used to be written only when the obligation had a category, and
 * "No category" is the editors' default, so paying an uncategorised ₹2,000 bill dropped it
 * from the forecast without ever taking it off the balance: the month ended ₹2,000 better
 * off for having paid it. A payment now always gets its expense, filed under the best
 * category that can be found, so the two rows can never disagree.
 */
@Singleton
class PaymentDefaults @Inject constructor(
    private val categoryDao: CategoryDao,
    private val profileDao: ProfileDao
) {

    /**
     * [preferredId] when it still names a category, otherwise the first of [namesToTry]
     * that exists, otherwise "Other", otherwise any expense category, and as a last resort
     * a freshly created "Other". Never null: an expense must be filed under something.
     */
    suspend fun categoryFor(preferredId: Long?, vararg namesToTry: String): Long {
        if (preferredId != null && categoryDao.getById(preferredId) != null) return preferredId

        val expenseCategories = categoryDao.getAll().filter { it.type == CategoryType.EXPENSE.name }
        val open = expenseCategories.filterNot { it.isArchived }

        for (name in namesToTry) {
            val wanted = name.trim()
            if (wanted.isEmpty()) continue
            open.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.let { return it.id }
        }
        open.firstOrNull { it.name.equals(OTHER, ignoreCase = true) }?.let { return it.id }
        open.firstOrNull()?.let { return it.id }
        expenseCategories.firstOrNull()?.let { return it.id }

        val created = categoryDao.insert(
            CategoryEntity(name = OTHER, type = CategoryType.EXPENSE.name, sortOrder = 1000)
        )
        return if (created > 0) created
        else checkNotNull(categoryDao.findByName(OTHER, CategoryType.EXPENSE.name)).id
    }

    /**
     * Where money paid by [method] most likely came from, using the rule the expense
     * editor uses: cash from the cash account, everything else from the first bank account.
     * A credit card purchase touches no account until the card bill is paid, so it has none.
     */
    suspend fun accountFor(method: PaymentMethod): Long? {
        if (!method.reducesCashImmediately) return null
        val open = profileDao.getAllAccounts().filterNot { it.isArchived }
        val wanted = if (method == PaymentMethod.CASH) AccountType.CASH else AccountType.BANK
        return (
            open.firstOrNull { it.type == wanted.name }
                ?: open.firstOrNull { method != PaymentMethod.CASH && it.type != AccountType.CASH.name }
                ?: open.firstOrNull()
            )?.id
    }

    private companion object {
        const val OTHER = "Other"
    }
}
