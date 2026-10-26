package `in`.artham.fundamentals.ui

import android.app.Application
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.artham.fundamentals.artham
import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.Export
import `in`.artham.fundamentals.data.Txn
import `in`.artham.fundamentals.data.TxnKind
import `in`.artham.fundamentals.domain.PortfolioSummary
import `in`.artham.fundamentals.domain.PortfolioEngine
import `in`.artham.fundamentals.ui.components.EmptyState
import `in`.artham.fundamentals.ui.components.KeyValueRow
import `in`.artham.fundamentals.ui.components.MessageBanner
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.StatTile
import `in`.artham.fundamentals.ui.components.Tone
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PortfolioViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()

    var summary by mutableStateOf<PortfolioSummary?>(null)
    var name by mutableStateOf("Portfolio")
    var companies by mutableStateOf<List<Company>>(emptyList())
    var transactions by mutableStateOf<List<Txn>>(emptyList())
    var notice by mutableStateOf<String?>(null)

    fun load() {
        viewModelScope.launch {
            val portfolio = c.portfolioDao.ensureDefault()
            name = portfolio.name
            companies = c.companyDao.all()
            val txns = c.portfolioDao.transactions(portfolio.id)
            transactions = txns.reversed()
            val records = c.companyDao.allRecords().associateBy { it.company.id }
            summary = PortfolioEngine.compute(txns, records)
        }
    }

    fun add(companyId: Long, kind: TxnKind, units: Double, price: Double, dateText: String) {
        viewModelScope.launch {
            val portfolio = c.portfolioDao.ensureDefault()
            val millis = parseDate(dateText) ?: System.currentTimeMillis()
            val amount = when (kind) {
                TxnKind.BUY, TxnKind.SELL -> units * price
                else -> units * price
            }
            c.portfolioDao.addTransaction(
                Txn(
                    portfolioId = portfolio.id, companyId = companyId, kind = kind,
                    tradedOn = millis, units = units, price = price, amount = amount
                )
            )
            notice = "${kind.name.lowercase().replaceFirstChar { it.uppercase() }} recorded."
            load()
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            c.portfolioDao.deleteTransaction(id)
            load()
        }
    }

    fun exportCsv(context: android.content.Context) {
        val s = summary ?: return
        Export.share(context, "artham-holdings", "text/csv", Export.holdingsToCsv(s))
    }

    private fun parseDate(text: String): Long? = runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.isLenient = false
        format.parse(text)?.time
    }.getOrNull()
}

private val kinds = listOf(TxnKind.BUY, TxnKind.SELL, TxnKind.DIVIDEND, TxnKind.BONUS)

