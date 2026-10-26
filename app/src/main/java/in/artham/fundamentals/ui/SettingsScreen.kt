package `in`.artham.fundamentals.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.artham.fundamentals.artham
import `in`.artham.fundamentals.ui.components.KeyValueRow
import `in`.artham.fundamentals.ui.components.MessageBanner
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Tone
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()
    var maxTokens by mutableStateOf(c.settings.maxTokens)
    var temperature by mutableStateOf(c.settings.temperature)
    var notice by mutableStateOf<String?>(null)

    fun persist() {
        c.settings.maxTokens = maxTokens
        c.settings.temperature = temperature
        notice = "Generation settings saved."
    }

    fun clearChats() {
        viewModelScope.launch {
            c.aiDao.clearAll()
            notice = "All analyst conversations cleared."
        }
    }
}

@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onOpenModels: () -> Unit
) {
    val vm: SettingsViewModel = viewModel()
    var tokens by remember { mutableStateOf(vm.maxTokens.toFloat()) }
    var temp by remember { mutableStateOf(vm.temperature) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (vm.notice != null) {
            item { MessageBanner(vm.notice!!, Tone.Positive) { vm.notice = null } }
        }

        item {
            SectionCard(title = "Appearance") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dark theme", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Designed first for a dark terminal, but the light theme is complete.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = darkTheme, onCheckedChange = { onToggleTheme() })
                }
            }
        }

        item {
            SectionCard(title = "Generation", subtitle = "Applies to the on-device model") {
                Text("Maximum tokens: ${tokens.roundToInt()}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = tokens,
                    onValueChange = { tokens = it },
                    onValueChangeFinished = { vm.maxTokens = tokens.roundToInt(); vm.persist() },
                    valueRange = 128f..2048f,
                    steps = 14
                )
                Text("Temperature: ${String.format("%.2f", temp)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = temp,
                    onValueChange = { temp = it },
                    onValueChangeFinished = { vm.temperature = temp; vm.persist() },
                    valueRange = 0f..1f
                )
                Text(
                    "A low temperature keeps the model close to the numbers it was given, which is what you want for financial work.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Analyst conversations") {
                Text(
                    "Conversations are stored in the local database so you can return to them. They never leave the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.clearChats() }) { Text("Clear all conversations") }
            }
        }

        item {
            SectionCard(title = "Models") {
                OutlinedButton(onClick = onOpenModels) { Text("Manage local models") }
            }
        }

        item {
            SectionCard(title = "About Artham") {
                KeyValueRow("Version", "1.0.0")
                KeyValueRow("Data storage", "On this device only")
                KeyValueRow("Network use", "None, unless you point the app at your own local server")
                KeyValueRow("Model", "Supplied by you")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Artham is an independent, offline fundamental-analysis tool. It is not affiliated with any data vendor or exchange. Figures are computed from files you import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Disclaimer") {
                Text(
                    "This application is for education and research. It is not investment advice, not a recommendation to buy or sell any security, and not a substitute for a licensed adviser. Computed ratios depend entirely on the accuracy of the data you import. Verify every number against the company's audited filings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
