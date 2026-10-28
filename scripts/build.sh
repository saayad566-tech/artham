#!/usr/bin/env bash
# One-command build with the failure modes this project has actually hit handled.
set -euo pipefail
cd "$(dirname "$0")/.."

# Gradle needs a real address space. A constrained shell reports
# "Could not reserve enough space for object heap" instead of a build error.
ulimit -v unlimited 2>/dev/null || true

if [ -f local.properties ]; then
  SDK_DIR=$(sed -n 's/^sdk.dir=//p' local.properties)
  export ANDROID_HOME="${ANDROID_HOME:-$SDK_DIR}"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
fi

TASK="${1:-assembleDebug}"
echo "==> ./gradlew $TASK"
./gradlew --no-daemon "$TASK"

APK=app/build/outputs/apk/debug/app-debug.apk
if [ -f "$APK" ]; then
  echo
  echo "==> artifact"
  ls -lh "$APK"
  if [ -x "$ANDROID_HOME/build-tools/34.0.0/apksigner" ]; then
    "$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --print-certs "$APK" | head -3
  fi
  sha256sum "$APK"
fi
