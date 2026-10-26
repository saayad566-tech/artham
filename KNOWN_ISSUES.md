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
