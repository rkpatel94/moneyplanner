package com.moneyplanner.data.backup

import android.content.Context
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.mapper.toDomain
import com.moneyplanner.domain.model.SettlementDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every transaction in a date range, as an Excel workbook.
 *
 * One sheet per kind rather than one flat list. The kinds do not share columns — an
 * expense has a category and a payment method, a transfer has two accounts and neither —
 * and forcing them into one table would leave most cells empty and the whole thing awkward
 * to filter. A sheet each keeps every column meaningful.
 *
 * Amounts are written as numbers and dates as dates, which is the entire reason for
 * preferring a workbook to a CSV: a column of amounts can be summed, and a column of dates
 * can be sorted and filtered. The same figures as text cannot.
 *
 * A sheet with no rows in the range is left out rather than written empty, so the workbook
 * shows what actually happened instead of a row of tabs to click through and find nothing.
 */
@Singleton
class TransactionExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    @com.moneyplanner.di.IoDispatcher private val io: CoroutineDispatcher
) {

    private companion object {
        const val EXPORT_DIR = "exports"
        val MONEY = 14
        val WIDE = 26
    }

    suspend fun export(from: LocalDate, to: LocalDate): ExportResult = withContext(io) {
        if (to.isBefore(from)) {
            return@withContext ExportResult.Failure("The end date is before the start date.")
        }

        val start = from.toEpochDay()
        val end = to.toEpochDay()
        fun inRange(day: Long) = day in start..end

        val workbook = XlsxWriter()

        val categories = database.categoryDao().getAll().associateBy { it.id }
        val people = database.peopleDao().getAll().associateBy { it.id }
        val accounts = database.profileDao().getAllAccounts().associateBy { it.id }
        val cards = database.creditCardDao().getAll().associateBy { it.id }
        val goals = database.savingsDao().getAll().associateBy { it.id }

        // ---- Expenses -------------------------------------------------------------
        database.expenseDao().getAll()
            .filter { inRange(it.dateEpochDay) }
            .sortedBy { it.dateEpochDay }
            .takeIf { it.isNotEmpty() }
            ?.let { rows ->
                workbook.sheet(
                    name = "Expenses",
                    columns = listOf(
                        XlsxWriter.Column("Date"),
                        XlsxWriter.Column("Description", WIDE),
                        XlsxWriter.Column("Category"),
                        XlsxWriter.Column("Amount", MONEY),
                        XlsxWriter.Column("Paid by"),
                        XlsxWriter.Column("Account"),
                        XlsxWriter.Column("Card"),
                        XlsxWriter.Column("Person"),
                        XlsxWriter.Column("Linked to"),
                        XlsxWriter.Column("Notes", WIDE)
                    ),
                    rows = rows.map { expense ->
                        listOf(
                            XlsxWriter.Cell.Date(LocalDate.ofEpochDay(expense.dateEpochDay)),
                            XlsxWriter.Cell.Text(expense.description),
                            XlsxWriter.Cell.Text(categories[expense.categoryId]?.name.orEmpty()),
                            XlsxWriter.Cell.Amount(expense.amountPaise),
                            XlsxWriter.Cell.Text(expense.paymentMethod),
                            XlsxWriter.Cell.Text(
                                expense.accountId?.let { accounts[it]?.name }.orEmpty()
                            ),
                            XlsxWriter.Cell.Text(
                                expense.creditCardId?.let { cards[it]?.name }.orEmpty()
                            ),
                            XlsxWriter.Cell.Text(
                                expense.personId?.let { people[it]?.name }.orEmpty()
                            ),
                            XlsxWriter.Cell.Text(
                                if (expense.linkType == "NONE") "" else expense.linkType
                            ),
                            XlsxWriter.Cell.Text(expense.notes)
                        )
                    }
                )
            }

        // ---- Income ---------------------------------------------------------------
        database.incomeDao().getAllTransactions()
            .filter { inRange(it.dateEpochDay) }
            .sortedBy { it.dateEpochDay }
            .takeIf { it.isNotEmpty() }
            ?.let { rows ->
                workbook.sheet(
                    name = "Income",
                    columns = listOf(
                        XlsxWriter.Column("Date"),
                        XlsxWriter.Column("Received for", WIDE),
                        XlsxWriter.Column("Type"),
                        XlsxWriter.Column("Amount", MONEY),
                        XlsxWriter.Column("Account"),
                        XlsxWriter.Column("For month"),
                        XlsxWriter.Column("Notes", WIDE)
                    ),
                    rows = rows.map { receipt ->
                        listOf(
                            XlsxWriter.Cell.Date(LocalDate.ofEpochDay(receipt.dateEpochDay)),
                            XlsxWriter.Cell.Text(receipt.name),
                            XlsxWriter.Cell.Text(receipt.type),
                            XlsxWriter.Cell.Amount(receipt.amountPaise),
                            XlsxWriter.Cell.Text(
                                receipt.accountId?.let { accounts[it]?.name }.orEmpty()
                            ),
                            XlsxWriter.Cell.Text(receipt.periodKey.orEmpty()),
                            XlsxWriter.Cell.Text(receipt.notes)
                        )
                    }
                )
            }

        // ---- People ---------------------------------------------------------------
        database.peopleDao().getAllSettlements()
            .filter { inRange(it.dateEpochDay) }
            .sortedBy { it.dateEpochDay }
            .takeIf { it.isNotEmpty() }
            ?.let { rows ->
                workbook.sheet(
                    name = "Settlements",
                    columns = listOf(
                        XlsxWriter.Column("Date"),
                        XlsxWriter.Column("Person"),
                        XlsxWriter.Column("Direction"),
                        XlsxWriter.Column("Amount", MONEY),
                        XlsxWriter.Column("Method"),
                        XlsxWriter.Column("Account"),
                        XlsxWriter.Column("Notes", WIDE)
                    ),
                    rows = rows.map { settlement ->
                        listOf(
                            XlsxWriter.Cell.Date(LocalDate.ofEpochDay(settlement.dateEpochDay)),
                            XlsxWriter.Cell.Text(people[settlement.personId]?.name.orEmpty()),
                            XlsxWriter.Cell.Text(
                                if (settlement.direction ==
                                    SettlementDirection.RECEIVED_FROM_THEM.name
                                ) "Received" else "Paid"
                            ),
                            XlsxWriter.Cell.Amount(settlement.amountPaise),
                            XlsxWriter.Cell.Text(settlement.paymentMethod),
                            XlsxWriter.Cell.Text(
                                settlement.accountId?.let { accounts[it]?.name }.orEmpty()
                            ),
                            XlsxWriter.Cell.Text(settlement.notes)
                        )
                    }
                )
            }

        // ---- Transfers ------------------------------------------------------------
        database.profileDao().getAllTransfers()
            .filter { inRange(it.dateEpochDay) }
            .sortedBy { it.dateEpochDay }
            .takeIf { it.isNotEmpty() }
            ?.let { rows ->
                workbook.sheet(
                    name = "Transfers",
                    columns = listOf(
                        XlsxWriter.Column("Date"),
                        XlsxWriter.Column("From", WIDE),
                        XlsxWriter.Column("To", WIDE),
                        XlsxWriter.Column("Amount", MONEY),
                        XlsxWriter.Column("Notes", WIDE)
                    ),
                    rows = rows.map { transfer ->
                        listOf(
                            XlsxWriter.Cell.Date(LocalDate.ofEpochDay(transfer.dateEpochDay)),
                            XlsxWriter.Cell.Text(
                                accounts[transfer.fromAccountId]?.name.orEmpty()
                            ),
                            XlsxWriter.Cell.Text(accounts[transfer.toAccountId]?.name.orEmpty()),
                            XlsxWriter.Cell.Amount(transfer.amountPaise),
                            XlsxWriter.Cell.Text(transfer.notes)
                        )
                    }
                )
            }

        // ---- Savings --------------------------------------------------------------
        database.savingsDao().getAllContributions()
            .filter { inRange(it.dateEpochDay) }
            .sortedBy { it.dateEpochDay }
            .takeIf { it.isNotEmpty() }
            ?.let { rows ->
                workbook.sheet(
                    name = "Savings",
                    columns = listOf(
                        XlsxWriter.Column("Date"),
                        XlsxWriter.Column("Goal", WIDE),
                        XlsxWriter.Column("Direction"),
                        XlsxWriter.Column("Amount", MONEY),
                        XlsxWriter.Column("Notes", WIDE)
                    ),
                    rows = rows.map { contribution ->
                        listOf(
                            XlsxWriter.Cell.Date(LocalDate.ofEpochDay(contribution.dateEpochDay)),
                            XlsxWriter.Cell.Text(goals[contribution.goalId]?.name.orEmpty()),
                            XlsxWriter.Cell.Text(
                                if (contribution.amountPaise < 0) "Taken out" else "Added"
                            ),
                            // Written as entered, sign included, so the column sums to the
                            // net change in the goal rather than to the traffic through it.
                            XlsxWriter.Cell.Amount(contribution.amountPaise),
                            XlsxWriter.Cell.Text(contribution.notes)
                        )
                    }
                )
            }

        // ---- Card bills -----------------------------------------------------------
        database.creditCardDao().getAllPayments()
            .filter { inRange(it.paidDateEpochDay) }
            .sortedBy { it.paidDateEpochDay }
            .takeIf { it.isNotEmpty() }
            ?.let { rows ->
                workbook.sheet(
                    name = "Card bills",
                    columns = listOf(
                        XlsxWriter.Column("Date"),
                        XlsxWriter.Column("Card", WIDE),
                        XlsxWriter.Column("Amount", MONEY),
                        XlsxWriter.Column("Account"),
                        XlsxWriter.Column("Notes", WIDE)
                    ),
                    rows = rows.map { payment ->
                        listOf(
                            XlsxWriter.Cell.Date(LocalDate.ofEpochDay(payment.paidDateEpochDay)),
                            XlsxWriter.Cell.Text(cards[payment.cardId]?.name.orEmpty()),
                            XlsxWriter.Cell.Amount(payment.amountPaise),
                            XlsxWriter.Cell.Text(
                                payment.accountId?.let { accounts[it]?.name }.orEmpty()
                            ),
                            XlsxWriter.Cell.Text(payment.notes)
                        )
                    }
                )
            }

        if (workbook.isEmpty) {
            return@withContext ExportResult.Empty
        }

        val directory = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val file = File(directory, "money-planner-$from-to-$to.xlsx")
        runCatching { workbook.writeTo(file) }
            .fold(
                onSuccess = { ExportResult.Success(file) },
                onFailure = { ExportResult.Failure("The workbook could not be written.") }
            )
    }
}

sealed interface ExportResult {
    data class Success(val file: File) : ExportResult
    /** Nothing happened in the range. Reported rather than handing over an empty file. */
    data object Empty : ExportResult
    data class Failure(val message: String) : ExportResult
}
