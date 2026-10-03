#!/usr/bin/env bash
# AGVN Player - mkxp-z for Android arm64: the RPG Maker XP/VX/VX Ace runner ("Chạy nhẹ", docs/agvn/engine-rieng.md).
# Output: app/src/main/jniLibs/arm64-v8a/libmkxp-z.so (stripped), app/src/main/assets/agvn/mkxp-z/*.rb (mkxp-z's preload
# scripts, CC0) and app/src/main/assets/agvn/mkxp-z-license.txt (the licenses of everything inside the library).
# Inputs are pinned in scripts/agvn/mkxp-z/sources.lock: mkxp-z by commit, every library by the SHA-256 that mkxp-z
# pins in its .wrap files (fetch-subprojects.py checks them). AGVN's changes are scripts/agvn/mkxp-z/patches.
# Linux x86_64 only (the cloud container or WSL). See scripts/agvn/mkxp-z/README.md.
# Copyright (c) 2026 agvn.io.vn - MIT License.
set -euo pipefail

ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
HERE="$ROOT/scripts/agvn/mkxp-z"
LOCK="$HERE/sources.lock"
WORK="${AGVN_MKXPZ_WORK:-$ROOT/build/agvn-mkxp-z}"
OUT_SO="$ROOT/app/src/main/jniLibs/arm64-v8a/libmkxp-z.so"
ASSETS="$ROOT/app/src/main/assets/agvn"
log() { echo "[AGVN mkxp-z] $*"; }
fail() { echo "[AGVN mkxp-z] LỖI: $*" >&2; exit 1; }
lock() { awk -v k="$1" '$1 == k { $1 = ""; sub(/^ /, ""); print; exit }' "$LOCK"; }

read -r MKXPZ_URL MKXPZ_COMMIT <<< "$(lock mkxp-z)"
NDK_VERSION=$(lock ndk)
API=$(lock api)
MESON_VERSION=$(lock meson)
TC="${ANDROID_HOME:-/opt/android-sdk}/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64"

# --- 0. tools ------------------------------------------------------------------------------------------------------
for t in git python3 ninja cmake pkg-config patch gzip ruby bison make strings; do
    command -v "$t" > /dev/null || fail "thiếu $t. Cài: apt-get install -y git python3-venv ninja-build cmake pkgconf patch ruby bison make binutils"
done
[ -x "$TC/bin/clang" ] || fail "không thấy NDK $NDK_VERSION (chạy scripts/agvn/cloud-setup.sh)"
mkdir -p "$WORK"
if [ "$("$WORK/venv/bin/meson" --version 2> /dev/null)" != "$MESON_VERSION" ]; then
    python3 -m venv "$WORK/venv" && "$WORK/venv/bin/pip" install -q "meson==$MESON_VERSION"
fi
export PATH="$WORK/venv/bin:$PATH"

