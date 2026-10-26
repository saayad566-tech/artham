package `in`.artham.fundamentals.ui

import android.app.Application
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.artham.fundamentals.artham
import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.domain.BacktestResult
import `in`.artham.fundamentals.domain.Backtester
import `in`.artham.fundamentals.domain.Templates
import `in`.artham.fundamentals.ui.components.EmptyState
import `in`.artham.fundamentals.ui.components.LoadingRow
import `in`.artham.fundamentals.ui.components.MessageBanner
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Tone
import `in`.artham.fundamentals.ui.components.toneColor
import kotlinx.coroutines.launch

class BacktestViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()

    var query by mutableStateOf("roe > 18 AND debt_to_equity < 0.5")
    var fromYear by mutableStateOf("")
    var toYear by mutableStateOf("")
    var running by mutableStateOf(false)
    var result by mutableStateOf<BacktestResult?>(null)

    fun run() {
        if (running) return
        viewModelScope.launch {
            running = true
            val records = c.companyDao.allRecords()
            val hasPrices = c.portfolioDao.priceHistoryCount() > 0
            val lookup: ((Long, Long) -> Double?)? = if (hasPrices) {
                { companyId, millis -> c.portfolioDao.priceOnOrBefore(companyId, millis) }
            } else null
            result = Backtester.run(
                query = query,
                records = records,
                fromYear = fromYear.trim().toIntOrNull(),
                toYear = toYear.trim().toIntOrNull(),
                priceLookup = lookup
            )
            running = false
        }
    }
}

@Composable
fun BacktestScreen() {
    val vm: BacktestViewModel = viewModel()

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Backtest", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Replays a screen year by year using only the data that existed in that year.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Screen to replay") {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("Conditions") }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vm.fromYear,
                        onValueChange = { vm.fromYear = it },
                        label = { Text("From FY") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = vm.toYear,
                        onValueChange = { vm.toYear = it },
                        label = { Text("To FY") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.run() }, enabled = !vm.running) { Text("Run backtest") }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Templates.all) { template ->
                        FilterChip(
                            selected = vm.query == template.query,
                            onClick = { vm.query = template.query },
                            label = { Text(template.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        if (vm.running) item { LoadingRow("Replaying every fiscal year") }

        val result = vm.result
        if (result != null && !vm.running) {
            if (result.error != null) {
                item { MessageBanner(result.error, Tone.Negative) }
            }
            result.warnings.forEach { warning ->
                item { MessageBanner(warning, Tone.Warning) }
            }

            item {
                Row {
                    Text("${result.years.size} years replayed", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Pill("avg ${String.format("%.1f", result.averageMatches)} matches", Tone.Accent)
                }
            }

            item {
                SectionCard(title = "Matches per year", subtitle = "How many companies the screen would have picked") {
                    if (result.years.isEmpty()) {
                        EmptyState("Nothing to replay", "Add annual data first.")
                    } else {
                        val max = result.years.maxOfOrNull { it.matched.size }?.coerceAtLeast(1) ?: 1
                        val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        result.years.forEach { year ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    "FY${year.fyEnd}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.width(64.dp)
                                )
                                androidx.compose.foundation.layout.Box(
                                    Modifier
                                        .weight(1f)
                                        .height(14.dp)
                                        .padding(end = 8.dp)
                                ) {
                                    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                                        val fraction = year.matched.size.toFloat() / max
                                        drawRect(
                                            barColor,
                                            size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height)
                                        )
                                    }
                                }
                                Text(
                                    "${year.matched.size}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }
                }
            }

            if (result.fundamental.isNotEmpty()) {
                item {
                    SectionCard(title = "What happened to the businesses", subtitle = "Fundamental outcome, independent of share price") {
                        result.fundamental.forEach { outcome ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(outcome.label, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        outcome.detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    Fmt.metric(outcome.value, outcome.unit),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = if ((outcome.value ?: 0.0) >= 0) toneColor(Tone.Positive) else toneColor(Tone.Negative)
                                )
                            }
                        }
                    }
                }
            }

            if (result.price.isNotEmpty()) {
                item {
                    SectionCard(title = "What the share price did") {
                        result.price.forEach { outcome ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(outcome.label, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        outcome.detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    Fmt.metric(outcome.value, outcome.unit),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Which companies, by year") {
                    result.years.takeLast(3).forEach { year ->
                        Text(
                            "FY${year.fyEnd} (${year.matched.size} of ${year.universe})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            year.matched.take(24).joinToString(", ") { it.code }.ifBlank { "none" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
