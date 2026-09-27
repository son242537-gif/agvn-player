# Building AGVN Player

## Requirements
- JDK 17+ (21 works), Android SDK with platform 35, build-tools 35.0.0, NDK 29.0.14206865, CMake 3.22.1
- `glslang-tools`, `ninja`, `zstd`
- Linux cloud machine: `bash scripts/agvn/cloud-setup.sh` installs all of the above.

## Steps
```bash
export ANDROID_HOME=/opt/android-sdk        # Windows: set ANDROID_HOME to your SDK folder
bash scripts/agvn/prepare-native-deps.sh     # libadrenotools + liblinkernsbypass at pinned commits
./gradlew assembleRelease --no-daemon
# -> app/build/outputs/apk/release/app-release.apk
```

## Version
`AGVN_VERSION_CODE` and `AGVN_VERSION_NAME` live in `gradle.properties`.
Environment variables with the same names override them (useful in CI). Raise the version code for every
release that customers install over an older one.

## Release signing (maintainer only)
Create `keystore.properties` **outside the repository** (or at the repo root, which git ignores):
```
storeFile=C:/AGVN/keys/agvn-release.jks
storePassword=...
keyAlias=agvn
keyPassword=...
```
Point the build at it with the environment variable `AGVN_KEYSTORE_PROPS=<path to keystore.properties>`.
- With the file: the release APK is signed with the AGVN key.
- Without the file: the release APK is debug-signed and Gradle prints a Vietnamese warning. Never hand
  such an APK to customers — it cannot update a correctly signed install.
- `-PagvnRequireReleaseSigning=true` makes a missing file a hard build error (use it for real releases).

Record the release certificate SHA-256 (from `apksigner verify --print-certs`) as
`release_cert_sha256=` in `scripts/agvn/pins.txt` and compare it on every release.
Never commit `.jks`, `.keystore` or `keystore.properties` files.

## Pinned downloads
`imagefs.tar.zst` and `proton-9.0-arm64ec.tar.zst` are downloaded at build time and checked against the
SHA-256 values in `scripts/agvn/pins.txt`; a mismatch fails the build.
For offline rebuilds pass a cache folder: `./gradlew assembleRelease -PagvnAssetCache=/path/to/cache`.
Verified files are copied into the cache after the first download and reused from it afterwards.

## Checks
```bash
$ANDROID_HOME/build-tools/35.0.0/aapt2 dump badging app/build/outputs/apk/release/app-release.apk | head -1
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
./gradlew testReleaseUnitTest --no-daemon
```
