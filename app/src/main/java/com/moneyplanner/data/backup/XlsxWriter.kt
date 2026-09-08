package com.moneyplanner.data.backup

import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a real Excel workbook, without a library.
 *
 * An .xlsx file is a zip of XML parts, and the subset needed for tabular data is small
 * enough to emit directly. The alternative was Apache POI, which would have taken the
 * release APK from around 3.3 MB to roughly 15 MB — a fivefold increase for a feature used
 * occasionally, on an app whose whole point is that it is small and offline. Writing the
 * format is a few hundred lines and costs nothing at runtime.
 *
 * What this deliberately does *not* do is as important as what it does. There are no
 * charts, no formulas, no images, no themes: those are where the format gets genuinely
 * hard. It writes typed cells, a header row, sensible column widths and a frozen header,
 * which is what a spreadsheet of transactions actually needs.
 *
 * The point of a workbook over a CSV is types. An amount written as a number can be summed
 * in Excel; the same amount as "₹1,500" is text, and a column of it sums to zero.
 */
class XlsxWriter {

    private val sheets = mutableListOf<Sheet>()

    fun sheet(name: String, columns: List<Column>, rows: List<List<Cell>>) {
        sheets += Sheet(sanitiseSheetName(name), columns, rows)
    }

    val isEmpty: Boolean get() = sheets.isEmpty()

    fun writeTo(file: File) {
        file.outputStream().use { out -> writeTo(out) }
    }

    fun writeTo(output: OutputStream) {
        require(sheets.isNotEmpty()) { "A workbook needs at least one sheet" }

        ZipOutputStream(output).use { zip ->
            zip.put("[Content_Types].xml", contentTypes())
            zip.put("_rels/.rels", rootRels())
            zip.put("xl/workbook.xml", workbook())
            zip.put("xl/_rels/workbook.xml.rels", workbookRels())
            zip.put("xl/styles.xml", styles())
            sheets.forEachIndexed { index, sheet ->
                zip.put("xl/worksheets/sheet${index + 1}.xml", worksheet(sheet))
            }
        }
    }

