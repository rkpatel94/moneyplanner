package com.moneyplanner.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.db.entity.AccountEntity
import com.moneyplanner.data.db.entity.AccountTransferEntity
import com.moneyplanner.data.db.entity.AnnualExpenseEntity
import com.moneyplanner.data.db.entity.AnnualExpensePaymentEntity
import com.moneyplanner.data.db.entity.BalanceAdjustmentEntity
import com.moneyplanner.data.db.entity.BillPaymentEntity
import com.moneyplanner.data.db.entity.CategoryEntity
import com.moneyplanner.data.db.entity.CreditCardEntity
import com.moneyplanner.data.db.entity.CreditCardPaymentEntity
import com.moneyplanner.data.db.entity.EmiEntity
import com.moneyplanner.data.db.entity.EmiPaymentEntity
import com.moneyplanner.data.db.entity.ExpenseEntity
import com.moneyplanner.data.db.entity.ExpenseAttachmentEntity
import com.moneyplanner.data.db.entity.ExpenseTagEntity
import com.moneyplanner.data.db.entity.FamilyMemberEntity
import com.moneyplanner.data.db.entity.IncomeSourceEntity
import com.moneyplanner.data.db.entity.IncomeTransactionEntity
import com.moneyplanner.data.db.entity.PersonEntity
import com.moneyplanner.data.db.entity.PersonLedgerEntryEntity
import com.moneyplanner.data.db.entity.RecurringBillEntity
import com.moneyplanner.data.db.entity.SavingsContributionEntity
import com.moneyplanner.data.db.entity.SavingsGoalEntity
import com.moneyplanner.data.db.entity.SettlementEntity
import com.moneyplanner.data.db.entity.SharedExpenseEntity
import com.moneyplanner.data.db.entity.SharedExpenseShareEntity
import com.moneyplanner.data.db.entity.UserProfileEntity
import com.moneyplanner.data.db.entity.VehicleEntity
import com.moneyplanner.data.db.entity.TagEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local backup, restore and export.
 *
 * Everything happens on the device and nothing is ever uploaded. A backup is written to
 * the app cache and then handed to the user through the system share sheet, so the user
 * decides where it goes rather than the app deciding for them.
 *
 * JSON is produced with the platform JSON classes rather than by string concatenation, so
 * a note containing a quotation mark cannot corrupt the file it is written into.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    @com.moneyplanner.di.IoDispatcher private val io: CoroutineDispatcher
) {

    companion object {
        /**
         * 2 added `linkPeriodKey` on expenses; 3 adds transfers between accounts. Older
         * 4 adds tags and attachment metadata. Older files still restore — a missing section simply yields no rows, which is exactly
         * what a database written before the feature existed contained.
         */
        const val FORMAT_VERSION = 4
        private const val EXPORT_DIR = "exports"

        /** Characters a spreadsheet reads as the start of a formula. */
        private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@')
    }

    private fun exportsDir(): File =
        File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }

    private fun stamp(today: LocalDate) = "%04d-%02d-%02d".format(
        today.year, today.monthValue, today.dayOfMonth
    )

    // ---- JSON backup -------------------------------------------------------------

    suspend fun exportJson(today: LocalDate): File = withContext(io) {
        val root = JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("exportedOn", today.toString())
            put("app", "Personal Money Planner")

            put("profile", database.profileDao().getProfile()?.let { profile ->
                JSONObject().apply {
                    put("displayName", profile.displayName)
                    put("emergencyFundMonths", profile.emergencyFundMonths)
                    put("monthStartDay", profile.monthStartDay)
                    put("onboardingCompleted", profile.onboardingCompleted)
                    put("createdAtEpochDay", profile.createdAtEpochDay)
                }
            } ?: JSONObject())

            put("accounts", database.profileDao().getAllAccounts().toJsonArray { account ->
                JSONObject().apply {
                    put("id", account.id)
                    put("name", account.name)
                    put("type", account.type)
                    put("openingBalancePaise", account.openingBalancePaise)
                    put("openingDateEpochDay", account.openingDateEpochDay)
                    put("isArchived", account.isArchived)
                    put("sortOrder", account.sortOrder)
                }
            })

            put("adjustments", database.profileDao().getAllAdjustments().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("accountId", row.accountId ?: JSONObject.NULL)
                    put("deltaPaise", row.deltaPaise)
                    put("dateEpochDay", row.dateEpochDay)
                    put("reason", row.reason)
                    put("createdAtEpochDay", row.createdAtEpochDay)
                }
            })

            put("accountTransfers", database.profileDao().getAllTransfers().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("fromAccountId", row.fromAccountId)
                    put("toAccountId", row.toAccountId)
                    put("amountPaise", row.amountPaise)
                    put("dateEpochDay", row.dateEpochDay)
                    put("notes", row.notes)
                }
            })

            put("categories", database.categoryDao().getAll().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("name", row.name)
                    put("type", row.type)
                    put("iconKey", row.iconKey)
                    put("colorHex", row.colorHex)
                    put("isEssential", row.isEssential)
                    put("isCustom", row.isCustom)
                    put("isArchived", row.isArchived)
                    put("sortOrder", row.sortOrder)
                }
            })

            put("people", database.peopleDao().getAll().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("name", row.name)
                    put("relation", row.relation)
                    put("phone", row.phone ?: JSONObject.NULL)
                    put("notes", row.notes)
                    put("isArchived", row.isArchived)
                    put("createdAtEpochDay", row.createdAtEpochDay)
                }
            })

            put("familyMembers", database.familyDao().getAll().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("name", row.name)
                    put("relation", row.relation)
                    put("isSelf", row.isSelf)
                    put("isArchived", row.isArchived)
                }
            })

            put("vehicles", database.vehicleDao().getAll().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("name", row.name)
                    put("type", row.type)
                    put("registrationNumber", row.registrationNumber ?: JSONObject.NULL)
                    put("purchaseDateEpochDay", row.purchaseDateEpochDay ?: JSONObject.NULL)
                    put("notes", row.notes)
                    put("isArchived", row.isArchived)
                }
            })

            put("incomeSources", database.incomeDao().getAllSources().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("name", row.name)
                    put("type", row.type)
                    put("amountPaise", row.amountPaise)
                    put("dayOfMonth", row.dayOfMonth)
                    put("frequency", row.frequency)
                    put("startDateEpochDay", row.startDateEpochDay)
                    put("endDateEpochDay", row.endDateEpochDay ?: JSONObject.NULL)
                    put("annualIncrementPercent", row.annualIncrementPercent)
                    put("accountId", row.accountId ?: JSONObject.NULL)
                    put("isActive", row.isActive)
                    put("notes", row.notes)
                }
            })

            put("incomeTransactions", database.incomeDao().getAllTransactions().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("sourceId", row.sourceId ?: JSONObject.NULL)
                    put("name", row.name)
                    put("type", row.type)
                    put("amountPaise", row.amountPaise)
                    put("dateEpochDay", row.dateEpochDay)
                    put("accountId", row.accountId ?: JSONObject.NULL)
                    put("periodKey", row.periodKey ?: JSONObject.NULL)
                    put("notes", row.notes)
                }
            })

            put("expenses", database.expenseDao().getAll().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("amountPaise", row.amountPaise)
                    put("description", row.description)
                    put("categoryId", row.categoryId)
                    put("dateEpochDay", row.dateEpochDay)
                    put("paymentMethod", row.paymentMethod)
                    put("accountId", row.accountId ?: JSONObject.NULL)
                    put("creditCardId", row.creditCardId ?: JSONObject.NULL)
                    put("personId", row.personId ?: JSONObject.NULL)
                    put("vehicleId", row.vehicleId ?: JSONObject.NULL)
                    put("familyMemberId", row.familyMemberId ?: JSONObject.NULL)
                    put("linkType", row.linkType)
                    put("linkId", row.linkId ?: JSONObject.NULL)
                    put("linkPeriodKey", row.linkPeriodKey ?: JSONObject.NULL)
                    put("notes", row.notes)
                    put("createdAtEpochDay", row.createdAtEpochDay)
                }
            })

            put("tags", database.expenseMetadataDao().getAllTags().toJsonArray { row ->
                JSONObject().apply { put("id", row.id); put("name", row.name) }
            })
            put("expenseTags", database.expenseMetadataDao().getAllExpenseTags().toJsonArray { row ->
                JSONObject().apply { put("expenseId", row.expenseId); put("tagId", row.tagId) }
            })
            // Attachment URIs point at documents chosen by the user. A restored URI may
            // no longer be readable on another device, but retaining its metadata is
            // more honest than silently pretending the receipt never existed.
            put("expenseAttachments", database.expenseMetadataDao().getAllAttachments().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id); put("expenseId", row.expenseId); put("uri", row.uri)
                    put("displayName", row.displayName); put("mimeType", row.mimeType)
                    put("addedAtEpochDay", row.addedAtEpochDay)
                }
            })

            put("ledgerEntries", database.peopleDao().getAllEntries().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("personId", row.personId)
                    put("amountPaise", row.amountPaise)
                    put("direction", row.direction)
                    put("dateEpochDay", row.dateEpochDay)
                    put("expectedDateEpochDay", row.expectedDateEpochDay ?: JSONObject.NULL)
                    put("description", row.description)
                    put("sourceType", row.sourceType)
                    put("sourceId", row.sourceId ?: JSONObject.NULL)
                    put("notes", row.notes)
                }
            })

            put("settlements", database.peopleDao().getAllSettlements().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("personId", row.personId)
                    put("amountPaise", row.amountPaise)
                    put("direction", row.direction)
                    put("dateEpochDay", row.dateEpochDay)
                    put("paymentMethod", row.paymentMethod)
                    put("accountId", row.accountId ?: JSONObject.NULL)
                    put("notes", row.notes)
                }
            })

            put("sharedExpenses", database.peopleDao().getAllSharedExpenses().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("description", row.description)
                    put("totalAmountPaise", row.totalAmountPaise)
                    put("dateEpochDay", row.dateEpochDay)
                    put("categoryId", row.categoryId ?: JSONObject.NULL)
                    put("splitType", row.splitType)
                    put("paidByPersonId", row.paidByPersonId ?: JSONObject.NULL)
                    put("notes", row.notes)
                }
            })

            put("sharedExpenseShares", database.peopleDao().getAllShares().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("sharedExpenseId", row.sharedExpenseId)
                    put("personId", row.personId ?: JSONObject.NULL)
                    put("shareAmountPaise", row.shareAmountPaise)
                    put("sharePercent", row.sharePercent ?: JSONObject.NULL)
                }
            })

            put("emis", database.emiDao().getAll().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("name", row.name)
                    put("categoryId", row.categoryId ?: JSONObject.NULL)
                    put("principalPaise", row.principalPaise)
                    put("emiAmountPaise", row.emiAmountPaise)
                    put("interestRatePercent", row.interestRatePercent ?: JSONObject.NULL)
                    put("startDateEpochDay", row.startDateEpochDay)
                    put("firstDueDateEpochDay", row.firstDueDateEpochDay)
                    put("frequency", row.frequency)
                    put("totalInstallments", row.totalInstallments)
                    put("openingPaidInstallments", row.openingPaidInstallments)
                    put("vehicleId", row.vehicleId ?: JSONObject.NULL)
                    put("accountReference", row.accountReference)
                    put("autoDebit", row.autoDebit)
                    put("isActive", row.isActive)
                    put("notes", row.notes)
                }
            })

            put("emiPayments", database.emiDao().getAllPayments().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("emiId", row.emiId)
                    put("installmentNumber", row.installmentNumber)
                    put("amountPaise", row.amountPaise)
                    put("dueDateEpochDay", row.dueDateEpochDay)
                    put("paidDateEpochDay", row.paidDateEpochDay)
                    put("notes", row.notes)
                }
            })

            put("creditCards", database.creditCardDao().getAll().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("name", row.name)
                    put("bank", row.bank)
                    put("lastFourDigits", row.lastFourDigits ?: JSONObject.NULL)
                    put("creditLimitPaise", row.creditLimitPaise)
                    put("currentOutstandingPaise", row.currentOutstandingPaise)
                    put("minimumDuePaise", row.minimumDuePaise)
                    put("statementDayOfMonth", row.statementDayOfMonth)
                    put("dueDayOfMonth", row.dueDayOfMonth)
                    put("isActive", row.isActive)
                    put("notes", row.notes)
                    put("lastUpdatedEpochDay", row.lastUpdatedEpochDay)
                }
            })

            put("creditCardPayments", database.creditCardDao().getAllPayments().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("cardId", row.cardId)
                    put("amountPaise", row.amountPaise)
                    put("paidDateEpochDay", row.paidDateEpochDay)
                    put("accountId", row.accountId ?: JSONObject.NULL)
                    put("notes", row.notes)
                }
            })

            put("bills", database.billDao().getAll().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("name", row.name)
                    put("categoryId", row.categoryId ?: JSONObject.NULL)
                    put("amountType", row.amountType)
                    put("amountPaise", row.amountPaise)
                    put("minAmountPaise", row.minAmountPaise ?: JSONObject.NULL)
                    put("maxAmountPaise", row.maxAmountPaise ?: JSONObject.NULL)
                    put("dueDayOfMonth", row.dueDayOfMonth)
                    put("frequency", row.frequency)
                    put("startDateEpochDay", row.startDateEpochDay)
                    put("endDateEpochDay", row.endDateEpochDay ?: JSONObject.NULL)
                    put("paymentMethod", row.paymentMethod)
                    put("autoDebit", row.autoDebit)
                    put("isEssential", row.isEssential)
                    put("isActive", row.isActive)
                    put("notes", row.notes)
                }
            })

            put("billPayments", database.billDao().getAllPayments().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("billId", row.billId)
                    put("periodKey", row.periodKey)
                    put("amountPaise", row.amountPaise)
                    put("dueDateEpochDay", row.dueDateEpochDay)
                    put("paidDateEpochDay", row.paidDateEpochDay)
                    put("notes", row.notes)
                }
            })

            put("annualExpenses", database.annualExpenseDao().getAll().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("name", row.name)
                    put("categoryId", row.categoryId ?: JSONObject.NULL)
                    put("amountPaise", row.amountPaise)
                    put("dueMonth", row.dueMonth)
                    put("dueDayOfMonth", row.dueDayOfMonth)
                    put("vehicleId", row.vehicleId ?: JSONObject.NULL)
                    put("isActive", row.isActive)
                    put("notes", row.notes)
                }
            })

            put(
                "annualExpensePayments",
                database.annualExpenseDao().getAllPayments().toJsonArray { row ->
                    JSONObject().apply {
                        put("id", row.id)
                        put("annualExpenseId", row.annualExpenseId)
                        put("year", row.year)
                        put("amountPaise", row.amountPaise)
                        put("paidDateEpochDay", row.paidDateEpochDay)
                        put("notes", row.notes)
                    }
                }
            )

            put("savingsGoals", database.savingsDao().getAll().toJsonArray { row ->
                JSONObject().apply {
                    put("id", row.id)
                    put("name", row.name)
                    put("targetAmountPaise", row.targetAmountPaise)
                    put("targetDateEpochDay", row.targetDateEpochDay ?: JSONObject.NULL)
                    put("priority", row.priority)
                    put("isEmergencyFund", row.isEmergencyFund)
                    put("isAchieved", row.isAchieved)
                    put("notes", row.notes)
                    put("createdAtEpochDay", row.createdAtEpochDay)
                }
            })

            put(
                "savingsContributions",
                database.savingsDao().getAllContributions().toJsonArray { row ->
                    JSONObject().apply {
                        put("id", row.id)
                        put("goalId", row.goalId)
                        put("amountPaise", row.amountPaise)
                        put("dateEpochDay", row.dateEpochDay)
                        put("accountId", row.accountId ?: JSONObject.NULL)
                        put("notes", row.notes)
                    }
                }
            )
        }

        val file = File(exportsDir(), "money-planner-backup-${stamp(today)}.json")
        file.writeText(root.toString(2))
        file
    }

    /**
     * Replaces everything with the contents of a backup.
     *
     * The whole restore runs in one transaction, so a corrupt or truncated file leaves
     * the existing data untouched rather than half replaced. Ids are preserved so that
     * the relationships between records survive intact.
     */
    /**
     * Reads a backup and reports what is in it, without changing anything.
     *
     * Restoring replaces every record in the app, and there is no undo. Doing that to a
     * file the user has only identified by its name is asking them to trust a filename
     * with their entire financial history. This lets them see what they are about to swap
     * in — when it was taken, and how much is in it — while the current records are still
     * there to compare against.
     */
    suspend fun previewJson(uri: Uri): BackupPreview = withContext(io) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext BackupPreview.Unreadable("The file could not be opened.")
        } catch (error: Exception) {
            return@withContext BackupPreview.Unreadable("The file could not be read.")
        }

        val root = try {
            JSONObject(text)
        } catch (error: Exception) {
            return@withContext BackupPreview.Unreadable(
                "This does not look like a Money Planner backup."
            )
        }

        val version = root.optInt("formatVersion", 0)
        if (version <= 0) {
            return@withContext BackupPreview.Unreadable(
                "This does not look like a Money Planner backup."
            )
        }
        if (version > FORMAT_VERSION) {
            return@withContext BackupPreview.Unreadable(
                "This backup was made by a newer version of the app."
            )
        }

        fun count(key: String) = root.optJSONArray(key)?.length() ?: 0

        BackupPreview.Readable(
            exportedOn = root.optStringOrNull("exportedOn")
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            formatVersion = version,
            expenses = count("expenses"),
            incomeTransactions = count("incomeTransactions"),
            people = count("people"),
            emis = count("emis"),
            bills = count("recurringBills"),
            goals = count("savingsGoals"),
            accounts = count("accounts"),
            // Everything countable, so a file that parses but holds nothing can be
            // recognised as empty before it replaces records that are not.
            totalRecords = listOf(
                "expenses", "incomeTransactions", "incomeSources", "people",
                "personLedgerEntries", "settlements", "sharedExpenses", "emis",
                "emiPayments", "creditCards", "creditCardPayments", "recurringBills",
                "billPayments", "annualExpenses", "annualExpensePayments",
                "savingsGoals", "savingsContributions", "accounts", "accountTransfers",
                "adjustments", "budgets"
            ).sumOf { count(it) }
        )
    }

    suspend fun importJson(uri: Uri): RestoreResult = withContext(io) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext RestoreResult.Failure("The file could not be opened.")
        } catch (error: Exception) {
            return@withContext RestoreResult.Failure("The file could not be read.")
        }

        val root = try {
            JSONObject(text)
        } catch (error: Exception) {
            return@withContext RestoreResult.Failure("This does not look like a Money Planner backup.")
        }

        val version = root.optInt("formatVersion", 0)
        if (version <= 0 || version > FORMAT_VERSION) {
            return@withContext RestoreResult.Failure(
                "This backup was made by a newer version of the app."
            )
        }

        try {
            database.withTransaction {
                database.maintenanceDao().clearEverything()

                root.optJSONObject("profile")?.takeIf { it.length() > 0 }?.let { profile ->
                    database.profileDao().upsertProfile(
                        UserProfileEntity(
                            displayName = profile.optString("displayName"),
                            emergencyFundMonths = profile.optInt("emergencyFundMonths", 6),
                            monthStartDay = profile.optInt("monthStartDay", 1),
                            onboardingCompleted = profile.optBoolean("onboardingCompleted"),
                            createdAtEpochDay = profile.optLong("createdAtEpochDay")
                        )
                    )
                }

                root.forEachObject("accounts") { row ->
                    database.profileDao().insertAccount(
                        AccountEntity(
                            id = row.getLong("id"),
                            name = row.getString("name"),
                            type = row.getString("type"),
                            openingBalancePaise = row.getLong("openingBalancePaise"),
                            openingDateEpochDay = row.getLong("openingDateEpochDay"),
                            isArchived = row.optBoolean("isArchived"),
                            sortOrder = row.optInt("sortOrder")
                        )
                    )
                }

                root.forEachObject("adjustments") { row ->
                    database.profileDao().insertAdjustment(
                        BalanceAdjustmentEntity(
                            id = row.getLong("id"),
                            accountId = row.optLongOrNull("accountId"),
                            deltaPaise = row.getLong("deltaPaise"),
                            dateEpochDay = row.getLong("dateEpochDay"),
                            reason = row.optString("reason"),
                            createdAtEpochDay = row.optLong("createdAtEpochDay")
                        )
                    )
                }

                // After accounts, because a transfer points at two of them.
                root.forEachObject("accountTransfers") { row ->
                    database.profileDao().insertTransfer(
                        AccountTransferEntity(
                            id = row.getLong("id"),
                            fromAccountId = row.getLong("fromAccountId"),
                            toAccountId = row.getLong("toAccountId"),
                            amountPaise = row.getLong("amountPaise"),
                            dateEpochDay = row.getLong("dateEpochDay"),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("categories") { row ->
                    database.categoryDao().insert(
                        CategoryEntity(
                            id = row.getLong("id"),
                            name = row.getString("name"),
                            type = row.getString("type"),
                            iconKey = row.optString("iconKey", "other"),
                            colorHex = row.optString("colorHex", "#FF6E7A8A"),
                            isEssential = row.optBoolean("isEssential"),
                            isCustom = row.optBoolean("isCustom"),
                            isArchived = row.optBoolean("isArchived"),
                            sortOrder = row.optInt("sortOrder")
                        )
                    )
                }

                root.forEachObject("people") { row ->
                    database.peopleDao().insert(
                        PersonEntity(
                            id = row.getLong("id"),
                            name = row.getString("name"),
                            relation = row.getString("relation"),
                            phone = row.optStringOrNull("phone"),
                            notes = row.optString("notes"),
                            isArchived = row.optBoolean("isArchived"),
                            createdAtEpochDay = row.optLong("createdAtEpochDay")
                        )
                    )
                }

                root.forEachObject("familyMembers") { row ->
                    database.familyDao().insert(
                        FamilyMemberEntity(
                            id = row.getLong("id"),
                            name = row.getString("name"),
                            relation = row.getString("relation"),
                            isSelf = row.optBoolean("isSelf"),
                            isArchived = row.optBoolean("isArchived")
                        )
                    )
                }

                root.forEachObject("vehicles") { row ->
                    database.vehicleDao().insert(
                        VehicleEntity(
                            id = row.getLong("id"),
                            name = row.getString("name"),
                            type = row.getString("type"),
                            registrationNumber = row.optStringOrNull("registrationNumber"),
                            purchaseDateEpochDay = row.optLongOrNull("purchaseDateEpochDay"),
                            notes = row.optString("notes"),
                            isArchived = row.optBoolean("isArchived")
                        )
                    )
                }

                root.forEachObject("incomeSources") { row ->
                    database.incomeDao().insertSource(
                        IncomeSourceEntity(
                            id = row.getLong("id"),
                            name = row.getString("name"),
                            type = row.getString("type"),
                            amountPaise = row.getLong("amountPaise"),
                            dayOfMonth = row.getInt("dayOfMonth"),
                            frequency = row.getString("frequency"),
                            startDateEpochDay = row.getLong("startDateEpochDay"),
                            endDateEpochDay = row.optLongOrNull("endDateEpochDay"),
                            annualIncrementPercent = row.optDouble("annualIncrementPercent", 0.0),
                            accountId = row.optLongOrNull("accountId"),
                            isActive = row.optBoolean("isActive", true),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("incomeTransactions") { row ->
                    database.incomeDao().insertTransaction(
                        IncomeTransactionEntity(
                            id = row.getLong("id"),
                            sourceId = row.optLongOrNull("sourceId"),
                            name = row.getString("name"),
                            type = row.getString("type"),
                            amountPaise = row.getLong("amountPaise"),
                            dateEpochDay = row.getLong("dateEpochDay"),
                            accountId = row.optLongOrNull("accountId"),
                            periodKey = row.optStringOrNull("periodKey"),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("expenses") { row ->
                    database.expenseDao().insert(
                        ExpenseEntity(
                            id = row.getLong("id"),
                            amountPaise = row.getLong("amountPaise"),
                            description = row.optString("description"),
                            categoryId = row.getLong("categoryId"),
                            dateEpochDay = row.getLong("dateEpochDay"),
                            paymentMethod = row.getString("paymentMethod"),
                            accountId = row.optLongOrNull("accountId"),
                            creditCardId = row.optLongOrNull("creditCardId"),
                            personId = row.optLongOrNull("personId"),
                            vehicleId = row.optLongOrNull("vehicleId"),
                            familyMemberId = row.optLongOrNull("familyMemberId"),
                            linkType = row.optString("linkType", "NONE"),
                            linkId = row.optLongOrNull("linkId"),
                            // Absent in backups written before version 2 of the format;
                            // those restore with a null key and fall back to date matching
                            // when a payment is undone, exactly as migrated rows do.
                            linkPeriodKey = row.optStringOrNull("linkPeriodKey"),
                            notes = row.optString("notes"),
                            createdAtEpochDay = row.optLong("createdAtEpochDay")
                        )
                    )
                }

                root.forEachObject("tags") { row ->
                    database.expenseMetadataDao().insertTag(TagEntity(id = row.getLong("id"), name = row.getString("name")))
                }
                root.forEachObject("expenseTags") { row ->
                    database.expenseMetadataDao().insertExpenseTag(
                        ExpenseTagEntity(expenseId = row.getLong("expenseId"), tagId = row.getLong("tagId"))
                    )
                }
                root.forEachObject("expenseAttachments") { row ->
                    database.expenseMetadataDao().insertAttachment(
                        ExpenseAttachmentEntity(
                            id = row.getLong("id"), expenseId = row.getLong("expenseId"), uri = row.getString("uri"),
                            displayName = row.getString("displayName"), mimeType = row.getString("mimeType"),
                            addedAtEpochDay = row.getLong("addedAtEpochDay")
                        )
                    )
                }

                root.forEachObject("ledgerEntries") { row ->
                    database.peopleDao().insertEntry(
                        PersonLedgerEntryEntity(
                            id = row.getLong("id"),
                            personId = row.getLong("personId"),
                            amountPaise = row.getLong("amountPaise"),
                            direction = row.getString("direction"),
                            dateEpochDay = row.getLong("dateEpochDay"),
                            expectedDateEpochDay = row.optLongOrNull("expectedDateEpochDay"),
                            description = row.optString("description"),
                            sourceType = row.optString("sourceType", "MANUAL"),
                            sourceId = row.optLongOrNull("sourceId"),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("settlements") { row ->
                    database.peopleDao().insertSettlement(
                        SettlementEntity(
                            id = row.getLong("id"),
                            personId = row.getLong("personId"),
                            amountPaise = row.getLong("amountPaise"),
                            direction = row.getString("direction"),
                            dateEpochDay = row.getLong("dateEpochDay"),
                            paymentMethod = row.optString("paymentMethod", "CASH"),
                            accountId = row.optLongOrNull("accountId"),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("sharedExpenses") { row ->
                    database.peopleDao().insertSharedExpense(
                        SharedExpenseEntity(
                            id = row.getLong("id"),
                            description = row.optString("description"),
                            totalAmountPaise = row.getLong("totalAmountPaise"),
                            dateEpochDay = row.getLong("dateEpochDay"),
                            categoryId = row.optLongOrNull("categoryId"),
                            splitType = row.getString("splitType"),
                            paidByPersonId = row.optLongOrNull("paidByPersonId"),
                            notes = row.optString("notes")
                        )
                    )
                }

                val shares = mutableListOf<SharedExpenseShareEntity>()
                root.forEachObject("sharedExpenseShares") { row ->
                    shares += SharedExpenseShareEntity(
                        id = row.getLong("id"),
                        sharedExpenseId = row.getLong("sharedExpenseId"),
                        personId = row.optLongOrNull("personId"),
                        shareAmountPaise = row.getLong("shareAmountPaise"),
                        sharePercent = row.optDoubleOrNull("sharePercent")
                    )
                }
                if (shares.isNotEmpty()) database.peopleDao().insertShares(shares)

                root.forEachObject("emis") { row ->
                    database.emiDao().insert(
                        EmiEntity(
                            id = row.getLong("id"),
                            name = row.getString("name"),
                            categoryId = row.optLongOrNull("categoryId"),
                            principalPaise = row.optLong("principalPaise"),
                            emiAmountPaise = row.getLong("emiAmountPaise"),
                            interestRatePercent = row.optDoubleOrNull("interestRatePercent"),
                            startDateEpochDay = row.getLong("startDateEpochDay"),
                            firstDueDateEpochDay = row.getLong("firstDueDateEpochDay"),
                            frequency = row.getString("frequency"),
                            totalInstallments = row.getInt("totalInstallments"),
                            openingPaidInstallments = row.optInt("openingPaidInstallments"),
                            vehicleId = row.optLongOrNull("vehicleId"),
                            accountReference = row.optString("accountReference"),
                            autoDebit = row.optBoolean("autoDebit"),
                            isActive = row.optBoolean("isActive", true),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("emiPayments") { row ->
                    database.emiDao().insertPayment(
                        EmiPaymentEntity(
                            id = row.getLong("id"),
                            emiId = row.getLong("emiId"),
                            installmentNumber = row.getInt("installmentNumber"),
                            amountPaise = row.getLong("amountPaise"),
                            dueDateEpochDay = row.getLong("dueDateEpochDay"),
                            paidDateEpochDay = row.getLong("paidDateEpochDay"),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("creditCards") { row ->
                    database.creditCardDao().insert(
                        CreditCardEntity(
                            id = row.getLong("id"),
                            name = row.getString("name"),
                            bank = row.optString("bank"),
                            lastFourDigits = row.optStringOrNull("lastFourDigits"),
                            creditLimitPaise = row.optLong("creditLimitPaise"),
                            currentOutstandingPaise = row.optLong("currentOutstandingPaise"),
                            minimumDuePaise = row.optLong("minimumDuePaise"),
                            statementDayOfMonth = row.optInt("statementDayOfMonth", 1),
                            dueDayOfMonth = row.optInt("dueDayOfMonth", 15),
                            isActive = row.optBoolean("isActive", true),
                            notes = row.optString("notes"),
                            lastUpdatedEpochDay = row.optLong("lastUpdatedEpochDay")
                        )
                    )
                }

                root.forEachObject("creditCardPayments") { row ->
                    database.creditCardDao().insertPayment(
                        CreditCardPaymentEntity(
                            id = row.getLong("id"),
                            cardId = row.getLong("cardId"),
                            amountPaise = row.getLong("amountPaise"),
                            paidDateEpochDay = row.getLong("paidDateEpochDay"),
                            accountId = row.optLongOrNull("accountId"),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("bills") { row ->
                    database.billDao().insert(
                        RecurringBillEntity(
                            id = row.getLong("id"),
                            name = row.getString("name"),
                            categoryId = row.optLongOrNull("categoryId"),
                            amountType = row.optString("amountType", "FIXED"),
                            amountPaise = row.getLong("amountPaise"),
                            minAmountPaise = row.optLongOrNull("minAmountPaise"),
                            maxAmountPaise = row.optLongOrNull("maxAmountPaise"),
                            dueDayOfMonth = row.getInt("dueDayOfMonth"),
                            frequency = row.getString("frequency"),
                            startDateEpochDay = row.getLong("startDateEpochDay"),
                            endDateEpochDay = row.optLongOrNull("endDateEpochDay"),
                            paymentMethod = row.optString("paymentMethod", "UPI"),
                            autoDebit = row.optBoolean("autoDebit"),
                            isEssential = row.optBoolean("isEssential", true),
                            isActive = row.optBoolean("isActive", true),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("billPayments") { row ->
                    database.billDao().insertPayment(
                        BillPaymentEntity(
                            id = row.getLong("id"),
                            billId = row.getLong("billId"),
                            periodKey = row.getString("periodKey"),
                            amountPaise = row.getLong("amountPaise"),
                            dueDateEpochDay = row.getLong("dueDateEpochDay"),
                            paidDateEpochDay = row.getLong("paidDateEpochDay"),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("annualExpenses") { row ->
                    database.annualExpenseDao().insert(
                        AnnualExpenseEntity(
                            id = row.getLong("id"),
                            name = row.getString("name"),
                            categoryId = row.optLongOrNull("categoryId"),
                            amountPaise = row.getLong("amountPaise"),
                            dueMonth = row.getInt("dueMonth"),
                            dueDayOfMonth = row.getInt("dueDayOfMonth"),
                            vehicleId = row.optLongOrNull("vehicleId"),
                            isActive = row.optBoolean("isActive", true),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("annualExpensePayments") { row ->
                    database.annualExpenseDao().insertPayment(
                        AnnualExpensePaymentEntity(
                            id = row.getLong("id"),
                            annualExpenseId = row.getLong("annualExpenseId"),
                            year = row.getInt("year"),
                            amountPaise = row.getLong("amountPaise"),
                            paidDateEpochDay = row.getLong("paidDateEpochDay"),
                            notes = row.optString("notes")
                        )
                    )
                }

                root.forEachObject("savingsGoals") { row ->
                    database.savingsDao().insert(
                        SavingsGoalEntity(
                            id = row.getLong("id"),
                            name = row.getString("name"),
                            targetAmountPaise = row.getLong("targetAmountPaise"),
                            targetDateEpochDay = row.optLongOrNull("targetDateEpochDay"),
                            priority = row.optString("priority", "MEDIUM"),
                            isEmergencyFund = row.optBoolean("isEmergencyFund"),
                            isAchieved = row.optBoolean("isAchieved"),
                            notes = row.optString("notes"),
                            createdAtEpochDay = row.optLong("createdAtEpochDay")
                        )
                    )
                }

                root.forEachObject("savingsContributions") { row ->
                    database.savingsDao().insertContribution(
                        SavingsContributionEntity(
                            id = row.getLong("id"),
                            goalId = row.getLong("goalId"),
                            amountPaise = row.getLong("amountPaise"),
                            dateEpochDay = row.getLong("dateEpochDay"),
                            accountId = row.optLongOrNull("accountId"),
                            notes = row.optString("notes")
                        )
                    )
                }
            }
            RestoreResult.Success
        } catch (error: Exception) {
            RestoreResult.Failure("The backup could not be restored. Your existing data is unchanged.")
        }
    }

    // ---- CSV export --------------------------------------------------------------

    /**
     * Expenses as CSV, which opens directly in Excel and Google Sheets.
     * Fields are quoted and embedded quotes are doubled, per the CSV convention.
     */
    suspend fun exportExpensesCsv(today: LocalDate): File = withContext(io) {
        val categories = database.categoryDao().getAll().associateBy { it.id }
        val people = database.peopleDao().getAll().associateBy { it.id }
        val expenses = database.expenseDao().getAll().sortedBy { it.dateEpochDay }

        val builder = StringBuilder()
        builder.appendLine(
            listOf(
                "Date", "Description", "Category", "Amount", "Payment method",
                "Person", "Linked to", "Notes"
            ).joinToString(",") { csvField(it) }
        )

        expenses.forEach { expense ->
            val date = LocalDate.ofEpochDay(expense.dateEpochDay)
            builder.appendLine(
                listOf(
                    date.toString(),
                    expense.description,
                    categories[expense.categoryId]?.name ?: "",
                    com.moneyplanner.core.money.IndianFormat.formatForExport(
                        com.moneyplanner.core.money.Money(expense.amountPaise)
                    ),
                    expense.paymentMethod,
                    expense.personId?.let { people[it]?.name } ?: "",
                    if (expense.linkType == "NONE") "" else expense.linkType,
                    expense.notes
                ).joinToString(",") { csvField(it) }
            )
        }

        val file = File(exportsDir(), "money-planner-expenses-${stamp(today)}.csv")
        file.writeText(builder.toString())
        file
    }

    /**
     * Quotes a field, doubling any embedded quotes.
     *
     * A leading =, +, - or @ is prefixed with an apostrophe first. Spreadsheets treat a
     * cell starting with one of those as a formula, so a description someone typed as
     * "=2+2" would be evaluated on open, and the sheet would show something the user
     * never wrote. The apostrophe is the standard way to force it back to plain text.
     */
    private fun csvField(value: String): String {
        val neutralised =
            if (value.isNotEmpty() && value[0] in FORMULA_TRIGGERS) "'" + value else value
        return "\"" + neutralised.replace("\"", "\"\"") + "\""
    }

    fun shareableUri(file: File): Uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

/**
 * What a backup file turns out to contain, read before anything is replaced.
 */
sealed interface BackupPreview {
    data class Readable(
        val exportedOn: LocalDate?,
        val formatVersion: Int,
        val expenses: Int,
        val incomeTransactions: Int,
        val people: Int,
        val emis: Int,
        val bills: Int,
        val goals: Int,
        val accounts: Int,
        val totalRecords: Int
    ) : BackupPreview {
        val isEmpty: Boolean get() = totalRecords == 0
    }

    data class Unreadable(val message: String) : BackupPreview
}

sealed interface RestoreResult {
    data object Success : RestoreResult
    data class Failure(val message: String) : RestoreResult
}

// ---- JSON helpers ----------------------------------------------------------------

private inline fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray {
    val array = JSONArray()
    forEach { array.put(transform(it)) }
    return array
}

private inline fun JSONObject.forEachObject(key: String, action: (JSONObject) -> Unit) {
    val array = optJSONArray(key) ?: return
    for (index in 0 until array.length()) {
        array.optJSONObject(index)?.let(action)
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key)

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
