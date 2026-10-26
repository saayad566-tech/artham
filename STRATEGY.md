# The thesis, stated honestly

**Basis tags used throughout:** `verified` = checked against a primary or first-party
source; `secondary` = a third-party account; `disputed` = credible sources disagree and I
say which reading I act on; `analysis` = my own inference.

---

## 1. The question, and a refusal

The brief was "make it a trillion dollar project". I am not going to pretend that a longer
feature list produces a trillion-dollar company. It does not. Valuation comes from four
things multiplied together:

> **market size × share of it × margin × durability**

Features affect the second term slightly and the fourth term a great deal. So the useful
work here is not "implement all the features" — it is to find the one place where a small
team can hold a position that a larger one cannot easily take, and then build the
machinery that position requires. This document states that position, what it would be
worth if right, and what would prove it wrong.

## 2. What the market actually is

**The user base.** India had **23.77 crore demat accounts in August 2026**, up 1.4% in a
month, but only about **4.6 crore active NSE clients** — an activation ratio of
**19.3%** (`verified`) (Source: Business Today, citing 360 ONE Capital Research, Sept 2026). Four out of five demat accounts belong to someone
who is not actively researching or trading. Separately, the government reports **21.6 crore
demat accounts and 12 crore unique investors** as of September 2025, with **2.35 crore
accounts added in FY26 to December** (`verified`) (Source: PIB / Economic Survey release, Jan 2026).

That gap is the single most important number in this document. The market is not short of
account holders. It is short of **activated researchers**, and the platforms that convert
them are the ones that win the next decade.

**The revenue pool.** Investment research platforms were **$6.5 billion in 2025, forecast
to $13.25 billion by 2030 at 15.2% CAGR** (`secondary`, The Business Research Company)
(Source: The Business Research Company, Sept 2026). A second house puts the same market at **$6.5 billion in 2026 rising to
$18.5 billion by 2034 at 14%** (`secondary`, Stratistics MRC). This is `disputed` on
absolute size — the two disagree by about 40% at the far end. I act on the more conservative
reading, because a thesis that only works under the optimistic number is not a thesis.

**What that implies, arithmetically.** A $13 billion revenue pool in 2030, at a generous
10× revenue multiple for a high-growth software business, is a **$130 billion** market
capitalisation for the *entire category*. A trillion-dollar company cannot come from
serving Indian retail research alone. Anyone who tells you otherwise is selling something.

So the trillion-dollar path has to expand the market, not just take share of it. Two routes
exist, and this product is positioned for both:

1. **Geographic and asset-class expansion** — the same engine over global equities, funds
   and credit, where the revenue pool is 10–20× larger (`analysis`).
2. **Becoming infrastructure** — licensing the on-device analysis and privacy layer to
   brokers, advisers and wealth managers who cannot send client data to a third party
   (`analysis`).

## 3. The three structural gaps

Read the competitor comparison and a pattern appears (`verified` on pricing and features,
(Source: Tapetide vendor comparison, prices checked 24 Oct 2026), (Source: Winvesta 2026 guide to Indian fundamental tools)):

| Gap | Evidence | Why it persists |
|---|---|---|
| **AI is metered** | Screener AI charges ₹10–30 per answer; Trendlyne gives 84 credits a year; StockEdge gates AI behind its top tier (`verified`) (Source: Tapetide vendor comparison, prices checked 24 Oct 2026) | Their marginal cost is real: every answer is a cloud inference bill. They cannot give it away. |
| **No backtesting at the reference product** | Screener has no backtest feature; Trendlyne sells 200–1,200 backtests a year as a paid tier (`verified`) (Source: Winvesta 2026 guide to Indian fundamental tools) | Backtesting needs point-in-time data discipline, which is hard to retrofit. |
| **Data depth is capped by history** | Screener shows ~12 years of annuals and 13 quarters; reverse DCF is a paid Tijori feature (`verified`) (Source: Winvesta 2026 guide to Indian fundamental tools) | Their value is the dataset, so they gate it. |

## 4. The moat: zero marginal cost on intelligence

Every competitor's AI cost scales with usage. Yours does not. That single asymmetry
produces four compounding advantages (`analysis`):

1. **Pricing power.** You can include unlimited analysis in a ₹1,000/year plan where a
   competitor's AI bill would exceed the subscription. Their gross margin forbids it.
2. **Privacy as a product, not a policy.** The DPDP Act 2023 applies to data fiduciaries
   processing digital personal data, and expressly does **not** apply to an individual
   processing data for personal or domestic purposes (`verified`) (Source: Digital Personal Data Protection Act 2023, MeitY). When the
   computation never leaves the device, the compliance surface shrinks to almost nothing —
   and that argument is what sells to advisers and brokers who cannot upload client
   holdings.
