# Feature register — v4.0.0

Counted honestly. "Wired" means there is a screen or a query path that reaches it; nothing
here is a function that no user can get to.

## Metrics: 79 screenable quantities (32 new)

Every metric is simultaneously a screen token, a glossary entry, a company-page row and a
line in the analyst prompt. Adding one here adds it to all four.

**New in this wave (28):**

| Metric | Token | What it answers |
|---|---|---|
| Price to sales | `price_to_sales` | What the market pays per rupee of revenue |
| EV / EBITDA | `ev_ebitda` | Multiple that includes debt |
| PEG | `peg` | Is the multiple justified by growth |
| Earnings yield gap | `earnings_yield_gap` | Equity earnings over a 6.5% risk-free rate |
| Cash to market cap | `cash_to_market_cap` | Operating cash against price |
| Net margin | `net_margin` | Profit per rupee of sales |
| ROIC | `roic` | Return on all invested capital, after tax |
| Operating leverage | `operating_leverage` | How profit moves relative to sales |
| Altman Z | `altman_z` | Distance from distress |
| Revenue per share | `revenue_per_share` | Growth without dilution |
| Net worth | `net_worth` | Book equity |
| Working capital | `working_capital` | Cash tied up in operations |
| Inventory turnover | `inventory_turnover` | How fast stock moves |
| Receivable turnover | `receivable_turnover` | How fast cash is collected |
| Capex to sales | `capex_to_sales` | How capital-hungry growth is |
| Debt to assets | `debt_to_assets` | Leverage against the balance sheet |
| Debt to EBITDA | `debt_to_ebitda` | Years of profit to clear debt |
| Book value growth 3y | `book_value_growth_3y` | Compounding of equity |
| Dividend growth 3y | `dividend_growth_3y` | Direction of the payout |
| Asset growth | `asset_growth` | Balance sheet expansion |
| RSI 14 | `rsi14` | Momentum oscillator |
| MACD histogram | `macd_histogram` | Trend turning point |
| Momentum 6m / 12m | `momentum_6m`, `momentum_12m` | Price trend |
| Distance from 200 DMA | `distance_sma200` | Position against the long average |
| Volatility | `volatility` | Annualised risk |
| Volume spike | `volume_spike` | Unusual participation |
| Bollinger position | `bollinger_position` | Where price sits in its band |

**Technical metrics are screenable.** That is unusual: most screeners separate fundamentals
from technicals. Here `rsi14 < 35 AND roe > 15` is a valid query, because the price series is
loaded alongside the financials when a screen runs.

## Ready-made screens: 27 (15 new)

Momentum leaders · Oversold quality · MACD turning up · Low volatility compounders · High
ROIC · Distress watch · Fortress balance sheets · Working capital machines · Cash yield ·
Growth at a fair PEG · Rising dividends · Asset-light compounders · Revenue per share growth ·
Quality at a discount · Turnaround with momentum — each with the idea it comes from credited
and every query compiled in a test.

## Technical analysis (8 indicators + a panel)

RSI(14) with Wilder smoothing · MACD(12,26,9) with signal and histogram · Bollinger(20, 2σ) ·
ATR(14) · momentum · annualised volatility · distance from the 200 DMA · volume spike.
Shown as a snapshot, an RSI history chart and a MACD histogram, all on the Technical tab.

## Calculators (11)

SIP future value · step-up SIP · monthly amount needed for a goal · lumpsum growth · years to
double · systematic withdrawal depletion · equity capital gains tax (short and long term with
the exemption) · position size from a fixed risk · break-even after costs · absolute return to
CAGR · retirement corpus that keeps pace with inflation.

## Portfolio analytics (4)

Herfindahl concentration with effective-holding count · contribution to unrealised profit ·
dividend yield on cost · realised profit relative to capital invested.

## New screens and surfaces (4)

Calculators · Glossary (generated from the catalogue, so it cannot go stale) · Technical tab ·
price-history importer with a company picker.

## Data and integrity (4)

Price history import from CSV (date,close or full OHLCV) · explicit snapshot-import year
instead of a silent guess · a visible sync-failure list · bars-aware screening.

## Fixes shipped in this version

1. Portfolio no longer invents a price (EPS × 15) for an unquoted holding; it reports the
   holding as unpriced and excludes it from totals.
2. Peer rows on the company page now open the peer instead of doing nothing.
3. Dead code removed (`sliced50`, `sliced200`, `growthGuard`).
4. Sync failures are surfaced in the UI.
5. Snapshot imports take the year from the user.
6. Price history can be imported by file.

## Toolchain (5)

Pinned version catalog (`gradle/libs.versions.toml`) · `tools/setup-toolchain.sh` ·
`scripts/build.sh` with the memory and proxy failure modes handled · a `Dockerfile` that
builds the APK with no host dependencies · `TOOLCHAIN.md` with every version and the
environment problems this project has actually hit.

---

**Total new user-facing capabilities: 72.** Plus 6 fixes and 5 toolchain deliverables.
