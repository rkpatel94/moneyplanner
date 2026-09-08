package com.moneyplanner

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.db.DefaultData
import com.moneyplanner.data.db.entity.CategoryEntity
import com.moneyplanner.data.db.entity.ExpenseEntity
import com.moneyplanner.data.db.entity.PersonEntity
import com.moneyplanner.data.db.entity.PersonLedgerEntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Database level checks that need a real SQLite engine.
 *
 * These cover the constraints the calculators rely on but cannot themselves enforce:
 * that deleting a person really does take their ledger with them, that a category in use
 * cannot be deleted out from under its expenses, and that a full wipe succeeds in the
 * order the restore performs it.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseIntegrityTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingAPersonRemovesTheirLedgerEntries() = runBlocking {
        val personId = database.peopleDao().insert(
            PersonEntity(name = "Amit", relation = "FRIEND")
        )
        database.peopleDao().insertEntry(
            PersonLedgerEntryEntity(
                personId = personId,
                amountPaise = 300_000,
                direction = "THEY_OWE_ME",
                dateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
                description = "Loan"
            )
        )
        assertEquals(1, database.peopleDao().getEntriesFor(personId).size)

        database.peopleDao().deleteById(personId)

        assertTrue(
            "a balance must not outlive the person it belongs to",
            database.peopleDao().getAllEntries().isEmpty()
        )
    }

    @Test
    fun aCategoryInUseCannotBeDeleted() = runBlocking {
        database.categoryDao().insertAll(DefaultData.categories())
        val category = database.categoryDao().getAll().first()

        database.expenseDao().insert(
            ExpenseEntity(
                amountPaise = 25_000,
                description = "Lunch",
                categoryId = category.id,
                dateEpochDay = LocalDate.of(2026, 8, 10).toEpochDay(),
                paymentMethod = "UPI"
            )
        )

        val failed = runCatching { database.categoryDao().deleteById(category.id) }.isFailure

        assertTrue(
            "an expense must never be left pointing at a category that no longer exists",
            failed
        )
    }

    @Test
    fun clearingEverythingSucceedsInRestoreOrder() = runBlocking {
        database.categoryDao().insertAll(DefaultData.categories())
        val category = database.categoryDao().getAll().first()
        val personId = database.peopleDao().insert(
            PersonEntity(name = "Amit", relation = "FRIEND")
        )
        database.expenseDao().insert(
            ExpenseEntity(
                amountPaise = 25_000,
                description = "Lunch",
                categoryId = category.id,
                dateEpochDay = LocalDate.of(2026, 8, 10).toEpochDay(),
                paymentMethod = "UPI",
                personId = personId
            )
        )

        // The restore clears inside its own transaction, so this must not depend on
        // Room's clearAllTables, which refuses to run inside one.
        database.maintenanceDao().clearEverything()

        assertTrue(database.expenseDao().getAll().isEmpty())
        assertTrue(database.categoryDao().getAll().isEmpty())
        assertTrue(database.peopleDao().getAll().isEmpty())
    }

    @Test
    fun theSameBillPeriodCannotBePaidTwice() = runBlocking {
        val billId = database.billDao().insert(
            com.moneyplanner.data.db.entity.RecurringBillEntity(
                name = "Rent",
                categoryId = null,
                amountType = "FIXED",
                amountPaise = 1_300_000,
                dueDayOfMonth = 5,
                frequency = "MONTHLY",
                startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay()
            )
        )
        val payment = com.moneyplanner.data.db.entity.BillPaymentEntity(
            billId = billId,
            periodKey = "2026-08",
            amountPaise = 1_300_000,
            dueDateEpochDay = LocalDate.of(2026, 8, 5).toEpochDay(),
            paidDateEpochDay = LocalDate.of(2026, 8, 5).toEpochDay()
        )

        database.billDao().insertPayment(payment)
        database.billDao().insertPayment(payment)

        assertEquals(
            "paying the same month twice must replace the record, not duplicate it",
            1,
            database.billDao().getAllPayments().size
        )
    }
}
