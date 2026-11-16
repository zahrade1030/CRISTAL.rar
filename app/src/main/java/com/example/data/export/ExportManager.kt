
package com.example.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.cv.ClassificationResult
import com.example.data.security.SecurityManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportManager(
    private val context: Context,
    private val securityManager: SecurityManager = SecurityManager()
) {

    private fun candidateDirectories(): List<File> {
        val directories = mutableListOf<File>()

        try {
            directories.add(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
            )
        } catch (_: Exception) {
        }

        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
            directories.add(it)
        }

        directories.add(context.cacheDir)

        return directories.distinctBy { it.absolutePath }
    }

    private fun saveBytes(
        fileName: String,
        content: ByteArray
    ): File {

        var lastError: Exception? = null

        for (directory in candidateDirectories()) {
            try {
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException(
                        "Cannot create directory: ${directory.absolutePath}"
                    )
                }

                if (!directory.isDirectory || !directory.canWrite()) {
                    continue
                }

                val file = File(directory, fileName)
                file.writeBytes(content)

                return file

            } catch (e: Exception) {
                lastError = e
            }
        }

        throw IllegalStateException(
            "Unable to save exported file.",
            lastError
        )
    }

    private fun csvEscape(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun number(
        value: Double,
        decimals: Int = 2
    ): String {
        return String.format(
            Locale.US,
            "%.${decimals}f",
            value
        )
    }

    /**
     * Exports the current classification result as a PDF.
     *
     * This report does not claim to calculate a laboratory
     * ICUMSA value. It reports classification and similarity.
     */
    fun exportToPdf(
        result: ClassificationResult,
        operatorName: String = "Lab Specialist"
    ): File {

        val pdf = PdfDocument()

        try {
            val titlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 19f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.rgb(2, 132, 199)
                textSize = 12f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 10f
                isAntiAlias = true
            }

            val dateText = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
            ).format(Date())

            val lines = mutableListOf<Pair<String, Paint>>()

            lines.add(
                "CRYSTAL VISION - ANALYSIS REPORT" to titlePaint
            )

            lines.add(
                "Islamic Azad University, Hamedan Branch" to subtitlePaint
            )

            lines.add(
                "Sugar Color Classification System" to bodyPaint
            )

            lines.add(
                "----------------------------------------" to bodyPaint
            )

            lines.add("Export Date: $dateText" to bodyPaint)
            lines.add("Operator: $operatorName" to bodyPaint)
            lines.add(
                "Processing Time: ${result.processingTimeMs} ms" to bodyPaint
            )

            lines.add(
                "CLASSIFICATION RESULT" to subtitlePaint
            )

            lines.add(
                "Nearest Reference: ${result.nearestSampleName}" to bodyPaint
            )

            lines.add(
                "Reference ID: ${result.nearestSampleId}" to bodyPaint
            )

            lines.add(
                "Reference ICUMSA Label: ${result.topMatch.icumsaValue}" to bodyPaint
            )

            lines.add(
                "Note: Reference label, not a measured ICUMSA result." to bodyPaint
            )

            lines.add(
                "Confidence: ${number(result.confidenceScorePct)}%" to bodyPaint
            )

            lines.add(
                "Confidence Level: ${result.confidenceLevel.name}" to bodyPaint
            )

            lines.add(
                "Unknown Sample: ${result.isUnknown}" to bodyPaint
            )

            lines.add(
                "Classifiable: ${result.isClassifiable}" to bodyPaint
            )

            lines.add(
                "Threshold: ${number(result.thresholdUsed)}%" to bodyPaint
            )

            lines.add(
                "Margin: ${number(result.marginPct)}%" to bodyPaint
            )

            lines.add(
                "SIMILARITY METRICS" to subtitlePaint
            )

            lines.add(
                "Euclidean Distance: ${number(result.topMatch.euclideanDistance, 4)}" to bodyPaint
            )

            lines.add(
                "Cosine Similarity: ${number(result.topMatch.cosineSimilarity, 4)}" to bodyPaint
            )

            lines.add(
                "Cosine Match: ${number(result.topMatch.cosineSimilarityPct)}%" to bodyPaint
            )

            lines.add(
                "Final Similarity: ${number(result.topMatch.finalSimilarityPct)}%" to bodyPaint
            )

            lines.add(
                "REFERENCE MATCHES" to subtitlePaint
            )

            for (match in result.allMatches.take(10)) {

                lines.add(
                    "${match.sampleName} | ICUMSA Label: ${match.icumsaValue}" to bodyPaint
                )

                lines.add(
                    "Euclidean: ${number(match.euclideanDistance, 4)} | " +
                        "Cosine: ${number(match.cosineSimilarityPct)}% | " +
                        "Match: ${number(match.finalSimilarityPct)}%" to bodyPaint
                )
            }

            lines.add(
                "----------------------------------------" to bodyPaint
            )

            lines.add(
                "This report is an image-based classification report." to bodyPaint
            )

            lines.add(
                "It is not a certified laboratory ICUMSA measurement." to bodyPaint
            )

            var pageNumber = 1

            var page = pdf.startPage(
                PdfDocument.PageInfo.Builder(
                    595,
                    842,
                    pageNumber
                ).create()
            )

            var canvas = page.canvas
            var y = 50f

            for ((text, paint) in lines) {

                if (y > 800f) {

                    pdf.finishPage(page)

                    pageNumber++

                    page = pdf.startPage(
                        PdfDocument.PageInfo.Builder(
                            595,
                            842,
                            pageNumber
                        ).create()
                    )

                    canvas = page.canvas
                    y = 50f
                }

                canvas.drawText(
                    text.take(100),
                    40f,
                    y,
                    paint
                )

                y += if (paint === titlePaint) {
                    28f
                } else if (paint === subtitlePaint) {
                    23f
                } else {
                    18f
                }
            }

            pdf.finishPage(page)

            val output = ByteArrayOutputStream()

            pdf.writeTo(output)

            val fileName =
                "Crystal_Analysis_${System.currentTimeMillis()}.pdf"

            return saveBytes(
                fileName,
                output.toByteArray()
            )

        } finally {
            pdf.close()
        }
    }

    /**
     * Builds CSV content using the current classification model.
     */
    private fun buildCsv(
        results: List<ClassificationResult>,
        persian: Boolean
    ): String {

        val dateText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.US
        ).format(Date())

        val headers = if (persian) {
            listOf(
                "زمان خروجی",
                "شناسه نمونه مرجع",
                "نام نمونه مرجع",
                "برچسب ICUMSA مرجع",
                "درصد اطمینان",
                "سطح اطمینان",
                "نمونه ناشناخته",
                "قابل طبقه‌بندی",
                "آستانه شباهت",
                "حاشیه اطمینان",
                "فاصله اقلیدسی",
                "شباهت کسینوسی",
                "درصد تطابق کسینوسی",
                "درصد شباهت نهایی",
                "زمان پردازش"
            )
        } else {
            listOf(
                "Export Date",
                "Reference ID",
                "Reference Sample",
                "Reference ICUMSA Label (Not Estimate)",
                "Confidence Score",
                "Confidence Level",
                "Unknown Sample",
                "Classifiable",
                "Threshold",
                "Margin",
                "Euclidean Distance",
                "Cosine Similarity",
                "Cosine Match Percent",
                "Final Similarity Percent",
                "Processing Time (ms)"
            )
        }

        val sb = StringBuilder()

        // UTF-8 BOM for Persian and Excel compatibility.
        sb.append("\uFEFF")

        sb.append(
            headers.joinToString(",") { csvEscape(it) }
        )

        sb.append("\n")

        for (result in results) {

            val values = listOf(
                dateText,
                result.nearestSampleId.toString(),
                result.nearestSampleName,
                result.topMatch.icumsaValue.toString(),
                number(result.confidenceScorePct),
                result.confidenceLevel.name,
                result.isUnknown.toString(),
                result.isClassifiable.toString(),
                number(result.thresholdUsed),
                number(result.marginPct),
                number(result.topMatch.euclideanDistance, 4),
                number(result.topMatch.cosineSimilarity, 4),
                number(result.topMatch.cosineSimilarityPct),
                number(result.topMatch.finalSimilarityPct),
                result.processingTimeMs.toString()
            )

            sb.append(
                values.joinToString(",") {
                    csvEscape(it)
                }
            )

            sb.append("\n")
        }

        return sb.toString()
    }

    /**
     * Exports result to English CSV.
     */
    fun exportToCsv(
        results: List<ClassificationResult>
    ): File {

        val csv = buildCsv(
            results = results,
            persian = false
        )

        val fileName =
            "Crystal_Analysis_${System.currentTimeMillis()}.csv"

        return saveBytes(
            fileName,
            csv.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Exports result to Persian CSV compatible with Excel.
     *
     * This is a CSV file, not a native XLSX workbook.
     */
    fun exportToExcel(
        results: List<ClassificationResult>
    ): File {

        val csv = buildCsv(
            results = results,
            persian = true
        )

        val fileName =
            "Crystal_Analysis_Excel_${System.currentTimeMillis()}.csv"

        return saveBytes(
            fileName,
            csv.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Escapes text for JSON strings.
     */
    private fun jsonEscape(value: String): String {

        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        return "\"$escaped\""
    }

    /**
     * Exports classification data as encrypted JSON.
     */
    fun exportToEncryptedJson(
        results: List<ClassificationResult>
    ): File {

        val sb = StringBuilder()

        sb.append("[\n")

        for ((index, result) in results.withIndex()) {

            sb.append("  {\n")

            sb.append(
                "    \"nearestSampleId\": ${result.nearestSampleId},\n"
            )

            sb.append(
                "    \"nearestSampleName\": ${jsonEscape(result.nearestSampleName)},\n"
            )

            sb.append(
                "    \"referenceIcumsaLabel\": ${result.topMatch.icumsaValue},\n"
            )

            sb.append(
                "    \"confidenceScorePct\": ${result.confidenceScorePct},\n"
            )

            sb.append(
                "    \"confidenceLevel\": ${jsonEscape(result.confidenceLevel.name)},\n"
            )

            sb.append(
                "    \"isUnknown\": ${result.isUnknown},\n"
            )

            sb.append(
                "    \"isClassifiable\": ${result.isClassifiable},\n"
            )

            sb.append(
                "    \"thresholdUsed\": ${result.thresholdUsed},\n"
            )

            sb.append(
                "    \"marginPct\": ${result.marginPct},\n"
            )

            sb.append(
                "    \"processingTimeMs\": ${result.processingTimeMs},\n"
            )

            sb.append(
                "    \"topMatch\": {\n"
            )

            sb.append(
                "      \"euclideanDistance\": ${result.topMatch.euclideanDistance},\n"
            )

            sb.append(
                "      \"cosineSimilarity\": ${result.topMatch.cosineSimilarity},\n"
            )

            sb.append(
                "      \"cosineSimilarityPct\": ${result.topMatch.cosineSimilarityPct},\n"
            )

            sb.append(
                "      \"finalSimilarityPct\": ${result.topMatch.finalSimilarityPct}\n"
            )

            sb.append("    },\n")

            sb.append("    \"allMatches\": [\n")

            for ((matchIndex, match) in result.allMatches.withIndex()) {

                sb.append("      {\n")

                sb.append(
                    "        \"sampleName\": ${jsonEscape(match.sampleName)},\n"
                )

                sb.append(
                    "        \"icumsaReferenceLabel\": ${match.icumsaValue},\n"
                )

                sb.append(
                    "        \"euclideanDistance\": ${match.euclideanDistance},\n"
                )

                sb.append(
                    "        \"cosineSimilarityPct\": ${match.cosineSimilarityPct},\n"
                )

                sb.append(
                    "        \"finalSimilarityPct\": ${match.finalSimilarityPct}\n"
                )

                sb.append("      }")

                if (matchIndex < result.allMatches.lastIndex) {
                    sb.append(",")
                }

                sb.append("\n")
            }

            sb.append("    ]\n")

            sb.append("  }")

            if (index < results.lastIndex) {
                sb.append(",")
            }

            sb.append("\n")
        }

        sb.append("]")

        val encrypted = securityManager.encryptString(
            sb.toString()
        )

        val fileName =
            "Crystal_Analysis_${System.currentTimeMillis()}.json.enc"

        return saveBytes(
            fileName,
            encrypted.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Opens an exported file in an external application.
     */
    fun openFile(
        file: File,
        mimeType: String
    ) {
        try {

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(
                Intent.ACTION_VIEW
            ).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Shares an exported file through Android's share chooser.
     */
    fun shareFile(
        file: File,
        mimeType: String
    ) {
        try {

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(
                Intent.ACTION_SEND
            ).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(
                intent,
                "اشتراک‌گذاری گزارش"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
