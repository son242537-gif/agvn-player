#!/bin/bash
#
# AGVN Player — cloud build environment setup (Claude Code cloud, Ubuntu 24.04 x86_64, runs as root).
#
# Paste this whole file into the cloud environment's "Setup script" box.
# The environment must use network access "Custom" with "Also include default list of common
# package managers" ticked AND `dl.google.com` added (Android SDK downloads live there).
# Set the environment variable ANDROID_HOME=/opt/android-sdk.
#
# Idempotent: when NDK 29 is already present the script only prints versions.
# JDK: the cloud image already ships OpenJDK 21 (AGP 8.8 / Gradle 8.10 accept it).

set -e

ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
NDK_VERSION="29.0.14206865"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"
SDK="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

log() { echo "[AGVN Cloud Setup] $*"; }

if [ -d "$ANDROID_HOME/ndk/$NDK_VERSION" ]; then
    log "NDK $NDK_VERSION already installed; nothing to do."
else
    log "Installing native build tools (glslang, ninja, zstd)..."
    # The cloud image ships extra PPAs (launchpad) that the Custom network blocks with 403; apt-get update then
    # exits 100 even though the Ubuntu archive indexes refreshed fine. Tolerate that and let the install decide.
    apt-get update -qq || log "apt-get update reported errors (blocked PPAs are expected); continuing"
    apt-get install -y -qq curl unzip git glslang-tools ninja-build zstd

    log "Downloading Android command-line tools..."
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    tmp="$(mktemp -d)"
    curl -fsSL -o "$tmp/$CMDLINE_TOOLS_ZIP" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
    unzip -q "$tmp/$CMDLINE_TOOLS_ZIP" -d "$tmp"
    rm -rf "$ANDROID_HOME/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -rf "$tmp"

    log "Accepting SDK licenses..."
    yes | "$SDK" --sdk_root="$ANDROID_HOME" --licenses > /dev/null || true

    log "Installing SDK packages (platform 35, build-tools 35.0.0, NDK $NDK_VERSION, CMake 3.22.1)..."
    "$SDK" --sdk_root="$ANDROID_HOME" \
        "platform-tools" \
        "platforms;android-35" \
        "build-tools;35.0.0" \
        "ndk;$NDK_VERSION" \
        "cmake;3.22.1" > /dev/null
fi

if ! grep -q "AGVN Player Android SDK" ~/.bashrc 2>/dev/null; then
    cat >> ~/.bashrc <<EOF

# AGVN Player Android SDK
export ANDROID_HOME=$ANDROID_HOME
export PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH
EOF
fi

log "Versions:"
java -version 2>&1 | head -1
"$SDK" --sdk_root="$ANDROID_HOME" --version
ls "$ANDROID_HOME/ndk"
glslangValidator --version | head -1
ninja --version
log "Done."
