package com.moneyplanner.data.repo

import androidx.room.withTransaction
import com.moneyplanner.core.money.Money
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.db.dao.ExpenseDao
import com.moneyplanner.data.db.dao.PeopleDao
import com.moneyplanner.data.db.entity.PersonEntity
import com.moneyplanner.data.db.entity.SharedExpenseEntity
import com.moneyplanner.data.db.entity.SharedExpenseShareEntity
import com.moneyplanner.data.mapper.toDomain
import com.moneyplanner.data.mapper.toEntity
import com.moneyplanner.domain.calc.SplitCalculator
import com.moneyplanner.domain.calc.SplitParticipant
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.LedgerSourceType
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.Person
import com.moneyplanner.domain.model.PersonLedgerEntry
import com.moneyplanner.domain.model.Relation
import com.moneyplanner.domain.model.Settlement
import com.moneyplanner.domain.model.SharedExpense
import com.moneyplanner.domain.model.SharedExpenseShare
import com.moneyplanner.domain.model.SplitType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeopleRepository @Inject constructor(
    private val dao: PeopleDao,
    private val expenseDao: ExpenseDao,
    private val database: AppDatabase,
    private val today: TodayProvider
) {
    val people: Flow<List<Person>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    val ledgerEntries: Flow<List<PersonLedgerEntry>> =
        dao.observeAllEntries().map { list -> list.map { it.toDomain() } }

    val settlements: Flow<List<Settlement>> =
        dao.observeAllSettlements().map { list -> list.map { it.toDomain() } }

    val sharedExpenses: Flow<List<SharedExpense>> =
        dao.observeSharedExpenses().map { list -> list.map { it.toDomain() } }

    fun person(id: Long): Flow<Person?> = dao.observeById(id).map { it?.toDomain() }

    fun entriesFor(personId: Long): Flow<List<PersonLedgerEntry>> =
        dao.observeEntriesFor(personId).map { list -> list.map { it.toDomain() } }

    fun settlementsFor(personId: Long): Flow<List<Settlement>> =
        dao.observeSettlementsFor(personId).map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<Person>> =
        dao.search(query).map { list -> list.map { it.toDomain() } }

    suspend fun addPerson(name: String, relation: Relation, phone: String?, notes: String): Long =
        dao.insert(
            PersonEntity(
                name = name.trim(),
                relation = relation.name,
                phone = phone?.takeIf { it.isNotBlank() },
                notes = notes,
                createdAtEpochDay = today.today().toEpochDay()
            )
        )

    suspend fun updatePerson(person: Person) = dao.update(
        PersonEntity(
            id = person.id,
            name = person.name,
            relation = person.relation.name,
            phone = person.phone,
            notes = person.notes,
            isArchived = person.isArchived
        )
    )

    /** Deleting a person also removes their ledger and settlements, by foreign key. */
    suspend fun deletePerson(id: Long) = dao.deleteById(id)

    suspend fun addEntry(entry: PersonLedgerEntry): Long = dao.insertEntry(entry.toEntity())

    suspend fun updateEntry(entry: PersonLedgerEntry) = dao.updateEntry(entry.toEntity())

    suspend fun deleteEntry(id: Long) = dao.deleteEntryById(id)

    suspend fun getEntry(id: Long) = dao.getEntry(id)?.toDomain()

    suspend fun addSettlement(settlement: Settlement): Long =
        dao.insertSettlement(settlement.toEntity())

    /**
     * Corrects a settlement that was already recorded.
     *
     * Nothing else has to move with it. A settlement is not linked to an obligation the
     * way a bill payment is linked to its bill: the balance with a person is recomputed
     * from the entries and the settlements on every read, so changing the amount, the
     * account or the date here is reflected everywhere at once.
     */
    suspend fun updateSettlement(settlement: Settlement) =
        dao.updateSettlement(settlement.toEntity())

    suspend fun deleteSettlement(id: Long) = dao.deleteSettlementById(id)

    suspend fun sharesFor(sharedExpenseId: Long): List<SharedExpenseShare> =
        dao.getShares(sharedExpenseId).map { it.toDomain() }

    suspend fun getSharedExpense(id: Long): SharedExpense? =
        dao.getSharedExpense(id)?.toDomain()

    /**
     * Saves a shared bill and the balances it creates, in one transaction.
     *
     * The split metadata and the ledger entries are written together, so a shared expense
     * can never exist without the balances it implies, and the balances can never survive
     * without the expense that explains them.
     */
    suspend fun saveSharedExpense(
        description: String,
        totalAmount: Money,
        date: LocalDate,
        categoryId: Long?,
        splitType: SplitType,
        participants: List<SplitParticipant>,
        paidByPersonId: Long?,
        notes: String,
        existingId: Long? = null
    ): Long = database.withTransaction {
        val result = SplitCalculator.split(totalAmount, splitType, participants, paidByPersonId)

        if (existingId != null) {
            dao.deleteEntriesFromSource(LedgerSourceType.SHARED_EXPENSE.name, existingId)
            dao.deleteShares(existingId)
            expenseDao.deleteLinkedAll(ExpenseLinkType.SHARED_EXPENSE.name, existingId)
        }

        val header = SharedExpenseEntity(
            id = existingId ?: 0,
            description = description.trim(),
            totalAmountPaise = totalAmount.paise,
            dateEpochDay = date.toEpochDay(),
            categoryId = categoryId,
            splitType = splitType.name,
            paidByPersonId = paidByPersonId,
            notes = notes
        )
        val sharedId = if (existingId != null) {
            dao.updateSharedExpense(header)
            existingId
        } else {
            dao.insertSharedExpense(header)
        }

        dao.insertShares(
            result.shares.map { share ->
                SharedExpenseShareEntity(
                    sharedExpenseId = sharedId,
                    personId = share.personId,
                    shareAmountPaise = share.amount.paise,
                    sharePercent = share.percent
                )
            }
        )

        dao.insertEntries(
            result.obligations.map { obligation ->
                PersonLedgerEntry(
                    id = 0,
                    personId = obligation.personId,
                    amount = obligation.amount,
                    direction = obligation.direction,
                    date = date,
                    expectedDate = null,
                    description = description.trim(),
                    sourceType = LedgerSourceType.SHARED_EXPENSE,
                    sourceId = sharedId,
                    notes = ""
                ).toEntity()
            }
        )

        // The money the user actually handed over.
        //
        // Without this the balance never falls when they pay a shared bill, while the
        // repayments still raise it, so settling a dinner they paid for would leave them
        // better off than before they bought it. Recorded at the full amount because that
        // is what left the account; what comes back is tracked as a person balance and
        // arrives as a settlement, which the balance already counts.
        if (result.amountPaidByUser.isPositive && categoryId != null) {
            expenseDao.insert(
                Expense(
                    id = 0,
                    amount = result.amountPaidByUser,
                    description = description.trim().ifBlank { "Shared expense" },
                    categoryId = categoryId,
                    date = date,
                    paymentMethod = PaymentMethod.UPI,
                    linkType = ExpenseLinkType.SHARED_EXPENSE,
                    linkId = sharedId,
                    notes = notes
                ).toEntity(date)
            )
        }

        sharedId
    }

    /** Removes a shared bill together with every balance and expense it created. */
    suspend fun deleteSharedExpense(id: Long) = database.withTransaction {
        expenseDao.deleteLinkedAll(ExpenseLinkType.SHARED_EXPENSE.name, id)
        dao.deleteSharedExpenseCascade(id)
    }

    /** A plain "they owe me" or "I owe them" record with no split behind it. */
    suspend fun addSimpleObligation(
        personId: Long,
        amount: Money,
        direction: LedgerDirection,
        description: String,
        date: LocalDate,
        expectedDate: LocalDate?,
        notes: String
    ): Long = dao.insertEntry(
        PersonLedgerEntry(
            id = 0,
            personId = personId,
            amount = amount,
            direction = direction,
            date = date,
            expectedDate = expectedDate,
            description = description.trim(),
            sourceType = LedgerSourceType.MANUAL,
            sourceId = null,
            notes = notes
        ).toEntity()
    )
}
