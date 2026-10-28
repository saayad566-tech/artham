# Audit of v3.0.0 — what is broken, incomplete, or dishonest

Run before adding anything. Every finding has a file and a line so it can be checked.

## A. Defects that produce a wrong number

| # | Finding | Where | Why it matters | Fixed |
|---|---|---|---|---|
| A1 | **Fabricated price.** When a holding has no quote, the portfolio values it at `EPS × 15` — an invented multiple — and reports it as market value. | `PortfolioEngine.kt:111` | A made-up number in a P&L is the worst kind of bug. It must be null and visibly unpriced. | yes |
| A2 | `sharesOutstanding()` dropped a factor of 10⁷ and ignored face value | `Financials.kt` | Fixed in v3; regression test added. | v3 |
| A3 | Wide-snapshot import silently assumes the previous calendar year | `ImportService.kt` | A whole year of data can land in the wrong period. Must be user-chosen. | yes |

## B. Incomplete wiring

| # | Finding | Where | Fixed |
|---|---|---|---|
| B1 | Peer row is `clickable` but sets a state field to null — tapping does nothing | `CompanyScreen.kt` | yes |
| B2 | `sliced50` / `sliced200` computed, then used only in a no-op `if` | `CompanyScreen.kt:715` | yes |
| B3 | `@Suppress("unused") growthGuard` — dead function | `Valuation.kt:176` | yes |
| B4 | Sync failures are recorded in `sync_state` but never shown to the user | `DataScreen.kt` | yes |
| B5 | No way to import historical prices by file, though the schema supports them | — | yes |
| B6 | Alerts only re-evaluate screens; no price-cross rules | `AlertsScreen.kt` | yes |

## C. Documented limitations that remain

- Fetched history is ~4 annual periods and 5 quarters (endpoint limit). Import extends it.
- Depreciation and investing cash flow are derived, not reported.
- No segment, shareholding or pledge data from the live source.
- No background worker; alerts evaluate when opened.
- Not run on physical hardware.

## D. Toolchain gaps

| # | Finding | Fixed |
|---|---|---|
| D1 | No reproducible toolchain: no pinned version catalog, no setup script, no container | yes |
| D2 | No documented dependency list with versions | yes |
| D3 | CI workflow cannot run on this account (account-level limit, not a defect) | documented |

## E. Feature register

65 new capabilities in `FEATURES.md`, each with the module it lives in and whether it is
wired to a screen. Counted honestly: a metric is a feature because it is screenable,
chartable and quotable in a prompt.
