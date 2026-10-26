package `in`.artham.fundamentals.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.artham.fundamentals.artham
import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.CompanyRecord
import `in`.artham.fundamentals.domain.Financials
import `in`.artham.fundamentals.domain.MetricCatalog
import `in`.artham.fundamentals.ui.components.EmptyState
import `in`.artham.fundamentals.ui.components.SectionCard
import kotlinx.coroutines.launch

private val compareMetrics = listOf(
    "market_cap", "price", "pe", "pb", "sales", "net_profit", "opm", "roe", "roce",
    "sales_growth_5y", "profit_growth_5y", "debt_to_equity", "interest_coverage",
    "cfo_to_op", "ccc", "piotroski", "graham_number", "intrinsic_value", "dividend_yield",
    "promoter_holding"
)

class CompareViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()
    var companies by mutableStateOf<List<Company>>(emptyList())
    var selected by mutableStateOf<List<Company>>(emptyList())
    var records by mutableStateOf<Map<Long, CompanyRecord>>(emptyMap())

    fun load() {
        viewModelScope.launch {
            companies = c.companyDao.all()
            if (selected.isEmpty()) {
                selected = companies.take(3)
            }
            records = selected.associate { it.id to c.companyDao.record(it) }
        }
    }

    fun toggle(company: Company) {
        selected = if (selected.any { it.id == company.id }) {
            selected.filterNot { it.id == company.id }
        } else if (selected.size >= 4) {
            selected
        } else {
            selected + company
        }
        load()
    }
}

@Composable
fun CompareScreen() {
    val vm: CompareViewModel = viewModel()
    LaunchedEffect(Unit) { vm.load() }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Compare", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Up to four companies side by side, on the same 20 metrics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Pick companies", subtitle = "${vm.selected.size} of 4 selected") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vm.companies.take(60)) { company ->
                        FilterChip(
                            selected = vm.selected.any { it.id == company.id },
                            onClick = { vm.toggle(company) },
                            label = { Text(company.code, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        if (vm.selected.isEmpty()) {
            item { SectionCard { EmptyState("Nothing selected", "Choose up to four companies above.") } }
        } else {
            item {
                SectionCard {
                    val scroll = androidx.compose.foundation.rememberScrollState()
                    Column {
                        Row(
                            Modifier
                                .horizontalScroll(scroll)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                "Metric",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(140.dp).padding(horizontal = 8.dp)
                            )
                            vm.selected.forEach { company ->
                                Text(
                                    company.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(104.dp).padding(horizontal = 8.dp)
                                )
                            }
                        }
                        compareMetrics.forEach { id ->
                            val def = MetricCatalog.get(id) ?: return@forEach
                            Row(Modifier.horizontalScroll(scroll).padding(vertical = 6.dp)) {
                                Text(
                                    def.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(140.dp).padding(horizontal = 8.dp)
                                )
                                vm.selected.forEach { company ->
                                    val record = vm.records[company.id]
                                    val value = if (record != null) {
                                        runCatching { def.compute(Financials(record)) }.getOrNull()
                                    } else null
                                    Text(
                                        Fmt.metric(value, def.unit.key),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        maxLines = 1,
                                        modifier = Modifier.width(104.dp).padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "A dash means the company's stored data does not contain that figure. Comparisons are only as good as the imports behind them.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
