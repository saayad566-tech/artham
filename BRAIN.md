# BRAIN — project memory

Read first, written last. Keeps mistakes from repeating.

## Mistakes made and the rule that prevents a repeat

| Mistake | Rule now |
|---|---|
| Wrote `EngineKind.MEDIPAPE_ALIAS` as a placeholder while drafting | Never write a placeholder identifier, even mid-draft. Compile-visible typos are free to fix; shipped ones are not. |
| Used `return@Constructor` inside lambdas passed to a constructor | Use explicit `if/else` expressions inside metric lambdas; no non-local returns from constructor arguments. |
| Left a `@Composable` theme read inside a `Canvas` draw scope | Read theme values into locals before entering a draw scope. |
| Assumed the sandbox's memory cap was hard | Check `ulimit -Hv` before declaring an environment incapable. |
| Assumed JVM networking would work like curl | Check for `http_proxy`/`https_proxy`; the JVM ignores them unless told. |
| Wrote three test expectations from memory instead of from the specification | Derive expected values from the documented rule, not from a guess about the output. |
| Added the extended Material icon set by habit | Check the build cost of a dependency before adding it, especially a large icon or asset pack. |

## Session 2 lessons

| Mistake | Rule now |
|---|---|
| Used `Icons.Outlined.Storage` / `Download`, which are not in the core icon pack | Check the icon exists in `material-icons-core` before using it; the extended pack is excluded for build-time reasons. |
| Wrote a retrieval test asserting two hits when only one chunk contained the terms | Derive expected counts from the data, not from an intuition about the algorithm. |
| Read `MaterialTheme` inside a `Canvas` draw scope again (second time) | Hoist every theme read into a local before entering a draw scope. This is now a standing rule, not a note. |
| Left a `[cite:...]` marker inside a generated markdown file | Citation markers belong in chat replies only. Files get plain `(Source: ...)` prose. |

## Session 3 lessons

| Mistake | Rule now |
|---|---|
| Assumed the working tree would survive between turns | Re-check for `/scratch/work` content at the start of every turn and restore from `/scratch/saved` before writing anything. |
| Named a local counter the same as a Boolean parameter | Never shadow a parameter with a local of a different type. The compiler catches the use, not the intent. |
| Let a share-count assumption sit untested for three versions | Every conversion between units (crore, rupees, shares, face value) gets a test with a real payload. The 10^7 error hid for two releases because the demo data took a different branch. |

## Impact radius — what touches what

- `MetricCatalog` is the contract for: the query language, the company page ratio list,
  the prompt context, and the checklist. Adding a metric is safe; renaming an `id`
  breaks saved screens, so `id` is treated as an API.
- `SyncService` is the only class that knows a URL; `YahooProvider` the only one that knows a
  response shape. Swapping data source means touching those two files.
- `Financials.sharesOutstanding()` feeds book value, Graham number, DCF and portfolio
  valuation. Any change there must re-run the end-to-end ratio test.
- `Financials` is the only view over stored rows. Any ratio that needs a new field needs
  it added to the schema, the model, the DAO mapping and the importer together.
- `SheetParsing` is pure and unit-tested; `ImportService` is the only caller. Keep it
  that way or the parser becomes untestable again.
- The engine interface is the seam: a third engine means one new class and one branch in
  `LlmManager`, nothing else.

## Blind spots worth a look next (v2)

- The price-history importer has a schema and a lookup but no UI, so backtests cannot use
  prices yet. That is the largest unfinished seam.
- `PortfolioEngine` falls back to a placeholder multiple when a company has no quote. That
  is a fabricated value in a real calculation and should become an explicit null.
- No test covers the XLSX reader or `DemoData`.
- The document index rebuilds tokenisation on every query; a corpus of a few hundred pages
  is fine, thousands would need caching.

## Blind spots worth a look next

- No test covers the XLSX reader (it needs a fixture file). That is the largest untested
  surface and the most likely source of a bad import.
- No test covers `DemoData`, which runs on first launch for every user.
- The prompt has no token budget: a company with 25 years of history would build a large
  prompt and could exceed a small model's context. Truncation is worth adding.
- `LlmManager` keeps engine instances for the process lifetime; a model swap closes and
  reloads, but nothing watches memory pressure.

## Maintenance notes

- AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, Gradle 8.9, compileSdk 34. Bumping
  compileSdk to 35 needs AGP 8.7+ (already satisfied) and a fresh SDK install.
- The Gradle wrapper jar is the 8.9.0 distribution's own wrapper.
