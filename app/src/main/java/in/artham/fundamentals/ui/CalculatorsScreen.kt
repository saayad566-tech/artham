package `in`.artham.fundamentals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.domain.Calculators
import `in`.artham.fundamentals.ui.components.KeyValueRow
import `in`.artham.fundamentals.ui.components.MessageBanner
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Tone

private data class CalcTab(val id: String, val label: String)

private val tabs = listOf(
    CalcTab("sip", "SIP"),
    CalcTab("stepup", "Step-up SIP"),
    CalcTab("goal", "Goal"),
    CalcTab("lumpsum", "Lumpsum"),
    CalcTab("swp", "Withdrawal"),
    CalcTab("tax", "Capital gains"),
    CalcTab("risk", "Position size"),
    CalcTab("retire", "Retirement")
)

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, suffix: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun Result(label: String, value: String, tone: Tone = Tone.Accent) {
    KeyValueRow(label, value, tone)
}

@Composable
fun CalculatorsScreen() {
    var selected by remember { mutableStateOf("sip") }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Calculators", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Planning arithmetic, computed locally. These are models with stated assumptions, not forecasts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(tabs) { tab ->
                    FilterChip(
                        selected = selected == tab.id,
                        onClick = { selected = tab.id },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        when (selected) {
            "sip" -> item { SipCalculator() }
            "stepup" -> item { StepUpCalculator() }
            "goal" -> item { GoalCalculator() }
            "lumpsum" -> item { LumpsumCalculator() }
            "swp" -> item { SwpCalculator() }
            "tax" -> item { TaxCalculator() }
            "risk" -> item { PositionSizeCalculator() }
            "retire" -> item { RetirementCalculator() }
        }

        item {
            MessageBanner(
                "Returns are assumed constant, which real markets never are. Use these to size a decision, not to predict an outcome.",
                Tone.Neutral
            )
        }
    }
}

@Composable
private fun SipCalculator() {
    var monthly by remember { mutableStateOf("10000") }
    var years by remember { mutableStateOf("15") }
    var rate by remember { mutableStateOf("12") }
    val m = monthly.toDoubleOrNull() ?: 0.0
    val y = years.toIntOrNull() ?: 0
    val r = rate.toDoubleOrNull() ?: 0.0
    val fv = Calculators.sipFutureValue(m, y, r)
    val invested = m * y * 12
    SectionCard(title = "SIP future value", subtitle = "Monthly contribution, compounded monthly") {
        NumberField("Monthly amount", monthly, { monthly = it }, "\u20B9")
        NumberField("Years", years, { years = it })
        NumberField("Expected annual return", rate, { rate = it }, "%")
        Spacer(Modifier.height(8.dp))
        Result("Invested", Fmt.cr(invested))
        Result("Estimated value", Fmt.cr(fv))
        Result("Gain", Fmt.cr(fv - invested), Tone.Positive)
    }
}

@Composable
private fun StepUpCalculator() {
    var monthly by remember { mutableStateOf("10000") }
    var step by remember { mutableStateOf("10") }
    var years by remember { mutableStateOf("15") }
    var rate by remember { mutableStateOf("12") }
    val fv = Calculators.stepUpSip(
        monthly.toDoubleOrNull() ?: 0.0, step.toDoubleOrNull() ?: 0.0,
        years.toIntOrNull() ?: 0, rate.toDoubleOrNull() ?: 0.0
    )
    SectionCard(title = "Step-up SIP", subtitle = "Contribution rises every year") {
        NumberField("Starting monthly amount", monthly, { monthly = it }, "\u20B9")
        NumberField("Annual increase", step, { step = it }, "%")
        NumberField("Years", years, { years = it })
        NumberField("Expected annual return", rate, { rate = it }, "%")
        Spacer(Modifier.height(8.dp))
        Result("Estimated value", Fmt.cr(fv))
    }
}

@Composable
private fun GoalCalculator() {
    var target by remember { mutableStateOf("10000000") }
    var years by remember { mutableStateOf("10") }
    var rate by remember { mutableStateOf("12") }
    val required = Calculators.sipRequired(
        target.toDoubleOrNull() ?: 0.0, years.toIntOrNull() ?: 0, rate.toDoubleOrNull() ?: 0.0
    )
    SectionCard(title = "Monthly amount for a goal") {
        NumberField("Target amount", target, { target = it }, "\u20B9")
        NumberField("Years available", years, { years = it })
        NumberField("Expected annual return", rate, { rate = it }, "%")
        Spacer(Modifier.height(8.dp))
        Result("Monthly contribution needed", required?.let { Fmt.rupees(it) } ?: "-")
    }
}

@Composable
private fun LumpsumCalculator() {
    var principal by remember { mutableStateOf("500000") }
    var years by remember { mutableStateOf("10") }
    var rate by remember { mutableStateOf("12") }
    val fv = Calculators.lumpsumFutureValue(
        principal.toDoubleOrNull() ?: 0.0, years.toIntOrNull() ?: 0, rate.toDoubleOrNull() ?: 0.0
    )
    val doubling = Calculators.yearsToDouble(rate.toDoubleOrNull() ?: 0.0)
    SectionCard(title = "Lumpsum growth") {
        NumberField("Amount invested", principal, { principal = it }, "\u20B9")
        NumberField("Years", years, { years = it })
        NumberField("Expected annual return", rate, { rate = it }, "%")
        Spacer(Modifier.height(8.dp))
        Result("Estimated value", Fmt.cr(fv))
        Result("Years to double at this rate", doubling?.let { String.format("%.1f", it) } ?: "-")
    }
}

