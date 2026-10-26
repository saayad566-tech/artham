# Known issues and limitations

## Blocking for distribution, not for use

1. **CI cannot run on this GitHub account.** Every workflow, including a trivial probe,
   fails in about five seconds and produces zero log entries. That signature is an
   account-level Actions restriction (minutes or spending limit), not a workflow bug.
   The workflow file itself is complete and lints clean. To use it, resolve billing on
   the account or run the build locally — the local build is verified and works.

## Functional limitations, stated in the app

2. **Shares outstanding** come from the quote when present; otherwise equity capital is
   divided by an assumed face value of ₹10. Every per-share figure depends on this, and
   the assumption is documented in `Financials.kt`.
3. **Piotroski F-score** substitutes two of the nine signals because no current ratio is
   stored: "current ratio improved" becomes working-capital intensity, and "no new
   shares" compares equity capital. Documented in `Ratios.kt`.
4. **Free cash flow** is CFO + CFI, so it treats investment income as capex. Adequate for
   a screening filter, not for a valuation argument.
5. **DCF** is a two-stage model on the latest FCF with fixed defaults (12% growth, 13%
   discount, 4% terminal). It is a model, not a target price, and the inputs are shown.
6. **Wide-snapshot imports** with no period column assume the previous calendar year and
   say so in a warning.

## Not implemented in v1

7. Spreadsheet export, email alerts, an announcements/filings feed, live price sync and
   multi-device sync. Each needs a server or a data licence.

## Untested on hardware

8. The app has not been run on a physical device or emulator in this environment. The
   APK is verified structurally (manifest, signature, dex, native libraries, launchable
   activity) and the logic is covered by unit tests, but first-run behaviour, Compose
   layout on real screens and model loading need a device. Model loading in particular
   depends on the file the user imports.

## Added in v2.0

9. **Backtesting has no price data by default.** The app stores no historical prices, so
   the default backtest answers "did the businesses grow", not "what would the share price
   have returned". Price-history import exists in the schema but no importer UI yet. This
   is stated in the app on every backtest run.
10. **Survivorship in your own dataset.** If the companies you imported are the ones that
    survived, the backtest inherits that bias. The app warns; it cannot correct it.
11. **Document retrieval is lexical (BM25).** A question phrased in different vocabulary
    from the document will miss. Semantic search needs an embedding model, which is a
    second model file — deliberately out of scope.
12. **No PDF text extraction.** Documents must be plain text. Scanned PDFs need OCR
    first, which this app does not do.
13. **Alerts are user-initiated.** No background worker, no notification permission. The
    count is refreshed when you open the Alerts screen. Stated in the UI.
14. **No broker sync.** Broker integration needs credentials, an aggregator licence and a
    security review. Deliberately absent.

## Added in v3.0 — data depth and scope

15. **Fetched history is shallow: about four annual periods and five quarters.** A mature
    screener shows twelve years. Deeper history must be imported; imported rows merge over
    fetched ones.
16. **Two fundamentals are derived, not reported:** depreciation (EBITDA − EBIT) and investing
    cash flow (capex only). Both are documented in `DATA_SOURCES.md`.
17. **No segment data, shareholding pattern or pledge data from the live source.** The
    ownership tab is empty until you import it.
18. **Stale tickers fail silently per company.** Demergers leave symbols that no longer
    resolve; the failure is recorded and the batch continues.
19. **The public endpoints are not a licence.** Fine for personal research on your own
    device; distributing the app commercially needs a data licence. See `DATA_SOURCES.md`.
20. **Still not run on hardware.** Charts, sync and model loading are verified structurally
    and by unit test only.
