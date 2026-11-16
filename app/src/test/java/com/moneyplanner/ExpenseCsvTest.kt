package com.moneyplanner

import com.moneyplanner.data.backup.Csv
import com.moneyplanner.data.backup.CsvImportResult
import com.moneyplanner.data.backup.ExpenseCsv
import com.moneyplanner.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The expenses CSV, read and written.
 *
 * The import used to split the file into lines before it looked at a single quote, so a
 * note with a line break in it came back as two broken rows, and one date it could not
 * parse threw out of the middle of the import and lost every good row with it. A file
 * saved from Excel as "CSV UTF-8" starts with a byte order mark, which stuck to the first
 * column's name so the Date column was never found. These pin down the reader on its own,
 * then the export and the import against each other.
 */
class ExpenseCsvTest {

    // ---- The reader ----------------------------------------------------------------

    @Test
    fun `plain fields are split on commas`() {
        val table = Csv.read("a,b,c\n1,2,3\n")

        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "2", "3")), table.rows)
        assertFalse(table.endedInsideQuotes)
    }

    @Test
    fun `a quoted field keeps its commas`() {
        val table = Csv.read("\"Rent, flat 4\",13000\n")

        assertEquals(listOf("Rent, flat 4", "13000"), table.rows.single())
    }

    @Test
    fun `a doubled quote inside quotes is one quote`() {
        val table = Csv.read("\"The \"\"good\"\" biryani\",450\n")

        assertEquals("The \"good\" biryani", table.rows.single()[0])
    }

    @Test
    fun `a line break inside quotes stays in the field instead of starting a row`() {
        val table = Csv.read("Date,Notes\r\n2026-11-01,\"first line\r\nsecond line\"\r\n2026-11-02,next\r\n")

        assertEquals(3, table.rows.size)
        assertEquals("first line\r\nsecond line", table.rows[1][1])
        assertEquals(listOf("2026-11-02", "next"), table.rows[2])
    }

    @Test
    fun `a leading byte order mark is dropped`() {
        val table = Csv.read("﻿Date,Amount\n2026-11-01,10\n")

        assertEquals("Date", table.rows.first().first())
    }

    @Test
    fun `rows may end in CRLF, LF or a bare CR`() {
        val table = Csv.read("a,b\r\nc,d\ne,f\rg,h")

        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d"), listOf("e", "f"), listOf("g", "h")),
            table.rows
        )
    }

    @Test
    fun `empty rows are left out rather than read as data`() {
        // Spreadsheets like to finish a file with a few rows of nothing but separators.
        val table = Csv.read("a,b\n\n,\n1,2\n,,\n")

        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), table.rows)
    }

    @Test
    fun `empty fields are kept in their place`() {
        val table = Csv.read("a,,c,\"\"\n")

        assertEquals(listOf("a", "", "c", ""), table.rows.single())
    }

    @Test
    fun `a quote in the middle of an unquoted field is an ordinary character`() {
        val table = Csv.read("5\" pipe,120\n")

        assertEquals(listOf("5\" pipe", "120"), table.rows.single())
    }

    @Test
    fun `the last row is read without a line break after it`() {
        assertEquals(listOf("x", "y"), Csv.read("x,y").rows.single())
    }

    @Test
    fun `a quote that never closes is reported rather than passed off as a clean read`() {
        val table = Csv.read("a,b\n\"unclosed,1\n2,3\n")

        assertTrue(table.endedInsideQuotes)
        assertEquals(listOf("a", "b"), table.rows.first())
    }

    @Test
    fun `the writer quotes every field and doubles quotes inside`() {
        assertEquals("\"plain\"", Csv.quote("plain"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.quote("say \"hi\""))
        assertEquals("\"a\",\"b,c\"\r\n", Csv.line(listOf("a", "b,c")))
    }

    // ---- Reading expenses ----------------------------------------------------------

    private val header = "Date,Description,Category,Amount,Payment method,Notes\n"

    private fun readRows(text: String): ExpenseCsv.ReadResult.Rows {
        val result = ExpenseCsv.read(text)
        assertTrue("expected rows, got $result", result is ExpenseCsv.ReadResult.Rows)
        return result as ExpenseCsv.ReadResult.Rows
    }

    @Test
    fun `one bad row is skipped and counted, and the good rows still come in`() {
        val result = readRows(
            header +
                "2026-11-01,Lunch,Food,250,UPI,\n" +
                "01/11/2026,Wrong date format,Food,100,UPI,\n" +
                "2026-11-31,No such day,Food,100,UPI,\n" +
                "2026-11-02,No amount,Food,,UPI,\n" +
                "2026-11-02,Zero amount,Food,0,UPI,\n" +
                "2026-11-02,Not a number,Food,lots,UPI,\n" +
                "2026-11-02,No category,,80,UPI,\n" +
                "2026-11-03,Short row\n" +
                "2026-11-04,Dinner,Food,\"1,200.50\",Cash,with friends\n"
        )

        assertEquals(listOf("Lunch", "Dinner"), result.rows.map { it.description })
        assertEquals(7, result.skipped)
        assertEquals(rupees(1_200).paise + 50, result.rows[1].amount.paise)
        assertEquals(PaymentMethod.CASH, result.rows[1].paymentMethod)
        assertEquals("with friends", result.rows[1].notes)
    }

    @Test
    fun `a file saved as Excel CSV UTF-8 is recognised`() {
        val result = readRows("﻿" + header + "2026-11-01,Tea,Food,20,UPI,\n")

        assertEquals(1, result.rows.size)
    }

    @Test
    fun `column names are matched whatever their case and spacing`() {
        val result = readRows(" DATE , description,Category ,AMOUNT\n2026-11-01,Tea,Food,20\n")

        assertEquals(date("2026-11-01"), result.rows.single().date)
        assertEquals(PaymentMethod.OTHER, result.rows.single().paymentMethod)
    }

    @Test
    fun `a payment method is matched by name or by label`() {
        val result = readRows(
            header +
                "2026-11-01,A,Food,1,bank_transfer,\n" +
                "2026-11-01,B,Food,1,Debit card,\n" +
                "2026-11-01,C,Food,1,Barter,\n"
        )

        assertEquals(
            listOf(PaymentMethod.BANK_TRANSFER, PaymentMethod.DEBIT_CARD, PaymentMethod.OTHER),
            result.rows.map { it.paymentMethod }
        )
    }

    @Test
    fun `a file without the needed columns is turned away with a reason`() {
        val result = ExpenseCsv.read("When,What\n2026-11-01,Tea\n")

        assertEquals(
            "Use a CSV with Date, Description, Category and Amount columns.",
            (result as ExpenseCsv.ReadResult.Unusable).message
        )
    }

    @Test
    fun `a file with only a header has no rows to import`() {
        val result = ExpenseCsv.read(header + "\n,,,\n")

        assertEquals(
            "The CSV has no expense rows.",
            (result as ExpenseCsv.ReadResult.Unusable).message
        )
    }

    // ---- The round trip ------------------------------------------------------------

    private fun row(
        date: String = "2026-11-01",
        description: String = "Expense",
        category: String = "Food",
        amount: String = "100.00",
        paymentMethod: String = "UPI",
        notes: String = ""
    ) = ExpenseCsv.Row(
        date = date,
        description = description,
        category = category,
        amount = amount,
        paymentMethod = paymentMethod,
        person = "",
        linkedTo = "",
        notes = notes
    )

    @Test
    fun `everything the export writes, the import reads back unchanged`() {
        val written = listOf(
            row(description = "Plain", amount = "250.00"),
            row(description = "Rent, flat 4", category = "Housing, rent", amount = "13000.00"),
            row(description = "The \"good\" biryani", amount = "450.50", paymentMethod = "CASH"),
            row(description = "Groceries", notes = "milk\nbread\r\neggs, and \"more\""),
            // The export puts an apostrophe in front of these so a spreadsheet does not
            // evaluate them; the import has to take it off again.
            row(description = "=2+2", notes = "-50 refund"),
            row(description = "+91 recharge", notes = "@home"),
            row(description = "", amount = "0.05", notes = "trailing space "),
            row(description = "दूध और ब्रेड", category = "किराना", amount = "1.00")
        )

        val read = readRows(ExpenseCsv.write(written))

        assertEquals(0, read.skipped)
        assertFalse(read.endedInsideQuotes)
        assertEquals(written.map { it.description }, read.rows.map { it.description })
        assertEquals(written.map { it.category }, read.rows.map { it.category })
        assertEquals(written.map { it.notes }, read.rows.map { it.notes })
        assertEquals(
            listOf(25_000L, 1_300_000L, 45_050L, 10_000L, 10_000L, 10_000L, 5L, 100L),
            read.rows.map { it.amount.paise }
        )
        assertEquals(PaymentMethod.CASH, read.rows[2].paymentMethod)
        assertTrue(read.rows.all { it.date == date("2026-11-01") })
    }

    @Test
    fun `the export protects formula characters from a spreadsheet`() {
        val text = ExpenseCsv.write(listOf(row(description = "=HYPERLINK(\"x\")")))

        assertTrue(text.contains("\"'=HYPERLINK(\"\"x\"\")\""))
    }

    @Test
    fun `the export starts with a byte order mark and ends its rows in CRLF`() {
        val text = ExpenseCsv.write(listOf(row()))

        assertTrue(text.startsWith("﻿\"Date\",\"Description\""))
        assertTrue(text.endsWith("\r\n"))
        assertEquals(2, text.split("\r\n").filter { it.isNotEmpty() }.size)
    }

    @Test
    fun `an export with no expenses reads back as a file with no rows, not a broken one`() {
        val result = ExpenseCsv.read(ExpenseCsv.write(emptyList()))

        assertEquals(
            "The CSV has no expense rows.",
            (result as ExpenseCsv.ReadResult.Unusable).message
        )
    }

    // ---- What the user is told -----------------------------------------------------

    @Test
    fun `the message counts what came in and what did not`() {
        assertEquals("Imported 1 expense.", CsvImportResult.Success(1, 0).message())
        assertEquals("Imported 12 expenses.", CsvImportResult.Success(12, 0).message())
        assertEquals(
            "Imported 12 expenses. Skipped 1 row that could not be read.",
            CsvImportResult.Success(12, 1).message()
        )
        assertEquals(
            "Imported 3 expenses. Skipped 2 rows that could not be read.",
            CsvImportResult.Success(3, 2).message()
        )
    }

    @Test
    fun `when nothing could be read the message says what each row needs`() {
        val message = CsvImportResult.Success(0, 4).message()

        assertTrue(message.startsWith("No expenses were imported: 4 rows could not be read."))
        assertTrue(message.contains("YYYY-MM-DD"))
    }

    @Test
    fun `an unclosed quote is mentioned so a short import is not taken as complete`() {
        val message = CsvImportResult.Success(5, 0, endedInsideQuotes = true).message()

        assertTrue(message.startsWith("Imported 5 expenses."))
        assertTrue(message.contains("never closed"))
    }
}
