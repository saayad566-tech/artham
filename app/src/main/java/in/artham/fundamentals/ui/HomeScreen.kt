package `in`.artham.fundamentals.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.artham.fundamentals.artham
import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.DatasetStats
import `in`.artham.fundamentals.data.SavedScreen
import `in`.artham.fundamentals.domain.Financials
import `in`.artham.fundamentals.domain.Ratios
import `in`.artham.fundamentals.domain.Templates
import `in`.artham.fundamentals.llm.EngineStatus
import `in`.artham.fundamentals.ui.components.DemoBadge
import `in`.artham.fundamentals.ui.components.KeyValueRow
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.StatTile
import `in`.artham.fundamentals.ui.components.Tone
import kotlinx.coroutines.launch

data class HomeWatchRow(
    val company: Company,
    val roe: String,
    val pe: String,
    val growth: String,
    val growthTone: Tone
)

data class HomeState(
    val loading: Boolean = true,
    val stats: DatasetStats = DatasetStats(0, 0, 0, 0, 0L, 0),
    val watchlist: List<HomeWatchRow> = emptyList(),
    val screens: List<SavedScreen> = emptyList(),
    val engineLabel: String = "",
    val engineReady: Boolean = false,
    val engineDetail: String = ""
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val c = app.artham()
    var state by mutableStateOf(HomeState())
        private set

    fun refresh() {
        viewModelScope.launch {
            state = state.copy(loading = true)
            val stats = c.companyDao.stats()
            val list = c.watchlistDao.ensureDefault()
            val members = c.watchlistDao.members(list.id).take(6).map { company ->
                val record = c.companyDao.record(company)
                val f = Financials(record)
                val a = f.latest
                val g = if (f.year(5)?.sales != null && a?.sales != null) {
                    `in`.artham.fundamentals.core.Num.cagr(f.year(5)?.sales, a.sales, 5)
                } else null
                HomeWatchRow(
                    company = company,
                    roe = Fmt.pct(a?.let { Ratios.roe(it) }),
                    pe = Fmt.times(Ratios.pe(f)),
                    growth = if (g == null) "-" else Fmt.pct(g),
                    growthTone = when {
                        g == null -> Tone.Neutral
                        g >= 15 -> Tone.Positive
                        g < 5 -> Tone.Negative
                        else -> Tone.Neutral
                    }
                )
            }
            val status = runCatching { c.llm.status() }.getOrNull()
            state = HomeState(
                loading = false,
                stats = stats,
                watchlist = members,
                screens = c.screenDao.all().take(4),
                engineLabel = c.settings.engine.label,
                engineReady = status is EngineStatus.Ready,
                engineDetail = when (status) {
                    is EngineStatus.Ready -> status.detail
                    is EngineStatus.NotReady -> status.reason
                    null -> "Status unavailable"
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    onOpenCompany: (Long) -> Unit,
    onOpenScreen: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenData: () -> Unit
) {
    val vm: HomeViewModel = viewModel()
    LaunchedEffect(Unit) { vm.refresh() }
    val state = vm.state

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Artham",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Fundamental research that never leaves your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.stats.demoCompanies > 0) DemoBadge()
                Pill(
                    if (state.engineReady) "LOCAL MODEL READY" else "LOCAL MODEL NOT SET",
                    if (state.engineReady) Tone.Positive else Tone.Warning
                )
            }
        }

        item {
            SectionCard {
                Row(Modifier.fillMaxWidth()) {
                    StatTile("Companies", state.stats.companies.toString(), Modifier.weight(1f))
                    StatTile("Annual periods", state.stats.annualRows.toString(), Modifier.weight(1f))
                    StatTile("Quarters", state.stats.quarterlyRows.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                KeyValueRow(
                    "Last import",
                    if (state.stats.lastImportAt == 0L) "Never" else
                        java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.US)
                            .format(java.util.Date(state.stats.lastImportAt))
                )
                KeyValueRow("Companies with a quote", state.stats.withQuotes.toString())
                if (state.stats.demoCompanies > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The demo dataset is synthetic and exists so you can try every feature before importing your own data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Your watchlist",
                subtitle = "Six most recent additions",
                trailing = {
                    Text(
                        "Open",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenScreen() }
                    )
                }
            ) {
                if (state.watchlist.isEmpty()) {
                    Text(
                        "Nothing on the watchlist yet. Add a company from any screen result.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.watchlist.forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCompany(row.company.id) }
                                .padding(vertical = 8.dp),
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "ROE ${row.roe}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                )
                                Text(
                                    "P/E ${row.pe}  •  5y ${row.growth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (row.growthTone == Tone.Neutral) MaterialTheme.colorScheme.onSurfaceVariant
                                    else `in`.artham.fundamentals.ui.components.toneColor(row.growthTone)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.stats.withQuotes == 0) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenData() }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Get real market data", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Two taps and about a minute: fetch the listed company universe, then five years of prices and reported fundamentals for the top 65 names. Everything is stored on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onOpenData) { Text("Open Data") }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Ready-made screens", subtitle = "Tap to load one into the query builder") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Templates.all) { template ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .width(168.dp)
                                .clickable { onOpenScreen() }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(template.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    template.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenModels() }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Local analyst model", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (state.engineReady) state.engineDetail else "${state.engineLabel}: ${state.engineDetail}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Text(
                "Artham is an educational research tool, not investment advice. Every figure shown is computed from data stored on this device; verify it against the company's filings before acting.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
