#!/usr/bin/env bash
# AGVN Player - Godot 4 for Android arm64: the engine of the Godot runner ("Chạy nhẹ", docs/agvn/engine-rieng.md).
# Godot's own Android library (org.godotengine:godot on Maven Central) opens no game pack outside the APK since 4.6:
# its export templates are built with disable_path_overrides=yes. This script builds the same engine from Godot's
# release source with disable_path_overrides=no, plus AGVN's patches (scripts/agvn/godot/patches). The Java side stays
# Godot's own library of the same version: app/agvn-godot.gradle puts this engine into it.
# Output: app/agvn-godot/libgodot_android.so.gz (stripped, gzip) and app/src/main/assets/agvn/godot-license.txt; the
# library's SHA-256 goes into scripts/agvn/pins.txt (godot_so_sha256), which the app build checks.
# Inputs are pinned in scripts/agvn/godot/sources.lock. Linux x86_64 only (the cloud container or WSL); about 10 minutes
# on 4 cores. See scripts/agvn/godot/README.md.
# Copyright (c) 2026 agvn.io - MIT License.
set -euo pipefail

ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
HERE="$ROOT/scripts/agvn/godot"
LOCK="$HERE/sources.lock"
WORK="${AGVN_GODOT_WORK:-$ROOT/build/agvn-godot}"
OUT="$ROOT/app/agvn-godot/libgodot_android.so.gz"
LICENSE_OUT="$ROOT/app/src/main/assets/agvn/godot-license.txt"
log() { echo "[AGVN Godot] $*"; }
fail() { echo "[AGVN Godot] LỖI: $*" >&2; exit 1; }
lock() { awk -v k="$1" '$1 == k { $1 = ""; sub(/^ /, ""); print; exit }' "$LOCK"; }

read -r GODOT_VERSION GODOT_URL GODOT_SHA <<< "$(lock godot)"
read -r SWAPPY_URL SWAPPY_SHA <<< "$(lock swappy)"
read -r SCONS_VERSION SCONS_SHA <<< "$(lock scons)"
read -r SPINE_BRANCH SPINE_COMMIT SPINE_REPO <<< "$(lock spine)"
NDK_VERSION=$(lock ndk)
SDK="${ANDROID_HOME:-/opt/android-sdk}"
TC="$SDK/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64"

# --- 0. tools ------------------------------------------------------------------------------------------------------
for t in git curl python3 tar xz unzip patch gzip sha256sum strings; do
    command -v "$t" > /dev/null || fail "thiếu $t. Cài: apt-get install -y git curl python3-venv xz-utils unzip patch binutils"
done
[ -x "$TC/bin/llvm-strip" ] || fail "không thấy NDK $NDK_VERSION (chạy scripts/agvn/cloud-setup.sh)"
mkdir -p "$WORK"
if [ ! -x "$WORK/venv/bin/scons" ] || ! "$WORK/venv/bin/scons" --version | grep -q "v$SCONS_VERSION"; then
    rm -rf "$WORK/venv"
    python3 -m venv "$WORK/venv"
    echo "scons==$SCONS_VERSION --hash=sha256:$SCONS_SHA" > "$WORK/scons-requirements.txt"
    "$WORK/venv/bin/pip" install -q --require-hashes -r "$WORK/scons-requirements.txt"
fi

