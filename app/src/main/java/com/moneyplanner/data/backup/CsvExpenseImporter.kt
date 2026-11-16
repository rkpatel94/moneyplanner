package com.moneyplanner.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.db.entity.CategoryEntity
import com.moneyplanner.data.db.entity.ExpenseEntity
import com.moneyplanner.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds expenses from a CSV file, such as one exported from this app or a spreadsheet.
 *
 * The file is read and checked in full before anything is written, through [ExpenseCsv],
 * which is also what the export writes with. Rows that cannot be read are skipped and
 * counted rather than abandoning the file, and the rows that can are saved in one
 * transaction, so an import either adds all of them or, if the database fails part-way,
 * none.
 */
@Singleton
class CsvExpenseImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun import(uri: Uri): CsvImportResult = withContext(io) {
        val text = readText(uri)
            ?: return@withContext CsvImportResult.Failure("The selected file could not be read.")

        val parsed = when (val read = ExpenseCsv.read(text)) {
            is ExpenseCsv.ReadResult.Unusable -> return@withContext CsvImportResult.Failure(read.message)
            is ExpenseCsv.ReadResult.Rows -> read
        }

        try {
            var imported = 0
            var skipped = parsed.skipped
            database.withTransaction {
                val categories = database.categoryDao().getAll().toMutableList()
                val createdOn = LocalDate.now().toEpochDay()
                parsed.rows.forEach { row ->
                    val category = findOrCreateCategory(row.category, categories)
                    if (category == null) {
                        skipped++
                        return@forEach
                    }
                    database.expenseDao().insert(
                        ExpenseEntity(
                            amountPaise = row.amount.paise,
                            description = row.description,
                            categoryId = category.id,
                            dateEpochDay = row.date.toEpochDay(),
                            paymentMethod = row.paymentMethod.name,
                            notes = row.notes,
                            createdAtEpochDay = createdOn
                        )
                    )
                    imported++
                }
            }
            CsvImportResult.Success(imported, skipped, parsed.endedInsideQuotes)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            CsvImportResult.Failure("The expenses could not be saved, so none were imported.")
        }
    }

    /** The whole file as UTF-8, which is what Excel's "CSV UTF-8" and this app's export are. */
    private fun readText(uri: Uri): String? =
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (error: Exception) {
            null
        }

    /**
     * The expense category with this name, matched regardless of case, created as a custom
     * category the first time a file mentions it. Null only if it can be neither found nor
     * made, and the row is then skipped rather than saved against the wrong category.
     */
    private suspend fun findOrCreateCategory(
        name: String,
        known: MutableList<CategoryEntity>
    ): CategoryEntity? {
        known.firstOrNull { it.name.equals(name, ignoreCase = true) && it.type == "EXPENSE" }
            ?.let { return it }

        val stored = name.take(40)
        val id = database.categoryDao().insert(
            CategoryEntity(name = stored, type = "EXPENSE", isCustom = true)
        )
        val created = if (id > 0) {
            CategoryEntity(id = id, name = stored, type = "EXPENSE", isCustom = true)
        } else {
            database.categoryDao().findByName(stored, "EXPENSE")
        }
        created?.let(known::add)
        return created
    }
}

sealed interface CsvImportResult {
    data class Success(
        val imported: Int,
        val skipped: Int,
        /** The file had a quote that was never closed, so its last rows may not have been read. */
        val endedInsideQuotes: Boolean = false
    ) : CsvImportResult

    data class Failure(val message: String) : CsvImportResult

    /**
     * What to tell the user. The counts are the point: a skipped row is otherwise invisible,
     * and someone importing two hundred expenses deserves to know that three did not arrive.
     */
    fun message(): String = when (this) {
        is Failure -> message
        is Success -> buildString {
            when {
                imported == 0 && skipped == 0 -> append("No expenses were found in the file.")
                imported == 0 -> append(
                    "No expenses were imported: ${rows(skipped)} could not be read. Each needs " +
                        "a date written as YYYY-MM-DD, a category and an amount above zero."
                )
                skipped == 0 -> append("Imported ${expenses(imported)}.")
                else -> append(
                    "Imported ${expenses(imported)}. Skipped ${rows(skipped)} that could not be read."
                )
            }
            if (endedInsideQuotes) {
                append(" A quotation mark in the file is never closed, so its last rows may be missing.")
            }
        }
    }

    private fun expenses(count: Int) = if (count == 1) "1 expense" else "$count expenses"

    private fun rows(count: Int) = if (count == 1) "1 row" else "$count rows"
}
