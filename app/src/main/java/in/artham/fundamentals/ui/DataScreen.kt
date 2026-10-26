package `in`.artham.fundamentals.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.artham.fundamentals.artham
import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.data.DatasetStats
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.DemoData
import `in`.artham.fundamentals.data.SeedSymbols
import `in`.artham.fundamentals.data.ImportBatch
import `in`.artham.fundamentals.ui.components.EmptyState
import `in`.artham.fundamentals.ui.components.LoadingRow
import `in`.artham.fundamentals.ui.components.MessageBanner
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Tone
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.displayName(uri: Uri): String {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            val name = cursor.getString(index)
            if (!name.isNullOrBlank()) return name
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "import.bin"
}

class DataViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()
    var stats by mutableStateOf(DatasetStats(0, 0, 0, 0, 0L, 0))
    var batches by mutableStateOf<List<ImportBatch>>(emptyList())
    var working by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    var noticeTone by mutableStateOf(Tone.Positive)
    var warnings by mutableStateOf<List<String>>(emptyList())
    var logs by mutableStateOf<List<String>>(emptyList())
    var syncing by mutableStateOf(false)
    var syncStage by mutableStateOf("")
    var syncDone by mutableStateOf(0)
    var syncTotal by mutableStateOf(0)
    var syncFailed by mutableStateOf(0)
    var barCount by mutableStateOf(0)
    var companiesWithBars by mutableStateOf(0)
    private var cancelRequested = false

    fun refreshSyncStats() {
        barCount = c.syncDao.totalBars()
        companiesWithBars = c.syncDao.companiesWithBars()
    }

    fun cancelSync() {
        cancelRequested = true
        syncStage = "Stopping after the current company"
    }

    fun syncUniverse() {
        if (syncing) return
        viewModelScope.launch {
            syncing = true
            syncStage = "Downloading the exchange master file"
            syncDone = 0; syncTotal = 0; syncFailed = 0
            val outcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                c.syncService.syncUniverse()
            }
            outcome.onSuccess { count ->
                notice = "Universe synced: $count listed companies are now searchable."
                noticeTone = Tone.Positive
            }.onFailure {
                notice = "Universe sync failed: ${it.message}"
                noticeTone = Tone.Negative
            }
            syncing = false
            syncStage = ""
            refresh()
            refreshSyncStats()
        }
    }

    private fun startBatch(companies: List<Company>, label: String) {
        if (syncing || companies.isEmpty()) return
        viewModelScope.launch {
            syncing = true
            cancelRequested = false
            syncTotal = companies.size
            syncDone = 0
            syncFailed = 0
            syncStage = label
            val summary = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                c.syncService.syncMany(
                    companies = companies,
                    withFundamentals = true,
                    onProgress = { progress ->
                        syncDone = progress.done
                        syncTotal = progress.total
                        syncFailed = progress.failed
                        syncStage = if (progress.stage == "Done") "Finished" else "Syncing ${progress.detail}"
                    },
                    isCancelled = { cancelRequested }
                )
            }
            notice = "Sync finished: ${summary.ok} companies updated, ${summary.failed} failed, ${summary.bars} price bars stored."
            noticeTone = if (summary.ok == 0) Tone.Negative else Tone.Positive
            syncing = false
            syncStage = ""
            refresh()
            refreshSyncStats()
        }
    }

    fun syncSeed() {
        viewModelScope.launch {
            val all = c.companyDao.all()
            val byCode = all.associateBy { it.code.uppercase() }
            val missing = SeedSymbols.liquidLargeCaps.filter { it.uppercase() !in byCode }
            if (missing.isNotEmpty()) {
                // create the shells first so the batch has rows to attach data to
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    for (code in missing) {
                        c.companyDao.upsert(Company(code = code, name = code, consolidated = true, isDemo = false))
                    }
                }
            }
            val refreshed = c.companyDao.all().filter { it.code.uppercase() in SeedSymbols.liquidLargeCaps.map(String::uppercase).toSet() }
            startBatch(refreshed, "Syncing the top ${refreshed.size} companies")
        }
    }

    fun syncWatchlist() {
        viewModelScope.launch {
            val list = c.watchlistDao.ensureDefault()
            val members = c.watchlistDao.members(list.id)
            if (members.isEmpty()) {
                notice = "The watchlist is empty. Add companies, or sync the top 65 first."
                noticeTone = Tone.Warning
                return@launch
            }
            startBatch(members, "Syncing ${members.size} watchlist companies")
        }
    }

    fun refresh() {
        viewModelScope.launch {
            stats = c.companyDao.stats()
            batches = c.importDao.all()
            logs = c.logDao.recent(60)
            refreshSyncStats()
        }
    }

    fun import(uri: Uri, name: String) {
        viewModelScope.launch {
            working = true
            notice = null
            warnings = emptyList()
            try {
                val dir = File(getApplication<Application>().cacheDir, "imports").apply { mkdirs() }
                val local = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    local.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("The file could not be opened.")
                val outcome = c.importService.import(local, name)
                warnings = outcome.warnings
                notice = if (outcome.companies == 0) {
                    "Nothing was imported from $name."
                } else {
                    "Imported ${outcome.companies} companies, ${outcome.annualRows} annual rows and ${outcome.quarterlyRows} quarterly rows."
                }
                noticeTone = if (outcome.companies == 0) Tone.Warning else Tone.Positive
                local.delete()
            } catch (e: Exception) {
                notice = "Import failed: ${e.message}"
                noticeTone = Tone.Negative
            } finally {
                working = false
                refresh()
            }
        }
    }

    fun loadDemo() {
        viewModelScope.launch {
            working = true
            val (companies, rows) = DemoData.load(c.companyDao, c.importDao)
            notice = "Loaded the synthetic demo dataset: $companies companies, $rows annual rows."
            noticeTone = Tone.Warning
            working = false
            refresh()
        }
    }

    fun clearDemo() {
        viewModelScope.launch {
            c.companyDao.deleteAll(includeDemo = false)
            notice = "Demo companies removed. Your imported data is untouched."
            noticeTone = Tone.Positive
            refresh()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            c.companyDao.deleteAll(includeDemo = true)
            c.screenDao.all().forEach { c.screenDao.delete(it.id) }
            notice = "All company data cleared from this device."
            noticeTone = Tone.Positive
            refresh()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            c.logDao.clear()
            refresh()
        }
    }
}

