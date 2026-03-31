package com.example.bettingarbitragecalculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat

sealed interface CalculationResult {
    data class Success(
        val backOdds: BigDecimal,
        val layOdds: BigDecimal,
        val backStake: BigDecimal,
        val layStake: BigDecimal,
        val profit: BigDecimal
    ) : CalculationResult

    data class Error(val message: String) : CalculationResult
}

object ArbitrageCalculator {
    private val mathContext = MathContext(16, RoundingMode.HALF_UP)
    private const val divideScale = 10
    private val numberFormat = DecimalFormat("#,##0.00####").apply {
        roundingMode = RoundingMode.HALF_UP
    }

    fun calculate(
        backOddsInput: String,
        layOddsInput: String,
        backStakeInput: String,
        layStakeInput: String
    ): CalculationResult {
        return runCatching {
            val backOddsText = backOddsInput.trim()
            val layOddsText = layOddsInput.trim()
            val backStakeText = backStakeInput.trim()
            val layStakeText = layStakeInput.trim()

            val hasBackStake = backStakeText.isNotEmpty()
            val hasLayStake = layStakeText.isNotEmpty()

            if (backOddsText.isEmpty() || layOddsText.isEmpty() || (!hasBackStake && !hasLayStake)) {
                return CalculationResult.Error("Enter all values")
            }

            if (hasBackStake && hasLayStake) {
                return CalculationResult.Error("Enter only one stake value")
            }

            val backOdds = parseNumber(backOddsText)
                ?: return CalculationResult.Error("Enter valid numbers")
            val layOdds = parseNumber(layOddsText)
                ?: return CalculationResult.Error("Enter valid numbers")

            if (layOdds.compareTo(BigDecimal.ZERO) == 0) {
                return CalculationResult.Error("Lay odds cannot be 0")
            }

            if (backOdds <= BigDecimal.ONE || layOdds <= BigDecimal.ONE) {
                return CalculationResult.Error("Invalid odds: back and lay odds must be greater than 1")
            }

            val backStake = if (hasBackStake) {
                parseNumber(backStakeText) ?: return CalculationResult.Error("Enter valid numbers")
            } else {
                null
            }

            val layStake = if (hasLayStake) {
                parseNumber(layStakeText) ?: return CalculationResult.Error("Enter valid numbers")
            } else {
                null
            }

            if ((backStake != null && backStake < BigDecimal.ZERO) || (layStake != null && layStake < BigDecimal.ZERO)) {
                return CalculationResult.Error("Stake cannot be negative")
            }

            if (backStake != null) {
                val computedLayStake = backStake
                    .multiply(backOdds, mathContext)
                    .divide(layOdds, divideScale, RoundingMode.HALF_UP)
                val profit = computedLayStake.subtract(backStake, mathContext)
                CalculationResult.Success(backOdds, layOdds, backStake, computedLayStake, profit)
            } else {
                val providedLayStake = layStake ?: return CalculationResult.Error("Enter exactly one stake value")
                val computedBackStake = providedLayStake
                    .multiply(layOdds, mathContext)
                    .divide(backOdds, divideScale, RoundingMode.HALF_UP)
                val profit = providedLayStake.subtract(computedBackStake, mathContext)
                CalculationResult.Success(backOdds, layOdds, computedBackStake, providedLayStake, profit)
            }
        }.getOrElse {
            CalculationResult.Error("Unable to calculate. Check your values.")
        }
    }

    fun format(value: BigDecimal): String = numberFormat.format(value)

    private fun parseNumber(value: String): BigDecimal? {
        val normalized = value.replace(",", ".")
        return normalized.toBigDecimalOrNull()
    }
}
