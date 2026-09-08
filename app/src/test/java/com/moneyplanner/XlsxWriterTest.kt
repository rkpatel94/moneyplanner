package com.moneyplanner

import com.moneyplanner.data.backup.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

/**
 * The workbook writer.
 *
 * Two things here are easy to get wrong and impossible to notice by eye. Excel dates are a
 * serial number anchored to a date that does not look right, because the format preserves a
 * 1900 leap year bug; and an amount held in paise has to become a decimal number, not a
 * formatted string, or the column will not sum.
 *
 * The rest is structural: a workbook that is missing a part, or that contains a character
 * XML does not allow, does not open at all.
 */
class XlsxWriterTest {

    private fun write(build: XlsxWriter.() -> Unit): Map<String, String> {
        val writer = XlsxWriter().apply(build)
        val bytes = ByteArrayOutputStream().also { writer.writeTo(it) }.toByteArray()

        val parts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return parts
    }

    private fun sample(writer: XlsxWriter) = writer.sheet(
        name = "Expenses",
        columns = listOf(XlsxWriter.Column("Date"), XlsxWriter.Column("Amount")),
        rows = listOf(
            listOf(
                XlsxWriter.Cell.Date(date("2026-09-08")),
                XlsxWriter.Cell.Amount(150_000)
            )
        )
    )

    @Test
    fun `a workbook contains every part Excel needs to open it`() {
        val parts = write { sample(this) }

        assertTrue(parts.containsKey("[Content_Types].xml"))
        assertTrue(parts.containsKey("_rels/.rels"))
        assertTrue(parts.containsKey("xl/workbook.xml"))
        assertTrue(parts.containsKey("xl/_rels/workbook.xml.rels"))
        assertTrue(parts.containsKey("xl/styles.xml"))
        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"))
    }

    @Test
    fun `an amount in paise is written as a number that can be summed`() {
        val parts = write { sample(this) }
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")

        // 150000 paise is 1500.00 rupees, written bare so Excel treats it as a number.
        assertTrue(sheet.contains("<v>1500.00</v>"))
        // And not as text, which is what would stop a column summing.
        assertTrue(!sheet.contains("₹1,500"))
    }

    @Test
    fun `paise below ten are padded so the decimal is not shifted`() {
        val parts = write {
            sheet(
                "S",
                listOf(XlsxWriter.Column("A")),
                listOf(listOf(XlsxWriter.Cell.Amount(1_205)))
            )
        }
        // 1205 paise is 12.05, not 12.5.
        assertTrue(parts.getValue("xl/worksheets/sheet1.xml").contains("<v>12.05</v>"))
    }

    @Test
    fun `a negative amount keeps its sign`() {
        val parts = write {
            sheet(
                "S",
                listOf(XlsxWriter.Column("A")),
                listOf(listOf(XlsxWriter.Cell.Amount(-200_000)))
            )
        }
        assertTrue(parts.getValue("xl/worksheets/sheet1.xml").contains("<v>-2000.00</v>"))
    }

    @Test
    fun `dates use the serial Excel actually expects`() {
        // Anchored to 1899-12-30 rather than 1900-01-01, which absorbs Excel's belief
        // that 1900 was a leap year. 1 Jan 2000 is 36526 in every spreadsheet program.
        assertEquals(36_526L, XlsxWriter.Cell.Date(LocalDate.of(2000, 1, 1)).serial())
        assertEquals(1L, XlsxWriter.Cell.Date(LocalDate.of(1899, 12, 31)).serial())
        assertEquals(46_273L, XlsxWriter.Cell.Date(LocalDate.of(2026, 9, 8)).serial())
    }

    @Test
    fun `text is escaped so a stray character cannot corrupt the file`() {
        val parts = write {
            sheet(
                "S",
                listOf(XlsxWriter.Column("Note")),
                listOf(listOf(XlsxWriter.Cell.Text("""Tea & <biscuits> "cheap"""")))
            )
        }
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")

        assertTrue(sheet.contains("Tea &amp; &lt;biscuits&gt; &quot;cheap&quot;"))
    }

    @Test
    fun `a control character is dropped rather than written into the xml`() {
        val parts = write {
            sheet(
                "S",
                listOf(XlsxWriter.Column("Note")),
                listOf(listOf(XlsxWriter.Cell.Text("before\u0007after")))
            )
        }
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")

        assertTrue(sheet.contains("beforeafter"))
        assertTrue(!sheet.contains("\u0007"))
    }

    @Test
    fun `a sheet name too long or holding illegal characters is made safe`() {
        val parts = write {
            sheet(
                "Expenses/2026: everything recorded this year and more",
                listOf(XlsxWriter.Column("A")),
                listOf(listOf(XlsxWriter.Cell.Text("x")))
            )
        }
        val workbook = parts.getValue("xl/workbook.xml")

        // Checked on the name itself, not the whole part: workbook.xml legitimately
        // contains colons and slashes in its namespace declarations.
        val name = Regex("""<sheet name="([^"]*)"""").find(workbook)!!.groupValues[1]

        assertTrue(!name.contains("/"))
        assertTrue(!name.contains(":"))
        // 31 characters is Excel's hard limit; a longer name makes the file unopenable.
        assertTrue(name.length <= 31)
    }

    @Test
    fun `several sheets each get their own part and relationship`() {
        val parts = write {
            sheet("Expenses", listOf(XlsxWriter.Column("A")), listOf(listOf(XlsxWriter.Cell.Text("a"))))
            sheet("Income", listOf(XlsxWriter.Column("A")), listOf(listOf(XlsxWriter.Cell.Text("b"))))
            sheet("Transfers", listOf(XlsxWriter.Column("A")), listOf(listOf(XlsxWriter.Cell.Text("c"))))
        }

        assertTrue(parts.containsKey("xl/worksheets/sheet3.xml"))
        val rels = parts.getValue("xl/_rels/workbook.xml.rels")
        assertTrue(rels.contains("worksheets/sheet3.xml"))
        // Styles takes the id after the sheets, so it must not collide with sheet 3.
        assertTrue(rels.contains("""Id="rId4""""))
    }

    @Test
    fun `the header row is written once and frozen`() {
        val parts = write { sample(this) }
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")

        assertTrue(sheet.contains("""<t>Date</t>"""))
        assertTrue(sheet.contains("""ySplit="1""""))
    }

    @Test
    fun `columns past the twenty-sixth carry on into two-letter references`() {
        val columns = (1..28).map { XlsxWriter.Column("C$it") }
        val parts = write {
            sheet("Wide", columns, listOf(columns.map { XlsxWriter.Cell.Text("x") }))
        }
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")

        assertTrue(sheet.contains("""r="Z1""""))
        assertTrue(sheet.contains("""r="AA1""""))
        assertTrue(sheet.contains("""r="AB1""""))
    }
}
