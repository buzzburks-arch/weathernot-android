#!/usr/bin/env bash
# Builds the WeatherNot app and launches it on a running emulator/device --
# one command instead of Android Studio's Run button, for iterating from
# a terminal/VS Code.
#
# Usage:
#   ./scripts/build-and-run.sh [device-serial]
#
#   ./scripts/build-and-run.sh                # first attached emulator/device
#   ./scripts/build-and-run.sh emulator-5554  # a specific one
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

APPLICATION_ID="com.weathernot.app"
MAIN_ACTIVITY=".MainActivity"

if [ ! -f "local.properties" ]; then
  echo "No local.properties found -- run ./scripts/setup.sh first (or see README.md)." >&2
  exit 1
fi

# Falls back to local.properties' sdk.dir when adb isn't on PATH, so this
# works right after ./scripts/setup.sh even before the shell profile is
# updated.
if command -v adb >/dev/null 2>&1; then
  ADB="adb"
else
  SDK_DIR=$(awk -F'=' '/^sdk\.dir=/{print $2}' local.properties | tail -1)
  ADB="$SDK_DIR/platform-tools/adb"
  if [ ! -x "$ADB" ]; then
    echo "adb not found on PATH or under $SDK_DIR/platform-tools -- run ./scripts/setup.sh." >&2
    exit 1
  fi
fi

DEVICE="${1:-}"
if [ -z "$DEVICE" ]; then
  DEVICE=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')
  if [ -z "$DEVICE" ]; then
    echo "No running emulator/device found. Start one (./scripts/setup.sh created a 'WeatherNot_Test' AVD), or pass a device serial explicitly." >&2
    exit 1
  fi
fi
echo "Device: $DEVICE"

# Gradle's own embedded Kotlin-DSL compiler (used to parse *.gradle.kts,
# separate from the app's own compileOptions/jvmTarget=17) can't parse
# very new JDK version strings like "26.0.2.1" -- this fails before any
# task even starts, with an opaque "26.0.2.1" error. JAVA_HOME (unset by
# default here) is whatever `java` resolves to on the machine, which may
# well be a JDK too new for this Gradle version -- pin it to a known-
# compatible one if one's already installed, regardless of the system
# default.
needs_compatible_jdk=true
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  current_major=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1)
  if [ -n "$current_major" ] && [ "$current_major" -ge 11 ] && [ "$current_major" -le 21 ]; then
    needs_compatible_jdk=false
  fi
fi
if [ "$needs_compatible_jdk" = true ]; then
  for candidate_prefix in openjdk@21 openjdk@17 openjdk; do
    if command -v brew >/dev/null 2>&1; then
      candidate="$(brew --prefix "$candidate_prefix" 2>/dev/null)/libexec/openjdk.jdk/Contents/Home"
      if [ -x "$candidate/bin/java" ]; then
        export JAVA_HOME="$candidate"
        break
      fi
    fi
  done
  if [ -z "${JAVA_HOME:-}" ]; then
    for v in 21 17; do
      candidate=$(/usr/libexec/java_home -v "$v" 2>/dev/null || true)
      if [ -n "$candidate" ]; then
        export JAVA_HOME="$candidate"
        break
      fi
    done
  fi
  if [ -n "${JAVA_HOME:-}" ]; then
    echo "Using JDK at $JAVA_HOME (system default is incompatible with this Gradle version)."
  else
    echo "Warning: no JDK 17-21 found (needed to run Gradle itself) -- install one, e.g. 'brew install openjdk@17'." >&2
  fi
fi

echo "Building..."
./gradlew :app:assembleDebug

APK_PATH=$(find app/build/outputs/apk/debug -name '*.apk' | head -1)
if [ -z "$APK_PATH" ]; then
  echo "Build succeeded but no APK was found under app/build/outputs/apk/debug." >&2
  exit 1
fi

echo "Installing $APK_PATH..."
"$ADB" -s "$DEVICE" install -r "$APK_PATH"

echo "Launching $APPLICATION_ID..."
"$ADB" -s "$DEVICE" shell am start -n "$APPLICATION_ID/$APPLICATION_ID$MAIN_ACTIVITY"