    private fun ZipOutputStream.put(path: String, body: String) {
        putNextEntry(ZipEntry(path))
        write(body.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // ---- Parts -------------------------------------------------------------------

    private fun contentTypes(): String = buildString {
        append(XML_HEADER)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        sheets.indices.forEach { index ->
            append("""<Override PartName="/xl/worksheets/sheet${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun rootRels(): String =
        XML_HEADER +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            "</Relationships>"

    private fun workbook(): String = buildString {
        append(XML_HEADER)
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { index, sheet ->
            append("""<sheet name="${escape(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(): String = buildString {
        append(XML_HEADER)
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sheets.indices.forEach { index ->
            append("""<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${index + 1}.xml"/>""")
        }
        append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    /**
     * Four cell formats, in the order the worksheet refers to them by index:
     * 0 plain, 1 bold header, 2 date, 3 rupee amount with thousands separators.
     *
     * The rupee format uses the Indian digit grouping the rest of the app uses, so a
     * figure reads the same in the spreadsheet as it does on the screen it came from.
     */
    private fun styles(): String =
        XML_HEADER +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<numFmts count="2">""" +
            """<numFmt numFmtId="164" formatCode="dd\-mmm\-yyyy"/>""" +
            """<numFmt numFmtId="165" formatCode="[$₹-4009]\ ##,##,##0.00"/>""" +
            "</numFmts>" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="2"><fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill></fills>""" +
            """<borders count="1"><border/></borders>""" +
            """<cellStyleXfs count="1"><xf/></cellStyleXfs>""" +
            """<cellXfs count="4">""" +
            """<xf xfId="0"/>""" +
            """<xf xfId="0" fontId="1" applyFont="1"/>""" +
            """<xf xfId="0" numFmtId="164" applyNumberFormat="1"/>""" +
            """<xf xfId="0" numFmtId="165" applyNumberFormat="1"/>""" +
            "</cellXfs>" +
            // The named default style. Excel tolerates its absence, but stricter readers
            // warn and substitute one of their own, which is a needless difference
            // between what this writes and what every other writer produces.
            """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
            "</styleSheet>"

    private fun worksheet(sheet: Sheet): String = buildString {
        append(XML_HEADER)
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        append("<cols>")
        sheet.columns.forEachIndexed { index, column ->
            append("""<col min="${index + 1}" max="${index + 1}" width="${column.width}" customWidth="1"/>""")
        }
        append("</cols>")

        // The header stays put while the rows scroll, which is the difference between a
        // usable export and one you have to keep scrolling back up in.
        append("""<sheetViews><sheetView workbookViewId="0">""")
        append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
        append("</sheetView></sheetViews>")

        append("<sheetData>")

        append("""<row r="1">""")
        sheet.columns.forEachIndexed { index, column ->
            append("""<c r="${cellRef(index, 1)}" s="1" t="inlineStr"><is><t>${escape(column.title)}</t></is></c>""")
        }
        append("</row>")

        sheet.rows.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 2
            append("""<row r="$rowNumber">""")
            row.forEachIndexed { columnIndex, cell ->
                append(cellXml(cellRef(columnIndex, rowNumber), cell))
            }
            append("</row>")
        }

        append("</sheetData></worksheet>")
    }

    private fun cellXml(ref: String, cell: Cell): String = when (cell) {
        is Cell.Blank -> ""
        is Cell.Text -> """<c r="$ref" t="inlineStr"><is><t>${escape(cell.value)}</t></is></c>"""
        is Cell.Number -> """<c r="$ref"><v>${cell.value}</v></c>"""
        is Cell.Amount ->
            """<c r="$ref" s="3"><v>${cell.rupees()}</v></c>"""
        is Cell.Date ->
            """<c r="$ref" s="2"><v>${cell.serial()}</v></c>"""
    }

    private companion object {
        const val XML_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

        /** Column letters, then the 1-based row. Enough for the 26 columns this needs. */
        fun cellRef(columnIndex: Int, rowNumber: Int): String {
            var index = columnIndex
            val letters = StringBuilder()
            do {
                letters.insert(0, ('A' + index % 26))
                index = index / 26 - 1
            } while (index >= 0)
            return "$letters$rowNumber"
        }

        fun escape(value: String): String = buildString(value.length) {
            value.forEach { character ->
                when {
                    character == '&' -> append("&amp;")
                    character == '<' -> append("&lt;")
                    character == '>' -> append("&gt;")
                    character == '"' -> append("&quot;")
                    character == '\'' -> append("&apos;")
                    // Control characters are not legal in XML 1.0 and would make the
                    // whole workbook unopenable, so a stray one in a note is dropped
                    // rather than allowed to corrupt the file it sits in.
                    character.code < 0x20 && character != '\n' && character != '\t' -> Unit
                    else -> append(character)
                }
            }
        }

        /** Excel sheet names cannot hold these, and cannot exceed 31 characters. */
        fun sanitiseSheetName(name: String): String =
            name.replace(Regex("""[\\/*?\[\]:]"""), " ").trim().take(31).ifBlank { "Sheet" }
    }

    data class Column(val title: String, val width: Int = 16)

    data class Sheet(val name: String, val columns: List<Column>, val rows: List<List<Cell>>)

    sealed interface Cell {
        data object Blank : Cell
        data class Text(val value: String) : Cell
        data class Number(val value: Long) : Cell

        /** Paise, written as rupees so Excel can sum the column. */
        data class Amount(val paise: Long) : Cell {
            fun rupees(): String {
                val negative = paise < 0
                val absolute = kotlin.math.abs(paise)
                val text = "${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
                return if (negative) "-$text" else text
            }
        }

        /**
         * A real date cell, so Excel can filter and sort by it.
         *
         * The serial number counts days from 30 December 1899. The offset is not a typo:
         * Excel treats 1900 as a leap year, which it was not, and every date after
         * February 1900 is shifted by one as a result. Anchoring to 1899-12-30 absorbs
         * that bug, which is what every other writer does.
         */
        data class Date(val value: LocalDate) : Cell {
            fun serial(): Long = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.of(1899, 12, 30),
                value
            )
        }
    }
}