3. **Works where the network does not.** Tier-2 and tier-3 investors are the growth
   segment: 3.5 crore of the 5.9 crore mutual-fund investors are from non-tier-1 cities
   (`verified`) (Source: PIB / Economic Survey release, Jan 2026). An app that works on a patchy connection and a ₹12,000
   phone is a different product from a web terminal.
4. **Trust compounds.** A financial tool that provably cannot phone home is a different
   trust category from one that merely promises not to.

**The falsifiable claim:** *for an Indian retail investor doing fundamental research, an
on-device app can deliver equal-or-better analysis at zero marginal cost, and that
advantage is durable because incumbents' cost structure prevents them from matching the
price.*

What would prove it wrong: on-device models staying too weak for useful analysis; phone
memory not growing; or an incumbent eating the inference cost and bundling AI free. The
first is the real risk, and it is the one worth watching.

## 5. What this build actually implements toward that

Not a roadmap slide — shipped, compiled, tested code.

| Capability | Why it matters strategically | State |
|---|---|---|
| On-device analysis with two real engines | The zero-marginal-cost wedge | **built** |
| Point-in-time backtesting | The gap the reference product does not fill | **built** |
| Reverse DCF and sensitivity | Tijori gates this; it is the most useful valuation question | **built** |
| Portfolio with FIFO cost basis and XIRR | Retention: it is the reason to open the app weekly | **built** |
| On-device document retrieval (BM25) | Annual reports and transcripts searched locally — institutional-grade capability at retail price | **built** |
| Query language over 47 metrics | The vocabulary contract; everything else is a projection of it | **built** |
| CSV export | Table stakes; also the escape hatch that makes lock-in unnecessary | **built** |
| Four-way comparison | Table stakes | **built** |

Deliberately **not** built, with reasons in `KNOWN_ISSUES.md`: broker sync (needs
credentials and an aggregator licence), price alerts in the background (needs a worker and
a permission, and the honest version is user-initiated), live price feeds (needs a data
licence), multi-device sync (needs a transport, and the offline-first design makes it a
real project rather than a checkbox).

## 6. Unit economics sketch

A sketch, not a forecast, and labelled as such (`analysis`).

At ₹1,499/year — below StockEdge's ₹1,499 and well below Screener's ₹4,999
(`verified` (Source: Tapetide vendor comparison, prices checked 24 Oct 2026)) — with infrastructure cost near zero because inference is on
the user's device:

| Line | Per user per year |
|---|---|
| Revenue | ₹1,499 |
| Model distribution, app hosting, support | ₹60–120 |
| Payment fees (~2%) | ₹30 |
| **Gross margin** | **~90%** |

The binding constraint is not margin, it is **activation** — and that is where the 19.3%
ratio becomes the whole game. The product has to convert a demat account holder into
someone who runs a screen. Everything in the interface is built for that: no login, no
empty state without a next action, a synthetic dataset so the app is useful in the first
thirty seconds.

## 7. What would have to be true

For the trillion-dollar version, all of these:

1. On-device models reach "good enough analyst" quality on a ₹15,000 phone. *(Trend is
   favourable: model sizes are falling faster than quality.)*
2. The product expands beyond Indian equities to global multi-asset, roughly 10× the pool.
3. An infrastructure licensing motion lands with brokers and advisers, where the privacy
   argument is worth more than the software.
4. Regulatory tailwind: DPDP enforcement makes "we never receive your data" a purchasing
   criterion rather than a marketing line.

If 1 and 2 hold, the category becomes large enough to matter. If 3 lands, the multiple
expands from software to infrastructure. Both are outside what any single build can prove.

## 8. What I did not research

Named explicitly, because a research note that claims completeness is lying:

- No primary research with users. No interviews, no survey, no churn data.
- No verification of competitors' revenue, user counts or retention. Their pricing and
  feature claims are checked against their own pages; their financials are not.
- No legal opinion on SEBI research-analyst registration. A product that generates
  opinions about securities may need registration in India; that is a question for a
  lawyer, and this app deliberately stops at "here is what the numbers show".
- No validation that any incumbent's cost structure actually prevents them from matching
  free AI. That is my inference from public pricing, not a verified fact.
- No device testing. Nothing in this build has run on real hardware.

---

## 9. The honest one-line version

The trillion-dollar outcome is not a feature count; it is a bet that **intelligence about
your own money should run on your own device**, and that the incumbents' cost structure
stops them from following. This build is the smallest complete system that makes that bet
real and testable.
