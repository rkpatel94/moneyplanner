package com.moneyplanner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneyplanner.data.backup.BackupManager
import com.moneyplanner.data.backup.BackupPreview
import com.moneyplanner.data.backup.RestoreResult
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.db.DefaultData
import com.moneyplanner.data.db.entity.AccountEntity
import com.moneyplanner.data.db.entity.BudgetEntity
import com.moneyplanner.data.db.entity.CategoryEntity
import com.moneyplanner.data.db.entity.ExpenseEntity
import com.moneyplanner.data.db.entity.ExpenseTagEntity
import com.moneyplanner.data.db.entity.TagEntity
import com.moneyplanner.data.db.entity.PersonEntity
import com.moneyplanner.data.db.entity.PersonLedgerEntryEntity
import com.moneyplanner.data.db.entity.RecurringBillEntity
import com.moneyplanner.data.db.entity.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * A backup, written out and read straight back in.
 *
 * A restore clears every table before it repopulates them, so a section the writer leaves
 * out is not merely missing from the file: it is deleted from the app the moment the file
 * is restored. Budgets were lost exactly that way, by every restore, for as long as the
 * writer did not write them. The preview had drifted too, counting sections under names the
 * writer never used. Both only show up when the real writer, the real reader and a real
 * database meet, which is what these tests arrange.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var database: AppDatabase
    private lateinit var backups: BackupManager

    private val today = LocalDate.of(2026, 8, 18)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        backups = BackupManager(context, database, Dispatchers.IO)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun readablePreview(text: String): BackupPreview.Readable {
        val preview = backups.previewText(text)
        assertTrue("expected a readable backup, got $preview", preview is BackupPreview.Readable)
        return preview as BackupPreview.Readable
    }

    @Test
    fun budgetsBillsAndLedgerEntriesComeBackFromABackup() = runBlocking {
        val categoryId = database.categoryDao().insert(
            CategoryEntity(name = "Grocery", type = "EXPENSE", iconKey = "grocery", isEssential = true)
        )
        val budget = BudgetEntity(
            categoryId = categoryId,
            amountPaise = 1_200_000,
            isActive = true,
            notes = "Weekly shop",
            createdAtEpochDay = today.toEpochDay(),
            rolloverEnabled = true,
            alertThresholdPercent = 65
        )
        val budgetId = database.budgetDao().insert(budget)
        val billId = database.billDao().insert(
            RecurringBillEntity(
                name = "Rent",
                categoryId = categoryId,
                amountType = "FIXED",
                amountPaise = 1_300_000,
                dueDayOfMonth = 5,
                frequency = "MONTHLY",
                startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay()
            )
        )
        val personId = database.peopleDao().insert(
            PersonEntity(name = "Amit", relation = "FRIEND")
        )
        val entryId = database.peopleDao().insertEntry(
            PersonLedgerEntryEntity(
                personId = personId,
                amountPaise = 300_000,
                direction = "THEY_OWE_ME",
                dateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
                description = "Loan"
            )
        )

        val json = backups.buildJson(today)

        val preview = readablePreview(json)
        assertEquals(BackupManager.FORMAT_VERSION, preview.formatVersion)
        assertTrue("a backup this build writes must carry budgets", preview.includesBudgets)
        assertEquals(1, preview.budgets)
        assertEquals(
            "bills must be counted under the name the writer gives them",
            1,
            preview.bills
        )
        assertEquals(1, preview.people)
        // The budget, the bill, the person and the ledger entry. The ledger entry is the
        // record the old preview skipped by looking for it under the wrong name. The
        // category is a standard one rather than a custom one, so it is structure, of the
        // kind every fresh install has, and is not counted as a record.
        assertEquals(4, preview.totalRecords)
        assertFalse(preview.isEmpty)

        database.maintenanceDao().clearEverything()
        assertTrue(database.budgetDao().getAll().isEmpty())

        assertEquals(RestoreResult.Success, backups.importText(json))

        val restored = database.budgetDao().getById(budgetId)
        assertEquals(
            "the budget must come back exactly as it was, every column included",
            budget.copy(id = budgetId),
            restored
        )
        assertEquals(categoryId, restored!!.categoryId)
        assertTrue("rollover must survive a restore", restored.rolloverEnabled)
        assertEquals("the alert threshold must survive a restore", 65, restored.alertThresholdPercent)
        assertEquals(listOf(billId), database.billDao().getAll().map { it.id })
        val entries = database.peopleDao().getAllEntries()
        assertEquals(listOf(entryId), entries.map { it.id })
        assertEquals(personId, entries.single().personId)
    }

    @Test
    fun aFormatFourFileWithoutBudgetsStillRestoresAndSaysSo() = runBlocking {
        // The shape a format 4 build wrote. Budgets were never written before format 5,
        // so there is no budgets section at all, even though the app had budgets then.
        val formatFour = """
            {
              "formatVersion": 4,
              "exportedOn": "2026-06-30",
              "app": "Personal Money Planner",
              "profile": {},
              "categories": [
                {"id": 1, "name": "Rent", "type": "EXPENSE", "iconKey": "rent",
                 "colorHex": "#FF2E7D6B", "isEssential": true, "isCustom": false,
                 "isArchived": false, "sortOrder": 0}
              ],
              "bills": [
                {"id": 7, "name": "Rent", "categoryId": 1, "amountType": "FIXED",
                 "amountPaise": 1300000, "minAmountPaise": null, "maxAmountPaise": null,
                 "dueDayOfMonth": 5, "frequency": "MONTHLY", "startDateEpochDay": 20454,
                 "endDateEpochDay": null, "paymentMethod": "UPI", "autoDebit": false,
                 "isEssential": true, "isActive": true, "notes": ""}
              ]
            }
        """.trimIndent()

        val preview = readablePreview(formatFour)
        assertEquals(4, preview.formatVersion)
        assertFalse(
            "a file older than format 5 must say it carries no budgets before it is restored",
            preview.includesBudgets
        )
        assertEquals(0, preview.budgets)
        assertEquals(1, preview.bills)
        assertFalse(preview.isEmpty)

        // A budget the user has now, which restoring the older file will replace.
        val foodId = database.categoryDao().insert(CategoryEntity(name = "Food", type = "EXPENSE"))
        database.budgetDao().insert(BudgetEntity(categoryId = foodId, amountPaise = 500_000))

        assertEquals(RestoreResult.Success, backups.importText(formatFour))

        assertTrue(
            "the file holds no budgets, so none survive, which is what the preview warned of",
            database.budgetDao().getAll().isEmpty()
        )
        val bill = database.billDao().getAll().single()
        assertEquals(7L, bill.id)
        assertEquals(1L, bill.categoryId)
        assertEquals(1_300_000L, bill.amountPaise)
        assertEquals(listOf(1L), database.categoryDao().getAll().map { it.id })
    }

    @Test
    fun aBackupOfAFreshInstallIsRecognisedAsEmpty() = runBlocking {
        // Exactly what the app seeds on first run, and nothing the user entered.
        database.categoryDao().insertAll(DefaultData.categories())
        database.profileDao().insertAccount(DefaultData.defaultAccount(today))
        database.profileDao().insertAccount(DefaultData.defaultCashAccount(today))
        database.familyDao().insert(DefaultData.defaultFamilyMember())
        database.profileDao().upsertProfile(
            UserProfileEntity(createdAtEpochDay = today.toEpochDay())
        )

        val fresh = readablePreview(backups.buildJson(today))
        assertTrue(
            "standard categories, empty accounts and the Self entry are not records, so " +
                "restoring this file would leave the user with nothing",
            fresh.isEmpty
        )
        assertEquals("the accounts are still reported, only not counted", 2, fresh.accounts)

        database.expenseDao().insert(
            ExpenseEntity(
                amountPaise = 25_000,
                description = "Lunch",
                categoryId = database.categoryDao().getAll().first().id,
                dateEpochDay = today.toEpochDay(),
                paymentMethod = "UPI"
            )
        )

        val withOneExpense = readablePreview(backups.buildJson(today))
        assertFalse(withOneExpense.isEmpty)
        assertEquals(1, withOneExpense.totalRecords)
    }

    /**
     * Accounts the user made are records, whatever their opening balance.
     *
     * A zero opening balance is an ordinary way to add an account, with the real figure
     * entered afterwards as a correction, so only the two accounts a fresh install creates
     * may be taken for seed data.
     */
    @Test
    fun zeroBalanceAccountsTheUserMadeAreNotMistakenForSeedData() = runBlocking {
        database.categoryDao().insertAll(DefaultData.categories())
        database.profileDao().insertAccount(DefaultData.defaultAccount(today))
        database.profileDao().insertAccount(DefaultData.defaultCashAccount(today))
        database.profileDao().insertAccount(
            AccountEntity(
                name = "HDFC Savings",
                type = "BANK",
                openingBalancePaise = 0L,
                openingDateEpochDay = today.toEpochDay(),
                sortOrder = 2
            )
        )

        val preview = readablePreview(backups.buildJson(today))

        assertFalse("a file holding an account the user made is not empty", preview.isEmpty)
        assertEquals(1, preview.totalRecords)
        assertEquals(3, preview.accounts)
    }

    /**
     * A restore replaces the tags too, including ones the device already had.
     *
     * The wipe before a restore used to leave the tags table alone, because nothing
     * cascades into it. A tag on this phone could then shadow the file's own: sharing its
     * name, the file's tag was silently skipped and the link pointing at it failed the whole
     * restore; sharing its id, the file's expenses came back carrying the wrong label.
     */
    @Test
    fun aRestoreReplacesTagsAlreadyOnTheDevice() = runBlocking {
        val categoryId = database.categoryDao().insert(
            CategoryEntity(name = "Travel", type = "EXPENSE", iconKey = "travel")
        )
        val expenseId = database.expenseDao().insert(
            ExpenseEntity(
                amountPaise = 480_000,
                description = "Train to Pune",
                categoryId = categoryId,
                dateEpochDay = today.toEpochDay(),
                paymentMethod = "UPI"
            )
        )
        val metadata = database.expenseMetadataDao()
        metadata.insertTag(TagEntity(id = 3, name = "Trip"))
        metadata.insertExpenseTag(ExpenseTagEntity(expenseId = expenseId, tagId = 3))

        val json = backups.buildJson(today)

        // The phone being restored onto has tags of its own: one with the file's tag name
        // under another id, and one holding the file's tag id under another name.
        database.maintenanceDao().clearEverything()
        metadata.insertTag(TagEntity(id = 1, name = "Trip"))
        metadata.insertTag(TagEntity(id = 3, name = "Office"))

        assertEquals(RestoreResult.Success, backups.importText(json))

        assertEquals(listOf("Trip"), metadata.tagNamesForExpense(expenseId))
        assertEquals(
            "only the file's tags remain after a restore",
            listOf(3L to "Trip"),
            metadata.getAllTags().map { it.id to it.name }
        )
    }

    /** A full wipe leaves no tags behind, so a reset really is a fresh start. */
    @Test
    fun clearingEverythingLeavesNoTagsBehind() = runBlocking {
        database.expenseMetadataDao().insertTag(TagEntity(name = "Office"))

        database.maintenanceDao().clearEverything()

        assertTrue(database.expenseMetadataDao().getAllTags().isEmpty())
    }
}
