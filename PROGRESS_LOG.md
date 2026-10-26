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
