package com.example.emicalculator.ui.home

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.emicalculator.R
import com.example.adslibrary.AdsLibrary
import com.example.adslibrary.banner.BannerAdComposable
import com.example.emicalculator.domain.model.TenureType
import com.example.emicalculator.ui.theme.EmiCalculatorTheme
import com.example.emicalculator.ui.theme.PrimaryBlue

@Composable
fun HomeScreen(
    viewModel: EmiViewModel,
    onNavigateToResult: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val principal by viewModel.principalInput.collectAsState()
    val interestRate by viewModel.interestRateInput.collectAsState()
    val tenure by viewModel.tenureInput.collectAsState()
    val tenureType by viewModel.tenureType.collectAsState()

    val principalError by viewModel.principalError.collectAsState()
    val interestRateError by viewModel.interestRateError.collectAsState()
    val tenureError by viewModel.tenureError.collectAsState()

    HomeScreenContent(
        principal = principal,
        interestRate = interestRate,
        tenure = tenure,
        tenureType = tenureType,
        principalError = principalError,
        interestRateError = interestRateError,
        tenureError = tenureError,
        onPrincipalChange = { viewModel.updatePrincipal(it) },
        onInterestRateChange = { viewModel.updateInterestRate(it) },
        onTenureChange = { viewModel.updateTenure(it) },
        onTenureTypeChange = { viewModel.setTenureType(it) },
        onQuickPrincipal = { viewModel.setQuickPrincipal(it) },
        onCalculate = {
            viewModel.calculateEmi { showInterstitial ->
                val activity = context as? Activity
                if (showInterstitial && activity != null) {
                    AdsLibrary.showInterstitialIfAllowed(activity) {
                        onNavigateToResult()
                    }
                } else {
                    onNavigateToResult()
                }
            }
        },
        onNavigateToSettings = onNavigateToSettings,
        showBannerAd = true
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreenContent(
    principal: String,
    interestRate: String,
    tenure: String,
    tenureType: TenureType,
    principalError: String?,
    interestRateError: String?,
    tenureError: String?,
    onPrincipalChange: (String) -> Unit,
    onInterestRateChange: (String) -> Unit,
    onTenureChange: (String) -> Unit,
    onTenureTypeChange: (TenureType) -> Unit,
    onQuickPrincipal: (Double) -> Unit,
    onCalculate: () -> Unit,
    onNavigateToSettings: () -> Unit,
    showBannerAd: Boolean = false
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (showBannerAd) {
                BannerAdComposable()
            }
        }
    ) { innerPadding ->
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {

                    Text(
                        text = stringResource(R.string.loan_amount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = principal,
                        onValueChange = onPrincipalChange,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Text(
                                "₹",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        },
                        trailingIcon = {
                            if (principal.isNotEmpty()) {
                                IconButton(onClick = { onPrincipalChange("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        isError = principalError != null,
                        supportingText = {
                            principalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { onQuickPrincipal(500000.0) },
                            label = { Text("₹ 5 Lakhs") }
                        )
                        SuggestionChip(
                            onClick = { onQuickPrincipal(1000000.0) },
                            label = { Text("₹ 10 Lakhs") }
                        )
                        SuggestionChip(
                            onClick = { onQuickPrincipal(2500000.0) },
                            label = { Text("₹ 25 Lakhs") }
                        )
                        SuggestionChip(
                            onClick = { onQuickPrincipal(5000000.0) },
                            label = { Text("₹ 50 Lakhs") }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.interest_rate),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = interestRate,
                        onValueChange = onInterestRateChange,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Percent,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        isError = interestRateError != null,
                        supportingText = {
                            interestRateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.tenure),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = tenureType == TenureType.YEARS,
                                onClick = { onTenureTypeChange(TenureType.YEARS) },
                                label = { Text(stringResource(R.string.years)) }
                            )
                            FilterChip(
                                selected = tenureType == TenureType.MONTHS,
                                onClick = { onTenureTypeChange(TenureType.MONTHS) },
                                label = { Text(stringResource(R.string.months)) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = tenure,
                        onValueChange = onTenureChange,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        isError = tenureError != null,
                        supportingText = {
                            tenureError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onCalculate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Calculate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.calculate_emi),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true, name = "Home Screen - Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Home Screen - Dark Mode")
@Composable
fun HomeScreenPreview() {
    EmiCalculatorTheme {
        HomeScreenContent(
            principal = "1000000",
            interestRate = "8.5",
            tenure = "5",
            tenureType = TenureType.YEARS,
            principalError = null,
            interestRateError = null,
            tenureError = null,
            onPrincipalChange = {},
            onInterestRateChange = {},
            onTenureChange = {},
            onTenureTypeChange = {},
            onQuickPrincipal = {},
            onCalculate = {},
            onNavigateToSettings = {},
            showBannerAd = false
        )
    }
}