@Composable
fun PortfolioScreen(onOpenCompany: (Long) -> Unit) {
    val vm: PortfolioViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }
    var showAdd by mutableStateOf(false)

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Portfolio", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Cost basis is first-in-first-out, the convention Indian tax rules use for delivery trades.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add transaction")
                }
                IconButton(onClick = { vm.exportCsv(context) }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Export holdings")
                }
            }
        }

        if (vm.notice != null) {
            item { MessageBanner(vm.notice!!, Tone.Positive) { vm.notice = null } }
        }

        val summary = vm.summary
        if (summary == null || (summary.holdings.isEmpty() && vm.transactions.isEmpty())) {
            item {
                SectionCard {
                    EmptyState(
                        "No holdings yet",
                        "Record a buy to start tracking cost basis, realised and unrealised profit, and an annualised return.",
                        "Add the first transaction",
                        { showAdd = true }
                    )
                }
            }
        } else {
            item {
                SectionCard {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile("Invested", Fmt.cr(summary.invested), Modifier.weight(1f))
                        StatTile("Value", Fmt.cr(summary.marketValue), Modifier.weight(1f))
                        StatTile(
                            "Unrealised",
                            Fmt.cr(summary.unrealised),
                            Modifier.weight(1f),
                            tone = if (summary.unrealised >= 0) Tone.Positive else Tone.Negative
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        StatTile("Realised", Fmt.cr(summary.realised), Modifier.weight(1f),
                            tone = if (summary.realised >= 0) Tone.Positive else Tone.Negative)
                        StatTile("Dividends", Fmt.cr(summary.dividends), Modifier.weight(1f))
                        StatTile(
                            "XIRR",
                            summary.xirr?.let { Fmt.pct(it) } ?: "-",
                            Modifier.weight(1f),
                            tone = when {
                                summary.xirr == null -> Tone.Neutral
                                summary.xirr!! >= 0 -> Tone.Positive
                                else -> Tone.Negative
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    KeyValueRow("Total gain", Fmt.cr(summary.totalGain),
                        if (summary.totalGain >= 0) Tone.Positive else Tone.Negative)
                    KeyValueRow("Total gain %", summary.totalGainPercent?.let { Fmt.pct(it) } ?: "-")
                    if (summary.xirr == null) {
                        Text(
                            "XIRR needs at least one purchase and one later value. Add dated transactions to see it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (summary.holdings.isNotEmpty()) {
                item {
                    SectionCard(title = "Holdings", subtitle = "Tap a row to open the company") {
                        summary.holdings.forEach { holding ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenCompany(holding.company.id) }
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(holding.company.code, style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.width(6.dp))
                                        if (holding.company.isDemo) Pill("DEMO", Tone.Warning)
                                    }
                                    Text(
                                        "${Fmt.num(holding.units, 2)} units at ${Fmt.rupees(holding.averageCost, 2)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        Fmt.cr(holding.marketValue),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                                    )
                                    Text(
                                        holding.unrealisedPercent?.let { Fmt.pct(it) } ?: "-",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (holding.unrealised >= 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (summary.allocation.isNotEmpty()) {
                item {
                    SectionCard(title = "Allocation by sector") {
                        summary.allocation.forEach { slice ->
                            KeyValueRow(
                                "${slice.sector}  ${Fmt.pct(slice.weightPercent)}",
                                Fmt.cr(slice.value)
                            )
                        }
                    }
                }
            }

            if (vm.transactions.isNotEmpty()) {
                item {
                    SectionCard(title = "Ledger", subtitle = "${vm.transactions.size} entries") {
                        val format = SimpleDateFormat("dd MMM yyyy", Locale.US)
                        vm.transactions.take(20).forEach { txn ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${txn.kind.name}  ${Fmt.num(txn.units, 2)} @ ${Fmt.rupees(txn.price, 2)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        format.format(Date(txn.tradedOn)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { vm.remove(txn.id) }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                "Holdings are valued at the quote stored on this device. If no quote exists, the app falls back to a placeholder multiple and says so on the company page.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showAdd) {
        AddTransactionDialog(
            companies = vm.companies,
            onDismiss = { showAdd = false },
            onConfirm = { companyId, kind, units, price, date ->
                vm.add(companyId, kind, units, price, date)
                showAdd = false
            }
        )
    }
}

@Composable
private fun AddTransactionDialog(
    companies: List<Company>,
    onDismiss: () -> Unit,
    onConfirm: (Long, TxnKind, Double, Double, String) -> Unit
) {
    var query by mutableStateOf("")
    var selected by mutableStateOf<Company?>(null)
    var kind by mutableStateOf(TxnKind.BUY)
    var units by mutableStateOf("")
    var price by mutableStateOf("")
    var date by mutableStateOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    )

    val matches = if (query.isBlank()) companies.take(6)
    else companies.filter {
        it.code.contains(query, true) || it.name.contains(query, true)
    }.take(6)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a transaction") },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; selected = null },
                        label = { Text("Company") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selected == null) {
                    items(matches) { company ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = company; query = company.code }
                                .padding(vertical = 9.dp)
                        ) {
                            Column {
                                Text(company.code, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    company.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        kinds.forEach { option ->
                            FilterChip(
                                selected = kind == option,
                                onClick = { kind = option },
                                label = { Text(option.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = units,
                        onValueChange = { units = it },
                        label = { Text(if (kind == TxnKind.DIVIDEND) "Units held" else "Units") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text(if (kind == TxnKind.DIVIDEND) "Dividend per unit" else "Price per unit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (yyyy-mm-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val u = units.toDoubleOrNull()
                    val p = price.toDoubleOrNull()
                    val company = selected
                    if (company != null && u != null && u > 0 && p != null && p >= 0) {
                        onConfirm(company.id, kind, u, p, date)
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
