package com.example.emicalculator.domain.model

enum class TenureType {
    YEARS,
    MONTHS
}

data class LoanInput(
    val principal: Double,
    val annualInterestRate: Double,
    val tenureValue: Int,
    val tenureType: TenureType = TenureType.YEARS
) {
    val totalMonths: Int
        get() = when (tenureType) {
            TenureType.YEARS -> tenureValue * 12
            TenureType.MONTHS -> tenureValue
        }
}

data class EmiResult(
    val principal: Double,
    val annualInterestRate: Double,
    val tenureMonths: Int,
    val monthlyEmi: Double,
    val totalInterest: Double,
    val totalPayment: Double,
    val principalPercentage: Float,
    val interestPercentage: Float
)

data class AmortizationMonth(
    val monthIndex: Int,
    val openingBalance: Double,
    val emiAmount: Double,
    val principalPaid: Double,
    val interestPaid: Double,
    val closingBalance: Double
)

data class AmortizationSchedule(
    val months: List<AmortizationMonth>,
    val totalPrincipalPaid: Double,
    val totalInterestPaid: Double,
    val totalAmountPaid: Double
)
