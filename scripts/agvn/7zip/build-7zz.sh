#!/usr/bin/env bash
# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
#
# Builds app/src/main/jniLibs/arm64-v8a/libagvn7zz.so: 7-Zip's console program 7zz (CPP/7zip/Bundles/Alone2) for Android
# arm64, unmodified, from Igor Pavlov's release source. "Thêm từ tệp nén" runs it as a separate process (AgvnUnpack7zz)
# for what the Java readers cannot read: RAR and RAR5, ZIP with Deflate64/LZMA/zstd, 7z with PPMd/BCJ2, tar and its
# gz/xz/zst forms. It is an executable named like a library, so Android unpacks it into the app's nativeLibraryDir at
# install (jniLibs useLegacyPackaging in app/build.gradle), the one place an app may run a program from.
#
# Licence: GNU LGPL-2.1-or-later, with the "unRAR license restriction" on CPP/7zip/Compress/Rar* (DOC/License.txt,
# DOC/unRarLicense.txt in the source); bundling approved by the maintainer on 2026-11-20, see docs/agvn/LICENSES.md.
# RAR stays enabled: DISABLE_RAR is never set.
#
# Input: 7z2501-src.tar.xz from github.com/ip7z/7zip releases, checked against sevenzip_src_sha256 in
# scripts/agvn/pins.txt. Needs curl, tar, xz, make and the NDK of app/build.gradle (scripts/agvn/cloud-setup.sh).
# Linux x86_64 only (the cloud container or WSL); under a minute on 4 cores. The same inputs give the same file.
#
#   build-7zz.sh          the Android program, written into the app, and app/src/main/assets/agvn/7zip-license.txt
#                         (the source's DOC/License.txt, unRarLicense.txt and copying.txt as written)
#   build-7zz.sh --host   a 7zz for this machine, at build/agvn-7zip/host/7zz (not committed): AgvnUnpack7zzTest and
#                         AgvnUnpack7zzRarTest unpack real archives with it and are skipped when it is not there
#                         (Agvn7zzHost in app/src/test; the environment variable AGVN_7ZZ names another 7zz). The
#                         Gradle build never needs it.
set -euo pipefail

ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
VERSION=25.01
TARBALL_NAME=7z2501-src.tar.xz
URL="https://github.com/ip7z/7zip/releases/download/$VERSION/$TARBALL_NAME"
WORK="${AGVN_7ZIP_WORK:-$ROOT/build/agvn-7zip}"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a/libagvn7zz.so"
log() { echo "[AGVN 7-Zip] $*"; }
fail() { echo "[AGVN 7-Zip] LỖI: $*" >&2; exit 1; }
pin() { sed -n "s/^$1=//p" "$ROOT/scripts/agvn/pins.txt"; }

for t in curl tar xz make sha256sum; do command -v "$t" > /dev/null || fail "thiếu $t"; done
SHA=$(pin sevenzip_src_sha256)
[ -n "$SHA" ] || fail "scripts/agvn/pins.txt thiếu sevenzip_src_sha256"
mkdir -p "$WORK"
TARBALL="$WORK/$TARBALL_NAME"
if ! { [ -f "$TARBALL" ] && echo "$SHA  $TARBALL" | sha256sum -c --status; }; then
    log "tải $TARBALL_NAME"
    curl -fsSL --retry 3 -o "$TARBALL.part" "$URL" || fail "không tải được $URL"
    echo "$SHA  $TARBALL.part" | sha256sum -c --status || { rm -f "$TARBALL.part"; fail "$TARBALL_NAME sai SHA-256"; }
    mv "$TARBALL.part" "$TARBALL"
fi
SRC="$WORK/src"
rm -rf "$SRC"
mkdir -p "$SRC"
tar -xf "$TARBALL" -C "$SRC"
ALONE2="$SRC/CPP/7zip/Bundles/Alone2"
JOBS=$(nproc 2> /dev/null || echo 2)

