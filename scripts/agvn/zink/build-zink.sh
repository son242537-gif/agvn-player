#!/usr/bin/env bash
# AGVN Player - release build of Mesa's Zink (OpenGL on Vulkan) for the imagefs, replacing Ludashi's debug build.
# Output: app/src/main/assets/graphics_driver/opengl_zink.tzst = usr/lib/libGL.so.1.5.0 + usr/lib/libgallium-<ver>.so
# Inputs are pinned in scripts/agvn/zink/sources.lock (Mesa by signed tag and commit, headers by SHA-256) and
# scripts/agvn/pins.txt (imagefs); patches are in scripts/agvn/zink/patches. Nothing downloaded is used before its
# hash is checked. Linux x86_64 only (the cloud container or WSL). See scripts/agvn/zink/README.md.
# Copyright (c) 2026 agvn.io.vn - MIT License.
set -euo pipefail

ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
HERE="$ROOT/scripts/agvn/zink"
LOCK="$HERE/sources.lock"
WORK="${AGVN_ZINK_WORK:-$ROOT/build/agvn-zink}"
OUT="$ROOT/app/src/main/assets/graphics_driver/opengl_zink.tzst"
IMAGEFS="$ROOT/app/src/main/assets/imagefs.tar.zst"
log() { echo "[AGVN Zink] $*"; }
fail() { echo "[AGVN Zink] LỖI: $*" >&2; exit 1; }
lock() { awk -v k="$1" '$1 == k { $1 = ""; sub(/^ /, ""); print; exit }' "$LOCK"; }
sha_of() { sha256sum "$1" | cut -c1-64; }

read -r MESA_TAG MESA_TAG_OBJ MESA_COMMIT MESA_URL <<< "$(lock mesa)"
NDK_VERSION=$(lock ndk)
API=$(lock api)
TC="${ANDROID_HOME:-/opt/android-sdk}/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64"

# --- 0. tools -----------------------------------------------------------------------------------------------------
for t in git curl meson ninja pkg-config patch python3 bison flex zstd tar dpkg-deb sha256sum readelf; do
    command -v "$t" > /dev/null || fail "thiếu $t. Cài: apt-get install -y meson ninja-build pkgconf patch bison flex zstd binutils python3-mako python3-yaml python3-packaging"
done
python3 -c 'import mako, yaml, packaging' 2> /dev/null || fail "thiếu python3-mako / python3-yaml / python3-packaging"
[ -x "$TC/bin/clang" ] || fail "không thấy NDK $NDK_VERSION (chạy scripts/agvn/cloud-setup.sh)"
mkdir -p "$WORK"

# --- 1. imagefs libraries: Zink links against exactly the ones it will load on the phone ----------------------------
want=$(grep '^imagefs_sha256=' "$ROOT/scripts/agvn/pins.txt" | cut -d= -f2)
[ -f "$IMAGEFS" ] || fail "chưa có $IMAGEFS (chạy ./gradlew :app:downloadImageFS)"
[ "$(sha_of "$IMAGEFS")" = "$want" ] || fail "imagefs.tar.zst sai SHA-256"
IFS_LIB="$WORK/imagefs/usr/lib"
rm -rf "$WORK/imagefs" && mkdir -p "$WORK/imagefs"
zstd -dcq "$IMAGEFS" | tar -x -C "$WORK/imagefs" --wildcards \
    'usr/lib/libX11*' 'usr/lib/libxcb*' 'usr/lib/libXext*' 'usr/lib/libXfixes*' 'usr/lib/libXxf86vm*' \
    'usr/lib/libXau*' 'usr/lib/libXdmcp*' 'usr/lib/libxshmfence*' 'usr/lib/libdrm.so*' 'usr/lib/libzstd*' \
    'usr/lib/libandroid-sysvshm*'
log "imagefs: $(ls "$IFS_LIB" | wc -l) thư viện để liên kết"

# --- 2. headers and .pc files from the pinned Ubuntu packages (dpkg-deb -x runs no package code) ---------------------
DEV="$WORK/dev"
rm -rf "$DEV" && mkdir -p "$DEV" "$WORK/debs"
grep '^deb ' "$LOCK" | while read -r _ pkg ver sha path; do
    f="$WORK/debs/$(basename "$path")"
    [ -f "$f" ] && [ "$(sha_of "$f")" = "$sha" ] || curl -fsSL -o "$f" "http://archive.ubuntu.com/ubuntu/$path"
    [ "$(sha_of "$f")" = "$sha" ] || fail "$pkg $ver sai SHA-256"
    dpkg-deb -x "$f" "$DEV"