# --- 1. downloads, each checked against sources.lock ---------------------------------------------------------------
fetch() { # <url> <sha256> <file>
    if [ -f "$3" ] && echo "$2  $3" | sha256sum -c --status; then return; fi
    log "tải $(basename "$1")"
    curl -fsSL --retry 3 -o "$3.part" "$1" || fail "không tải được $1"
    echo "$2  $3.part" | sha256sum -c --status || { rm -f "$3.part"; fail "$(basename "$1") sai SHA-256"; }
    mv "$3.part" "$3"
}
TARBALL="$WORK/$(basename "$GODOT_URL")"
SWAPPY="$WORK/$(basename "$SWAPPY_URL")"
fetch "$GODOT_URL" "$GODOT_SHA" "$TARBALL"
fetch "$SWAPPY_URL" "$SWAPPY_SHA" "$SWAPPY"
# Spine Runtimes at the pinned commit: spine-cpp, spine-godot and the top-level files (LICENSE) only
SPINE_SRC="$WORK/spine-runtimes-$SPINE_COMMIT"
if [ "$(git -C "$SPINE_SRC" rev-parse HEAD 2> /dev/null)" != "$SPINE_COMMIT" ]; then
    log "tải Spine Runtimes $SPINE_BRANCH ($SPINE_COMMIT)"
    rm -rf "$SPINE_SRC"
    git init -q "$SPINE_SRC"
    git -C "$SPINE_SRC" remote add origin "$SPINE_REPO"
    git -C "$SPINE_SRC" sparse-checkout set --cone spine-cpp spine-godot
    GIT_LFS_SKIP_SMUDGE=1 git -C "$SPINE_SRC" fetch -q --depth 1 --filter=blob:none origin "$SPINE_COMMIT" \
        || fail "không tải được Spine Runtimes $SPINE_COMMIT"
    GIT_LFS_SKIP_SMUDGE=1 git -C "$SPINE_SRC" checkout -q FETCH_HEAD
    [ "$(git -C "$SPINE_SRC" rev-parse HEAD)" = "$SPINE_COMMIT" ] \
        || fail "Spine Runtimes không đúng commit $SPINE_COMMIT"
fi

