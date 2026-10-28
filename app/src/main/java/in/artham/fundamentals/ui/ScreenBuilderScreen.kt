package `in`.artham.fundamentals.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.artham.fundamentals.artham
import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.data.SavedScreen
import `in`.artham.fundamentals.data.ScreenResult
import `in`.artham.fundamentals.domain.MetricCatalog
import `in`.artham.fundamentals.domain.Templates
import `in`.artham.fundamentals.ui.components.EmptyState
import `in`.artham.fundamentals.ui.components.LoadingRow
import `in`.artham.fundamentals.ui.components.MessageBanner
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Tone
import kotlinx.coroutines.launch

class ScreenViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()

    var query by mutableStateOf("roe > 15 AND debt_to_equity < 0.5")
    var natural by mutableStateOf("")
    var running by mutableStateOf(false)
    var translating by mutableStateOf(false)
    var result by mutableStateOf<ScreenResult?>(null)
    var savedScreens by mutableStateOf<List<SavedScreen>>(emptyList())
    var notice by mutableStateOf<String?>(null)
    var noticeTone by mutableStateOf(Tone.Warning)
    var lastOrigin by mutableStateOf("")

    fun loadSaved() {
        viewModelScope.launch { savedScreens = c.screenDao.all() }
    }

    fun run() {
        if (running) return
        viewModelScope.launch {
            running = true
            notice = null
            val records = c.companyDao.allRecords()
            val out = c.screenEngine.run(query, records, barsProvider = { id -> c.syncDao.bars(id) })
            result = out
            running = false
            if (out.error != null) {
                notice = out.error
                noticeTone = Tone.Negative
            }
        }
    }

    fun translate() {
        if (natural.isBlank() || translating) return
        viewModelScope.launch {
            translating = true
            notice = null
            when (val outcome = c.llm.nlToQuery().translate(natural, c.llm.config())) {
                is `in`.artham.fundamentals.llm.NlToQuery.Outcome.Query -> {
                    query = outcome.query
                    lastOrigin = outcome.origin
                    notice = "Query built by the ${outcome.origin}."
                    noticeTone = Tone.Positive
                    translating = false
                    run()
                }
                is `in`.artham.fundamentals.llm.NlToQuery.Outcome.Failed -> {
                    notice = outcome.reason
                    noticeTone = Tone.Warning
                    translating = false
                }
            }
        }
    }

    fun save(name: String) {
        viewModelScope.launch {
            c.screenDao.save(name, query, "Saved from the query builder")
            loadSaved()
            notice = "Screen \"$name\" saved."
            noticeTone = Tone.Positive
        }
    }

    fun addAllToWatchlist() {
        viewModelScope.launch {
            val list = c.watchlistDao.ensureDefault()
            val hits = result?.hits ?: return@launch
            var added = 0
            for (hit in hits) {
                if (c.watchlistDao.add(list.id, hit.company.id)) added++
            }
            notice = if (added == 0) "All matches were already on the watchlist."
            else "Added $added companies to \"${list.name}\"."
            noticeTone = Tone.Positive
        }
    }
}

@Composable
fun ScreenBuilderScreen(onOpenCompany: (Long) -> Unit) {
    val vm: ScreenViewModel = viewModel()
    LaunchedEffect(Unit) { vm.loadSaved() }
    var showSave by mutableStateOf(false)
    var saveName by mutableStateOf("")

    val result = vm.result
    val columns = result?.hits?.firstOrNull()?.values?.keys?.toList()
        ?: MetricCatalog.defaultColumns

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Screen", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Filter the whole local dataset with a query that runs on the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Describe what you want", subtitle = "Works offline with the phrase parser; a local model makes it smarter") {
                OutlinedTextField(
                    value = vm.natural,
                    onValueChange = { vm.natural = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    placeholder = { Text("profitable companies with roe above 18 and low debt") },
                    label = { Text("In plain English") }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.translate() }, enabled = !vm.translating && vm.natural.isNotBlank()) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (vm.translating) "Thinking" else "Build query")
                    }
                }
                if (vm.lastOrigin.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Last translation came from the ${vm.lastOrigin}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Query",
                trailing = {
                    Row {
                        IconButton(onClick = { vm.run() }) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "Run screen")
                        }
                        IconButton(onClick = { showSave = true }) {
                            Icon(Icons.Outlined.Check, contentDescription = "Save screen")
                        }
                        IconButton(onClick = { vm.addAllToWatchlist() }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add matches to watchlist")
                        }
                    }
                }
            ) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("Conditions") }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Use >, <, >=, <=, BETWEEN, AND, OR, NOT and parentheses. Metric names are the ones in the Data dictionary.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Templates.all) { template ->
                        FilterChip(
                            selected = vm.query == template.query,
                            onClick = {
                                vm.query = template.query
                                vm.lastOrigin = "template \"${template.name}\""
                                vm.run()
                            },
                            label = { Text(template.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        if (vm.notice != null) {
            item { MessageBanner(vm.notice!!, vm.noticeTone) { vm.notice = null } }
        }

        if (vm.running) {
            item { LoadingRow("Evaluating every company on the device") }
        }

        if (result != null && !vm.running) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${result.hits.size} matches",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill("${result.evaluated} evaluated", Tone.Neutral)
                    Spacer(Modifier.width(6.dp))
                    Pill("${result.durationMs} ms", Tone.Accent)
                }
            }
            item {
                SectionCard {
                    if (result.hits.isEmpty()) {
                        EmptyState(
                            "No company matched",
                            "Loosen one condition at a time. If the dataset is the demo set, remember those figures are synthetic."
                        )
                    } else {
                        val scroll = rememberScrollState()
                        Column {
                            Row(
                                Modifier
                                    .horizontalScroll(scroll)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    "Company",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(150.dp).padding(horizontal = 8.dp)
                                )
                                columns.forEach { id ->
                                    val def = MetricCatalog.get(id)
                                    Text(
                                        def?.label ?: id,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(104.dp).padding(horizontal = 8.dp)
                                    )
                                }
                            }
                            result.hits.forEach { hit ->
                                Row(
                                    Modifier
                                        .horizontalScroll(scroll)
                                        .clickable { onOpenCompany(hit.company.id) }
                                        .padding(vertical = 9.dp)
                                ) {
                                    Column(Modifier.width(150.dp).padding(horizontal = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(hit.company.code, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            if (hit.company.isDemo) {
                                                Spacer(Modifier.width(4.dp))
                                                Pill("D", Tone.Warning)
                                            }
                                        }
                                        Text(
                                            hit.company.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    columns.forEach { id ->
                                        val def = MetricCatalog.get(id)
                                        val value = hit.values[id]
                                        Text(
                                            if (def != null) Fmt.metric(value, def.unit.key) else Fmt.num(value, 2),
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
        }

        if (vm.savedScreens.isNotEmpty()) {
            item {
                SectionCard(title = "Saved screens") {
                    vm.savedScreens.forEach { saved ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.query = saved.query
                                    vm.run()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(saved.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    saved.query,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                "${saved.lastCount}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Save this screen") },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (saveName.isNotBlank()) {
                        vm.save(saveName.trim())
                        saveName = ""
                        showSave = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } }
        )
    }
}
