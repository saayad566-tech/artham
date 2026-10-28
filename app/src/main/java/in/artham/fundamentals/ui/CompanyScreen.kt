package `in`.artham.fundamentals.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import `in`.artham.fundamentals.data.AnnualFinancial
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.CompanyRecord
import `in`.artham.fundamentals.data.QuarterlyFinancial
import `in`.artham.fundamentals.domain.DcfInputs
import `in`.artham.fundamentals.domain.PePoint
import `in`.artham.fundamentals.domain.PriceBar
import `in`.artham.fundamentals.domain.PriceSeries
import `in`.artham.fundamentals.domain.SeriesStats
import `in`.artham.fundamentals.domain.Technical
import `in`.artham.fundamentals.domain.Financials
import `in`.artham.fundamentals.domain.Insight
import `in`.artham.fundamentals.domain.MetricCatalog
import `in`.artham.fundamentals.domain.ProsConsEngine
import `in`.artham.fundamentals.domain.Ratios
import `in`.artham.fundamentals.domain.RelativeValuation
import `in`.artham.fundamentals.domain.Valuation
import `in`.artham.fundamentals.ui.components.BarSeries
import `in`.artham.fundamentals.ui.components.CandleChart
import `in`.artham.fundamentals.ui.components.PeBandChart
import `in`.artham.fundamentals.ui.components.StackedBar
import `in`.artham.fundamentals.ui.components.DataTable
import `in`.artham.fundamentals.ui.components.DemoBadge
import `in`.artham.fundamentals.ui.components.EmptyState
import `in`.artham.fundamentals.ui.components.KeyValueRow
import `in`.artham.fundamentals.ui.components.MessageBanner
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Sparkline
import `in`.artham.fundamentals.ui.theme.ArthamColors
import `in`.artham.fundamentals.ui.components.StatTile
import `in`.artham.fundamentals.ui.components.Tone
import kotlinx.coroutines.launch

/** Cross-screen hand-off: which company the analyst tab should open with. */
object AiSelection {
    var companyId by mutableStateOf<Long?>(null)
}

data class PeerRow(val company: Company, val roe: String, val pe: String, val roce: String)

data class CompanyState(
    val loading: Boolean = true,
    val record: CompanyRecord? = null,
    val insights: List<Insight> = emptyList(),
    val peers: List<PeerRow> = emptyList(),
    val inWatchlist: Boolean = false,
    val relative: RelativeValuation? = null,
    val bars: List<PriceBar> = emptyList(),
    val sma50: List<Double?> = emptyList(),
    val sma200: List<Double?> = emptyList(),
    val peBand: List<PePoint> = emptyList(),
    val stats: SeriesStats? = null,
    val syncing: Boolean = false,
    val syncMessage: String? = null
)

class CompanyViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()
    var state by mutableStateOf(CompanyState())
        private set

    fun load(companyId: Long) {
        viewModelScope.launch {
            state = state.copy(loading = true)
            val record = c.companyDao.recordById(companyId)
            if (record == null) {
                state = CompanyState(loading = false)
                return@launch
            }
            val f = Financials(record)
            val insights = ProsConsEngine.analyse(f)
            val watchlist = c.watchlistDao.ensureDefault()
            val peers = c.companyDao.all()
                .filter { it.sector.isNotBlank() && it.sector == record.company.sector && it.id != record.company.id }
                .take(8)
                .map { peer ->
                    val pf = Financials(c.companyDao.record(peer))
                    PeerRow(
                        company = peer,
                        roe = Fmt.pct(pf.latest?.let { Ratios.roe(it) }),
                        pe = Fmt.times(Ratios.pe(pf)),
                        roce = Fmt.pct(pf.latest?.let { Ratios.roce(it) })
                    )
                }
            val peerFinancials = c.companyDao.all()
                .filter { it.sector.isNotBlank() && it.sector == record.company.sector && it.id != record.company.id }
                .map { Financials(c.companyDao.record(it)) }
            val bars = c.syncDao.bars(companyId)
            state = CompanyState(
                loading = false,
                record = record,
                insights = insights,
                peers = peers,
                inWatchlist = c.watchlistDao.contains(watchlist.id, companyId),
                relative = Valuation.relative(f, peerFinancials),
                bars = bars,
                sma50 = PriceSeries.sma(bars, 50),
                sma200 = PriceSeries.sma(bars, 200),
                peBand = PriceSeries.peBand(bars, record.annual),
                stats = PriceSeries.stats(bars)
            )
        }
    }

    fun syncNow() {
        val record = state.record ?: return
        viewModelScope.launch {
            state = state.copy(syncing = true, syncMessage = null)
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                c.syncService.syncCompany(record.company, withFundamentals = true)
            }
            state = state.copy(
                syncing = false,
                syncMessage = if (result.ok) "Synced from the public endpoint." else "Sync failed: ${result.message}"
            )
            load(record.company.id)
        }
    }

    fun toggleWatchlist() {
        val record = state.record ?: return
        viewModelScope.launch {
            val list = c.watchlistDao.ensureDefault()
            if (state.inWatchlist) {
                c.watchlistDao.remove(list.id, record.company.id)
                state = state.copy(inWatchlist = false)
            } else {
                c.watchlistDao.add(list.id, record.company.id)
                state = state.copy(inWatchlist = true)
            }
        }
    }
}

private data class LineItem(val label: String, val values: List<String>, val tone: Tone = Tone.Neutral)

private fun annualItems(annual: List<AnnualFinancial>): List<LineItem> {
    fun line(label: String, pick: (AnnualFinancial) -> Double?, decimals: Int = 0, percent: Boolean = false) =
        LineItem(label, annual.map { row ->
            val v = pick(row)
            if (percent) Fmt.pct(v) else Fmt.num(v, decimals)
        })
    return listOf(
        line("Sales", { it.sales }),
        line("Expenses", { it.expenses }),
        line("Operating profit", { it.operatingProfit }),
        line("OPM %", { it.opmPercent }, percent = true),
        line("Other income", { it.otherIncome }),
        line("Interest", { it.interest }),
        line("Depreciation", { it.depreciation }),
        line("Profit before tax", { it.profitBeforeTax }),
        line("Tax %", { it.taxPercent }, percent = true),
        line("Net profit", { it.netProfit }),
        line("EPS", { it.eps }, decimals = 2),
        line("Dividend", { it.dividend }, decimals = 2),
        line("Equity capital", { it.equityCapital }),
        line("Reserves", { it.reserves }),
        line("Borrowings", { it.borrowings }),
        line("Other liabilities", { it.otherLiabilities }),
        line("Total liabilities", { it.totalLiabilities }),
        line("Fixed assets", { it.fixedAssets }),
        line("CWIP", { it.cwip }),
        line("Investments", { it.investments }),
        line("Other assets", { it.otherAssets }),
        line("Total assets", { it.totalAssets }),
        line("Receivables", { it.receivables }),
        line("Inventory", { it.inventory }),
        line("Payables", { it.payables }),
        line("Cash from operations", { it.cfo }),
        line("Cash from investing", { it.cfi }),
        line("Cash from financing", { it.cff }),
        line("Net cash flow", { it.netCashFlow })
    )
}

private fun quarterlyItems(quarterly: List<QuarterlyFinancial>): List<LineItem> {
    fun line(label: String, pick: (QuarterlyFinancial) -> Double?, decimals: Int = 0, percent: Boolean = false) =
        LineItem(label, quarterly.map { row ->
            val v = pick(row)
            if (percent) Fmt.pct(v) else Fmt.num(v, decimals)
        })
    return listOf(
        line("Sales", { it.sales }),
        line("Expenses", { it.expenses }),
        line("Operating profit", { it.operatingProfit }),
        line("OPM %", { it.opmPercent }, percent = true),
        line("Other income", { it.otherIncome }),
        line("Interest", { it.interest }),
        line("Depreciation", { it.depreciation }),
        line("Profit before tax", { it.profitBeforeTax }),
        line("Tax %", { it.taxPercent }, percent = true),
        line("Net profit", { it.netProfit }),
        line("EPS", { it.eps }, decimals = 2)
    )
}

