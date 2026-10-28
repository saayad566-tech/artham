# Toolchain

Every version lives in `gradle/libs.versions.toml`. Nothing else hard-codes one.

## Required

| Component | Version | Why this one |
|---|---|---|
| JDK | Temurin 17 | AGP 8.7 requires 17; 21 also works but is not what was tested |
| Gradle | 8.9 | Minimum for AGP 8.7.3 |
| Android Gradle Plugin | 8.7.3 | |
| Kotlin | 2.0.21 | Compose compiler ships as a Kotlin plugin from 2.0 |
| Compose BOM | 2024.10.01 | Pins every Compose artifact together |
| compileSdk / targetSdk | 34 | |
| minSdk | 24 | Android 7.0, chosen for reach |
| Build tools | 34.0.0 | |
| Android SDK platform | android-34 | |

## Runtime dependencies

| Artifact | Version | Role |
|---|---|---|
| androidx.core:core-ktx | 1.13.1 | |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.8.7 | |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.7 | |
| androidx.activity:activity-compose | 1.9.3 | |
| androidx.compose:compose-bom | 2024.10.01 | ui, ui-graphics, material3, tooling |
| androidx.navigation:navigation-compose | 2.8.4 | |
| kotlinx-coroutines-android | 1.9.0 | |
| com.google.mediapipe:tasks-genai | 0.10.24 | On-device LLM inference. The only heavyweight dependency; it carries native libraries for four ABIs, which is most of the 58 MB APK |

Test-only: `junit:junit:4.13.2`, `kotlinx-coroutines-test:1.9.0`, and `org.json:json:20240303`
— the last one exists because the `android.jar` stub returns defaults instead of parsing, so
the JSON parser tests would silently pass on nothing.

**Deliberately not included:** Room (needs KSP and pins the Kotlin version — the database is
hand-written SQL), Retrofit/OkHttp (the HTTP layer is 100 lines and does exactly what is
needed), any charting library (the charts are drawn on the platform Canvas), the extended
Material icon pack (thousands of classes for eight icons; it dominated dexing time).

## Setup

```bash
./tools/setup-toolchain.sh     # JDK, Gradle, Android SDK, local.properties
./scripts/build.sh             # assembleDebug, then verify and hash the APK
./gradlew testDebugUnitTest    # 92 unit tests
```

Or with no host dependencies at all:

```bash
docker build -t artham .
docker create --name artham-out artham
docker cp artham-out:/app/app/build/outputs/apk/debug/app-debug.apk .
```

## Environment problems this project has actually hit

These are recorded because each one cost a build cycle and each looks like a code error:

1. **`Could not reserve enough space for object heap`** — the shell capped virtual address
   space (`ulimit -v`). `scripts/build.sh` raises it. Shrinking the heap instead makes Kotlin
   fail with a native malloc error that looks unrelated.
2. **`Could not allocate compressed class space`** — a small memory cgroup. Fix:
   `-XX:-UseCompressedClassPointers` and a modest `-XX:MaxMetaspaceSize`.
3. **`UnknownHostException` while `curl` works** — all egress goes through an HTTP proxy
   configured in environment variables, which the JVM ignores. Pass
   `-Dhttps.proxyHost` / `-Dhttps.proxyPort` to Gradle, or set `systemProp.https.proxyHost`.
4. **`mergeDebugGlobalSynthetics` and `dexBuilderDebug` appearing to hang** — they are slow,
   not stuck, on a two-core machine. They complete; re-running resumes from cache.
5. **Kotlin compilation exceeding a short wall-clock limit** — with 14.6k lines and Compose,
   a full cold compile takes about four and a half minutes. `kotlin.incremental=true` keeps
   subsequent runs short.
