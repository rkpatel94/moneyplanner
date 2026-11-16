package com.moneyplanner.data.backup

import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.model.PaymentMethod
import java.time.LocalDate

/**
 * Comma-separated values as RFC 4180 defines them.
 *
 * A field may be wrapped in double quotes, and inside the quotes a comma, a line break or a
 * doubled quote ("") is part of the field rather than the end of it. Splitting the text into
 * lines first cannot honour that, which is how a note with a line break in it used to come
 * back from an import as two broken rows. So the reader walks the text a character at a
 * time and only ends a row at a line break outside quotes.
 *
 * It is forgiving where real files are untidy. A byte order mark at the start is dropped:
 * Excel's "CSV UTF-8" begins with one, and it would otherwise stick to the first column's
 * name so that "Date" was never found. Rows may end in CRLF, LF or a bare CR. A quote that
 * appears in the middle of an unquoted field is kept as an ordinary character.
 */
internal object Csv {

    const val BYTE_ORDER_MARK = '﻿'

    /** RFC 4180's record separator. Every reader accepts it, Excel included. */
    const val LINE_END = "\r\n"

    /**
     * The rows of [text], each a list of fields. Rows with nothing in them, such as the
     * trailing ",,,," lines spreadsheets like to add, are left out entirely.
     */
    fun read(text: String): CsvTable {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        // Whether the current field opened with a quote, so a second quote after the closing
        // one is read as a character rather than as the start of another quoted part.
        var fieldWasQuoted = false

        fun endField() {
            row += field.toString()
            field.setLength(0)
            fieldWasQuoted = false
        }

        fun endRow() {
            endField()
            if (row.any { it.isNotBlank() }) rows += row
            row = mutableListOf()
        }

        var index = if (text.startsWith(BYTE_ORDER_MARK)) 1 else 0
        while (index < text.length) {
            val char = text[index]
            if (inQuotes) {
                if (char == '"') {
                    if (index + 1 < text.length && text[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(char)
                }
            } else {
                when (char) {
                    '"' -> if (field.isEmpty() && !fieldWasQuoted) {
                        inQuotes = true
                        fieldWasQuoted = true
                    } else {
                        field.append(char)
                    }
                    ',' -> endField()
                    '\r' -> {
                        endRow()
                        if (index + 1 < text.length && text[index + 1] == '\n') index++
                    }
                    '\n' -> endRow()
                    else -> field.append(char)
                }
            }
            index++
        }

        val endedInsideQuotes = inQuotes
        if (row.isNotEmpty() || field.isNotEmpty() || fieldWasQuoted) endRow()
        return CsvTable(rows, endedInsideQuotes)
    }

    /**
     * One field, always quoted, with any quote inside it doubled.
     *
     * Quoting every field rather than only the ones that need it costs two characters and
     * removes a decision that is easy to get wrong: a field holding a comma, a quote or a
     * line break is safe however it got there.
     */
    fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    /** A whole record, separator included. */
    fun line(fields: List<String>): String = fields.joinToString(",") { quote(it) } + LINE_END
}

/**
 * What [Csv.read] found.
 *
 * [endedInsideQuotes] means a quote was opened and never closed, so everything after it
 * was read as one field. The rows before it are sound; those after it cannot be told apart.
 */
internal data class CsvTable(
    val rows: List<List<String>>,
    val endedInsideQuotes: Boolean
)

/**
 * The expenses CSV: the export writes it, the import reads it, and both come through here
 * so that the two cannot drift apart. Whatever the export produces, the import reads back
 * as the same expenses.
 */
internal object ExpenseCsv {

    val HEADER = listOf(
        "Date", "Description", "Category", "Amount", "Payment method",
        "Person", "Linked to", "Notes"
    )

    /** Characters a spreadsheet reads as the start of a formula. */
    private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@')

    /** One expense as the file carries it, every field already text. */
    data class Row(
        val date: String,
        val description: String,
        val category: String,
        val amount: String,
        val paymentMethod: String,
        val person: String,
        val linkedTo: String,
        val notes: String
    ) {
        fun fields(): List<String> =
            listOf(date, description, category, amount, paymentMethod, person, linkedTo, notes)
    }

    /**
     * The whole file. It starts with a byte order mark because Excel opens a CSV without
     * one in the computer's older regional encoding rather than as UTF-8, which turns a
     * rupee sign or a description in Hindi into gibberish, and saving it again keeps the
     * damage. The import drops the mark again.
     */
    fun write(rows: List<Row>): String = buildString {
        append(Csv.BYTE_ORDER_MARK)
        append(Csv.line(HEADER))
        rows.forEach { row -> append(Csv.line(row.fields().map(::neutralise))) }
    }

    /** An expense read from a file, checked and ready to save. */
    data class ImportRow(
        val date: LocalDate,
        val description: String,
        val category: String,
        val amount: Money,
        val paymentMethod: PaymentMethod,
        val notes: String
    )

    sealed interface ReadResult {
        /**
         * The rows that can be imported, and how many could not be. [endedInsideQuotes] is
         * carried through so the user can be told that the end of the file may be missing.
         */
        data class Rows(
            val rows: List<ImportRow>,
            val skipped: Int,
            val endedInsideQuotes: Boolean
        ) : ReadResult

        /** Nothing in the file can be imported, and [message] says why. */
        data class Unusable(val message: String) : ReadResult
    }

    /**
     * Reads a file, keeping every row that makes sense and counting the rest.
     *
     * A row is skipped, rather than failing the whole import, when its date is not a real
     * YYYY-MM-DD date, its amount is not a number above zero, or it has no category. One bad
     * row used to throw out of the import and take every good row with it. Dates are
     * accepted only in that one form
     * on purpose: 03/04/2026 is the 3rd of April here and the 4th of March in other places,
     * and an import that guessed would put expenses in the wrong month without saying so.
     */
    fun read(text: String): ReadResult {
        val table = Csv.read(text)
        if (table.rows.size < 2) return ReadResult.Unusable("The CSV has no expense rows.")

        val header = table.rows.first().map { it.trim().lowercase() }
        fun column(name: String) = header.indexOf(name)
        val dateIndex = column("date")
        val descriptionIndex = column("description")
        val categoryIndex = column("category")
        val amountIndex = column("amount")
        if (listOf(dateIndex, descriptionIndex, categoryIndex, amountIndex).any { it < 0 }) {
            return ReadResult.Unusable(
                "Use a CSV with Date, Description, Category and Amount columns."
            )
        }
        val paymentIndex = column("payment method")
        val notesIndex = column("notes")

        var skipped = 0
        val rows = table.rows.drop(1).mapNotNull { values ->
            fun value(index: Int): String? = values.getOrNull(index)?.let(::restore)

            val date = value(dateIndex)?.trim()
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val amount = value(amountIndex)?.let(Money::parseOrNull)?.takeIf { it.isPositive }
            val category = value(categoryIndex)?.trim().orEmpty()

            if (date == null || amount == null || category.isBlank()) {
                skipped++
                return@mapNotNull null
            }

            val paymentText = value(paymentIndex)?.trim().orEmpty()
            ImportRow(
                date = date,
                description = value(descriptionIndex).orEmpty(),
                category = category,
                amount = amount,
                paymentMethod = PaymentMethod.entries.firstOrNull {
                    it.name.equals(paymentText, ignoreCase = true) ||
                        it.label.equals(paymentText, ignoreCase = true)
                } ?: PaymentMethod.OTHER,
                notes = value(notesIndex).orEmpty()
            )
        }
        return ReadResult.Rows(rows, skipped, table.endedInsideQuotes)
    }

    /**
     * Prefixes an apostrophe to a value a spreadsheet would read as a formula.
     *
     * A description someone typed as "=2+2" would otherwise be evaluated when the file is
     * opened, and the sheet would show something the user never wrote. The apostrophe is
     * the standard way to force a cell back to plain text.
     */
    private fun neutralise(value: String): String =
        if (value.isNotEmpty() && value[0] in FORMULA_TRIGGERS) "'$value" else value

    /**
     * Undoes [neutralise], so a description the export protected comes back as it was
     * typed. Only an apostrophe in front of a formula character is removed, which is the
     * only place the export ever adds one; a spreadsheet saving the file does not write the
     * apostrophe at all, so no real value is changed by this.
     */
    private fun restore(value: String): String =
        if (value.length >= 2 && value[0] == '\'' && value[1] in FORMULA_TRIGGERS) {
            value.substring(1)
        } else {
            value
        }
}