@Composable
fun CompanyScreen(companyId: Long, onBack: () -> Unit, onOpenAi: () -> Unit, onOpenCompany: (Long) -> Unit = {}) {
    val vm: CompanyViewModel = viewModel()
    LaunchedEffect(companyId) { vm.load(companyId) }
    val state = vm.state
    val record = state.record
    var tab by mutableStateOf(0)
    var dcfGrowth by remember(companyId) { mutableStateOf(12f) }
    var dcfDiscount by remember(companyId) { mutableStateOf(13f) }
    var dcfTerminal by remember(companyId) { mutableStateOf(4f) }

    if (record == null) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Text("Company", style = MaterialTheme.typography.titleLarge)
                }
                EmptyState("Company not found", "It may have been removed when the dataset was reset.")
            }
        }
        return
    }

    val f = Financials(record)
    val a = f.latest
    val tabs = listOf("Overview", "Annual", "Quarterly", "Value", "Shareholding", "Peers", "Price", "Technical")

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(record.company.code, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(8.dp))
                            if (record.company.isDemo) DemoBadge()
                        }
                        Text(
                            record.company.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.toggleWatchlist() }) {
                        Icon(
                            if (state.inWatchlist) Icons.Outlined.Check else Icons.Outlined.Add,
                            contentDescription = "Toggle watchlist",
                            tint = if (state.inWatchlist) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        AiSelection.companyId = record.company.id
                        onOpenAi()
                    }) {
                        Icon(Icons.Outlined.Person, contentDescription = "Ask the analyst")
                    }
                }
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        Fmt.rupees(f.quote?.price),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(Modifier.width(8.dp))
                    val change = f.quote?.changePercent
                    if (change != null) {
                        Pill(
                            "${if (change >= 0) "+" else ""}${Fmt.pct(change)}",
                            if (change >= 0) Tone.Positive else Tone.Negative
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    if (f.quote?.source == "demo") Pill("synthetic quote", Tone.Warning)
                }
            }
        }

        item {
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background,
                divider = {}
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }

        when (tab) {
            0 -> {
                item {
                    SectionCard {
                        Row(Modifier.fillMaxWidth()) {
                            StatTile("Market cap", Fmt.croreCompact(Ratios.marketCap(f)), Modifier.weight(1f))
                            StatTile("P/E", Fmt.times(Ratios.pe(f)), Modifier.weight(1f))
                            StatTile("Book value", Fmt.rupees(Ratios.bookValuePerShare(f)), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            StatTile("ROE", Fmt.pct(a?.let { Ratios.roe(it) }), Modifier.weight(1f))
                            StatTile("ROCE", Fmt.pct(a?.let { Ratios.roce(it) }), Modifier.weight(1f))
                            StatTile("OPM", Fmt.pct(a?.let { Ratios.opm(it) }), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            StatTile("Debt/Equity", Fmt.times(a?.let { Ratios.debtToEquity(it) }), Modifier.weight(1f))
                            StatTile("Div yield", Fmt.pct(Ratios.dividendYield(f)), Modifier.weight(1f))
                            StatTile("Piotroski", Ratios.piotroski(f)?.toString() ?: "-", Modifier.weight(1f))
                        }
                    }
                }
                if (record.company.about.isNotBlank()) {
                    item {
                        SectionCard(title = "About") {
                            Text(record.company.about, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {
                    SectionCard(title = "Sales and profit trend", subtitle = "Rupees crore, annual") {
                        Text("Sales", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Sparkline(f.annual.map { it.sales })
                        Spacer(Modifier.height(10.dp))
                        Text("Net profit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Sparkline(f.annual.map { it.netProfit }, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                item {
                    SectionCard(title = "Checklist", subtitle = "Deterministic rules applied to the stored numbers") {
                        val positives = state.insights.filter { it.positive }
                        val negatives = state.insights.filterNot { it.positive }
                        if (state.insights.isEmpty()) {
                            Text(
                                "Not enough history to run the checklist.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (positives.isNotEmpty()) {
                            Text("Strengths", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            positives.forEach { insight ->
                                Text(
                                    "•  ${insight.text}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                            }
                        }
                        if (negatives.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text("Concerns", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            negatives.forEach { insight ->
                                Text(
                                    "•  ${insight.text}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = "All computed ratios") {
                        MetricCatalog.groups.forEach { group ->
                            val defs = MetricCatalog.all.filter { it.group == group }
                            val shown = defs.mapNotNull { def ->
                                val v = runCatching { def.compute(f) }.getOrNull() ?: return@mapNotNull null
                                def.label to Fmt.metric(v, def.unit.key)
                            }
                            if (shown.isNotEmpty()) {
                                Text(
                                    group.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                )
                                shown.forEach { (label, value) -> KeyValueRow(label, value) }
                            }
                        }
                    }
                }
            }
            1 -> {
                item {
                    SectionCard(
                        title = "Annual results",
                        subtitle = if (f.annual.isEmpty()) "No annual rows stored" else "${f.annual.size} periods, values in Rs crore"
                    ) {
                        if (f.annual.isEmpty()) {
                            EmptyState("No annual data", "Import a spreadsheet from the Data tab to populate this table.")
                        } else {
                            val items = annualItems(f.annual)
                            DataTable(
                                header = listOf("") + f.annual.map { "FY${it.fyEnd}" },
                                rows = items.map { item -> listOf(item.label) + item.values },
                                firstColumnWidth = 150,
                                columnWidth = 92
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = "Operating margin by year") {
                        BarSeries(f.annual.map { "FY${it.fyEnd}" }, f.annual.map { it.opmPercent })
                    }
                }
            }
            2 -> {
                item {
                    SectionCard(
                        title = "Quarterly results",
                        subtitle = if (f.quarterly.isEmpty()) "No quarterly rows stored" else "${f.quarterly.size} quarters"
                    ) {
                        if (f.quarterly.isEmpty()) {
                            EmptyState("No quarterly data", "Quarterly figures arrive with a spreadsheet that includes them.")
                        } else {
                            DataTable(
                                header = listOf("") + f.quarterly.map { it.label },
                                rows = quarterlyItems(f.quarterly).map { item -> listOf(item.label) + item.values },
                                firstColumnWidth = 150,
                                columnWidth = 92
                            )
                        }
                    }
                }
                if (f.quarterly.isNotEmpty()) {
                    item {
                        SectionCard(title = "Quarterly net profit") {
                            BarSeries(f.quarterly.map { it.label }, f.quarterly.map { it.netProfit })
                        }
                    }
                }
            }
            3 -> {
                item {
                    SectionCard(
                        title = "Discounted cash flow",
                        subtitle = "Two-stage model on free cash flow. Every input is yours to change."
                    ) {
                        val inputs = DcfInputs(
                            growthRate = dcfGrowth.toDouble(),
                            years = 5,
                            discountRate = dcfDiscount.toDouble(),
                            terminalGrowth = dcfTerminal.toDouble()
                        )
                        Text(
                            "Growth for five years: ${String.format("%.1f", dcfGrowth)}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = dcfGrowth,
                            onValueChange = { dcfGrowth = it },
                            valueRange = -10f..35f
                        )
                        Text(
                            "Discount rate: ${String.format("%.1f", dcfDiscount)}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = dcfDiscount,
                            onValueChange = { dcfDiscount = it },
                            valueRange = 8f..20f
                        )
                        Text(
                            "Terminal growth: ${String.format("%.1f", dcfTerminal)}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = dcfTerminal,
                            onValueChange = { dcfTerminal = it },
                            valueRange = 0f..7f
                        )
                        Spacer(Modifier.height(10.dp))
                        val dcf = Valuation.dcf(f, inputs)
                        if (dcf == null) {
                            Text(
                                "This company has no positive free cash flow on the stored data, so a discounted cash flow is not meaningful. That is a finding, not an error.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            KeyValueRow("Free cash flow (base)", Fmt.cr(dcf.baseFreeCashFlow))
                            KeyValueRow("Net debt deducted", Fmt.cr(dcf.netDebt))
                            KeyValueRow("Value per share", Fmt.rupees(dcf.valuePerShare, 2))
                            KeyValueRow("Market price", Fmt.rupees(dcf.price, 2))
                            KeyValueRow(
                                "Margin of safety",
                                dcf.marginOfSafetyPercent?.let { Fmt.pct(it) } ?: "-",
                                if ((dcf.marginOfSafetyPercent ?: 0.0) >= 0) Tone.Positive else Tone.Negative
                            )
                            KeyValueRow("Terminal value share", Fmt.pct(dcf.terminalSharePercent))
                            if (dcf.terminalSharePercent > 75.0) {
                                Spacer(Modifier.height(8.dp))
                                MessageBanner(
                                    "More than three quarters of this valuation sits in the terminal value, which means the answer depends mostly on assumptions beyond year five.",
                                    Tone.Warning
                                )
                            }
                        }
                    }
                }
                item {
                    SectionCard(
                        title = "Sensitivity",
                        subtitle = "Value per share across discount rate (rows) and growth (columns)"
                    ) {
                        val (growthAxis, rows) = Valuation.sensitivity(
                            f,
                            DcfInputs(dcfGrowth.toDouble(), 5, dcfDiscount.toDouble(), dcfTerminal.toDouble())
                        )
                        val scroll = rememberScrollState()
                        Column(Modifier.horizontalScroll(scroll)) {
                            Row(Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    "discount \\ growth",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(120.dp).padding(horizontal = 6.dp)
                                )
                                growthAxis.forEach { growth ->
                                    Text(
                                        String.format("%.0f%%", growth),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(78.dp).padding(horizontal = 6.dp)
                                    )
                                }
                            }
                            rows.forEach { row ->
                                Row(Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        String.format("%.1f%%", row.discountRate),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.width(120.dp).padding(horizontal = 6.dp)
                                    )
                                    row.values.forEach { value ->
                                        Text(
                                            Fmt.num(value, 0),
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.width(78.dp).padding(horizontal = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard(
                        title = "Reverse DCF",
                        subtitle = "The growth the current price already assumes"
                    ) {
                        val implied = Valuation.reverseDcf(
                            f,
                            DcfInputs(dcfGrowth.toDouble(), 5, dcfDiscount.toDouble(), dcfTerminal.toDouble())
                        )
                        if (implied == null) {
                            Text(
                                "The price is above what this model produces even at 80% growth, or there is no free cash flow to discount.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "At a ${String.format("%.1f", dcfDiscount)}% discount rate, today's price implies free cash flow growth of ${String.format("%.1f", implied)}% a year for five years.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "This is the more useful direction of the model: rather than arguing about what the company is worth, it states what the market already believes, which you can then agree or disagree with.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = "Relative valuation", subtitle = "Against companies in the same sector") {
                        val relative = state.relative
                        if (relative == null || relative.peerCount < 2) {
                            Text(
                                "Peer ranking needs at least two other companies in the same sector with a price and earnings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            KeyValueRow("Sector median P/E", Fmt.times(relative.medianPe))
                            KeyValueRow("Sector median P/B", Fmt.times(relative.medianPb))
                            KeyValueRow("This company's P/E percentile", relative.pePercentile?.let { Fmt.pct(it) } ?: "-")
                            KeyValueRow("This company's P/B percentile", relative.pbPercentile?.let { Fmt.pct(it) } ?: "-")
                            Spacer(Modifier.height(6.dp))
                            Text(relative.verdict, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            4 -> {
                item {
                    SectionCard(title = "Shareholding pattern", subtitle = "Percent of total equity") {
                        if (f.shareholding.isEmpty()) {
                            EmptyState("No shareholding data", "This table needs a dataset that includes the ownership breakdown.")
                        } else {
                            val latest = f.shareholding.last()
                            StackedBar(
                                listOf(
                                    "Promoters" to (latest.promoters ?: 0.0),
                                    "FII" to (latest.fii ?: 0.0),
                                    "DII" to (latest.dii ?: 0.0),
                                    "Public" to (latest.publicHolding ?: 0.0)
                                )
                            )
                            Spacer(Modifier.height(10.dp))
                            DataTable(
                                header = listOf("") + f.shareholding.map { it.label },
                                rows = listOf(
                                    listOf("Promoters") + f.shareholding.map { Fmt.pct(it.promoters) },
                                    listOf("FII") + f.shareholding.map { Fmt.pct(it.fii) },
                                    listOf("DII") + f.shareholding.map { Fmt.pct(it.dii) },
                                    listOf("Government") + f.shareholding.map { Fmt.pct(it.government) },
                                    listOf("Public") + f.shareholding.map { Fmt.pct(it.publicHolding) }
                                ),
                                firstColumnWidth = 150,
                                columnWidth = 92
                            )
                        }
                    }
                }
            }
            6 -> {
                item {
                    SectionCard(
                        title = "Price history",
                        subtitle = if (state.bars.isEmpty()) "No bars stored yet" else "${state.bars.size} daily bars",
                        trailing = {
                            Text(
                                if (state.syncing) "Syncing..." else "Sync",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { if (!state.syncing) vm.syncNow() }
                            )
                        }
                    ) {
                        var rangeDays by remember(companyId) { mutableStateOf(365) }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(30 to "1M", 90 to "3M", 365 to "1Y", 1095 to "3Y", 1825 to "5Y").forEach { (days, label) ->
                                FilterChip(
                                    selected = rangeDays == days,
                                    onClick = { rangeDays = days },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val window = PriceSeries.since(state.bars, rangeDays)
                        CandleChart(
                            bars = PriceSeries.downsample(window, 260),
                            smaShort = if (rangeDays >= 90) PriceSeries.sma(window, 50) else null,
                            smaLong = if (rangeDays >= 365) PriceSeries.sma(window, 200) else null,
                            showVolume = true
                        )
                        if (state.syncMessage != null) {
                            Spacer(Modifier.height(8.dp))
                            MessageBanner(state.syncMessage!!, if (state.syncMessage!!.startsWith("Sync failed")) Tone.Negative else Tone.Positive)
                        }
                        if (state.bars.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tap Sync to pull five years of daily prices for this company from the public quote endpoint. The request happens on this device.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (state.stats != null) {
                    item {
                        SectionCard(title = "Series statistics", subtitle = "Over the stored history") {
                            val stats = state.stats!!
                            KeyValueRow("Bars stored", stats.barCount.toString())
                            KeyValueRow("Period high", Fmt.rupees(stats.periodHigh, 2))
                            KeyValueRow("Period low", Fmt.rupees(stats.periodLow, 2))
                            KeyValueRow("Total return", stats.changePercent?.let { Fmt.pct(it) } ?: "-",
                                if ((stats.changePercent ?: 0.0) >= 0) Tone.Positive else Tone.Negative)
                            KeyValueRow("Maximum drawdown", stats.maxDrawdownPercent?.let { Fmt.pct(it) } ?: "-", Tone.Negative)
                            KeyValueRow("Average volume", Fmt.num(stats.averageVolume.toDouble(), 0))
                        }
                    }
                }
                item {
                    SectionCard(title = "P/E band", subtitle = "Price against trailing reported EPS, with a 120-day reporting lag") {
                        PeBandChart(state.peBand)
                        val percentile = PriceSeries.pePercentile(state.peBand)
                        if (percentile != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Today's multiple sits above ${Fmt.pct(percentile)} of its own history over this window.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            7 -> {
                item {
                    SectionCard(
                        title = "Technical snapshot",
                        subtitle = if (state.bars.isEmpty()) "Sync prices to populate this" else "From ${state.bars.size} daily bars"
                    ) {
                        val snapshot = Technical.snapshot(state.bars)
                        if (state.bars.size < 20) {
                            Text(
                                "Indicators need at least twenty sessions. Sync this company's price history from the Price tab.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            KeyValueRow("RSI (14)", snapshot.rsi14?.let { Fmt.num(it, 1) } ?: "-",
                                when {
                                    snapshot.rsi14 == null -> Tone.Neutral
                                    snapshot.rsi14!! >= 70 -> Tone.Negative
                                    snapshot.rsi14!! <= 30 -> Tone.Positive
                                    else -> Tone.Neutral
                                })
                            KeyValueRow("RSI reading", Technical.rsiLabel(snapshot.rsi14))
                            KeyValueRow("MACD", snapshot.macd?.let { Fmt.num(it, 2) } ?: "-")
                            KeyValueRow("MACD signal", snapshot.macdSignal?.let { Fmt.num(it, 2) } ?: "-")
                            KeyValueRow("MACD histogram", snapshot.macdHistogram?.let { Fmt.num(it, 2) } ?: "-",
                                if ((snapshot.macdHistogram ?: 0.0) >= 0) Tone.Positive else Tone.Negative)
                            KeyValueRow("Bollinger upper", Fmt.rupees(snapshot.bollingerUpper, 2))
                            KeyValueRow("Bollinger lower", Fmt.rupees(snapshot.bollingerLower, 2))
                            KeyValueRow("ATR (14)", snapshot.atr14?.let { Fmt.num(it, 2) } ?: "-")
                            KeyValueRow("Momentum 6m", snapshot.momentum6m?.let { Fmt.pct(it) } ?: "-",
                                if ((snapshot.momentum6m ?: 0.0) >= 0) Tone.Positive else Tone.Negative)
                            KeyValueRow("Momentum 12m", snapshot.momentum12m?.let { Fmt.pct(it) } ?: "-",
                                if ((snapshot.momentum12m ?: 0.0) >= 0) Tone.Positive else Tone.Negative)
                            KeyValueRow("Distance from 200 DMA", snapshot.distanceFromSma200?.let { Fmt.pct(it) } ?: "-")
                            KeyValueRow("Annualised volatility", snapshot.annualisedVolatility?.let { Fmt.pct(it) } ?: "-")
                            KeyValueRow("Volume vs 20 day average", snapshot.volumeSpikeRatio?.let { Fmt.times(it) } ?: "-")
                        }
                    }
                }
                if (state.bars.size >= 20) {
                    item {
                        SectionCard(title = "RSI (14) history", subtitle = "Above 70 is conventionally overbought, below 30 oversold") {
                            val rsiSeries = Technical.rsi(state.bars).takeLast(180)
                            Sparkline(rsiSeries, color = ArthamColors.Sky)
                        }
                    }
                    item {
                        SectionCard(title = "MACD histogram", subtitle = "Positive means the short average is above the long one") {
                            val macd = Technical.macd(state.bars).takeLast(120)
                            BarSeries(
                                labels = macd.map { "" },
                                values = macd.map { it?.histogram }
                            )
                        }
                    }
                }
            }
            else -> {
                item {
                    SectionCard(title = "Peers in ${record.company.sector.ifBlank { "this sector" }}") {
                        if (state.peers.isEmpty()) {
                            EmptyState("No peers", "Peer comparison needs more than one company in the same sector.")
                        } else {
                            state.peers.forEach { peer ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenCompany(peer.company.id) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(peer.company.code, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            peer.company.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        "ROE ${peer.roe}   P/E ${peer.pe}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                MessageBanner(
                    "Values are computed from the data stored on this device. They are not a substitute for the audited annual report.",
                    Tone.Neutral
                )
            }
        }
    }
}
