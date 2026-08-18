#!/usr/bin/env bash
# Builds Subtle Alarm without the Google Android SDK, using only:
#   - Ubuntu packages: aapt, dalvik-exchange (dx), zipalign, apksigner
#     (apt-get install aapt dalvik-exchange zipalign apksigner android-framework-res)
#   - A framework classes jar for the compile classpath (API 34), e.g. Robolectric's
#     android-all from Maven Central.
#   - A JDK (11+; javac is invoked with --release 8 so dx accepts the bytecode).
#
# Usage: scripts/build-offline.sh /path/to/android-all-14.jar
# Output: build-offline/SubtleAlarm.apk (debug-signed, ready to sideload)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_JAR="${1:?usage: build-offline.sh /path/to/android-framework.jar}"
FRAMEWORK_RES="${FRAMEWORK_RES:-/usr/share/android-framework-res/framework-res.apk}"
OUT="$ROOT/build-offline"
SRC="$ROOT/app/src/main"

# version comes from version.properties (shared with the Gradle build)
VERSION_CODE=$(grep '^versionCode=' "$ROOT/version.properties" | cut -d= -f2)
VERSION_NAME=$(grep '^versionName=' "$ROOT/version.properties" | cut -d= -f2)

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

# aapt (v1) needs the package attribute on <manifest>; AGP 8 forbids it in the
# checked-in manifest, so inject it into a working copy.
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android"|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.subtlealarm.app"|' \
    "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"

echo "== aapt: compiling resources"
aapt package -f -m \
    -M "$OUT/AndroidManifest.xml" \
    -S "$SRC/res" \
    -I "$FRAMEWORK_RES" \
    -J "$OUT/gen" \
    -F "$OUT/resources.ap_" \
    --min-sdk-version 26 \
    --target-sdk-version 34 \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME"

echo "== javac: compiling sources"
find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 8 -Xlint:-options \
    -cp "$ANDROID_JAR" \
    -d "$OUT/classes" \
    @"$OUT/sources.txt"

echo "== dx: dexing"
dalvik-exchange --dex --min-sdk-version=26 \
    --output="$OUT/dex/classes.dex" "$OUT/classes"

echo "== packaging"
cp "$OUT/resources.ap_" "$OUT/unaligned.apk"
(cd "$OUT/dex" && zip -q -X "$OUT/unaligned.apk" classes.dex)

zipalign -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

# use the committed debug keystore so every build has the same signature
# (otherwise `adb install -r` upgrades fail with a signature mismatch)
KS="$ROOT/scripts/debug.keystore"
if [ ! -f "$KS" ]; then
    KS="$OUT/debug.keystore"
    keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
        -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi

APK="$OUT/SubtleAlarm-v$VERSION_NAME.apk"
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --min-sdk-version 26 --out "$APK" "$OUT/aligned.apk"

rm -f "$OUT/unaligned.apk" "$OUT/aligned.apk"
echo "== done: $APK"
