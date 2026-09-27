#!/bin/bash
#
# AGVN Player — fetch the native dependencies that are not stored in this repo.
# Mirrors .github/workflows/build.yml of upstream Winlator-Ludashi:
#   - libadrenotools   (Pipetto-crypto/libadrenotools) @ pinned commit  -> app/src/main/cpp/adrenotools
#   - liblinkernsbypass (Pipetto-crypto/liblinkernsbypass)               -> app/src/main/cpp/adrenotools/lib/linkernsbypass
# linkernsbypass is unpinned upstream; the first run records its commit in scripts/agvn/pins.txt and
# every later run checks out exactly that commit. Safe to rerun.

set -e

ROOT="$(git rev-parse --show-toplevel)"
PINS="$ROOT/scripts/agvn/pins.txt"
ADRENO_DIR="$ROOT/app/src/main/cpp/adrenotools"
BYPASS_DIR="$ADRENO_DIR/lib/linkernsbypass"
ADRENO_REPO="https://github.com/Pipetto-crypto/libadrenotools.git"
BYPASS_REPO="https://github.com/Pipetto-crypto/liblinkernsbypass.git"
ADRENO_COMMIT="8483dfdaa2abf97ee89ad0e5f337e7b508550c6b"

log() { echo "[AGVN Deps] $*"; }
pin() { [ -f "$PINS" ] && grep -E "^$1=" "$PINS" | cut -d= -f2 || true; }
set_pin() {
    touch "$PINS"
    grep -v -E "^$1=" "$PINS" > "$PINS.tmp" || true
    echo "$1=$2" >> "$PINS.tmp"
    mv "$PINS.tmp" "$PINS"
}

# libadrenotools at the pinned commit (the submodule path may be empty or a gitlink checkout)
if [ ! -e "$ADRENO_DIR/.git" ]; then
    rm -rf "$ADRENO_DIR"
    git clone "$ADRENO_REPO" "$ADRENO_DIR"
fi
git -C "$ADRENO_DIR" fetch -q origin "$ADRENO_COMMIT" 2>/dev/null || git -C "$ADRENO_DIR" fetch -q origin
git -C "$ADRENO_DIR" -c advice.detachedHead=false checkout -q "$ADRENO_COMMIT"
set_pin adrenotools_commit "$ADRENO_COMMIT"
log "libadrenotools @ $(git -C "$ADRENO_DIR" rev-parse --short HEAD)"

# liblinkernsbypass: pinned after the first fetch
[ -e "$BYPASS_DIR/.git" ] || git clone -q "$BYPASS_REPO" "$BYPASS_DIR"
BYPASS_COMMIT="$(pin linkernsbypass_commit)"
if [ -n "$BYPASS_COMMIT" ]; then
    git -C "$BYPASS_DIR" fetch -q origin "$BYPASS_COMMIT" 2>/dev/null || git -C "$BYPASS_DIR" fetch -q origin
    git -C "$BYPASS_DIR" -c advice.detachedHead=false checkout -q "$BYPASS_COMMIT"
else
    BYPASS_COMMIT="$(git -C "$BYPASS_DIR" rev-parse HEAD)"
    set_pin linkernsbypass_commit "$BYPASS_COMMIT"
    log "recorded new pin linkernsbypass_commit=$BYPASS_COMMIT"
fi
log "liblinkernsbypass @ $(git -C "$BYPASS_DIR" rev-parse --short HEAD)"
log "pins: $(tr '\n' ' ' < "$PINS")"
