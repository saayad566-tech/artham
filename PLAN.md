# PLAN — Artham v1.0.0

Living build plan. Status is cross-checked against the code and the build, not from memory.

| # | Item | Status | Evidence |
|---|---|---|---|
| 1 | Teardown of the reference product, public surface only | done | `RESEARCH_SCREENER.md` |
| 2 | Project scaffold, Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 / Compose | done | `app/build.gradle.kts` |
| 3 | Local SQLite schema, 11 tables, hand-written SQL | done | `data/Db.kt` |
| 4 | DAOs for company, watchlist, screen, chat, models, log, import | done | `data/Dao.kt` |
| 5 | CSV reader + XLSX reader (ZIP + XML, no office dependency) | done | `data/import/Csv.kt`, `XlsxReader.kt` |
| 6 | Import shape detection: transposed, long, wide snapshot | done | `data/import/ImportService.kt` |
| 7 | Labelled synthetic demo dataset (12 entities) | done | `data/DemoData.kt` |
| 8 | Ratio library with documented formulas | done | `domain/Ratios.kt` |
| 9 | 47-metric catalog as the single vocabulary | done | `domain/Metrics.kt` |
| 10 | Query lexer, parser, AST, evaluator | done | `domain/Query.kt` |
| 11 | Screen engine over the local dataset | done | `domain/Evaluator.kt` |
| 12 | 12 ready-made screens, every query compiled in a test | done | `domain/Templates.kt`, `EngineTest` |
| 13 | Deterministic strengths/concerns checklist | done | `domain/ProsCons.kt` |
| 14 | On-device engine (MediaPipe / LiteRT) | done | `llm/MediaPipeEngine.kt` |
| 15 | Local-server engine (Ollama + OpenAI-compatible, streaming) | done | `llm/LocalServerEngine.kt` |
| 16 | Grounded prompt builder | done | `llm/PromptBuilder.kt` |
| 17 | Natural language to query, model + offline fallback | done | `llm/NlToQuery.kt` |
| 18 | Model import with checksum into app-private storage | done | `llm/ModelPacks.kt` |
| 19 | Seven screens + navigation + design system | done | `ui/` |
| 20 | 22 unit tests | done | `EngineTest.kt`, 22/22 pass |
| 21 | Compiled, signed, installable APK | done | `app-debug.apk`, v2 signed, 4 ABIs |
| 22 | CI workflow | written, blocked | GitHub Actions fails on this account with no logs |

## v2.0 — market-gap wave

| # | Item | Status | Evidence |
|---|---|---|---|
| 23 | Deep market research with evidence grading | done | `STRATEGY.md` |
| 24 | Schema v3: portfolios, transactions, prices, documents, chunks, alerts | done | `data/Db.kt`, migration path |
| 25 | Portfolio engine: FIFO cost basis, realised/unrealised, XIRR by bisection | done | `domain/PortfolioEngine.kt` |
| 26 | Valuation: DCF, sensitivity grid, reverse DCF, relative percentile | done | `domain/Valuation.kt` |
| 27 | Point-in-time backtesting with quotes stripped for history | done | `domain/Backtest.kt` |
| 28 | BM25 document index with chunking and citations | done | `domain/DocumentIndex.kt` |
| 29 | CSV export through FileProvider | done | `data/Export.kt` |
| 30 | Six new screens: Portfolio, Backtest, Documents, Compare, Alerts, More | done | `ui/` |
| 31 | Company "Value" tab with editable DCF | done | `ui/CompanyScreen.kt` |
| 32 | 19 further unit tests | done | `V2EngineTest.kt`, 41/41 total |
| 33 | v2.0.0 APK compiled, signed, verified | done | 58 MB, versionCode 2 |

## Not in v1 (explicitly out of scope)

Spreadsheet export, email alerts, announcements feed, price sync from a live source,
multi-device sync. Each needs either a server or a data licence, and shipping a stub
for them would be worse than not shipping them.
