package com.example.bettingarbitragecalculator

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.bettingarbitragecalculator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindLiveCalculation()
        renderCalculation()
    }

    private fun bindLiveCalculation() {
        listOf(
            binding.backOddsInput,
            binding.layOddsInput,
            binding.backStakeInput,
            binding.layStakeInput
        ).forEach { editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    renderCalculation()
                }
            })
        }
    }

    private fun renderCalculation() {
        clearFieldErrors()

        when (
            val result = ArbitrageCalculator.calculate(
                backOddsInput = binding.backOddsInput.text?.toString().orEmpty(),
                layOddsInput = binding.layOddsInput.text?.toString().orEmpty(),
                backStakeInput = binding.backStakeInput.text?.toString().orEmpty(),
                layStakeInput = binding.layStakeInput.text?.toString().orEmpty()
            )
        ) {
            is CalculationResult.Success -> {
                binding.statusText.text = getString(R.string.balanced_profit_message)
                binding.statusText.setTextColor(ContextCompat.getColor(this, R.color.textPrimary))
                binding.statusText.background = ContextCompat.getDrawable(this, R.drawable.bg_status_neutral)

                binding.backStakeResultValue.text = ArbitrageCalculator.format(result.backStake)
                binding.layStakeResultValue.text = ArbitrageCalculator.format(result.layStake)
                binding.profitResultValue.text = ArbitrageCalculator.format(result.profit)

                val profitColor = when {
                    result.profit.signum() > 0 -> R.color.profitGreen
                    result.profit.signum() < 0 -> R.color.lossRed
                    else -> R.color.textPrimary
                }
                binding.profitResultValue.setTextColor(ContextCompat.getColor(this, profitColor))
            }

            is CalculationResult.Error -> {
                binding.statusText.text = result.message
                binding.statusText.setTextColor(ContextCompat.getColor(this, R.color.lossRed))
                binding.statusText.background = ContextCompat.getDrawable(this, R.drawable.bg_status_error)
                applyFieldErrors(result.message)
                resetResults()
            }
        }
    }

    private fun clearFieldErrors() {
        binding.backOddsLayout.error = null
        binding.layOddsLayout.error = null
        binding.backStakeLayout.error = null
        binding.layStakeLayout.error = null
    }

    private fun applyFieldErrors(message: String) {
        when (message) {
            "Enter all values" -> {
                if (binding.backOddsInput.text.isNullOrBlank()) {
                    binding.backOddsLayout.error = message
                }
                if (binding.layOddsInput.text.isNullOrBlank()) {
                    binding.layOddsLayout.error = message
                }
                if (binding.backStakeInput.text.isNullOrBlank() && binding.layStakeInput.text.isNullOrBlank()) {
                    binding.backStakeLayout.error = message
                    binding.layStakeLayout.error = message
                }
            }

            "Enter only one stake value" -> {
                binding.backStakeLayout.error = message
                binding.layStakeLayout.error = message
            }

            "Lay odds cannot be 0",
            "Invalid odds: back and lay odds must be greater than 1" -> {
                binding.backOddsLayout.error = message
                binding.layOddsLayout.error = message
            }

            "Stake cannot be negative" -> {
                if (!binding.backStakeInput.text.isNullOrBlank()) {
                    binding.backStakeLayout.error = message
                }
                if (!binding.layStakeInput.text.isNullOrBlank()) {
                    binding.layStakeLayout.error = message
                }
            }
        }
    }

    private fun resetResults() {
        binding.backStakeResultValue.text = getString(R.string.placeholder_value)
        binding.layStakeResultValue.text = getString(R.string.placeholder_value)
        binding.profitResultValue.text = getString(R.string.placeholder_value)
        binding.profitResultValue.setTextColor(ContextCompat.getColor(this, R.color.textPrimary))
    }
}
