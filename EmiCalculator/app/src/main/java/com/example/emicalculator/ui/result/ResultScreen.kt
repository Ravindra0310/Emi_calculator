package com.example.emicalculator.ui.result

import android.app.Activity
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.emicalculator.R
import com.example.adslibrary.AdsLibrary
import com.example.adslibrary.native_ad.NativeAdComposable
import com.example.emicalculator.ads.AdConstants
import com.example.emicalculator.domain.model.EmiResult
import com.example.emicalculator.ui.components.LoanBreakdownBar
import com.example.emicalculator.ui.home.EmiViewModel
import com.example.emicalculator.ui.theme.AccentTeal
import com.example.emicalculator.ui.theme.EmiCalculatorTheme
import com.example.emicalculator.ui.theme.PrimaryBlue
import com.example.emicalculator.util.CurrencyFormatter

@Composable
fun ResultScreen(
    viewModel: EmiViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSchedule: () -> Unit
) {
    val context = LocalContext.current
    val emiResult by viewModel.emiResult.collectAsState()
    val isScheduleUnlocked by viewModel.isScheduleUnlocked.collectAsState()
    val pdfEntitlements by viewModel.pdfExportEntitlements.collectAsState()

    ResultScreenContent(
        emiResult = emiResult,
        isScheduleUnlocked = isScheduleUnlocked,
        pdfEntitlements = pdfEntitlements,
        onNavigateBack = onNavigateBack,
        onUnlockSchedule = {
            if (isScheduleUnlocked) {
                onNavigateToSchedule()
            } else {
                val activity = context as? Activity
                if (activity != null) {
                    Toast.makeText(context, "Loading video ad to unlock schedule...", Toast.LENGTH_SHORT).show()
                    AdsLibrary.showRewardedIfAvailable(
                        activity = activity,
                        onRewardEarned = { _, _ ->
                            viewModel.unlockSchedule()
                            Toast.makeText(context, "Full schedule unlocked!", Toast.LENGTH_SHORT).show()
                        },
                        onAdClosed = { earned ->
                            if (earned) {
                                onNavigateToSchedule()
                            } else {
                                Toast.makeText(context, "Ad was not completed. Watch full ad to unlock.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                } else {
                    onNavigateToSchedule()
                }
            }
        },
        onExportPdf = {
            if (pdfEntitlements > 0) {
                viewModel.exportPdf(
                    context = context,
                    onSuccess = { Toast.makeText(context, "PDF generated successfully!", Toast.LENGTH_SHORT).show() },
                    onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                )
            } else {
                val activity = context as? Activity
                if (activity != null) {
                    Toast.makeText(context, "Watch a short ad to earn a PDF export pass", Toast.LENGTH_SHORT).show()
                    AdsLibrary.showRewardedIfAvailable(
                        activity = activity,
                        onRewardEarned = { _, _ -> viewModel.grantPdfExport() },
                        onAdClosed = { earned ->
                            if (earned) {
                                viewModel.exportPdf(
                                    context = context,
                                    onSuccess = { Toast.makeText(context, "PDF generated!", Toast.LENGTH_SHORT).show() },
                                    onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                                )
                            } else {
                                Toast.makeText(context, "Watch the complete ad to unlock export.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        },
        onExportCsv = {
            viewModel.exportCsv(
                context = context,
                onSuccess = { Toast.makeText(context, "CSV generated successfully!", Toast.LENGTH_SHORT).show() },
                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
            )
        },
        showNativeAd = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreenContent(
    emiResult: EmiResult?,
    isScheduleUnlocked: Boolean,
    pdfEntitlements: Int,
    onNavigateBack: () -> Unit,
    onUnlockSchedule: () -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    showNativeAd: Boolean = false
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculation Result", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (emiResult == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No calculation available", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        val res = emiResult

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.monthly_emi),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = CurrencyFormatter.formatInr(res.monthlyEmi),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "For ${res.tenureMonths} Months (${res.tenureMonths / 12} Years) at ${res.annualInterestRate}% p.a.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.principal),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyFormatter.formatInr(res.principal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.total_interest),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyFormatter.formatInr(res.totalInterest),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.total_amount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = CurrencyFormatter.formatInr(res.totalPayment),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LoanBreakdownBar(
                principal = res.principal,
                totalInterest = res.totalInterest,
                principalPercentage = res.principalPercentage,
                interestPercentage = res.interestPercentage
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onUnlockSchedule,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScheduleUnlocked) AccentTeal else PrimaryBlue
                )
            ) {
                Icon(
                    imageVector = if (isScheduleUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScheduleUnlocked) "View Amortization Schedule" else stringResource(R.string.unlock_schedule),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onExportPdf,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export PDF")
                }

                OutlinedButton(
                    onClick = onExportCsv,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export CSV")
                }
            }

            if (showNativeAd) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.ad_sponsored),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                NativeAdComposable(
                    adUnitId = AdConstants.NATIVE_RESULT_SCREEN,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Preview(showBackground = true, name = "Result Screen - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Result Screen - Dark")
@Composable
fun ResultScreenPreview() {
    EmiCalculatorTheme {
        ResultScreenContent(
            emiResult = EmiResult(
                principal = 1000000.0,
                annualInterestRate = 8.5,
                tenureMonths = 60,
                monthlyEmi = 20516.53,
                totalInterest = 230991.80,
                totalPayment = 1230991.80,
                principalPercentage = 81.2f,
                interestPercentage = 18.8f
            ),
            isScheduleUnlocked = false,
            pdfEntitlements = 0,
            onNavigateBack = {},
            onUnlockSchedule = {},
            onExportPdf = {},
            onExportCsv = {},
            showNativeAd = false
        )
    }
}
