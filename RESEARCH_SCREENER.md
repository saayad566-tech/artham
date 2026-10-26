# Teardown: what a fundamental screener actually does

Research note written before any code. Every claim carries its basis: **verified** (checked against
the live public surface), **secondary** (a third party's account), or **analysis** (my own inference
from observed behaviour).

## Scope and boundaries

The target is a public website serving company pages, a screen builder and saved screens. I worked
only the top rungs of the public-surface ladder:

1. The public pages themselves, fetched normally. No login, no paywall bypass, no rate-limit evasion.
2. No attempt was made to defeat any protection, and no account was created.

**What I deliberately did not do:** copy their markup, their CSS, their icons, their prose, their
compiled code, or their dataset. Their financial data is their product and is not redistributable.
Artham therefore ships **no** third-party data and reads only files the user already owns. This is a
one-agent analysis and a one-agent rebuild, so it is **not** a clean-room reconstruction and is not
claimed as one.

## What the public pages expose

Observed on a live company page for a large Indian IT-services company:

* A header with price, day change, and a compact block of headline figures: market cap, current
  price, 52-week high and low, stock P/E, book value, dividend yield, ROCE, ROE, face value.
  *(verified)*
* A free-text "About" block and a "Key Points" block, including a revenue break-up by vertical.
  *(verified)*
* A **Pros** and **Cons** list, explicitly labelled as machine generated from a checklist.
  *(verified)*
* A peer comparison block, listing the indices the company belongs to. *(verified)*
* A **Quarterly Results** table: sales, expenses, operating profit, OPM %, other income, interest,
  depreciation, profit before tax, tax %, net profit, EPS — with a raw-PDF column. *(verified)*
* A **Profit & Loss** table with the same rows extended across roughly ten annual periods plus a
  trailing-twelve-month column. *(verified)*
* The screens index lists popular screens with descriptions: FII buying, low on 10-year average
  earnings, Magic Formula, growth stocks with a G-factor, highest dividend yield, companies at
  new highs, golden crossover, capacity expansion, Piotroski scan, high growth / high ROE / low PE,
  debt reduction, loss to profit, a Graham-and-Buffett filter, Coffee Can, price-volume action,
  a Darvas scan, RSI oversold, blue chips, value stocks, quarterly growers, multibaggers, breakout
  stocks, growth without dilution. *(verified)*

## The transferable model

**Information architecture** *(analysis)*: one canonical entity (the company), one canonical
longitudinal record (period rows), and everything else is a projection of it — the company page is a
pivot of the period rows, the screen is a filter over the same rows, the peer table is a group-by on
sector. That is the right shape and Artham adopts it.

**The metric layer is the product** *(analysis)*: the screens are not hand-written per screen; they
are expressions over a metric vocabulary. Once the vocabulary exists, a screen is one line. This is
the single most important structural lesson and it drives Artham's `MetricCatalog` plus query
language.

**Pros and cons are a checklist, not a model** *(verified — the page says so)*. They are thresholds
applied to computed ratios. Artham implements the same idea with its own rules and its own wording,
and every sentence names the number that triggered it.

**What they do not do** *(analysis)*: no on-device anything, no offline mode, no local model. Their
machine-generated commentary runs on their servers. That is the gap Artham is built into.

## Feature matrix

| Capability | Target | Artham | Note |
|---|---|---|---|
| Company page with 10y statements | Yes | Yes | Built from imported rows |
| Quarterly results | Yes | Yes | When the import contains them |
| Ratio analytics | Yes | Yes | 47 metrics, formulas documented |
| Query language | Yes | Yes | Own lexer/parser/evaluator, not theirs |
| Saved screens | Yes | Yes | Local table |
| Popular screen templates | Yes | Yes | Reimplemented from the published idea, own thresholds |
| Peer comparison | Yes | Yes | Same-sector, on ROE/ROCE/PE |
| Machine-generated pros/cons | Yes | Yes | Own rule engine, deterministic |
| Shareholding pattern | Yes | Yes | Table + trend |
| Export to spreadsheet | Yes | No | Out of scope for v1 |
| Email alerts | Yes | No | Needs a server; deliberately absent |
| Announcements / filings feed | Yes | No | Needs network + a data source |
| **Offline operation** | No | **Yes** | Local database |
| **On-device language model** | No | **Yes** | No tokens, no upload |

## What I did not check

I did not open their query-builder syntax in detail, their pricing page, their API (if any), their
mobile app, or their export file format internals. Those surfaces were not needed to build Artham,
and guessing at them would have produced invented facts.

## Resulting design decisions

1. Local-first SQLite, hand-written SQL, no annotation processor.
2. A metric catalog as the single vocabulary, with the query language compiled over it.
3. Import over scraping — the user supplies a file they own; the app never fetches third-party data.
4. Two local model engines, both real, selectable, with honest status reporting.
5. Every computed number traceable to a formula in the source, and a dash where data is missing.