# --- 1. mkxp-z at the pinned commit, plus AGVN's patches -----------------------------------------------------------
SRC="$WORK/mkxp-z"
[ -d "$SRC/.git" ] || { git init -q "$SRC" && git -C "$SRC" remote add origin "$MKXPZ_URL"; }
git -C "$SRC" fetch -q --depth 1 origin "$MKXPZ_COMMIT"
git -C "$SRC" checkout -q --force "$MKXPZ_COMMIT"
git -C "$SRC" clean -qfdx -e subprojects/packagecache # sources unpack again, so the wraps' patches apply anew
for p in "$HERE"/patches/*.patch; do
    patch -d "$SRC" -p1 --forward --fuzz=0 --no-backup-if-mismatch -s < "$p" || fail "không áp được $(basename "$p")"
done

# --- 2. library sources: GitHub tag tarballs and some hosts are blocked in the cloud, see fetch-subprojects.py -------
python3 "$HERE/fetch-subprojects.py" "$LOCK" "$SRC"
# libjxl's skcms submodule is on skia.googlesource.com; GitHub has Google's mirror of it
export GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0="url.https://github.com/google/skcms.insteadOf"
export GIT_CONFIG_VALUE_0="https://skia.googlesource.com/skcms"

# --- 3. configure and build ----------------------------------------------------------------------------------------
CROSS="$WORK/android-aarch64.ini"
cat > "$CROSS" << EOF
[binaries]
c = '$TC/bin/aarch64-linux-android$API-clang'
cpp = '$TC/bin/aarch64-linux-android$API-clang++'
ar = '$TC/bin/llvm-ar'
nm = '$TC/bin/llvm-nm'
ranlib = '$TC/bin/llvm-ranlib'
strip = '$TC/bin/llvm-strip'
objcopy = '$TC/bin/llvm-objcopy'
cmake = 'cmake'
pkg-config = 'pkg-config'

[properties]
sys_root = '$TC/sysroot'
pkg_config_libdir = []
cmake_toolchain_file = '${TC%/toolchains/*}/build/cmake/android.toolchain.cmake'

[cmake]
ANDROID_ABI = 'arm64-v8a'
ANDROID_PLATFORM = 'android-$API'
ANDROID_NDK = '${TC%/toolchains/*}'

[host_machine]
system = 'android'
subsystem = 'android'
kernel = 'linux'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'
EOF
BUILD="$WORK/build"
rm -rf "$BUILD"
# OpenGL ES; no ANGLE, no HTTPS; the phone's iconv; WenQuanYi Micro Hei as the built-in font (Vietnamese and Japanese)
meson setup "$BUILD" "$SRC" --cross-file "$CROSS" --buildtype release -Db_ndebug=true \
    -Dgfx_backend=gles -Dangle=disabled -Dopenssl=disabled -Diconv_system=enabled -Dcjk_fallback_font=true
ninja -C "$BUILD"

# --- 4. check the library before it replaces anything --------------------------------------------------------------
LIB="$BUILD/libmkxp-z.so"
[ -f "$LIB" ] || fail "không thấy $LIB"
# outputs go to variables first: with pipefail, "cmd | grep -q" fails when grep stops reading early
dynamic=$("$TC/bin/llvm-readelf" -d "$LIB")
exports=$("$TC/bin/llvm-nm" -D --defined-only "$LIB" | awk '{print $3}')
texts=$(strings -n 6 "$LIB")
for need in $(sed -n 's/.*Shared library: \[\(.*\)\]/\1/p' <<< "$dynamic"); do
    case "$need" in libc.so|libm.so|libdl.so|liblog.so|libandroid.so|libGLESv2.so) ;;
        *) fail "libmkxp-z.so cần $need, không phải thư viện có sẵn trên Android";; esac
done
! grep -qE 'RUNPATH|RPATH' <<< "$dynamic" || fail "libmkxp-z.so còn RUNPATH"
for sym in SDL_main JNI_OnLoad Java_com_winlator_cmod_agvn_sdl_SDLActivity_nativeRunMain; do
    grep -qx "$sym" <<< "$exports" || fail "libmkxp-z.so thiếu $sym"
done
! grep -q '^Java_org_libsdl' <<< "$exports" || fail "SDL còn gói Java org.libsdl.app (bản vá 0002)"
! grep -q 'org/libsdl/app/' <<< "$texts" || fail "JNI_OnLoad của SDL còn đăng ký lớp org/libsdl/app (bản vá 0002)"
grep -qx 'libOpenSLES.so' <<< "$texts" || fail "OpenAL không có OpenSL ES: game sẽ không có tiếng (bản vá 0001)"
log "libmkxp-z.so: chỉ cần thư viện của Android, xuất SDL_main, JNI_OnLoad và các hàm JNI của com.winlator.cmod.agvn.sdl"

# --- 5. install into the app ---------------------------------------------------------------------------------------
mkdir -p "$(dirname "$OUT_SO")" "$ASSETS/mkxp-z"
"$TC/bin/llvm-strip" --strip-unneeded -o "$OUT_SO" "$LIB"
for rb in ruby_classic_wrap.rb mkxp_wrap.rb win32_wrap.rb; do cp "$SRC/scripts/preload/$rb" "$ASSETS/mkxp-z/$rb"; done
"$HERE/licenses.sh" "$SRC" "$ASSETS/mkxp-z-license.txt"
SO_SHA=$(sha256sum "$OUT_SO" | cut -c1-64)
sed -i "s/^mkxpz_so_sha256=.*/mkxpz_so_sha256=$SO_SHA/" "$ROOT/scripts/agvn/pins.txt" # app/agvn-rgss.gradle checks it
log "xong: $OUT_SO ($(du -h "$OUT_SO" | cut -f1), sha256 $SO_SHA, đã ghi vào scripts/agvn/pins.txt)"
