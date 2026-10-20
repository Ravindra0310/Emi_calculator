package com.example.emicalculator.domain.calculator

import com.example.emicalculator.domain.model.AmortizationMonth
import com.example.emicalculator.domain.model.AmortizationSchedule
import com.example.emicalculator.domain.model.EmiResult
import com.example.emicalculator.domain.model.LoanInput
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

object EmiCalculator {

    /**
     * Calculates the monthly EMI, total interest, and total payment.
     * Accurately supports both standard interest-bearing loans and 0% interest loans.
     */
    fun calculateEmi(input: LoanInput): EmiResult {
        val principal = input.principal
        val annualRate = input.annualInterestRate
        val tenureMonths = input.totalMonths

        require(principal > 0.0) { "Loan principal must be greater than 0" }
        require(annualRate >= 0.0) { "Annual interest rate cannot be negative" }
        require(tenureMonths > 0) { "Tenure must be at least 1 month" }

        val monthlyEmi: Double
        val totalPayment: Double
        val totalInterest: Double

        if (annualRate == 0.0) {
            // 0% interest loan calculation
            monthlyEmi = round(principal / tenureMonths)
            totalPayment = round(monthlyEmi * tenureMonths)
            totalInterest = 0.0
        } else {
            // Standard reducing balance formula: E = P * r * (1+r)^n / ((1+r)^n - 1)
            val monthlyRate = annualRate / (12.0 * 100.0)
            val factor = (1.0 + monthlyRate).pow(tenureMonths.toDouble())
            val rawEmi = principal * monthlyRate * factor / (factor - 1.0)

            monthlyEmi = round(rawEmi)
            totalPayment = round(monthlyEmi * tenureMonths)
            totalInterest = round(totalPayment - principal).coerceAtLeast(0.0)
        }

        val principalPercent = if (totalPayment > 0.0) {
            ((principal / totalPayment) * 100.0).toFloat().coerceIn(0f, 100f)
        } else 0f

        val interestPercent = (100f - principalPercent).coerceIn(0f, 100f)

        return EmiResult(
            principal = principal,
            annualInterestRate = annualRate,
            tenureMonths = tenureMonths,
            monthlyEmi = monthlyEmi,
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            principalPercentage = principalPercent,
            interestPercentage = interestPercent
        )
    }

    /**
     * Generates a month-by-month amortization schedule.
     * Tracks opening balance, monthly EMI, principal paid, interest paid, and closing balance.
     */
    fun generateSchedule(input: LoanInput, emiResult: EmiResult): AmortizationSchedule {
        val monthsList = mutableListOf<AmortizationMonth>()
        var openingBalance = input.principal
        val monthlyRate = if (input.annualInterestRate > 0.0) {
            input.annualInterestRate / (12.0 * 100.0)
        } else 0.0

        var totalPrincipalSum = 0.0
        var totalInterestSum = 0.0

        for (month in 1..emiResult.tenureMonths) {
            val interestForMonth = if (monthlyRate > 0.0) {
                round(openingBalance * monthlyRate)
            } else 0.0

            var principalForMonth = round(emiResult.monthlyEmi - interestForMonth)
            var closingBalance = round(openingBalance - principalForMonth)

            // Adjust final month for any penny rounding discrepancy
            if (month == emiResult.tenureMonths || closingBalance < 0.0) {
                principalForMonth = openingBalance
                closingBalance = 0.0
            }

            val actualEmiForMonth = round(principalForMonth + interestForMonth)
            totalPrincipalSum += principalForMonth
            totalInterestSum += interestForMonth

            monthsList.add(
                AmortizationMonth(
                    monthIndex = month,
                    openingBalance = openingBalance,
                    emiAmount = actualEmiForMonth,
                    principalPaid = principalForMonth,
                    interestPaid = interestForMonth,
                    closingBalance = closingBalance.coerceAtLeast(0.0)
                )
            )

            openingBalance = closingBalance
            if (openingBalance <= 0.0) break
        }

        return AmortizationSchedule(
            months = monthsList,
            totalPrincipalPaid = round(totalPrincipalSum),
            totalInterestPaid = round(totalInterestSum),
            totalAmountPaid = round(totalPrincipalSum + totalInterestSum)
        )
    }

    private fun round(value: Double): Double {
        return BigDecimal(value.toString())
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }
}
