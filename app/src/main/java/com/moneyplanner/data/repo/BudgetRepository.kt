package com.moneyplanner.data.repo

import com.moneyplanner.core.money.Money
import com.moneyplanner.data.db.dao.BudgetDao
import com.moneyplanner.data.mapper.toDomain
import com.moneyplanner.data.mapper.toEntity
import com.moneyplanner.domain.model.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val dao: BudgetDao,
    private val today: TodayProvider
) {
    val all: Flow<List<Budget>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Budget? = dao.getById(id)?.toDomain()

    /**
     * Creates or updates the limit for a category.
     *
     * Setting a budget for a category that already has one updates it rather than adding
     * a second, since two limits on the same spending would have no meaningful answer.
     * The same applies to the single overall budget, which is identified by a null
     * category and so cannot be caught by a unique index.
     */
    suspend fun setBudget(
        categoryId: Long?,
        amount: Money,
        rolloverEnabled: Boolean = false,
        alertThresholdPercent: Int = Budget.DEFAULT_ALERT_THRESHOLD
    ): Long {
        val threshold = alertThresholdPercent.coerceIn(1, 100)
        val existing = dao.findForCategory(categoryId)
        return if (existing == null) {
            dao.insert(
                Budget(
                    id = 0,
                    categoryId = categoryId,
                    amount = amount,
                    rolloverEnabled = rolloverEnabled,
                    alertThresholdPercent = threshold
                ).toEntity(today.today())
            )
        } else {
            dao.update(
                existing.copy(
                    amountPaise = amount.paise,
                    isActive = true,
                    rolloverEnabled = rolloverEnabled,
                    alertThresholdPercent = threshold
                )
            )
            existing.id
        }
    }

    suspend fun update(budget: Budget) = dao.update(budget.toEntity(today.today()))

    suspend fun delete(id: Long) = dao.deleteById(id)
}
