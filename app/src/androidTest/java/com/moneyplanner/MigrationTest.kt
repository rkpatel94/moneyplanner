package com.moneyplanner

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.db.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The migrations, run against real databases.
 *
 * A migration is the one piece of code that gets exactly one attempt on data the user
 * cannot recreate, so it is worth proving rather than assuming. Room validates the final
 * schema itself; what these check is the part it cannot, which is whether the rows that
 * were already there came through intact and meaning the same thing.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesFromTwoToThreeAndKeepsExistingRecords() {
        val paidOn = LocalDate.of(2026, 8, 5).toEpochDay()

        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, name, type, iconKey, colorHex, isEssential, " +
                    "isCustom, isArchived, sortOrder) " +
                    "VALUES (1, 'Rent', 'EXPENSE', 'rent', '#FF2E7D6B', 1, 0, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO recurring_bills (id, name, categoryId, amountType, amountPaise, " +
                    "dueDayOfMonth, frequency, startDateEpochDay, paymentMethod, autoDebit, " +
                    "isEssential, isActive, notes) " +
                    "VALUES (7, 'Rent', 1, 'FIXED', 1300000, 5, 'MONTHLY', 20000, 'UPI', 0, 1, 1, '')"
            )
            db.execSQL(
                "INSERT INTO bill_payments (id, billId, periodKey, amountPaise, " +
                    "dueDateEpochDay, paidDateEpochDay, notes) " +
                    "VALUES (1, 7, '2026-08', 1300000, $paidOn, $paidOn, '')"
            )
            db.execSQL(
                "INSERT INTO expenses (id, amountPaise, description, categoryId, dateEpochDay, " +
                    "paymentMethod, linkType, linkId, notes, createdAtEpochDay) " +
                    "VALUES (1, 1300000, 'Rent', 1, $paidOn, 'UPI', 'RECURRING_BILL', 7, '', $paidOn)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        db.query("SELECT linkPeriodKey FROM expenses WHERE id = 1").use { cursor ->
            assertTrue("the expense must survive the migration", cursor.moveToFirst())
            assertEquals(
                "an expense written before the period key existed must be backfilled " +
                    "from the payment it belongs to, or it could never be undone on its own",
                "2026-08",
                cursor.getString(0)
            )
        }

        db.query("SELECT COUNT(*) FROM bill_payments").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun migrationDropsSharesWhoseOwnerIsAlreadyGone() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO shared_expenses (id, description, totalAmountPaise, dateEpochDay, " +
                    "categoryId, splitType, paidByPersonId, notes) " +
                    "VALUES (1, 'Dinner', 200000, 20000, NULL, 'EQUAL', NULL, '')"
            )
            // A share left behind by a person deleted before the foreign key existed. The
            // new constraint cannot be applied while it is still there.
            db.execSQL(
                "INSERT INTO shared_expense_shares (id, sharedExpenseId, personId, " +
                    "shareAmountPaise, sharePercent) VALUES (1, 1, 999, 100000, NULL)"
            )
            db.execSQL(
                "INSERT INTO shared_expense_shares (id, sharedExpenseId, personId, " +
                    "shareAmountPaise, sharePercent) VALUES (2, 1, NULL, 100000, NULL)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        db.query("SELECT id, personId FROM shared_expense_shares ORDER BY id").use { cursor ->
            assertEquals("the orphan goes, the user's own share stays", 1, cursor.count)
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
            assertTrue(cursor.isNull(1))
        }
    }

    @Test
    fun migratingToFourLeavesExistingAccountsAlone() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, name, type, openingBalancePaise, " +
                    "openingDateEpochDay, isArchived, sortOrder) " +
                    "VALUES (1, 'Savings', 'BANK', 4000000, 20000, 0, 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_3_4)

        db.query("SELECT name, openingBalancePaise FROM accounts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Savings", cursor.getString(0))
            assertEquals(4_000_000L, cursor.getLong(1))
        }
        db.query("SELECT COUNT(*) FROM account_transfers").use { cursor ->
            cursor.moveToFirst()
            assertEquals("a fresh transfers table starts empty", 0, cursor.getInt(0))
        }
    }

    @Test
    fun migratesAllTheWayFromOne() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 4, true, *Migrations.ALL)
    }
}
