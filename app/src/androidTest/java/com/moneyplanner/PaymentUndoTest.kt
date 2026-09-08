package com.moneyplanner

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneyplanner.core.money.Money
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.db.DefaultData
import com.moneyplanner.data.repo.AnnualExpenseRepository
import com.moneyplanner.data.repo.BillRepository
import com.moneyplanner.data.repo.EmiRepository
import com.moneyplanner.data.repo.PeopleRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.domain.calc.SplitParticipant
import com.moneyplanner.domain.model.AnnualExpense
import com.moneyplanner.domain.model.BillAmountType
import com.moneyplanner.domain.model.Emi
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.RecurringBill
import com.moneyplanner.domain.model.Relation
import com.moneyplanner.domain.model.SplitType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/**
 * What happens to the expense when a payment is undone.
 *
 * Marking an obligation paid writes two rows that describe one event: the payment, and
 * the expense that takes the money out of the balance. Undoing has to remove exactly the
 * pair it created — no more, because the other months are somebody's real history, and no
 * less, because an orphaned expense keeps spending money that was never spent.
 *
 * These need a real SQLite engine, which is why they live here rather than in the JVM
 * suite. That is also why the defects they cover went unnoticed: nothing exercised the
 * repository layer at all.
 */
@RunWith(AndroidJUnit4::class)
class PaymentUndoTest {

    private lateinit var database: AppDatabase
    private lateinit var bills: BillRepository
    private lateinit var annuals: AnnualExpenseRepository
    private lateinit var emis: EmiRepository
    private lateinit var people: PeopleRepository
    private var categoryId: Long = 0

    private val today = object : TodayProvider {
        override fun today(): LocalDate = LocalDate.of(2026, 8, 18)
    }

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()

        bills = BillRepository(database.billDao(), database.expenseDao(), database, today)
        annuals = AnnualExpenseRepository(
            database.annualExpenseDao(), database.expenseDao(), database, today
        )
        emis = EmiRepository(database.emiDao(), database.expenseDao(), database, today)
        people = PeopleRepository(database.peopleDao(), database.expenseDao(), database, today)

