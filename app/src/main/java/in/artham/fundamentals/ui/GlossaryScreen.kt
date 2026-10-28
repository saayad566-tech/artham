package `in`.artham.fundamentals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import `in`.artham.fundamentals.domain.MetricCatalog
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Tone

/**
 * A dictionary generated from the metric catalogue itself, so it can never drift out of
 * date: every metric in the query language is listed here with the formula it implements
 * and the token you type to use it.
 */
@Composable
fun GlossaryScreen() {
    var query by remember { mutableStateOf("") }
    val groups = MetricCatalog.groups

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Glossary", style = MaterialTheme.typography.displaySmall)
                Text(
                    "${MetricCatalog.all.size} metrics, each with the formula it implements and the token to type in a screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search metrics") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        groups.forEach { group ->
            val metrics = MetricCatalog.all.filter { it.group == group }.filter {
                query.isBlank() || it.id.contains(query, true) || it.label.contains(query, true)
            }
            if (metrics.isEmpty()) return@forEach
            item {
                SectionCard(title = group, subtitle = "${metrics.size} metrics") {
                    metrics.forEach { metric ->
                        Column(Modifier.padding(vertical = 7.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(metric.label, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.weight(1f))
                                Pill(metric.unit.key, Tone.Neutral)
                            }
                            Text(
                                metric.id,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                metric.formula,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "Values are in rupees crore unless the unit says otherwise. A dash means the stored data cannot produce the figure.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
