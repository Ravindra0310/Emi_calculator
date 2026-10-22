package com.example.emicalculator.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.emicalculator.ads.AdPolicy
import com.example.emicalculator.data.AppPreferencesRepository
import com.example.emicalculator.domain.calculator.EmiCalculator
import com.example.emicalculator.domain.model.AmortizationSchedule
import com.example.emicalculator.domain.model.EmiResult
import com.example.emicalculator.domain.model.LoanInput
import com.example.emicalculator.domain.model.TenureType
import com.example.emicalculator.util.CsvExporter
import com.example.emicalculator.util.PdfExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmiViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = AppPreferencesRepository.getInstance(application)

    // DataStore Flow Bindings
    val isOnboardingCompleted: StateFlow<Boolean> = preferencesRepository.isOnboardingCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val selectedLanguage: StateFlow<String> = preferencesRepository.selectedLanguage
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    val isScheduleUnlocked: StateFlow<Boolean> = preferencesRepository.isScheduleUnlocked
    val pdfExportEntitlements: StateFlow<Int> = preferencesRepository.pdfExportEntitlementCount

    // Input States
    private val _principalInput = MutableStateFlow("1000000")
    val principalInput: StateFlow<String> = _principalInput.asStateFlow()

    private val _interestRateInput = MutableStateFlow("8.5")
    val interestRateInput: StateFlow<String> = _interestRateInput.asStateFlow()

    private val _tenureInput = MutableStateFlow("5")
    val tenureInput: StateFlow<String> = _tenureInput.asStateFlow()

    private val _tenureType = MutableStateFlow(TenureType.YEARS)
    val tenureType: StateFlow<TenureType> = _tenureType.asStateFlow()

    // Validation Error States
    private val _principalError = MutableStateFlow<String?>(null)
    val principalError: StateFlow<String?> = _principalError.asStateFlow()

    private val _interestRateError = MutableStateFlow<String?>(null)
    val interestRateError: StateFlow<String?> = _interestRateError.asStateFlow()

    private val _tenureError = MutableStateFlow<String?>(null)
    val tenureError: StateFlow<String?> = _tenureError.asStateFlow()

    // Calculation Results
    private val _currentLoanInput = MutableStateFlow<LoanInput?>(null)
    val currentLoanInput: StateFlow<LoanInput?> = _currentLoanInput.asStateFlow()

    private val _emiResult = MutableStateFlow<EmiResult?>(null)
    val emiResult: StateFlow<EmiResult?> = _emiResult.asStateFlow()

    private val _amortizationSchedule = MutableStateFlow<AmortizationSchedule?>(null)
    val amortizationSchedule: StateFlow<AmortizationSchedule?> = _amortizationSchedule.asStateFlow()

    fun updatePrincipal(value: String) {
        _principalInput.value = value
        if (_principalError.value != null) _principalError.value = null
    }

    fun updateInterestRate(value: String) {
        _interestRateInput.value = value
        if (_interestRateError.value != null) _interestRateError.value = null
    }

    fun updateTenure(value: String) {
        _tenureInput.value = value
        if (_tenureError.value != null) _tenureError.value = null
    }

    fun setTenureType(type: TenureType) {
        _tenureType.value = type
    }

    fun setQuickPrincipal(amount: Double) {
        _principalInput.value = amount.toLong().toString()
        _principalError.value = null
    }

    fun calculateEmi(onSuccess: (showInterstitial: Boolean) -> Unit) {
        var isValid = true

        val principal = _principalInput.value.toDoubleOrNull()
        if (principal == null || principal <= 0.0) {
            _principalError.value = "Enter a valid loan amount (> 0)"
            isValid = false
        } else {
            _principalError.value = null
        }

        val rate = _interestRateInput.value.toDoubleOrNull()
        if (rate == null || rate < 0.0) {
            _interestRateError.value = "Enter a valid interest rate (≥ 0)"
            isValid = false
        } else {
            _interestRateError.value = null
        }

        val tenure = _tenureInput.value.toIntOrNull()
        if (tenure == null || tenure <= 0) {
            _tenureError.value = "Enter a valid tenure (> 0)"
            isValid = false
        } else {
            _tenureError.value = null
        }

        if (!isValid) return

        val input = LoanInput(
            principal = principal!!,
            annualInterestRate = rate!!,
            tenureValue = tenure!!,
            tenureType = _tenureType.value
        )

        val result = EmiCalculator.calculateEmi(input)
        val schedule = EmiCalculator.generateSchedule(input, result)

        _currentLoanInput.value = input
        _emiResult.value = result
        _amortizationSchedule.value = schedule

        // Coordinate ad policy: check if interstitial should trigger for this calculation
        val shouldShowAd = AdPolicy.onValidCalculation()
        onSuccess(shouldShowAd)
    }

    fun unlockSchedule() {
        preferencesRepository.unlockScheduleForSession()
    }

    fun grantPdfExport() {
        preferencesRepository.grantPdfExportEntitlement()
    }

    fun exportPdf(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val input = _currentLoanInput.value
        val result = _emiResult.value
        val schedule = _amortizationSchedule.value

        if (input == null || result == null || schedule == null) {
            onError("No active EMI calculation found to export.")
            return
        }

        // Consume entitlement only upon initiating export
        val hasEntitlement = preferencesRepository.consumePdfExportEntitlement()
        if (!hasEntitlement) {
            onError("PDF export entitlement missing. Please watch a short video ad to unlock.")
            return
        }

        PdfExporter.exportAndSharePdf(context, input, result, schedule)
            .onSuccess {
                onSuccess()
            }
            .onFailure { exception ->
                // Restore entitlement if generation failed
                preferencesRepository.grantPdfExportEntitlement()
                onError("Failed to generate PDF: ${exception.localizedMessage ?: "Unknown error"}")
            }
    }

    fun exportCsv(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val input = _currentLoanInput.value
        val result = _emiResult.value
        val schedule = _amortizationSchedule.value

        if (input == null || result == null || schedule == null) {
            onError("No active EMI calculation found to export.")
            return
        }

        CsvExporter.exportAndShareCsv(context, input, result, schedule)
            .onSuccess { onSuccess() }
            .onFailure { exception ->
                onError("Failed to generate CSV: ${exception.localizedMessage ?: "Unknown error"}")
            }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            preferencesRepository.setSelectedLanguage(languageCode)
        }
    }
}
