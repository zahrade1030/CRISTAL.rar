
package com.example.ui.screens.result

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cv.ConfidenceLevel
import com.example.data.export.ExportManager
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisResultScreen(
    viewModel: MainViewModel,
    onNavigateToAdvanced: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val classification by viewModel.classificationResult.collectAsState()
    val pipelineOutput by viewModel.pipelineOutput.collectAsState()
    val isPersian = viewModel.language.collectAsState().value == "fa"
    val exportManager = remember { ExportManager(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isPersian) "نتیجه آنالیز" else "Analysis Result")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            classification?.let {
                                val pdf = exportManager.exportToPdf(it)
                                Toast.makeText(
                                    context,
                                    "PDF saved: ${pdf.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = "Export PDF"
                        )
                    }
                }
            )
        }
    ) { padding ->

        if (classification == null || pipelineOutput == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isPersian) "هیچ نتیجه‌ای موجود نیست"
                    else "No active analysis result"
                )
            }
            return@Scaffold
        }

        val result = classification!!
        val output = pipelineOutput!!

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Primary Classification Header
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.isUnknown) {
                            Color(0xFF7F1D1D)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPersian) {
                                    "شناسایی نزدیک‌ترین مرجع (بردار ۶۳ بعدی):"
                                } else {
                                    "Nearest Reference (63-dim Vector):"
                                },
                                fontSize = 12.sp,
                                color = Color.Gray
                            )

                            Badge(
                                containerColor = when (result.confidenceLevel) {
                                    ConfidenceLevel.VERY_HIGH -> Color(0xFF10B981)
                                    ConfidenceLevel.HIGH -> Color(0xFF0284C7)
                                    ConfidenceLevel.MEDIUM -> Color(0xFFF59E0B)
                                    ConfidenceLevel.LOW -> Color(0xFFEF4444)
                                }
                            ) {
                                Text(
                                    result.confidenceLevel.name,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }

                        Text(
                            text = result.nearestSampleName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        if (result.isUnknown) {
                            Text(
                                text = if (isPersian) {
                                    "نمونه ناشناخته یا خارج از آستانه شناسایی"
                                } else {
                                    "Unknown sample or outside classification threshold"
                                },
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${if (isPersian) "امتیاز شباهت" else "Match"}: " +
                                    "${String.format("%.1f", result.confidenceScorePct)}%",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )

                            Text(
                                text = "${if (isPersian) "زمان" else "Time"}: " +
                                    "${result.processingTimeMs} ms",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // 2. Captured and Processed Images
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (isPersian) "تصویر اصلی" else "Original Image",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Image(
                            bitmap = output.originalBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .height(140.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    Color.Gray,
                                    RoundedCornerShape(12.dp)
                                )
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (isPersian) "تصویر پردازش‌شده" else "Processed Image",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Image(
                            bitmap = output.processedBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .height(140.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(12.dp)
                                )
                        )
                    }
                }
            }

            // 3. Similarity Metrics
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (isPersian) {
                                "جدول مقایسه شباهت‌ها:"
                            } else {
                                "Similarity Metrics Breakdown:"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (result.allMatches.isEmpty()) {
                            Text(
                                text = if (isPersian) {
                                    "اطلاعات مقایسه‌ای موجود نیست"
                                } else {
                                    "No comparison data available"
                                },
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        } else {
                            for (match in result.allMatches) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1.5f)
                                    ) {
                                        Text(
                                            match.sampleName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Text(
                                            "ICUMSA: ${match.icumsaValue}",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            "Euc: ${String.format("%.1f", match.euclideanSimilarityPct)}%",
                                            fontSize = 11.sp
                                        )

                                        Text(
                                            "Cos: ${String.format("%.1f", match.cosineSimilarityPct)}%",
                                            fontSize = 11.sp
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            "${String.format("%.1f", match.finalSimilarityPct)}%",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (
                                                match.sampleId == result.nearestSampleId
                                            ) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    color = Color.DarkGray.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Advanced Analysis Button
            item {
                Button(
                    onClick = onNavigateToAdvanced,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Insights,
                        contentDescription = null
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        if (isPersian) {
                            "ورود به مرکز آنالیز پیشرفته"
                        } else {
                            "Open Advanced Analysis Center"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 5. Export Actions
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isPersian) {
                            "ذخیره و خروجی گزارش‌ها:"
                        } else {
                            "Export & Save Reports:"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val pdf = exportManager.exportToPdf(result)

                                Toast.makeText(
                                    context,
                                    if (isPersian) {
                                        "فایل PDF ذخیره شد"
                                    } else {
                                        "PDF saved"
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()

                                exportManager.openFile(
                                    pdf,
                                    "application/pdf"
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7)
                            )
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                if (isPersian) "گزارش PDF" else "PDF Report",
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                val excel = exportManager.exportToExcel(
                                    listOf(result)
                                )

                                Toast.makeText(
                                    context,
                                    if (isPersian) {
                                        "فایل خروجی ذخیره شد"
                                    } else {
                                        "Export file saved"
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()

                                exportManager.openFile(
                                    excel,
                                    "text/csv"
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981)
                            )
                        ) {
                            Icon(
                                Icons.Default.TableChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                if (isPersian) "خروجی اکسل" else "Excel Export",
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                val pdf = exportManager.exportToPdf(result)
                                exportManager.shareFile(
                                    pdf,
                                    "application/pdf"
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                if (isPersian) "اشتراک" else "Share",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
