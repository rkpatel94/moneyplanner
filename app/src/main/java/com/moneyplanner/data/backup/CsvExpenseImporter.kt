package com.moneyplanner.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.moneyplanner.core.money.Money
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.db.entity.CategoryEntity
import com.moneyplanner.data.db.entity.ExpenseEntity
import com.moneyplanner.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import com.moneyplanner.domain.model.PaymentMethod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExpenseImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun import(uri: Uri): CsvImportResult = withContext(io) {
        runCatching {
            val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
                ?: return@runCatching CsvImportResult.Failure("The selected file could not be read.")
            if (lines.size < 2) return@runCatching CsvImportResult.Failure("The CSV has no expense rows.")
            val header = parseLine(lines.first()).map { it.trim().lowercase() }
            fun column(name: String) = header.indexOf(name)
            val dateIndex = column("date"); val descriptionIndex = column("description")
            val categoryIndex = column("category"); val amountIndex = column("amount")
            if (listOf(dateIndex, descriptionIndex, categoryIndex, amountIndex).any { it < 0 }) {
                return@runCatching CsvImportResult.Failure("Use a CSV with Date, Description, Category and Amount columns.")
            }
            val paymentIndex = column("payment method"); val notesIndex = column("notes")
            val categories = database.categoryDao().getAll().toMutableList()
            var imported = 0; var skipped = 0
            database.withTransaction {
                lines.drop(1).forEach { line ->
                    val values = parseLine(line)
                    val date = values.getOrNull(dateIndex)?.let(LocalDate::parse)
                    val amount = values.getOrNull(amountIndex)?.let(Money::parseOrNull)
                    val categoryName = values.getOrNull(categoryIndex)?.trim().orEmpty()
                    if (date == null || amount == null || !amount.isPositive || categoryName.isBlank()) { skipped++; return@forEach }
                    var category = categories.firstOrNull { it.name.equals(categoryName, true) && it.type == "EXPENSE" }
                    if (category == null) {
                        val id = database.categoryDao().insert(CategoryEntity(name = categoryName.take(40), type = "EXPENSE", isCustom = true))
                        category = if (id > 0) CategoryEntity(id = id, name = categoryName.take(40), type = "EXPENSE", isCustom = true)
                        else database.categoryDao().findByName(categoryName.take(40), "EXPENSE")
                        category?.let(categories::add)
                    }
                    val paymentText = values.getOrNull(paymentIndex).orEmpty()
                    val payment = PaymentMethod.entries.firstOrNull { it.name.equals(paymentText, true) || it.label.equals(paymentText, true) } ?: PaymentMethod.OTHER
                    database.expenseDao().insert(ExpenseEntity(
                        amountPaise = amount.paise, description = values.getOrNull(descriptionIndex).orEmpty(),
                        categoryId = category!!.id, dateEpochDay = date.toEpochDay(), paymentMethod = payment.name,
                        notes = values.getOrNull(notesIndex).orEmpty(), createdAtEpochDay = LocalDate.now().toEpochDay()
                    ))
                    imported++
                }
            }
            CsvImportResult.Success(imported, skipped)
        }.getOrElse { CsvImportResult.Failure("The CSV could not be imported. Check its dates and amounts.") }
    }

    /** RFC 4180-style fields, including doubled quote characters. */
    private fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> { cell.append(char); index++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { result += cell.toString(); cell.clear() }
                else -> cell.append(char)
            }
            index++
        }
        result += cell.toString(); return result
    }
}

sealed interface CsvImportResult {
    data class Success(val imported: Int, val skipped: Int) : CsvImportResult
    data class Failure(val message: String) : CsvImportResult
}
