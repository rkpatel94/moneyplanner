package com.moneyplanner.data.repo

import com.moneyplanner.core.time.periodKey
import com.moneyplanner.data.db.dao.ExpenseDao
import com.moneyplanner.data.db.dao.IncomeDao
import com.moneyplanner.data.mapper.toDomain
import com.moneyplanner.data.mapper.toEntity
import com.moneyplanner.domain.calc.RecurrenceCalculator
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.IncomeSource
import com.moneyplanner.domain.model.IncomeTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncomeRepository @Inject constructor(
    private val dao: IncomeDao,
    private val today: TodayProvider
) {
    val sources: Flow<List<IncomeSource>> =
        dao.observeSources().map { list -> list.map { it.toDomain() } }

    val transactions: Flow<List<IncomeTransaction>> =
        dao.observeTransactions().map { list -> list.map { it.toDomain() } }

    fun transactionsIn(month: YearMonth): Flow<List<IncomeTransaction>> =
        dao.observeTransactionsBetween(
            month.atDay(1).toEpochDay(),
            month.atEndOfMonth().toEpochDay()
        ).map { list -> list.map { it.toDomain() } }

    suspend fun addSource(source: IncomeSource): Long = dao.insertSource(source.toEntity())
    suspend fun updateSource(source: IncomeSource) = dao.updateSource(source.toEntity())
    suspend fun deleteSource(id: Long) = dao.deleteSourceById(id)
    suspend fun getSource(id: Long) = dao.getSource(id)?.toDomain()

    suspend fun addTransaction(transaction: IncomeTransaction): Long =
        dao.insertTransaction(transaction.toEntity())

    suspend fun updateTransaction(transaction: IncomeTransaction) =
        dao.updateTransaction(transaction.toEntity())

    suspend fun deleteTransaction(id: Long) = dao.deleteTransactionById(id)

    suspend fun getTransaction(id: Long) = dao.getTransaction(id)?.toDomain()

    /**
     * Records that a scheduled income has arrived.
     *
     * The receipt is stamped with the period it satisfies, so the forecast can tell the
     * difference between a salary that has landed and one that is still expected, and
     * will not count the same month twice.
     *
     * The period comes from the due date the receipt is nearest to, not from the day it
     * happened to be credited. A salary due on the 1st and paid on the 31st of the month
     * before settles the coming month, and stamping it with the month it landed in would
     * leave the forecast still expecting a payment that has already been made.
     */
    suspend fun markSourceReceived(
        source: IncomeSource,
        amount: com.moneyplanner.core.money.Money,
        receivedOn: LocalDate = today.today(),
        forPeriod: YearMonth? = null
    ): Long {
        val period = forPeriod ?: RecurrenceCalculator.nearestOccurrence(
            around = receivedOn,
            start = source.startDate,
            end = source.endDate,
            dayOfMonth = source.dayOfMonth,
            frequency = source.frequency
        )?.let { YearMonth.from(it) } ?: YearMonth.from(receivedOn)

        return dao.insertTransaction(
            IncomeTransaction(
                id = 0,
                sourceId = source.id,
                name = source.name,
                type = source.type,
                amount = amount,
                date = receivedOn,
                accountId = source.accountId,
                periodKey = period.periodKey(),
                notes = ""
            ).toEntity()
        )
    }
}

@Singleton
class ExpenseRepository @Inject constructor(
    private val dao: ExpenseDao,
    private val today: TodayProvider
) {
    val all: Flow<List<Expense>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun recent(limit: Int = 10): Flow<List<Expense>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun inMonth(month: YearMonth): Flow<List<Expense>> =
        dao.observeBetween(
            month.atDay(1).toEpochDay(),
            month.atEndOfMonth().toEpochDay()
        ).map { list -> list.map { it.toDomain() } }

    fun byCategory(categoryId: Long): Flow<List<Expense>> =
        dao.observeByCategory(categoryId).map { list -> list.map { it.toDomain() } }

    fun byVehicle(vehicleId: Long): Flow<List<Expense>> =
        dao.observeByVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    fun byFamilyMember(memberId: Long): Flow<List<Expense>> =
        dao.observeByFamilyMember(memberId).map { list -> list.map { it.toDomain() } }

    fun byPerson(personId: Long): Flow<List<Expense>> =
        dao.observeByPerson(personId).map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<Expense>> =
        dao.search(query).map { list -> list.map { it.toDomain() } }

    suspend fun add(expense: Expense): Long = dao.insert(expense.toEntity(today.today()))

    suspend fun update(expense: Expense) = dao.update(expense.toEntity())

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun getById(id: Long): Expense? = dao.getById(id)?.toDomain()

    /**
     * Removes every expense recorded against an obligation.
     *
     * Only for when the obligation itself is being deleted. Undoing a single payment goes
     * through the obligation's own repository, which deletes just that period.
     */
    suspend fun deleteAllLinkedTo(linkType: ExpenseLinkType, linkId: Long) =
        dao.deleteLinkedAll(linkType.name, linkId)

    suspend fun earliestDate(): LocalDate? =
        dao.earliestDate()?.let { LocalDate.ofEpochDay(it) }
}
