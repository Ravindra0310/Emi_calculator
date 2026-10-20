package com.example.emicalculator.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.emicalculator.domain.model.AmortizationSchedule
import com.example.emicalculator.domain.model.EmiResult
import com.example.emicalculator.domain.model.LoanInput
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =========================================================================
// 1. INDIAN CURRENCY FORMATTER
// =========================================================================
object CurrencyFormatter {
    private val indianSymbols = DecimalFormatSymbols(Locale("en", "IN")).apply { currencySymbol = "₹" }

    fun formatInr(amount: Double, includeSymbol: Boolean = true): String {
        val df = DecimalFormat("#,##,##0.00", indianSymbols)
        val formatted = df.format(amount)
        return if (includeSymbol) "₹ $formatted" else formatted
    }
}

// =========================================================================
// 2. PDF STATEMENT EXPORTER
// =========================================================================
object PdfExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    fun exportAndSharePdf(
        context: Context,
        input: LoanInput,
        emiResult: EmiResult,
        schedule: AmortizationSchedule
    ): Result<File> = runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val pdfFile = File(exportDir, "EMI_Schedule_$timeStamp.pdf")

        val document = PdfDocument()

        val titlePaint = Paint().apply {
            color = Color.rgb(30, 58, 138)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9f
            isAntiAlias = true
        }
        val headerBgPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }
        val tableHeaderBg = Paint().apply {
            color = Color.rgb(37, 99, 235)
            style = Paint.Style.FILL
        }
        val tableHeaderTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.rgb(31, 41, 55)
            textSize = 9f
            isAntiAlias = true
        }
        val boldTextPaint = Paint().apply {
            color = Color.rgb(17, 24, 39)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        var currentPageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN + 20f

        canvas.drawText("EMI Calculation & Amortization Statement", MARGIN, y, titlePaint)
        y += 14f
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText("Generated on: $dateStr", MARGIN, y, subtitlePaint)
        y += 20f

        // Summary Card
        canvas.drawRoundRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 80f, 8f, 8f, headerBgPaint)
        canvas.drawRoundRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 80f, 8f, 8f, linePaint)

        var cardY = y + 24f
        canvas.drawText("Loan Principal:", MARGIN + 16f, cardY, subtitlePaint)
        canvas.drawText(CurrencyFormatter.formatInr(input.principal), MARGIN + 16f, cardY + 14f, boldTextPaint)

        canvas.drawText("Annual Interest Rate:", MARGIN + 180f, cardY, subtitlePaint)
        canvas.drawText("${input.annualInterestRate} %", MARGIN + 180f, cardY + 14f, boldTextPaint)

        canvas.drawText("Tenure Duration:", MARGIN + 360f, cardY, subtitlePaint)
        canvas.drawText("${input.totalMonths} Months", MARGIN + 360f, cardY + 14f, boldTextPaint)

        cardY += 34f
        canvas.drawText("Monthly EMI:", MARGIN + 16f, cardY, subtitlePaint)
        canvas.drawText(CurrencyFormatter.formatInr(emiResult.monthlyEmi), MARGIN + 16f, cardY + 14f, boldTextPaint)

        canvas.drawText("Total Interest:", MARGIN + 180f, cardY, subtitlePaint)
        canvas.drawText(CurrencyFormatter.formatInr(emiResult.totalInterest), MARGIN + 180f, cardY + 14f, boldTextPaint)

        canvas.drawText("Total Repayment:", MARGIN + 360f, cardY, subtitlePaint)
        canvas.drawText(CurrencyFormatter.formatInr(emiResult.totalPayment), MARGIN + 360f, cardY + 14f, boldTextPaint)

        y += 106f

        fun drawHeader(c: Canvas, startY: Float): Float {
            c.drawRect(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + 22f, tableHeaderBg)
            val textY = startY + 15f
            c.drawText("Mo", MARGIN + 8f, textY, tableHeaderTextPaint)
            c.drawText("Opening Bal (₹)", MARGIN + 50f, textY, tableHeaderTextPaint)
            c.drawText("EMI (₹)", MARGIN + 140f, textY, tableHeaderTextPaint)
            c.drawText("Principal (₹)", MARGIN + 230f, textY, tableHeaderTextPaint)
            c.drawText("Interest (₹)", MARGIN + 320f, textY, tableHeaderTextPaint)
            c.drawText("Closing Bal (₹)", MARGIN + 410f, textY, tableHeaderTextPaint)
            return startY + 22f
        }

        y = drawHeader(canvas, y)

        for (month in schedule.months) {
            if (y + 18f > PAGE_HEIGHT - MARGIN - 20f) {
                canvas.drawText("Page $currentPageNumber", PAGE_WIDTH / 2f - 15f, PAGE_HEIGHT - 16f, subtitlePaint)
                document.finishPage(page)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = drawHeader(canvas, MARGIN + 10f)
            }

            val rowY = y + 13f
            canvas.drawText("${month.monthIndex}", MARGIN + 8f, rowY, textPaint)
            canvas.drawText(CurrencyFormatter.formatInr(month.openingBalance, false), MARGIN + 50f, rowY, textPaint)
            canvas.drawText(CurrencyFormatter.formatInr(month.emiAmount, false), MARGIN + 140f, rowY, textPaint)
            canvas.drawText(CurrencyFormatter.formatInr(month.principalPaid, false), MARGIN + 230f, rowY, textPaint)
            canvas.drawText(CurrencyFormatter.formatInr(month.interestPaid, false), MARGIN + 320f, rowY, textPaint)
            canvas.drawText(CurrencyFormatter.formatInr(month.closingBalance, false), MARGIN + 410f, rowY, textPaint)
            canvas.drawLine(MARGIN, y + 18f, PAGE_WIDTH - MARGIN, y + 18f, linePaint)
            y += 18f
        }

        canvas.drawText("Page $currentPageNumber", PAGE_WIDTH / 2f - 15f, PAGE_HEIGHT - 16f, subtitlePaint)
        document.finishPage(page)

        FileOutputStream(pdfFile).use { out -> document.writeTo(out) }
        document.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share EMI Statement (PDF)").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        pdfFile
    }
}

// =========================================================================
// 3. CSV EXPORTER
// =========================================================================
object CsvExporter {
    fun exportAndShareCsv(
        context: Context,
        input: LoanInput,
        emiResult: EmiResult,
        schedule: AmortizationSchedule
    ): Result<File> = runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val csvFile = File(exportDir, "EMI_Schedule_$timeStamp.csv")

        FileWriter(csvFile).use { writer ->
            writer.append("EMI Calculation Summary\n")
            writer.append("Loan Principal,${input.principal}\n")
            writer.append("Annual Interest Rate (%),${input.annualInterestRate}\n")
            writer.append("Tenure (Months),${input.totalMonths}\n")
            writer.append("Monthly EMI,${emiResult.monthlyEmi}\n")
            writer.append("Total Interest,${emiResult.totalInterest}\n")
            writer.append("Total Repayment,${emiResult.totalPayment}\n\n")
            writer.append("Month,Opening Balance,Monthly EMI,Principal Paid,Interest Paid,Closing Balance\n")

            for (m in schedule.months) {
                writer.append("${m.monthIndex},${m.openingBalance},${m.emiAmount},${m.principalPaid},${m.interestPaid},${m.closingBalance}\n")
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share EMI Schedule (CSV)").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        csvFile
    }
}
