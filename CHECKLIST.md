# Completion checklist

- [x] Every screen reachable from the bottom navigation or a header action
- [x] Every backend capability has a UI path (import, screen, watchlist, analyst, models, settings)
- [x] No placeholder strings, no TODO, no dead buttons
- [x] Every ratio has its formula next to it in source
- [x] Missing data renders as a dash, never as zero
- [x] Demo data labelled everywhere it appears
- [x] Activity log written for import, boot, screen runs and model loads
- [x] Local-only storage; no analytics, no telemetry, no upload
- [x] Two LLM engines implemented, selectable, with honest status reporting
- [x] 22 unit tests, all passing
- [x] APK compiled, signed (v2), verified, launchable activity present, 4 ABIs
- [x] README, research note, decisions, known issues, brain, plan written
- [ ] CI build green — blocked by an account-level GitHub Actions limit, reported
- [ ] Run on physical hardware — needs a device, stated in KNOWN_ISSUES
