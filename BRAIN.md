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

## Impact radius — what touches what

- `MetricCatalog` is the contract for: the query language, the company page ratio list,
  the prompt context, and the checklist. Adding a metric is safe; renaming an `id`
  breaks saved screens, so `id` is treated as an API.
- `Financials` is the only view over stored rows. Any ratio that needs a new field needs
  it added to the schema, the model, the DAO mapping and the importer together.
- `SheetParsing` is pure and unit-tested; `ImportService` is the only caller. Keep it
  that way or the parser becomes untestable again.
- The engine interface is the seam: a third engine means one new class and one branch in
  `LlmManager`, nothing else.

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