done
PC="$WORK/pkgconfig"
rm -rf "$PC" && mkdir -p "$PC"
for f in "$DEV"/usr/lib/x86_64-linux-gnu/pkgconfig/*.pc "$DEV"/usr/share/pkgconfig/*.pc; do
    sed -e "s|^prefix=.*|prefix=$DEV/usr|" -e "s|^libdir=.*|libdir=$IFS_LIB|" "$f" > "$PC/$(basename "$f")"
done
export PKG_CONFIG_LIBDIR="$PC" PKG_CONFIG_PATH=""

# --- 3. Mesa at the pinned signed tag, plus AGVN's patch series ------------------------------------------------------
SRC="$WORK/mesa"
[ -d "$SRC/.git" ] || { git init -q "$SRC" && git -C "$SRC" remote add origin "$MESA_URL"; }
git -C "$SRC" fetch -q --depth 1 origin "+refs/tags/$MESA_TAG:refs/tags/$MESA_TAG"
[ "$(git -C "$SRC" rev-parse "refs/tags/$MESA_TAG")" = "$MESA_TAG_OBJ" ] || fail "thẻ $MESA_TAG khác bản đã ghim"
[ "$(git -C "$SRC" rev-parse "refs/tags/$MESA_TAG^{commit}")" = "$MESA_COMMIT" ] || fail "commit $MESA_TAG khác bản đã ghim"
git -C "$SRC" checkout -q --force "$MESA_COMMIT" && git -C "$SRC" clean -qfdx
for p in "$HERE"/patches/*.patch; do
    patch -d "$SRC" -p1 --forward --fuzz=0 --no-backup-if-mismatch -s < "$p" || fail "không áp được $(basename "$p")"
done
MESA_VERSION=$(cat "$SRC/VERSION")

# --- 4. configure and build (release: no asserts, no debug info, stripped) ------------------------------------------
CROSS="$WORK/android-aarch64.ini"
cat > "$CROSS" << EOF
[binaries]
c = '$TC/bin/aarch64-linux-android$API-clang'
cpp = '$TC/bin/aarch64-linux-android$API-clang++'
ar = '$TC/bin/llvm-ar'
strip = '$TC/bin/llvm-strip'
pkg-config = '$(command -v pkg-config)'

[built-in options]
c_args = ['-I$HERE/shim']
cpp_args = ['-I$HERE/shim']
c_link_args = ['-L$IFS_LIB', '-Wl,--as-needed', '-landroid-sysvshm']
cpp_link_args = ['-L$IFS_LIB', '-Wl,--as-needed', '-landroid-sysvshm', '-static-libstdc++']

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8'
endian = 'little'
EOF
BUILD="$WORK/build"
rm -rf "$BUILD"
meson setup "$BUILD" "$SRC" --cross-file "$CROSS" --prefix=/usr --libdir=lib \
    -Dbuildtype=release -Db_ndebug=true -Dstrip=true \
    -Dplatforms=x11 -Dglx=dri -Dgallium-drivers=zink -Dvulkan-drivers= \
    -Dglvnd=disabled -Degl=disabled -Dgbm=disabled -Dllvm=disabled \
    -Dxmlconfig=disabled -Dexpat=disabled -Dzstd=enabled -Dzlib=disabled \
    -Dlibunwind=disabled -Dvalgrind=disabled -Dlmsensors=disabled -Dandroid-libbacktrace=disabled \
    -Dgallium-va=disabled -Dgallium-vdpau=disabled -Dgallium-xa=disabled -Dvideo-codecs=
ninja -C "$BUILD"
DEST="$WORK/dest"
rm -rf "$DEST" && DESTDIR="$DEST" meson install -C "$BUILD" --no-rebuild --quiet

# --- 5. check the result before it replaces anything ---------------------------------------------------------------
GALLIUM="libgallium-$MESA_VERSION.so"
GL=$(ls "$DEST/usr/lib" | grep -E '^libGL\.so(\.1(\.5\.0)?)?$' | sort | tail -1)
[ -n "$GL" ] && [ -f "$DEST/usr/lib/$GALLIUM" ] || fail "không thấy libGL hoặc $GALLIUM trong $DEST/usr/lib"
for lib in "$DEST/usr/lib/$GL" "$DEST/usr/lib/$GALLIUM"; do
    name=$(basename "$lib")
    readelf -d "$lib" | grep -qE 'RUNPATH|RPATH' && fail "$name còn RUNPATH"
    readelf -S "$lib" | grep -q '\.debug_' && fail "$name còn thông tin gỡ lỗi"
    for need in $(readelf -d "$lib" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p'); do
        case "$need" in libc.so|libm.so|libdl.so|"$GALLIUM") ;;
            *) [ -e "$IFS_LIB/$need" ] || fail "$name cần $need nhưng imagefs không có";; esac
    done
done
grep -qa 'WRAPPER_SURFACE_FORMAT' "$DEST/usr/lib/$GALLIUM" || fail "thiếu bản vá thứ tự màu (0006)"
log "$GL + $GALLIUM: không RUNPATH, không thông tin gỡ lỗi, mọi thư viện cần đều có trong imagefs"

# --- 6. pack: file names the imagefs symlinks expect (libGL.so -> libGL.so.1 -> libGL.so.1.5.0) ----------------------
STAGE="$WORK/stage"
rm -rf "$STAGE" && mkdir -p "$STAGE/usr/lib"
cp "$DEST/usr/lib/$GL" "$STAGE/usr/lib/libGL.so.1.5.0"
cp "$DEST/usr/lib/$GALLIUM" "$STAGE/usr/lib/$GALLIUM"
chmod 0644 "$STAGE"/usr/lib/*
tar --sort=name --owner=0 --group=0 --numeric-owner --mtime='2026-01-01 00:00Z' --format=gnu \
    -C "$STAGE" -cf - usr/lib/libGL.so.1.5.0 "usr/lib/$GALLIUM" | zstd -19 -q -f -o "$OUT"
log "xong: $OUT ($(du -h "$OUT" | cut -f1), sha256 $(sha_of "$OUT"))"
log "Mesa $MESA_VERSION: nhớ giữ AgvnGlDriver.ZINK_REVISION khớp với bản này"
