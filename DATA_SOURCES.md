# Where the numbers come from

Everything the app displays is either fetched on your device from a public endpoint or
imported from a file you own. Nothing is bundled, nothing is resold, and no request is made
until you press a sync button.

## 1. The company universe

**Endpoint:** the exchange's published equity master file (`EQUITY_L.csv`), a static CSV of
every listed security.
**Gives:** 2,615 securities with symbol, company name, series, listing date, ISIN and face
value.
**Used for:** the searchable company list, and the face value — which is what lets the app
convert equity capital into a share count instead of assuming ₹10.

Filtering: only `EQ` and `BE` series are kept, so bonds and preference series do not appear
in screens.

## 2. Prices

**Endpoint:** the public chart endpoint, `…/v8/finance/chart/{SYMBOL}.NS?range=5y&interval=1d`.
**Gives:** up to five years of daily OHLCV, plus the current price, 52-week high and low, and
the long company name.
**Used for:** the candlestick chart, the 50/200-day moving averages, the P/E band, series
statistics, and — importantly — historical prices for backtesting and portfolio valuation.

## 3. Fundamentals

**Endpoint:** the public fundamentals timeseries endpoint,
`…/ws/fundamentals-timeseries/v1/finance/timeseries/{SYMBOL}.NS`.
**Gives:** annual and quarterly reported figures. Fields mapped into the app's model:

| App field | Endpoint field | Note |
|---|---|---|
| Sales | `annualTotalRevenue` | |
| Operating profit | `annualEBITDA`, else `annualOperatingIncome` | |
| Depreciation | derived: EBITDA − EBIT | **derived**, not published separately |
| Interest | `annualInterestExpense` | |
| Profit before tax | `annualPretaxIncome` | |
| Tax % | derived: `annualTaxProvision` ÷ PBT | |
| Net profit | `annualNetIncome` | |
| EPS | `annualDilutedEPS`, else `annualBasicEPS` | |
| Dividend per share | derived: `annualCashDividendsPaid` ÷ shares | |
| Equity capital | derived: shares × face value | uses the exchange face value |
| Reserves | derived: `annualStockholdersEquity` − equity capital | |
| Borrowings | `annualTotalDebt` | |
| Total assets | `annualTotalAssets` | |
| Receivables / inventory / payables | `annualAccountsReceivable` / `annualInventory` / `annualAccountsPayable` | |
| Operating cash flow | `annualCashFlowFromContinuingOperatingActivities` | |
| Investing cash flow | `annualCapitalExpenditure` | **capex only**, not the full investing section |
| Quarterly | `quarterlyTotalRevenue`, `quarterlyNetIncome`, `quarterlyDilutedEPS` | |

Units: the endpoint reports rupees; the app stores rupees crore, dividing by 10⁷. A test
asserts the conversion, because a factor-of-ten-million error is the classic failure here.

## Honest limitations

1. **Depth is shallow.** The endpoint returns roughly **four annual periods and five
   quarters**, against the twelve years a mature screener shows. Deeper history has to come
   from your own import, and imported rows are merged over fetched ones.
2. **Two fields are derived, not reported:** depreciation (EBITDA − EBIT) and investing cash
   flow (capex only). Both are labelled in the code and in this table.
3. **No segment data, no shareholding pattern, no pledge data.** The ownership tab stays
   empty unless you import it.
4. **Symbols drift.** Demergers and renames leave stale tickers; a failed sync is recorded
   per company and does not stop the batch.
5. **Nothing is real-time.** Daily bars, end of day. Not a trading tool.

## Rate limiting and politeness

One request at a time, minimum 400 ms apart, a single retry with backoff, a browser user
agent, and a hard timeout. A full 2,615-company sync is not offered as one tap for exactly
this reason: the app syncs a named set (the top 65, or your watchlist) so it cannot become a
flood. Each company is committed as it arrives, so a sync can be stopped and resumed.

## Legal position

The app fetches publicly accessible endpoints for the user's own research, on the user's own
device, and stores the result locally. It does not redistribute data, does not operate a
service over it, and does not scrape any site that forbids it.

This is a defensible personal-research position, **not a licence**. Public endpoints have
terms of use, and those terms can change or forbid automated access. If you intend to
distribute this app commercially, or to serve data to other people, you need a data licence
— from the exchange, or from a vendor. That is a commercial and legal decision this document
cannot make for you, and I am not a lawyer.

The app is built so that swapping the source is a contained change: `SyncService` is the only
class that knows a URL, and `YahooProvider` is the only class that knows a response shape.