# The makefiles' -Werror -Weverything are for the author's compilers: a newer clang's new warnings would stop the build.
if [ "${1:-}" = "--host" ]; then
    command -v g++ > /dev/null || fail "thiếu g++ (apt-get install -y g++)"
    make -C "$ALONE2" -f ../../cmpl_gcc.mak -j"$JOBS" O="$WORK/obj-host" CFLAGS_WARN_WALL= CFLAGS_WARN=-w > "$WORK/host.log" 2>&1 \
        || { tail -30 "$WORK/host.log" >&2; fail "không dựng được 7zz cho máy này"; }
    mkdir -p "$WORK/host"
    cp "$WORK/obj-host/7zz" "$WORK/host/7zz"
    "$WORK/host/7zz" i > /dev/null || fail "7zz vừa dựng không chạy được"
    grep -q 'Rar5' <<< "$("$WORK/host/7zz" i)" || fail "7zz không có RAR"
    log "xong: $WORK/host/7zz ($("$WORK/host/7zz" | sed -n 2p))"
    exit 0
fi

NDK_VERSION=$(sed -n "s/^ *ndkVersion '\(.*\)'.*/\1/p" "$ROOT/app/build.gradle")
API=$(sed -n 's/^ *minSdkVersion \([0-9]*\).*/\1/p' "$ROOT/app/build.gradle")
TC="${ANDROID_HOME:-/opt/android-sdk}/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TC/bin/aarch64-linux-android$API-clang"
[ -x "$CC" ] || fail "không thấy NDK $NDK_VERSION cho API $API (chạy scripts/agvn/cloud-setup.sh)"

# cmpl_clang_arm64.mak: clang, with 7-Zip's ARM64 assembly (LZMA decoder, 7zAsm); the AES, SHA and CRC code checks the
# CPU before it uses its instructions. Bionic has pthread and dl in libc (LIB2 empty); libc++ goes in statically, so
# the program needs nothing the phone does not have. PIE as Android wants, pages of 16 kB for Android 15+ phones.
build() { # <object folder> <USE_ASM value>
    make -C "$ALONE2" -f ../../cmpl_clang_arm64.mak -j"$JOBS" O="$1" CC="$CC" CXX="$CC++" USE_ASM="$2" \
        CFLAGS_WARN_WALL= CFLAGS_WARN=-w LIB2= LFLAGS_NOEXECSTACK="-z noexecstack" \
        LDFLAGS_STATIC_3="-static-libstdc++ -pie -Wl,--build-id=none -Wl,-z,max-page-size=16384"
}
if build "$WORK/obj-android" 1 > "$WORK/android.log" 2>&1; then
    BIN="$WORK/obj-android/7zz"
    log "dựng với mã ARM64 assembly của 7-Zip"
else
    log "mã assembly không dựng được (xem $WORK/android.log): dựng bản C thuần"
    build "$WORK/obj-android-c" "" > "$WORK/android-c.log" 2>&1 || { tail -30 "$WORK/android-c.log" >&2; fail "không dựng được 7zz"; }
    BIN="$WORK/obj-android-c/7zz"
fi

# --- check the program before it replaces anything -------------------------------------------------------------------
header=$("$TC/bin/llvm-readelf" -h "$BIN")
dynamic=$("$TC/bin/llvm-readelf" -d "$BIN")
grep -q 'Machine:.*AArch64' <<< "$header" || fail "7zz không phải ARM64"
grep -q 'Type:.*DYN' <<< "$header" || fail "7zz không phải PIE"
for need in $(sed -n 's/.*Shared library: \[\(.*\)\]/\1/p' <<< "$dynamic"); do
    case "$need" in libc.so|libm.so|libdl.so) ;;
        *) fail "7zz cần $need, không phải thư viện có sẵn trên Android";; esac
done
! grep -qE 'RUNPATH|RPATH' <<< "$dynamic" || fail "7zz còn RUNPATH"
texts=$(strings -n 4 "$BIN")
grep -q 'Rar5' <<< "$texts" || fail "7zz không có RAR"
mkdir -p "$(dirname "$OUT")"
LICENSE_OUT="$ROOT/app/src/main/assets/agvn/7zip-license.txt"
{ echo "7-Zip $VERSION: DOC/License.txt, DOC/unRarLicense.txt and DOC/copying.txt of $TARBALL_NAME, as written."
  for f in License.txt unRarLicense.txt copying.txt; do
      printf '\n==================== %s ====================\n\n' "$f"
      tr -d '\r' < "$SRC/DOC/$f"
  done; } > "$LICENSE_OUT"
"$TC/bin/llvm-strip" --strip-all -o "$OUT" "$BIN"
chmod 755 "$OUT"
log "xong: $OUT ($(du -h "$OUT" | cut -f1), sha256 $(sha256sum "$OUT" | cut -c1-64))"