@Composable
private fun SwpCalculator() {
    var corpus by remember { mutableStateOf("10000000") }
    var withdrawal by remember { mutableStateOf("60000") }
    var rate by remember { mutableStateOf("8") }
    val result = Calculators.swpDepletion(
        corpus.toDoubleOrNull() ?: 0.0, withdrawal.toDoubleOrNull() ?: 0.0, rate.toDoubleOrNull() ?: 0.0
    )
    SectionCard(title = "Systematic withdrawal", subtitle = "How long a corpus lasts") {
        NumberField("Corpus", corpus, { corpus = it }, "\u20B9")
        NumberField("Monthly withdrawal", withdrawal, { withdrawal = it }, "\u20B9")
        NumberField("Expected annual return", rate, { rate = it }, "%")
        Spacer(Modifier.height(8.dp))
        Result(
            "Corpus lasts",
            if (result.exhausted) "${result.monthsLasted / 12} years ${result.monthsLasted % 12} months"
            else "beyond 100 years",
            if (result.exhausted) Tone.Warning else Tone.Positive
        )
        if (!result.exhausted) Result("Balance at the end", Fmt.cr(result.finalBalance))
    }
}

@Composable
private fun TaxCalculator() {
    var buy by remember { mutableStateOf("100000") }
    var sell by remember { mutableStateOf("250000") }
    var months by remember { mutableStateOf("18") }
    val result = Calculators.equityCapitalGains(
        buy.toDoubleOrNull() ?: 0.0, sell.toDoubleOrNull() ?: 0.0, months.toIntOrNull() ?: 0
    )
    SectionCard(
        title = "Equity capital gains",
        subtitle = "Defaults: 20% short term, 12.5% long term above \u20B91.25 lakh"
    ) {
        NumberField("Purchase value", buy, { buy = it }, "\u20B9")
        NumberField("Sale value", sell, { sell = it }, "\u20B9")
        NumberField("Months held", months, { months = it })
        Spacer(Modifier.height(8.dp))
        Result("Gain", Fmt.rupees(result.gain), if (result.gain >= 0) Tone.Positive else Tone.Negative)
        Result("Treatment", if (result.longTerm) "Long term" else "Short term")
        Result("Taxable gain", Fmt.rupees(result.taxableGain))
        Result("Estimated tax", Fmt.rupees(result.tax), Tone.Warning)
        Result("Effective rate on the gain", Fmt.pct(result.effectiveRatePercent))
        Spacer(Modifier.height(6.dp))
        Text(result.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "Rates change with every budget. Confirm the current rules with a chartered accountant before acting; this is arithmetic, not tax advice.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PositionSizeCalculator() {
    var capital by remember { mutableStateOf("500000") }
    var risk by remember { mutableStateOf("1") }
    var entry by remember { mutableStateOf("1500") }
    var stop by remember { mutableStateOf("1400") }
    val units = Calculators.positionSize(
        capital.toDoubleOrNull() ?: 0.0, risk.toDoubleOrNull() ?: 0.0,
        entry.toDoubleOrNull() ?: 0.0, stop.toDoubleOrNull() ?: 0.0
    )
    val exposure = units?.times(entry.toDoubleOrNull() ?: 0.0)
    SectionCard(title = "Position size from risk", subtitle = "Fixes the loss first, then the quantity") {
        NumberField("Capital", capital, { capital = it }, "\u20B9")
        NumberField("Risk per trade", risk, { risk = it }, "%")
        NumberField("Entry price", entry, { entry = it }, "\u20B9")
        NumberField("Stop loss", stop, { stop = it }, "\u20B9")
        Spacer(Modifier.height(8.dp))
        Result("Units", units?.let { String.format("%.0f", it) } ?: "stop must be below entry", Tone.Accent)
        Result("Exposure", exposure?.let { Fmt.rupees(it) } ?: "-")
        Result("Maximum loss", Fmt.rupees((capital.toDoubleOrNull() ?: 0.0) * (risk.toDoubleOrNull() ?: 0.0) / 100.0), Tone.Negative)
    }
}

@Composable
private fun RetirementCalculator() {
    var expense by remember { mutableStateOf("80000") }
    var years by remember { mutableStateOf("20") }
    var inflation by remember { mutableStateOf("6") }
    var postReturn by remember { mutableStateOf("8") }
    val corpus = Calculators.retirementCorpus(
        expense.toDoubleOrNull() ?: 0.0, years.toIntOrNull() ?: 0,
        inflation.toDoubleOrNull() ?: 0.0, postReturn.toDoubleOrNull() ?: 0.0
    )
    SectionCard(title = "Retirement corpus", subtitle = "Corpus that keeps pace with inflation") {
        NumberField("Monthly expense today", expense, { expense = it }, "\u20B9")
        NumberField("Years to retirement", years, { years = it })
        NumberField("Inflation", inflation, { inflation = it }, "%")
        NumberField("Post-retirement return", postReturn, { postReturn = it }, "%")
        Spacer(Modifier.height(8.dp))
        Result("Corpus needed", corpus?.let { Fmt.croreCompact(it) } ?: "return must beat inflation", Tone.Accent)
        Spacer(Modifier.height(6.dp))
        Text(
            "Assumes withdrawals rise with inflation forever and the corpus is never exhausted. A conservative plan needs more than this.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
