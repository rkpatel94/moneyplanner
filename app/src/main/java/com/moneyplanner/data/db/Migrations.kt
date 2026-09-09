package com.moneyplanner.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations.
 *
 * These exist so that installing a new version never costs the user their records. Room
 * is deliberately *not* configured with a destructive fallback: if a migration is missing
 * the app fails loudly on open rather than quietly deleting years of financial history,
 * which is the right failure for data that cannot be recreated.
 *
 * Each statement below is copied from the schema Room itself exported, so the result is
 * byte-identical to a fresh install. Room validates that on open and will refuse to start
 * if a migration produces anything different.
 */
object Migrations {

    /**
     * Adds monthly spending budgets. Purely additive: no existing table is touched, so
     * every expense, balance and loan carries over untouched.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `budgets` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`categoryId` INTEGER, " +
                    "`amountPaise` INTEGER NOT NULL, " +
                    "`isActive` INTEGER NOT NULL, " +
                    "`notes` TEXT NOT NULL, " +
                    "`createdAtEpochDay` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` " +
                    "ON `budgets` (`categoryId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_budgets_isActive` " +
                    "ON `budgets` (`isActive`)"
            )
        }
    }

    /**
     * Lets a payment be undone one period at a time, and stops a deleted person leaving
     * orphaned shares behind.
     *
     * `linkPeriodKey` records which occurrence of an obligation an expense settled.
     * Without it, `linkId` names only the bill, so undoing one month had to delete every
     * month's expense. Existing rows are backfilled from the payment tables they belong
     * to, matched on the date the payment was recorded, so history written before this
     * version can still be undone one period at a time.
     *
     * The shares table is rebuilt rather than altered because SQLite cannot add a foreign
     * key to an existing table. Any share whose person has already been deleted is
     * dropped first, since the new constraint would otherwise refuse to apply.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `expenses` ADD COLUMN `linkPeriodKey` TEXT")

            // Backfill: match each linked expense to the payment recorded on the same day.
            db.execSQL(
                "UPDATE `expenses` SET `linkPeriodKey` = (" +
                    "SELECT bp.`periodKey` FROM `bill_payments` bp " +
                    "WHERE bp.`billId` = `expenses`.`linkId` " +
                    "AND bp.`paidDateEpochDay` = `expenses`.`dateEpochDay` LIMIT 1) " +
                    "WHERE `linkType` = 'RECURRING_BILL'"
            )
            db.execSQL(
                "UPDATE `expenses` SET `linkPeriodKey` = (" +
                    "SELECT CAST(ap.`year` AS TEXT) FROM `annual_expense_payments` ap " +
                    "WHERE ap.`annualExpenseId` = `expenses`.`linkId` " +
                    "AND ap.`paidDateEpochDay` = `expenses`.`dateEpochDay` LIMIT 1) " +
                    "WHERE `linkType` = 'ANNUAL_EXPENSE'"
            )
            db.execSQL(
                "UPDATE `expenses` SET `linkPeriodKey` = (" +
                    "SELECT CAST(ep.`installmentNumber` AS TEXT) FROM `emi_payments` ep " +
                    "WHERE ep.`emiId` = `expenses`.`linkId` " +
                    "AND ep.`paidDateEpochDay` = `expenses`.`dateEpochDay` LIMIT 1) " +
                    "WHERE `linkType` = 'EMI'"
            )

            // shared_expense_shares gains a foreign key on personId.
            db.execSQL(
                "DELETE FROM `shared_expense_shares` WHERE `personId` IS NOT NULL " +
                    "AND `personId` NOT IN (SELECT `id` FROM `people`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `shared_expense_shares_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sharedExpenseId` INTEGER NOT NULL, " +
                    "`personId` INTEGER, " +
                    "`shareAmountPaise` INTEGER NOT NULL, " +
                    "`sharePercent` REAL, " +
                    "FOREIGN KEY(`sharedExpenseId`) REFERENCES `shared_expenses`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`personId`) REFERENCES `people`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "INSERT INTO `shared_expense_shares_new` " +
                    "(`id`, `sharedExpenseId`, `personId`, `shareAmountPaise`, `sharePercent`) " +
                    "SELECT `id`, `sharedExpenseId`, `personId`, `shareAmountPaise`, `sharePercent` " +
                    "FROM `shared_expense_shares`"
            )
            db.execSQL("DROP TABLE `shared_expense_shares`")
            db.execSQL("ALTER TABLE `shared_expense_shares_new` RENAME TO `shared_expense_shares`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_shared_expense_shares_sharedExpenseId` " +
                    "ON `shared_expense_shares` (`sharedExpenseId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_shared_expense_shares_personId` " +
                    "ON `shared_expense_shares` (`personId`)"
            )
        }
    }

    /**
     * Adds transfers between the user's own accounts.
     *
     * Purely additive. Every existing record keeps the account it was already filed
     * against, and a database with no transfers in it describes exactly the same position
     * as it did before.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `account_transfers` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`fromAccountId` INTEGER NOT NULL, " +
                    "`toAccountId` INTEGER NOT NULL, " +
                    "`amountPaise` INTEGER NOT NULL, " +
                    "`dateEpochDay` INTEGER NOT NULL, " +
                    "`notes` TEXT NOT NULL, " +
                    "FOREIGN KEY(`fromAccountId`) REFERENCES `accounts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`toAccountId`) REFERENCES `accounts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_account_transfers_fromAccountId` " +
                    "ON `account_transfers` (`fromAccountId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_account_transfers_toAccountId` " +
                    "ON `account_transfers` (`toAccountId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_account_transfers_dateEpochDay` " +
                    "ON `account_transfers` (`dateEpochDay`)"
            )
        }
    }

    /** Adds optional labels and document-picker receipt attachments to expenses. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `expense_tags` (`expenseId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`expenseId`, `tagId`), " +
                    "FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_tags_tagId` ON `expense_tags` (`tagId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `expense_attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `expenseId` INTEGER NOT NULL, `uri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `addedAtEpochDay` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_attachments_expenseId` ON `expense_attachments` (`expenseId`)")
        }
    }

    /**
     * Budgets gain rollover and their own alert threshold.
     *
     * Purely additive, with defaults that reproduce the previous behaviour exactly: no
     * rollover, and the 80 percent warning that used to be a constant in the calculator.
     * An existing budget therefore behaves on upgrade precisely as it did before.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `budgets` ADD COLUMN `rolloverEnabled` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE `budgets` ADD COLUMN `alertThresholdPercent` INTEGER NOT NULL DEFAULT 80"
            )
        }
    }

    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6
    )
}
