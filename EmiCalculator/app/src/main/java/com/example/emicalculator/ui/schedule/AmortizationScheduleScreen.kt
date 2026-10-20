package com.example.emicalculator.ui.schedule

import android.app.Activity
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.emicalculator.R
import com.example.emicalculator.ads.AdManager
import com.example.emicalculator.ads.AdPolicy
import com.example.emicalculator.domain.model.AmortizationMonth
import com.example.emicalculator.domain.model.AmortizationSchedule
import com.example.emicalculator.ui.home.EmiViewModel
import com.example.emicalculator.ui.theme.AccentAmber
import com.example.emicalculator.ui.theme.EmiCalculatorTheme
import com.example.emicalculator.ui.theme.PrimaryBlue
import com.example.emicalculator.util.CurrencyFormatter

@Composable
fun AmortizationScheduleScreen(
    viewModel: EmiViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val schedule by viewModel.amortizationSchedule.collectAsState()
    val pdfEntitlements by viewModel.pdfExportEntitlements.collectAsState()

    fun handleBack() {
        val activity = context as? Activity
        if (activity != null && AdPolicy.canShowScheduleReturnInterstitial()) {
            AdManager.getInstance(context).showInterstitialIfAllowed(activity) {
                onNavigateBack()
            }
        } else {
            onNavigateBack()
        }
    }

    BackHandler {
        handleBack()
    }

    AmortizationScheduleContent(
        schedule = schedule,
        onNavigateBack = { handleBack() },
        onExportPdf = {
            if (pdfEntitlements > 0) {
                viewModel.exportPdf(
                    context = context,
                    onSuccess = { Toast.makeText(context, "PDF Exported!", Toast.LENGTH_SHORT).show() },
                    onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                )
            } else {
                val activity = context as? Activity
                if (activity != null) {
                    Toast.makeText(context, "Watch video to export PDF statement", Toast.LENGTH_SHORT).show()
                    AdManager.getInstance(context).showRewardedPdf(
                        activity = activity,
                        onRewardEarned = { viewModel.grantPdfExport() },
                        onDismissed = { earned ->
                            if (earned) {
                                viewModel.exportPdf(
                                    context = context,
                                    onSuccess = { Toast.makeText(context, "PDF Exported!", Toast.LENGTH_SHORT).show() },
                                    onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                                )
                            }
                        }
                    )
                }
            }
        },
        onExportCsv = {
            viewModel.exportCsv(
                context = context,
                onSuccess = { Toast.makeText(context, "CSV Exported!", Toast.LENGTH_SHORT).show() },
                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmortizationScheduleContent(
    schedule: AmortizationSchedule?,
    onNavigateBack: () -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.amortization_schedule), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onExportPdf) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    IconButton(onClick = onExportCsv) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Export CSV")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (schedule == null || schedule.months.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No schedule available", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(schedule.months, key = { it.monthIndex }) { item ->
                AmortizationMonthCard(month = item)
            }
        }
    }
}

@Composable
fun AmortizationMonthCard(month: AmortizationMonth) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${month.monthIndex}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Month ${month.monthIndex}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = CurrencyFormatter.formatInr(month.emiAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Principal Paid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatInr(month.principalPaid),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Interest Paid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatInr(month.interestPaid),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AccentAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Remaining Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatInr(month.closingBalance),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Schedule Screen - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Schedule Screen - Dark")
@Composable
fun AmortizationSchedulePreview() {
    EmiCalculatorTheme {
        AmortizationScheduleContent(
            schedule = AmortizationSchedule(
                months = listOf(
                    AmortizationMonth(1, 1000000.0, 20516.53, 13433.20, 7083.33, 986566.80),
                    AmortizationMonth(2, 986566.80, 20516.53, 13528.34, 6988.19, 973038.46),
                    AmortizationMonth(3, 973038.46, 20516.53, 13624.16, 6892.37, 959414.30)
                ),
                totalPrincipalPaid = 1000000.0,
                totalInterestPaid = 230991.80,
                totalAmountPaid = 1230991.80
            ),
            onNavigateBack = {},
            onExportPdf = {},
            onExportCsv = {}
        )
    }
}
