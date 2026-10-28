# Progress log

## Session 1 — build to a verified APK

1. Read the reference product's public pages. Recorded what is verified versus inferred
   in `RESEARCH_SCREENER.md`. Decided the boundaries: no scraping, no asset copying, no
   clean-room claim.
2. Provisioned a toolchain in the sandbox: Temurin JDK 17, Gradle 8.9, Android SDK 34 +
   build-tools 34.0.0.
3. Wrote the application: 38 Kotlin files across core, data, domain, llm and ui.
4. First compile: three errors — a private type leaking through an `internal` function,
   a `String`/`Enum` argument mismatch, and a `@Composable` call inside a draw scope.
   All three fixed.
5. Local build blocked twice by the environment, not the code:
   - the pod's virtual-memory ulimit was 1.5 GB (raised: the hard limit allowed it);
   - all egress goes through an HTTP proxy the JVM ignores (passed explicitly to Gradle).
6. Dexing was the long pole; the extended icon set was the cause. Swapped to the core
   pack and removed the dependency.
7. `assembleDebug` succeeded. Verified the artifact: package `in.artham.fundamentals.debug`,
   minSdk 24, targetSdk 34, launchable `MainActivity`, native libs for four ABIs,
   APK Signature Scheme v2 valid.
8. Wrote 22 unit tests. First run: 17 passed, 5 failed. Three failures were wrong
   expectations in my own tests, one was a real parser gap (`Mar-24` periods were not
   recognised), one was `android.util.Log` not being mocked. Fixed all five; 22/22 pass.
9. Rebuilt the APK from the corrected sources and re-verified.
10. Attempted a CI build as a second route. Every workflow on this GitHub account —
    including a one-line `echo` probe — fails within five seconds with no logs produced,
    which is an account-level Actions limit rather than a workflow defect. Reported, not
    hidden; the local build is the verified path.

## Session 2 — market research and the v2 wave

1. Researched the category rather than the codebase: user base, revenue pool, competitor
   pricing, AI metering and the DPDP Act. Recorded basis tags and named what I did not
   check. Two market-size estimates disagreed by ~40%, so I acted on the conservative one.
2. Concluded that "trillion dollar" cannot come from Indian retail research alone — a
   $13B revenue pool does not produce a trillion-dollar company at any sane multiple — and
   wrote the honest version in `STRATEGY.md` instead of a feature list.
3. Chose four capabilities that close observed market gaps rather than gaps in my own
   backlog: point-in-time backtesting, reverse DCF, portfolio with XIRR, and on-device
   document retrieval.
4. Built schema v3 with a migration path, four engines, six screens and a navigation
   rebuild.
5. First compile: six errors — a `@Composable` read inside a `Canvas` scope, two missing
   imports, and three icons (`Storage`, `Download`, `Download`) that are not in the core
   Material icon pack. All fixed.
6. First test run: 40 of 41 passed. The single failure was my own expectation — I asserted
   two retrieval hits where only one chunk actually contained the query terms. Corrected
   the test, not the code.
7. Rebuilt and verified the v2.0.0 APK: signed, v2 scheme, four ABIs, new engines present
   in the dex.

## Session 3 — live data, charts, and a recycled sandbox

1. Verified the public endpoints live before writing code: the exchange master file returned
   2,615 securities; the chart endpoint returned five years of daily bars; the fundamentals
   endpoint returned four annual and five quarterly periods. Checked a bank and a
   mid-cap to confirm the mapping holds across statement shapes.
2. The sandbox was recycled mid-session and took the working tree and the toolchain with it.
   Restored the v2 source from the saved archive, kept the new network layer, and
   re-provisioned JDK 17, Gradle 8.9 and the Android SDK.
3. Built the data layer: HTTP client with throttling, two parsers written as pure functions,
   schema v4, and a resumable sync service.
4. Built the charts on the platform Canvas: candles, volume, moving averages, P/E band,
   stacked ownership bar.
5. First compile: two errors, both real — a local counter shadowed a Boolean parameter.
6. Test run: 59 tests, one failure. The end-to-end test caught a genuine latent bug:
   `sharesOutstanding()` ignored the company's face value and dropped a factor of 10^7,
   so book value, Graham number and DCF were wrong whenever a quote had no market cap. It
   had been wrong since v1 and never surfaced because demo data always carried a market cap.
   Fixed the method; all 59 pass.
7. Built and verified the v3.0.0 APK: signed, v2 scheme, four ABIs, live-data classes present
   in the dex.

## Session 4 — audit, 72 features, toolchain

1. Sandbox recycled again; restored v3 from the saved archive and re-provisioned JDK, Gradle
   and the Android SDK before touching code.
2. Ran the audit first. It found a fabricated valuation in the portfolio (EPS × 15 for any
   holding without a quote), two dead locals, a dead function, a no-op click handler, sync
   failures written to a table nobody displayed, and a silent year assumption in the snapshot
   importer. All fixed before any feature work.
3. Added 72 capabilities across metrics, technicals, calculators, portfolio analytics,
   templates, screens and data integrity — listed in `FEATURES.md`.
4. Moved the price parser into the pure `SheetParsing` object so it could be tested without a
   database or a mock; that was a better design than the original placement.
5. Converted the build to a pinned version catalog.
6. Compile problems were environmental, not logical: a full cold Kotlin compile now takes
   about four and a half minutes, which exceeded the call limit, so the build had to be run
   repeatedly to completion. Recorded in `TOOLCHAIN.md`.
7. Test run: 92 tests, four failures on the first pass — all wrong expectations in my own new
   tests (zero-gain portfolios produce zero contribution shares; two positions is below the
   threshold at which the app will judge concentration; a bad open column does not invalidate
   a good close; the ROIC fixture lacked pre-tax profit). Corrected the tests, not the code.
8. Built and verified the v4.0.0 APK: signed, v2 scheme, versionCode 4.