@Composable
fun DataScreen(onOpenModels: () -> Unit) {
    val vm: DataViewModel = viewModel()
    LaunchedEffect(Unit) { vm.refresh() }
    var showLogs by mutableStateOf(false)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val context = vm.getApplication<Application>()
            vm.import(uri, context.displayName(uri))
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Data", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Everything the app knows lives in a local database file. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Dataset") {
                Row(Modifier.fillMaxWidth()) {
                    `in`.artham.fundamentals.ui.components.StatTile("Companies", vm.stats.companies.toString(), Modifier.weight(1f))
                    `in`.artham.fundamentals.ui.components.StatTile("Annual rows", vm.stats.annualRows.toString(), Modifier.weight(1f))
                    `in`.artham.fundamentals.ui.components.StatTile("Quarter rows", vm.stats.quarterlyRows.toString(), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    `in`.artham.fundamentals.ui.components.StatTile("Demo companies", vm.stats.demoCompanies.toString(), Modifier.weight(1f))
                    `in`.artham.fundamentals.ui.components.StatTile("With quotes", vm.stats.withQuotes.toString(), Modifier.weight(1f))
                }
            }
        }

        if (vm.notice != null) {
            item { MessageBanner(vm.notice!!, vm.noticeTone) { vm.notice = null } }
        }
        vm.warnings.forEach { warning ->
            item { MessageBanner(warning, Tone.Warning) }
        }
        if (vm.working) item { LoadingRow("Reading the file and writing to the local database") }

        item {
            SectionCard(
                title = "Live data",
                subtitle = "Fetched on this device from public endpoints, stored locally"
            ) {
                Row(Modifier.fillMaxWidth()) {
                    `in`.artham.fundamentals.ui.components.StatTile("Price bars", vm.barCount.toString(), Modifier.weight(1f))
                    `in`.artham.fundamentals.ui.components.StatTile("With prices", vm.companiesWithBars.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.syncUniverse() }, enabled = !vm.syncing) {
                        Text("Sync company list")
                    }
                    Button(onClick = { vm.syncSeed() }, enabled = !vm.syncing) {
                        Text("Sync top 65")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.syncWatchlist() }, enabled = !vm.syncing) {
                        Text("Sync watchlist")
                    }
                    if (vm.syncing) {
                        OutlinedButton(onClick = { vm.cancelSync() }) { Text("Stop") }
                    }
                }
                if (vm.syncing || vm.syncStage.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(vm.syncStage, style = MaterialTheme.typography.labelMedium)
                    if (vm.syncTotal > 0) {
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { vm.syncDone.toFloat() / vm.syncTotal.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${vm.syncDone} of ${vm.syncTotal}${if (vm.syncFailed > 0) "  •  ${vm.syncFailed} failed" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "The company list is the exchange's published master file. Prices and fundamentals come from a public quote endpoint. Each company is saved as it arrives, so stopping halfway keeps what has already been fetched. Data is for personal research; verify anything material against the company's filings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                title = "Import a spreadsheet",
                subtitle = "CSV or XLSX. Company exports, long tables and wide snapshots are all recognised."
            ) {
                Text(
                    "Artham reads a file you already own. A single-company export with metrics down the rows and periods across the columns imports perfectly; a long table with a period column per row works too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !vm.working) {
                        Text("Choose file")
                    }
                    OutlinedButton(onClick = { vm.loadDemo() }, enabled = !vm.working) {
                        Text("Load demo data")
                    }
                }
                if (vm.stats.demoCompanies > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.clearDemo() }, enabled = !vm.working) {
                            Text("Remove demo companies")
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Recognised column names") {
                Text(
                    "Sales, Revenue, Expenses, Operating Profit, OPM %, Other Income, Interest, Depreciation, " +
                        "Profit before tax, Tax %, Net Profit, EPS, Dividend, Equity Capital, Reserves, Borrowings, " +
                        "Total Assets, Total Liabilities, Fixed Assets, CWIP, Investments, Receivables, Inventory, " +
                        "Payables, Cash from Operating Activity, Cash from Investing Activity, Cash from Financing Activity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Period headers such as \"Mar 2024\", \"Mar-24\" or \"FY24\" are understood, and TTM columns are skipped.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (vm.batches.isNotEmpty()) {
            item {
                SectionCard(title = "Import history") {
                    val format = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
                    vm.batches.forEach { batch ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(batch.fileName, style = MaterialTheme.typography.bodySmall)
                                    if (batch.isDemo) {
                                        Spacer(Modifier.width(6.dp))
                                        Pill("DEMO", Tone.Warning)
                                    }
                                }
                                Text(
                                    "${format.format(Date(batch.createdAt))}  •  ${batch.companies} companies  •  ${batch.note}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "${batch.annualRows}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Danger zone") {
                Text(
                    "Clearing removes rows from the local database. It cannot be undone, and imported files are not kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.clearAll() }, enabled = !vm.working) {
                    Text("Clear all company data")
                }
            }
        }

        item {
            SectionCard(
                title = "Activity log",
                subtitle = "A plain-English record of what the app did",
                trailing = {
                    OutlinedButton(onClick = { showLogs = !showLogs }) {
                        Text(if (showLogs) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                    }
                }
            ) {
                if (showLogs) {
                    if (vm.logs.isEmpty()) {
                        EmptyState("No entries yet", "Actions you take will be listed here.")
                    } else {
                        vm.logs.take(30).forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { vm.clearLogs() }) { Text("Clear log") }
                    }
                } else {
                    Text(
                        "${vm.logs.size} recent entries recorded.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OutlinedButton(onClick = onOpenModels) { Text("Manage local models") }
        }
    }
}
