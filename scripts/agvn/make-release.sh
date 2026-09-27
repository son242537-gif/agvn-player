#!/bin/bash
#
# AGVN Player — build a release candidate into release/v<version>/ (Linux/macOS/Git Bash).
#   AGVN_KEYSTORE_PROPS=/path/keystore.properties bash scripts/agvn/make-release.sh
# Steps: secret gate -> unit tests -> signed assembleRelease (fails without keystore) -> package/label check ->
# certificate check against release_cert_sha256 in scripts/agvn/pins.txt -> APK + .sha256 + guide + notes.
# AGVN_ALLOW_DEBUG_SIGNING=1 builds a debug-signed dry run (never give that APK to customers).
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"; cd "$ROOT"
ANDROID_HOME="${ANDROID_HOME:?set ANDROID_HOME}"
BT="$ANDROID_HOME/build-tools/35.0.0"
VERSION="$(grep -E '^AGVN_VERSION_NAME=' gradle.properties | cut -d= -f2)"
CODE="$(grep -E '^AGVN_VERSION_CODE=' gradle.properties | cut -d= -f2)"
OUT="release/v$VERSION"; APK_NAME="AGVN-Player-v$VERSION.apk"
log() { echo "[AGVN Release] $*"; }
fail() { echo "[AGVN Release] LỖI: $*" >&2; exit 1; }

log "v$VERSION (versionCode $CODE)"
if git ls-files | grep -E '\.(jks|keystore)$|keystore\.properties$|\.tar\.zst$'; then fail "file bí mật/lưu trữ lớn đang nằm trong git"; fi

SIGN_FLAG="-PagvnRequireReleaseSigning=true"
[ "${AGVN_ALLOW_DEBUG_SIGNING:-0}" = "1" ] && { SIGN_FLAG=""; log "CHẠY THỬ: ký bằng khóa debug"; }
./gradlew testReleaseUnitTest assembleRelease --no-daemon $SIGN_FLAG
python3 -m unittest discover -s tools/agvn/tests >/dev/null 2>&1 && log "toolkit tests OK" || fail "toolkit tests"

APK=app/build/outputs/apk/release/app-release.apk
BADGING="$("$BT/aapt2" dump badging "$APK")"
echo "$BADGING" | grep -q "package: name='com.agvn.player' versionCode='$CODE' versionName='$VERSION'" || fail "package/version sai"
echo "$BADGING" | grep -q "application-label:'AGVN Player'" || fail "tên app sai"

CERT="$("$BT/apksigner" verify --print-certs "$APK" 2>/dev/null | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)"
[ -n "$CERT" ] || fail "APK chưa được ký hợp lệ"
PIN="$(grep -E '^release_cert_sha256=' scripts/agvn/pins.txt | cut -d= -f2 || true)"
if [ -z "${AGVN_ALLOW_DEBUG_SIGNING:-}" ] || [ "${AGVN_ALLOW_DEBUG_SIGNING}" != "1" ]; then
    if [ -z "$PIN" ]; then
        log "Chưa có release_cert_sha256 trong pins.txt. Chứng chỉ bản này: $CERT"
        log "Nếu đúng keystore AGVN, thêm dòng: release_cert_sha256=$CERT"
    elif [ "$PIN" != "$CERT" ]; then
        fail "chứng chỉ $CERT khác pin $PIN (sai keystore?)"
    fi
fi

mkdir -p "$OUT"
cp "$APK" "$OUT/$APK_NAME"
(cd "$OUT" && sha256sum "$APK_NAME" > "$APK_NAME.sha256")
cp docs/agvn/release/HUONG-DAN-CAI-AGVN-PLAYER.txt "$OUT/"
[ -f "docs/agvn/release/ghi-chu-phat-hanh-v$VERSION.txt" ] && cp "docs/agvn/release/ghi-chu-phat-hanh-v$VERSION.txt" "$OUT/"
log "Xong: $OUT"; ls -l "$OUT"
log "Tiếp theo: quét virus APK, chạy docs/agvn/rc-test-report.md, rồi: git tag v$VERSION && git push origin v$VERSION"
