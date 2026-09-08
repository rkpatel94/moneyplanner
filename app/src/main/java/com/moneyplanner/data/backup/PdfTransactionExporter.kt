package com.moneyplanner.data.backup

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.data.db.dao.CategoryDao
import com.moneyplanner.data.db.dao.ExpenseDao
import com.moneyplanner.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A compact, shareable expense statement created entirely on the device. */
@Singleton
class PdfTransactionExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenses: ExpenseDao,
    private val categories: CategoryDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun export(from: LocalDate, to: LocalDate): ExportResult = withContext(io) {
        val rows = expenses.getBetween(from.toEpochDay(), to.toEpochDay()).sortedByDescending { it.dateEpochDay }
        if (rows.isEmpty()) return@withContext ExportResult.Empty
        runCatching {
            val names = categories.getAll().associate { it.id to it.name }
            val document = PdfDocument()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            var page: PdfDocument.Page? = null
            var canvas: android.graphics.Canvas? = null
            var y = 0f
            var pageNumber = 0
            fun startPage() {
                page?.let(document::finishPage)
                page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
                canvas = page!!.canvas
                y = 48f
                paint.color = android.graphics.Color.rgb(0, 6, 102); paint.textSize = 19f; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                canvas!!.drawText("Personal Money Planner", 42f, y, paint)
                y += 22f
                paint.color = android.graphics.Color.DKGRAY; paint.textSize = 10f; paint.typeface = android.graphics.Typeface.DEFAULT
                canvas!!.drawText("Expenses from $from to $to", 42f, y, paint)
                y += 26f
                paint.color = android.graphics.Color.rgb(232, 234, 246); canvas!!.drawRect(42f, y, 553f, y + 24f, paint)
                paint.color = android.graphics.Color.rgb(0, 6, 102); paint.typeface = android.graphics.Typeface.DEFAULT_BOLD; paint.textSize = 9f
                listOf("DATE" to 48f, "DESCRIPTION" to 120f, "CATEGORY" to 335f, "AMOUNT" to 480f).forEach { (text, x) -> canvas!!.drawText(text, x, y + 16f, paint) }
                y += 40f
            }
            startPage()
            rows.forEach { expense ->
                if (y > 778f) startPage()
                paint.color = android.graphics.Color.DKGRAY; paint.typeface = android.graphics.Typeface.DEFAULT; paint.textSize = 9f
                canvas!!.drawText(LocalDate.ofEpochDay(expense.dateEpochDay).toString(), 48f, y, paint)
                canvas!!.drawText(expense.description.ifBlank { "Expense" }.take(34), 120f, y, paint)
                canvas!!.drawText((names[expense.categoryId] ?: "Uncategorised").take(20), 335f, y, paint)
                paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                canvas!!.drawText(IndianFormat.format(Money(expense.amountPaise)), 480f, y, paint)
                y += 18f
            }
            if (y > 760f) startPage()
            paint.color = android.graphics.Color.rgb(0, 6, 102); paint.typeface = android.graphics.Typeface.DEFAULT_BOLD; paint.textSize = 12f
            canvas!!.drawText("Total: ${IndianFormat.format(Money(rows.sumOf { it.amountPaise }))}", 390f, y + 20f, paint)
            page?.let(document::finishPage)
            val file = File(File(context.cacheDir, "exports").also { it.mkdirs() }, "money-planner-expenses-${from}-to-${to}.pdf")
            file.outputStream().use(document::writeTo)
            document.close()
            ExportResult.Success(file)
        }.getOrElse { ExportResult.Failure("The PDF could not be created.") }
    }
}
