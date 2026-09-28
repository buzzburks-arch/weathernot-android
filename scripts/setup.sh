#!/usr/bin/env bash
# One-time environment setup: installs the Android SDK pieces this repo
# needs (platform-tools for adb, an emulator + system image, build-tools/
# platform for Gradle) and creates a ready-to-boot AVD -- so
# build-and-run.sh has a working `adb`
# and something to install onto. Safe to re-run; every step here is
# idempotent (brew/sdkmanager/avdmanager all skip what's already there).
#
# Usage:
#   ./scripts/setup.sh
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

AVD_NAME="WeatherNot_Test"
PLATFORM="android-35"
BUILD_TOOLS="35.0.0"
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
  SYSTEM_IMAGE="system-images;${PLATFORM};google_apis;arm64-v8a"
else
  SYSTEM_IMAGE="system-images;${PLATFORM};google_apis;x86_64"
fi

if ! command -v brew >/dev/null 2>&1; then
  echo "Homebrew isn't installed -- install it first: https://brew.sh" >&2
  exit 1
fi

# Resolve (or install) the SDK root. Prefers an sdk.dir this repo already
# has recorded in local.properties, then ANDROID_HOME/ANDROID_SDK_ROOT if
# set, then falls back to installing the android-commandlinetools cask.
SDK_DIR=""
if [ -f "local.properties" ]; then
  SDK_DIR=$(awk -F'=' '/^sdk\.dir=/{print $2}' local.properties | tail -1)
fi
SDK_DIR="${SDK_DIR:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"

if [ -z "$SDK_DIR" ] || [ ! -d "$SDK_DIR" ]; then
  echo "Installing Android SDK command-line tools..."
  brew install --cask android-commandlinetools
  SDK_DIR="$(brew --prefix)/share/android-commandlinetools"
fi
echo "SDK root: $SDK_DIR"

echo "sdk.dir=$SDK_DIR" > local.properties

CMDLINE_BIN=$(find "$SDK_DIR/cmdline-tools" -maxdepth 2 -type d -name bin | head -1)
if [ -z "$CMDLINE_BIN" ]; then
  echo "No cmdline-tools found under $SDK_DIR -- try 'brew reinstall --cask android-commandlinetools'." >&2
  exit 1
fi
SDKMANAGER="$CMDLINE_BIN/sdkmanager"
AVDMANAGER="$CMDLINE_BIN/avdmanager"

echo "Accepting SDK licenses..."
# `yes` gets SIGPIPE'd once sdkmanager stops reading, which -- combined
# with `pipefail` above -- would otherwise kill this script right here
# even though license acceptance itself succeeded; `|| true` swallows
# that specific false failure (a real sdkmanager problem still surfaces
# clearly at the install step right after this).
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true

echo "Installing SDK packages (platform-tools, build-tools, emulator, platform, system image)..."
"$SDKMANAGER" --sdk_root="$SDK_DIR" \
  "platform-tools" \
  "build-tools;${BUILD_TOOLS}" \
  "platforms;${PLATFORM}" \
  "emulator" \
  "$SYSTEM_IMAGE"

ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"

if "$AVDMANAGER" list avd | grep -qF "Name: $AVD_NAME"; then
  echo "AVD '$AVD_NAME' already exists."
else
  echo "Creating AVD '$AVD_NAME'..."
  # A real phone's screen size -- avdmanager's default device is a tiny 320x640.
  echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d pixel_7 --force
fi

# avdmanager has no flag for this -- created AVDs default to
# hw.keyboard=no, which makes the emulator ignore the host's physical
# keyboard entirely (only on-screen-keyboard taps register). Flip it
# straight to yes so typing just works; takes effect on the next boot.
AVD_CONFIG="$HOME/.android/avd/${AVD_NAME}.avd/config.ini"
if [ -f "$AVD_CONFIG" ]; then
  sed -i.bak "s/^hw\.keyboard=no$/hw.keyboard=yes/" "$AVD_CONFIG" && rm -f "${AVD_CONFIG}.bak"
fi

echo
echo "Setup complete."
echo
echo "adb:      $ADB"
echo "emulator: $EMULATOR"
echo
echo "Add platform-tools and emulator to your PATH so build-and-run.sh can"
echo "find them directly (it also works without this -- it falls back to"
echo "local.properties' sdk.dir on its own):"
echo
echo "  echo 'export PATH=\"$SDK_DIR/platform-tools:$SDK_DIR/emulator:\$PATH\"' >> ~/.zshrc"
echo "  source ~/.zshrc"
echo
echo "Boot the emulator, then build and run:"
echo
echo "  \"$EMULATOR\" -avd $AVD_NAME &"
echo "  ./scripts/build-and-run.sh"