        database.categoryDao().insertAll(DefaultData.categories())
        categoryId = database.categoryDao().getAll().first().id
    }

    @After
    fun tearDown() = database.close()

    private fun rentBill(id: Long = 0) = RecurringBill(
        id = id,
        name = "Rent",
        categoryId = categoryId,
        amountType = BillAmountType.FIXED,
        amount = Money.ofRupees(13_000),
        minAmount = null,
        maxAmount = null,
        dueDayOfMonth = 5,
        frequency = Frequency.MONTHLY,
        startDate = LocalDate.of(2026, 1, 1),
        endDate = null,
        paymentMethod = PaymentMethod.UPI,
        autoDebit = false,
        isEssential = true,
        isActive = true,
        notes = ""
    )

    @Test
    fun undoingOneBillPeriodKeepsEveryOtherMonth() = runBlocking {
        val billId = bills.add(rentBill())
        val bill = bills.getById(billId)!!

        listOf(6, 7, 8).forEach { month ->
            bills.markPaid(
                bill = bill,
                month = YearMonth.of(2026, month),
                amount = Money.ofRupees(13_000),
                dueDate = LocalDate.of(2026, month, 5),
                paidOn = LocalDate.of(2026, month, 5)
            )
        }
        assertEquals(3, database.expenseDao().getAll().size)

        bills.undoPayment(billId, YearMonth.of(2026, 8))

        val remaining = database.expenseDao()
            .getLinked(ExpenseLinkType.RECURRING_BILL.name, billId)
        assertEquals(
            "undoing August must not take June and July with it",
            2,
            remaining.size
        )
        assertTrue(
            remaining.map { it.linkPeriodKey }.containsAll(listOf("2026-06", "2026-07"))
        )
        assertEquals(2, database.billDao().getAllPayments().size)
    }

    @Test
    fun undoingOneYearOfAnAnnualExpenseKeepsTheOthers() = runBlocking {
        val annualId = annuals.add(
            AnnualExpense(
                id = 0,
                name = "Car insurance",
                categoryId = categoryId,
                amount = Money.ofRupees(18_000),
                dueMonth = 4,
                dueDayOfMonth = 10,
                vehicleId = null,
                isActive = true,
                notes = ""
            )
        )
        val annual = annuals.getById(annualId)!!

        listOf(2025, 2026).forEach { year ->
            annuals.markPaid(annual, year, Money.ofRupees(18_000), LocalDate.of(year, 4, 10))
        }
        assertEquals(2, database.expenseDao().getAll().size)

        annuals.undoPayment(annualId, 2026)

        val remaining = database.expenseDao()
            .getLinked(ExpenseLinkType.ANNUAL_EXPENSE.name, annualId)
        assertEquals("2025 must survive undoing 2026", 1, remaining.size)
        assertEquals("2025", remaining.single().linkPeriodKey)
    }

    @Test
    fun undoingAnInstallmentRemovesItsExpenseToo() = runBlocking {
        val emiId = emis.add(
            Emi(
                id = 0,
                name = "Bike loan",
                categoryId = categoryId,
                principal = Money.ofRupees(60_000),
                emiAmount = Money.ofRupees(5_500),
                interestRatePercent = 11.0,
                startDate = LocalDate.of(2026, 1, 1),
                firstDueDate = LocalDate.of(2026, 1, 5),
                frequency = Frequency.MONTHLY,
                totalInstallments = 12,
                openingPaidInstallments = 0,
                vehicleId = null,
                accountReference = "",
                autoDebit = true,
                isActive = true,
                notes = ""
            )
        )
        val emi = emis.getById(emiId)!!

        listOf(1, 2).forEach { number ->
            emis.payInstallment(
                emi = emi,
                installmentNumber = number,
                amount = Money.ofRupees(5_500),
                paidOn = LocalDate.of(2026, number, 5),
                categoryId = categoryId
            )
        }
        assertEquals(2, database.expenseDao().getAll().size)

        emis.undoInstallment(emi, 2)

        val remaining = database.expenseDao().getLinked(ExpenseLinkType.EMI.name, emiId)
        assertEquals(
            "an undone installment must not leave an expense still spending the money",
            1,
            remaining.size
        )
        assertEquals("1", remaining.single().linkPeriodKey)
        assertEquals(1, database.emiDao().getPaymentsFor(emiId).size)
    }

    @Test
    fun payingASharedBillTakesTheMoneyOutOfTheBalance() = runBlocking {
        val amit = people.addPerson("Amit", Relation.FRIEND, null, "")
        val riya = people.addPerson("Riya", Relation.FRIEND, null, "")

        people.saveSharedExpense(
            description = "Dinner",
            totalAmount = Money.ofRupees(3_000),
            date = LocalDate.of(2026, 8, 10),
            categoryId = categoryId,
            splitType = SplitType.EQUAL,
            participants = listOf(
                SplitParticipant(personId = null),
                SplitParticipant(personId = amit),
                SplitParticipant(personId = riya)
            ),
            paidByPersonId = null,
            notes = ""
        )

        val expenses = database.expenseDao().getAll()
        assertEquals(
            "paying a shared bill must record what actually left the account",
            1,
            expenses.size
        )
        assertEquals(300_000L, expenses.single().amountPaise)
        assertEquals(ExpenseLinkType.SHARED_EXPENSE.name, expenses.single().linkType)

        // The two shares owed back are what makes the net cost the user's own third.
        val owed = database.peopleDao().getAllEntries()
        assertEquals(2, owed.size)
        assertEquals(200_000L, owed.sumOf { it.amountPaise })
    }

    @Test
    fun aSharedBillSomebodyElsePaidCreatesNoExpense() = runBlocking {
        val amit = people.addPerson("Amit", Relation.FRIEND, null, "")

        people.saveSharedExpense(
            description = "Dinner",
            totalAmount = Money.ofRupees(2_000),
            date = LocalDate.of(2026, 8, 10),
            categoryId = categoryId,
            splitType = SplitType.EQUAL,
            participants = listOf(
                SplitParticipant(personId = null),
                SplitParticipant(personId = amit)
            ),
            paidByPersonId = amit,
            notes = ""
        )

        assertTrue(
            "no cash left the user's account, so there is nothing to record",
            database.expenseDao().getAll().isEmpty()
        )
        assertEquals(1, database.peopleDao().getAllEntries().size)
    }

    @Test
    fun deletingASharedBillTakesItsExpenseWithIt() = runBlocking {
        val amit = people.addPerson("Amit", Relation.FRIEND, null, "")
        val sharedId = people.saveSharedExpense(
            description = "Dinner",
            totalAmount = Money.ofRupees(2_000),
            date = LocalDate.of(2026, 8, 10),
            categoryId = categoryId,
            splitType = SplitType.EQUAL,
            participants = listOf(
                SplitParticipant(personId = null),
                SplitParticipant(personId = amit)
            ),
            paidByPersonId = null,
            notes = ""
        )
        assertEquals(1, database.expenseDao().getAll().size)

        people.deleteSharedExpense(sharedId)

        assertTrue(database.expenseDao().getAll().isEmpty())
        assertTrue(database.peopleDao().getAllEntries().isEmpty())
    }

    @Test
    fun deletingAPersonTakesTheirSharesWithThem() = runBlocking {
        val amit = people.addPerson("Amit", Relation.FRIEND, null, "")
        val sharedId = people.saveSharedExpense(
            description = "Dinner",
            totalAmount = Money.ofRupees(2_000),
            date = LocalDate.of(2026, 8, 10),
            categoryId = categoryId,
            splitType = SplitType.EQUAL,
            participants = listOf(
                SplitParticipant(personId = null),
                SplitParticipant(personId = amit)
            ),
            paidByPersonId = null,
            notes = ""
        )
        assertEquals(2, database.peopleDao().getShares(sharedId).size)

        people.deletePerson(amit)

        assertTrue(
            "a deleted person must not leave a share pointing at an id that no longer resolves",
            database.peopleDao().getShares(sharedId).none { it.personId == amit }
        )
    }
}
