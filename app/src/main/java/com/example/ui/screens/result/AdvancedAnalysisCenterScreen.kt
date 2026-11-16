
package com.example.ui.screens.result

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.export.ExportManager
import com.example.ui.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedAnalysisCenterScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val classificationState by
        viewModel.classificationResult.collectAsState()

    val pipelineOutputState by
        viewModel.pipelineOutput.collectAsState()

    val isDevMode by
        viewModel.isDeveloperMode.collectAsState()

    val language by
        viewModel.language.collectAsState()

    val isPersian = language == "fa"

    val exportManager = remember {
        ExportManager(context)
    }

    var selectedTab by remember {
        mutableStateOf(0)
    }

    val tabs = listOf(
        if (isPersian) "شباهت" else "Similarity",
        if (isPersian) "مقایسه نمونه‌ها" else "References",
        if (isPersian) "ویژگی‌ها و نمودارها" else "Features & Charts",
        if (isPersian) "کیفیت تصویر" else "Image Quality",
        if (isPersian) "معیارهای پردازش" else "Benchmark",
        if (isPersian) "ذخیره و خروجی" else "Save / Export"
    ) + if (isDevMode) {
        listOf(if (isPersian) "مراحل توسعه" else "Dev Stages")
    } else {
        emptyList()
    }

    val activeTab = selectedTab.coerceIn(0, tabs.lastIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isPersian) {
                            "مرکز آنالیز پیشرفته"
                        } else {
                            "Advanced Analysis Center"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->

        val result = classificationState
        val output = pipelineOutputState

        if (result == null || output == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isPersian) {
                        "اطلاعات آنالیز موجود نیست. ابتدا یک تصویر را تحلیل کنید."
                    } else {
                        "No analysis data. Analyze an image first."
                    }
                )
            }
        } else {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {

                ScrollableTabRow(
                    selectedTabIndex = activeTab
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = activeTab == index,
                            onClick = {
                                selectedTab = index
                            },
                            text = {
                                Text(
                                    title,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    when (activeTab) {

                        // 1. Similarity analysis
                        0 -> {

                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            if (isPersian) {
                                                "نتیجه طبقه‌بندی نمونه"
                                            } else {
                                                "Sample Classification Result"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(Modifier.height(12.dp))

                                        MetricRow(
                                            "Nearest Reference",
                                            result.nearestSampleName
                                        )

                                        MetricRow(
                                            "Reference ID",
                                            result.nearestSampleId.toString()
                                        )

                                        MetricRow(
                                            "Confidence Score",
                                            String.format(
                                                Locale.US,
                                                "%.2f%%",
                                                result.confidenceScorePct
                                            )
                                        )

                                        MetricRow(
                                            "Confidence Level",
                                            result.confidenceLevel.name
                                        )

                                        MetricRow(
                                            "Classification Status",
                                            if (result.isUnknown) {
                                                "Unknown Sample"
                                            } else {
                                                "Matched"
                                            }
                                        )

                                        MetricRow(
                                            "Classifiable",
                                            result.isClassifiable.toString()
                                        )

                                        MetricRow(
                                            "Threshold",
                                            String.format(
                                                Locale.US,
                                                "%.2f%%",
                                                result.thresholdUsed
                                            )
                                        )

                                        MetricRow(
                                            "Processing Time",
                                            "${result.processingTimeMs} ms"
                                        )
                                    }
                                }
                            }

                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            "Similarity Metrics (63-dim Vector)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(Modifier.height(12.dp))

                                        MetricRow(
                                            "Euclidean Distance",
                                            String.format(
                                                Locale.US,
                                                "%.4f",
                                                result.topMatch.euclideanDistance
                                            )
                                        )

                                        MetricRow(
                                            "Euclidean Similarity",
                                            String.format(
                                                Locale.US,
                                                "%.2f%%",
                                                result.topMatch.euclideanSimilarityPct
                                            )
                                        )

                                        MetricRow(
                                            "Cosine Similarity",
                                            String.format(
                                                Locale.US,
                                                "%.4f",
                                                result.topMatch.cosineSimilarity
                                            )
                                        )

                                        MetricRow(
                                            "Cosine Match",
                                            String.format(
                                                Locale.US,
                                                "%.2f%%",
                                                result.topMatch.cosineSimilarityPct
                                            )
                                        )

                                        MetricRow(
                                            "Bhattacharyya Distance",
                                            String.format(
                                                Locale.US,
                                                "%.4f",
                                                result.topMatch.bhattacharyyaDistance
                                            )
                                        )

                                        MetricRow(
                                            "Final Similarity",
                                            String.format(
                                                Locale.US,
                                                "%.2f%%",
                                                result.topMatch.finalSimilarityPct
                                            )
                                        )
                                    }
                                }
                            }

                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Text(
                                        if (isPersian) {
                                            "توجه: این نتیجه، نزدیک‌ترین نمونه مرجع را از نظر ویژگی‌های تصویری مشخص می‌کند. مقدار عددی ICUMSA از این نتیجه به‌تنهایی قابل استخراج یا تأیید آزمایشگاهی نیست."
                                        } else {
                                            "Note: This result identifies the closest reference by image features. It does not provide a certified numerical ICUMSA measurement."
                                        },
                                        modifier = Modifier.padding(16.dp),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // 2. Reference comparison
                        1 -> {

                            if (result.allMatches.isEmpty()) {
                                item {
                                    Text("No reference matches available.")
                                }
                            }

                            items(result.allMatches) { match ->

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Text(
                                            match.sampleName,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(Modifier.height(8.dp))

                                        MetricRow(
                                            "Sample ID",
                                            match.sampleId.toString()
                                        )

                                        MetricRow(
                                            "Reference ICUMSA Label",
                                            match.icumsaValue.toString()
                                        )

                                        MetricRow(
                                            "Euclidean Distance",
                                            String.format(
                                                Locale.US,
                                                "%.4f",
                                                match.euclideanDistance
                                            )
                                        )

                                        MetricRow(
                                            "Euclidean Similarity",
                                            String.format(
                                                Locale.US,
                                                "%.2f%%",
                                                match.euclideanSimilarityPct
                                            )
                                        )

                                        MetricRow(
                                            "Cosine Similarity",
                                            String.format(
                                                Locale.US,
                                                "%.2f%%",
                                                match.cosineSimilarityPct
                                            )
                                        )

                                        MetricRow(
                                            "Bhattacharyya Match",
                                            String.format(
                                                Locale.US,
                                                "%.2f%%",
                                                match.bhattacharyyaSimilarityPct
                                            )
                                        )

                                        MetricRow(
                                            "Final Similarity",
                                            String.format(
                                                Locale.US,
                                                "%.2f%%",
                                                match.finalSimilarityPct
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Feature visualization
                        2 -> {

                            item {
                                val features = output.completeFeatureSet
                                val histogram = features.colorHistogram

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            "Color Histogram",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )

                                        Spacer(Modifier.height(12.dp))

                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(130.dp)
                                        ) {
                                            if (histogram.isNotEmpty()) {
                                                val maxValue =
                                                    histogram.maxOrNull()
                                                        ?.takeIf { it > 0f }
                                                        ?: 1f

                                                val barWidth =
                                                    size.width / histogram.size

                                                for (i in histogram.indices) {
                                                    val barHeight =
                                                        (histogram[i] / maxValue) *
                                                            size.height

                                                    drawRect(
                                                        color = Color(0xFF38BDF8),
                                                        topLeft = Offset(
                                                            i * barWidth,
                                                            size.height - barHeight
                                                        ),
                                                        size = Size(
                                                            barWidth * 0.8f,
                                                            barHeight
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(16.dp))

                                        Text(
                                            "CIELAB Means",
                                            fontWeight = FontWeight.Bold
                                        )

                                        val lab = features.labMeans

                                        MetricRow(
                                            "L*",
                                            String.format(
                                                Locale.US,
                                                "%.2f",
                                                lab.first
                                            )
                                        )

                                        MetricRow(
                                            "a*",
                                            String.format(
                                                Locale.US,
                                                "%.2f",
                                                lab.second
                                            )
                                        )

                                        MetricRow(
                                            "b*",
                                            String.format(
                                                Locale.US,
                                                "%.2f",
                                                lab.third
                                            )
                                        )

                                        Spacer(Modifier.height(12.dp))

                                        Text(
                                            "HSV Means",
                                            fontWeight = FontWeight.Bold
                                        )

                                        val hsv = features.hsvMeans

                                        MetricRow(
                                            "H",
                                            String.format(
                                                Locale.US,
                                                "%.2f°",
                                                hsv.first
                                            )
                                        )

                                        MetricRow(
                                            "S",
                                            String.format(
                                                Locale.US,
                                                "%.3f",
                                                hsv.second
                                            )
                                        )

                                        MetricRow(
                                            "V",
                                            String.format(
                                                Locale.US,
                                                "%.3f",
                                                hsv.third
                                            )
                                        )

                                        Spacer(Modifier.height(12.dp))

                                        MetricRow(
                                            "Feature Vector Size",
                                            features.concatenatedVector.size.toString()
                                        )

                                        MetricRow(
                                            "Normalized Vector Size",
                                            output.normalizedVector.size.toString()
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Image quality
                        3 -> {

                            item {
                                val quality = output.qualityResult

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            "Image Quality Assessment",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(Modifier.height(12.dp))

                                        MetricRow(
                                            "Overall Quality",
                                            "${quality.overallScore.toInt()}%"
                                        )

                                        MetricRow(
                                            "Blur Score",
                                            quality.blurScore.toInt().toString()
                                        )

                                        MetricRow(
                                            "Brightness Score",
                                            quality.brightnessScore.toInt().toString()
                                        )

                                        MetricRow(
                                            "Contrast Score",
                                            quality.contrastScore.toInt().toString()
                                        )

                                        MetricRow(
                                            "Center Alignment",
                                            "${(quality.centerAlignmentScore * 100).toInt()}%"
                                        )

                                        MetricRow(
                                            "Quality Passed",
                                            quality.isPassed.toString()
                                        )

                                        Spacer(Modifier.height(12.dp))

                                        Text(
                                            if (quality.isPassed) {
                                                "Image quality validation passed."
                                            } else {
                                                quality.rejectionReason
                                                    ?: "Image quality validation failed."
                                            },
                                            color = if (quality.isPassed) {
                                                Color(0xFF10B981)
                                            } else {
                                                Color(0xFFEF4444)
                                            },
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Benchmark
                        4 -> {

                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            "Processing Benchmark",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )

                                        Spacer(Modifier.height(12.dp))

                                        MetricRow(
                                            "Feature Extraction Time",
                                            "${output.completeFeatureSet.extractionTimeMs} ms"
                                        )

                                        MetricRow(
                                            "Total Processing Time",
                                            "${result.processingTimeMs} ms"
                                        )

                                        MetricRow(
                                            "Feature Vector Dimensions",
                                            output.normalizedVector.size.toString()
                                        )

                                        MetricRow(
                                            "Reference Matches",
                                            result.allMatches.size.toString()
                                        )

                                        MetricRow(
                                            "Classification Threshold",
                                            "${result.thresholdUsed}%"
                                        )
                                    }
                                }
                            }
                        }

                        // 6. Save and export
                        5 -> {

                            item {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {

                                    Button(
                                        onClick = {
                                            try {
                                                val pdf =
                                                    exportManager.exportToPdf(result)

                                                Toast.makeText(
                                                    context,
                                                    "PDF: ${pdf.name}",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                exportManager.openFile(
                                                    pdf,
                                                    "application/pdf"
                                                )
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "PDF export failed: ${e.localizedMessage}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.PictureAsPdf,
                                            contentDescription = null
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Export PDF Report")
                                    }

                                    Button(
                                        onClick = {
                                            try {
                                                val csv =
                                                    exportManager.exportToExcel(
                                                        listOf(result)
                                                    )

                                                Toast.makeText(
                                                    context,
                                                    "CSV: ${csv.name}",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                exportManager.openFile(
                                                    csv,
                                                    "text/csv"
                                                )
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "CSV export failed: ${e.localizedMessage}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF059669)
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.TableChart,
                                            contentDescription = null
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Export CSV for Excel")
                                    }

                                    Button(
                                        onClick = {
                                            try {
                                                val encrypted =
                                                    exportManager.exportToEncryptedJson(
                                                        listOf(result)
                                                    )

                                                Toast.makeText(
                                                    context,
                                                    "Encrypted JSON: ${encrypted.name}",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                exportManager.shareFile(
                                                    encrypted,
                                                    "application/octet-stream"
                                                )
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "Encrypted export failed: ${e.localizedMessage}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF6B21A8)
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Export Encrypted JSON")
                                    }
                                }
                            }
                        }

                        // 7. Developer stages
                        6 -> {

                            if (isDevMode) {
                                items(output.stages) { stage ->

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Text(
                                                stage.stageName,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            Text(
                                                stage.description,
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )

                                            Spacer(Modifier.height(8.dp))

                                            Image(
                                                bitmap = stage.bitmap.asImageBitmap(),
                                                contentDescription = stage.stageName,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(180.dp)
                                                    .clip(
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        Color.Gray,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
