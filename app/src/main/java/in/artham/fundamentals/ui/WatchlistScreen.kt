package `in`.artham.fundamentals.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.artham.fundamentals.artham
import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.domain.Financials
import `in`.artham.fundamentals.domain.Ratios
import `in`.artham.fundamentals.ui.components.EmptyState
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Tone
import kotlinx.coroutines.launch

data class WatchRow(
    val company: Company,
    val price: String,
    val roe: String,
    val pe: String,
    val debt: String
)

class WatchlistViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()
    var rows by mutableStateOf<List<WatchRow>>(emptyList())
    var listName by mutableStateOf("Watchlist")
    var searchResults by mutableStateOf<List<Company>>(emptyList())
    var notice by mutableStateOf<String?>(null)

    fun load() {
        viewModelScope.launch {
            val list = c.watchlistDao.ensureDefault()
            listName = list.name
            rows = c.watchlistDao.members(list.id).map { company ->
                val f = Financials(c.companyDao.record(company))
                WatchRow(
                    company = company,
                    price = Fmt.rupees(f.quote?.price),
                    roe = Fmt.pct(f.latest?.let { Ratios.roe(it) }),
                    pe = Fmt.times(Ratios.pe(f)),
                    debt = Fmt.times(f.latest?.let { Ratios.debtToEquity(it) })
                )
            }
        }
    }

    fun search(q: String) {
        viewModelScope.launch { searchResults = c.companyDao.search(q, 12) }
    }

    fun add(companyId: Long) {
        viewModelScope.launch {
            val list = c.watchlistDao.ensureDefault()
            val added = c.watchlistDao.add(list.id, companyId)
            notice = if (added) "Added to $listName." else "That company is already on the list."
            load()
        }
    }

    fun remove(companyId: Long) {
        viewModelScope.launch {
            val list = c.watchlistDao.ensureDefault()
            c.watchlistDao.remove(list.id, companyId)
            load()
        }
    }
}

@Composable
fun WatchlistScreen(onOpenCompany: (Long) -> Unit) {
    val vm: WatchlistViewModel = viewModel()
    LaunchedEffect(Unit) { vm.load() }
    var showAdd by mutableStateOf(false)
    var query by mutableStateOf("")

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Watchlist", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "${vm.rows.size} companies tracked on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showAdd = true; vm.search("") }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add company")
                }
            }
        }
        if (vm.notice != null) {
            item {
                Text(
                    vm.notice!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (vm.rows.isEmpty()) {
            item {
                SectionCard {
                    EmptyState(
                        "The watchlist is empty",
                        "Add a company here, or send every match of a screen to the watchlist with the + button on the Screen tab.",
                        "Add a company",
                        { showAdd = true; vm.search("") }
                    )
                }
            }
        } else {
            item {
                SectionCard {
                    vm.rows.forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCompany(row.company.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.company.code, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.width(6.dp))
                                    if (row.company.isDemo) Pill("DEMO", Tone.Warning)
                                }
                                Text(
                                    row.company.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Text(
                                    "ROE ${row.roe}  •  P/E ${row.pe}  •  D/E ${row.debt}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    row.price,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                                )
                                IconButton(onClick = { vm.remove(row.company.id) }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add a company") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; vm.search(it) },
                        label = { Text("Search by name or code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.width(4.dp))
                    vm.searchResults.take(8).forEach { company ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.add(company.id)
                                    showAdd = false
                                    query = ""
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                    if (vm.searchResults.isEmpty()) {
                        Text(
                            "No match. Import a dataset from the Data tab first.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text("Close") } }
        )
    }
}