# --- 2. Godot's source with AGVN's patches; kept between runs while nothing changed, so SCons builds only what did ---
# Spine goes in as the engine module spine_godot beside Godot's source (its SCsub looks for #../spine_godot), with
# spine-cpp inside it as Esoteric's setup.sh puts it, and AGVN's patches for Godot 4.7 (spine-patches/).
SRC="$WORK/godot-$GODOT_VERSION-stable"
SPINE_MODULE="$WORK/spine_godot"
STAMP=$(cat "$LOCK" "$HERE"/patches/*.patch "$HERE"/spine-patches/*.patch | sha256sum | cut -c1-64)
if [ "$(cat "$SPINE_MODULE/.agvn-stamp" 2> /dev/null)" != "$STAMP" ]; then
    rm -rf "$SPINE_MODULE"
    cp -r "$SPINE_SRC/spine-godot/spine_godot" "$SPINE_MODULE"
    cp -r "$SPINE_SRC/spine-cpp/spine-cpp" "$SPINE_MODULE/spine-cpp"
    for p in "$HERE"/spine-patches/*.patch; do
        patch -d "$SPINE_MODULE" -p1 --forward --fuzz=0 --no-backup-if-mismatch -s < "$p" \
            || fail "không áp được $(basename "$p")"
    done
    echo "$STAMP" > "$SPINE_MODULE/.agvn-stamp"
fi
if [ "$(cat "$SRC/.agvn-stamp" 2> /dev/null)" != "$STAMP" ]; then
    rm -rf "$SRC"
    tar -xJf "$TARBALL" -C "$WORK"
    [ -f "$SRC/SConstruct" ] || fail "không thấy $SRC/SConstruct"
    for p in "$HERE"/patches/*.patch; do
        patch -d "$SRC" -p1 --forward --fuzz=0 --no-backup-if-mismatch -s < "$p" || fail "không áp được $(basename "$p")"
    done
    for arch in arm64-v8a armeabi-v7a x86 x86_64; do # Godot's detect.py wants all four, though only arm64 is built
        mkdir -p "$SRC/thirdparty/swappy-frame-pacing/$arch"
        unzip -q -o -j "$SWAPPY" "$arch/libswappy_static.a" -d "$SRC/thirdparty/swappy-frame-pacing/$arch"
    done
    echo "$STAMP" > "$SRC/.agvn-stamp"
fi

# --- 3. build: Godot's release template for arm64, as official builds are made, but with path overrides -------------
# production=yes: no debug symbols, static checks off, Swappy frame pacing (as Godot's own Android templates).
# SCons' cache keeps compiled files across runs, so a changed patch rebuilds only what it touches.
(cd "$SRC" && ANDROID_HOME="$SDK" "$WORK/venv/bin/scons" platform=android target=template_release arch=arm64 \
    production=yes swappy=yes disable_path_overrides=no custom_modules="$SPINE_MODULE" \
    cache_path="$WORK/scons-cache" cache_limit=4 -j"$(nproc)")
LIB="$SRC/platform/android/java/lib/libs/release/arm64-v8a/libgodot_android.so"
[ -f "$LIB" ] || fail "không thấy $LIB"

# --- 4. check the library before it replaces anything --------------------------------------------------------------
# outputs go to variables first: with pipefail, "cmd | grep -q" fails when grep stops reading early
dynamic=$("$TC/bin/llvm-readelf" -d "$LIB")
exports=$("$TC/bin/llvm-nm" -D --defined-only "$LIB" | awk '{print $3}')
texts=$(strings -n 8 "$LIB")
for need in $(sed -n 's/.*Shared library: \[\(.*\)\]/\1/p' <<< "$dynamic"); do
    case "$need" in
        libc.so|libm.so|libdl.so|liblog.so|libz.so|libandroid.so|libEGL.so|libGLESv3.so|libOpenSLES.so) ;;
        libcamera2ndk.so|libmediandk.so|libc++_shared.so) ;;
        *) fail "libgodot_android.so cần $need, thư viện mà bản Godot chính thức không cần";;
    esac
done
for sym in Java_org_godotengine_godot_GodotLib_initialize Java_org_godotengine_godot_GodotLib_setup Java_org_godotengine_godot_GodotLib_step; do
    grep -qx "$sym" <<< "$exports" || fail "libgodot_android.so thiếu $sym"
done
! grep -q 'attempting to load from outside of the executable' <<< "$texts" || fail "engine vẫn chặn gói game ngoài APK (disable_path_overrides)"
grep -q 'AGVN_GODOT_APPDATA' <<< "$texts" || fail "engine thiếu bản vá user:// (patches/0001)"
grep -q 'AGVN_GODOT_EXECUTABLE' <<< "$texts" || fail "engine thiếu bản vá đường dẫn .exe (patches/0002)"
grep -q 'AGVN Player: binary GDScript is cut short' <<< "$texts" || fail "engine thiếu bản vá đọc script Godot 4.3, 4.4 (patches/0003)"
# patches/0004 adds no text of its own: step 2 stops when it does not apply
grep -q 'SpineSkeletonDataResource' <<< "$texts" || fail "engine thiếu Spine (module spine_godot)"
grep -q "does not match runtime version $SPINE_BRANCH" <<< "$texts" \
    || grep -q 'does not match runtime version %s' <<< "$texts" || fail "engine thiếu spine-cpp $SPINE_BRANCH"
grep -q "Godot Engine v$GODOT_VERSION" <<< "$texts" || grep -q "$GODOT_VERSION.stable" <<< "$texts" || fail "không phải Godot $GODOT_VERSION"
log "libgodot_android.so: Godot $GODOT_VERSION, mở được gói game ngoài APK, có các bản vá của AGVN"

# --- 5. install into the app ---------------------------------------------------------------------------------------
STRIPPED="$WORK/libgodot_android.so"
"$TC/bin/llvm-strip" --strip-unneeded -o "$STRIPPED" "$LIB"
mkdir -p "$(dirname "$OUT")"
gzip -9 -n -c "$STRIPPED" > "$OUT"
{
    echo "Godot Engine $GODOT_VERSION (libgodot_android.so, built by AGVN Player from Godot's release source with"
    echo "disable_path_overrides=no and the patches in scripts/agvn/godot/patches; the Java side is Godot's own library)"
    echo
    cat "$SRC/LICENSE.txt"
    echo
    cat "$SRC/COPYRIGHT.txt"
    echo
    echo "Swappy Frame Pacing (Android Game SDK), linked into libgodot_android.so"
    echo
    unzip -p "$SWAPPY" LICENSE
    echo
    echo "Spine Runtimes $SPINE_BRANCH (spine-cpp and spine-godot, commit $SPINE_COMMIT), built into"
    echo "libgodot_android.so with the patches in scripts/agvn/godot/spine-patches. Not open source: see below."
    echo
    cat "$SPINE_SRC/LICENSE"
} > "$LICENSE_OUT"
SO_SHA=$(sha256sum "$STRIPPED" | cut -c1-64)
PINS="$ROOT/scripts/agvn/pins.txt"
if grep -q '^godot_so_sha256=' "$PINS"; then
    sed -i "s/^godot_so_sha256=.*/godot_so_sha256=$SO_SHA/" "$PINS" # app/agvn-godot.gradle checks it
else
    echo "godot_so_sha256=$SO_SHA" >> "$PINS"
fi
log "xong: $OUT ($(du -h "$OUT" | cut -f1); thư viện $(du -h "$STRIPPED" | cut -f1), sha256 $SO_SHA, đã ghi vào scripts/agvn/pins.txt)"
