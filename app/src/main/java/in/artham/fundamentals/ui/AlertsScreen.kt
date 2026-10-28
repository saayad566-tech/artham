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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import `in`.artham.fundamentals.data.AlertRule
import `in`.artham.fundamentals.domain.Templates
import `in`.artham.fundamentals.ui.components.EmptyState
import `in`.artham.fundamentals.ui.components.MessageBanner
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Tone
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()
    var rules by mutableStateOf<List<AlertRule>>(emptyList())
    var notice by mutableStateOf<String?>(null)
    var checking by mutableStateOf(false)

    fun load() {
        viewModelScope.launch { rules = c.alertDao.all() }
    }

    fun create(name: String, query: String) {
        viewModelScope.launch {
            c.alertDao.create(name.ifBlank { query.take(40) }, query)
            load()
        }
    }

    fun toggle(rule: AlertRule, enabled: Boolean) {
        viewModelScope.launch {
            c.alertDao.toggle(rule.id, enabled)
            load()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            c.alertDao.delete(id)
            load()
        }
    }

    fun checkAll() {
        if (checking) return
        viewModelScope.launch {
            checking = true
            val records = c.companyDao.allRecords()
            var changed = 0
            for (rule in rules.filter { it.enabled }) {
                val result = c.screenEngine.run(rule.query, records, barsProvider = { id -> c.syncDao.bars(id) })
                val count = result.hits.size
                if (count != rule.lastCount) changed++
                c.alertDao.setResult(rule.id, count)
            }
            notice = when {
                rules.isEmpty() -> "No alerts defined yet."
                changed == 0 -> "Checked ${rules.count { it.enabled }} alerts. Nothing changed since the last check."
                else -> "$changed of ${rules.count { it.enabled }} alerts changed since the last check."
            }
            checking = false
            load()
        }
    }
}

@Composable
fun AlertsScreen() {
    val vm: AlertsViewModel = viewModel()
    LaunchedEffect(Unit) { vm.load(); vm.checkAll() }
    var showAdd by mutableStateOf(false)
    var name by mutableStateOf("")
    var query by mutableStateOf("roe > 20 AND sales_growth_5y > 15")

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alerts", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Saved screens, re-evaluated every time you open this screen. No background service, no notification spam.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "New alert")
                }
            }
        }

        if (vm.notice != null) {
            item { MessageBanner(vm.notice!!, Tone.Positive) { vm.notice = null } }
        }

        item {
            Button(onClick = { vm.checkAll() }, enabled = !vm.checking) {
                Text(if (vm.checking) "Checking" else "Check now")
            }
        }

        if (vm.rules.isEmpty()) {
            item {
                SectionCard {
                    EmptyState(
                        "No alerts yet",
                        "An alert is a saved screen plus a count. When the count moves, you will see it here.",
                        "Create the first alert",
                        { showAdd = true }
                    )
                }
            }
        } else {
            item {
                SectionCard(title = "Rules") {
                    val format = SimpleDateFormat("dd MMM, HH:mm", Locale.US)
                    vm.rules.forEach { rule ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(
                                    rule.query,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Pill("${rule.lastCount} matches", Tone.Accent)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (rule.lastChecked == 0L) "never checked"
                                        else "checked ${format.format(Date(rule.lastChecked))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(checked = rule.enabled, onCheckedChange = { vm.toggle(rule, it) })
                            IconButton(onClick = { vm.delete(rule.id) }) {
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

        item {
            Text(
                "Android would need a background worker and a notification permission to check these while the app is closed. That is deliberately not built: a financial app that wakes up to run queries on your data should be your choice, not a default.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New alert") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Screen query") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(Templates.all) { template ->
                            FilterChip(
                                selected = query == template.query,
                                onClick = { query = template.query; if (name.isBlank()) name = template.name },
                                label = { Text(template.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (query.isNotBlank()) {
                        vm.create(name.trim(), query.trim())
                        name = ""
                        showAdd = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}
