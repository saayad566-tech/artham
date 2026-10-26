# Decisions

Each entry records the choice, the alternative, and why.

## D1 — Hand-written SQL instead of Room
Room brings KSP, which pins the Kotlin version exactly and adds a codegen step to every
build. The database here is eleven tables and a dozen queries; hand-written SQL is
shorter, has no build-time coupling, and every query is readable in one file.
**Cost:** no compile-time query validation. **Mitigation:** the DAOs are the only place
SQL appears, and every read maps through one cursor helper.

## D2 — Import instead of scrape
The reference product's data is its product. Artham reads a file the user already owns
and never fetches third-party financials. This keeps the app legitimate, offline and
free of rate limits, at the cost of requiring the user to bring data.

## D3 — Two LLM engines, both real
MediaPipe for a fully in-app model, and an HTTP engine for llama.cpp/Ollama. The HTTP
engine also covers the case where the phone cannot hold a model but a machine on the
LAN can. Both are implemented; neither is a stub.

## D4 — No model weights bundled
Model files are hundreds of megabytes and carry their own licence. The app imports one
the user obtained, copies it to private storage and records its SHA-256.

## D5 — Synthetic demo data, clearly labelled
The app must be usable on install. Twelve fictional companies with generated financials
give every screen something to show. They are flagged `isDemo`, carry a DEMO badge, and
the Settings screen says so. No real company is ever shown with invented numbers.

## D6 — Metric catalog as the contract
Every screen, table, prompt and checklist reads from `MetricCatalog`. Adding a metric in
one place makes it available to the query language, the company page and the analyst
prompt. This is the structural lesson taken from the reference product.

## D7 — Nulls propagate, never become zero
Every ratio returns `Double?`. A missing field renders as a dash. A zero would be a
fabricated fact.

## D8 — Core icon pack, not the extended one
The extended Material icon set is thousands of classes and dominated dexing time on a
constrained machine. Eight icons were mapped onto core equivalents. APK and build time
both dropped sharply.

## D9 — Debug APK as the deliverable
A debug APK installs on any device without a keystore. A release build needs a keystore
the user should own, so the signing config reads it from `app/artham-release.jks` and
falls back cleanly when it is absent.

## D10 — Bisection, not Newton-Raphson, for XIRR
Newton converges faster on well-behaved streams and diverges spectacularly on the irregular
sign-changing flows a real ledger produces (buy, sell, dividend, buy). Bisection is slower
and always bounded. A financial figure that is occasionally wrong is worse than one that is
occasionally absent, so the engine returns null when no root exists in range.

## D11 — Strip the quote for historical backtest years
The tempting shortcut is to evaluate a historical screen with today's price. That leaks the
future and makes every valuation-based screen look brilliant in hindsight. Historical
records are therefore rebuilt with `quote = null`, so price metrics return unknown and the
screen matches fewer companies. The app says so in a warning rather than hiding it.

## D12 — BM25 instead of embeddings for document search
A vector index needs a second model file on top of the language model, on a device where
memory is the binding constraint. BM25 needs nothing but the text, is fast at a few hundred
pages, and can show the user exactly which terms matched — which a cosine score cannot.
The limitation is stated in the UI.

## D13 — No background alert worker
Checking screens while the app is closed means a WorkManager dependency, a notification
permission and battery cost, on a financial dataset the user may consider private. Alerts
are evaluated when the user opens the screen. That is a product decision, documented in the
app, not an omission.

## D14 — Version 2 keeps the debug APK as the deliverable
Still no keystore. A release build needs a signing key the user should own and control;
shipping one generated in a sandbox would be worse than shipping a debug build.
