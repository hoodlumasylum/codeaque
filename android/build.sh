#!/usr/bin/env bash
# Builds android/dist/codeaque-launcher.apk with only the Android SDK command-line tools (no Gradle,
# no libraries to download). Needs: JDK 17+, ANDROID_HOME with build-tools 34 and platform 34.
#   ./build.sh          build + sign (debug key, or $CODEAQUE_KEYSTORE if set)
#   ./build.sh test     run the plain-Java catalogue checks
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-/opt/android}"; BT="$SDK/build-tools/34.0.0"; JAR="$SDK/platforms/android-34/android.jar"
B=build; rm -rf "$B"; mkdir -p "$B/classes" "$B/dex" dist
if [ "${1:-}" = test ]; then
  javac -d "$B/test" app/src/main/java/com/codeaque/launcher/Catalog.java app/src/main/java/com/codeaque/launcher/Palette.java app/src/main/java/com/codeaque/launcher/UpdateInfo.java app/src/main/java/com/codeaque/launcher/BootAnim.java app/src/main/java/com/codeaque/launcher/Rotation.java app/src/main/java/com/codeaque/launcher/PlayArt.java app/src/main/java/com/codeaque/launcher/DcScan.java app/src/main/java/com/codeaque/launcher/PspScan.java app/src/main/java/com/codeaque/launcher/LockPin.java app/src/main/java/com/codeaque/launcher/SafeContacts.java app/src/test/java/com/codeaque/launcher/CatalogTest.java
  exec java -cp "$B/test" com.codeaque.launcher.CatalogTest
fi
sed "s/<manifest /<manifest package=\"com.codeaque.launcher\" /" app/src/main/AndroidManifest.xml > "$B/AndroidManifest.xml"   # Gradle uses namespace instead
"$BT/aapt2" compile --dir app/src/main/res -o "$B/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest "$B/AndroidManifest.xml" -A app/src/main/assets -o "$B/base.apk" \
  --java "$B/gen" "$B/res.zip" --min-sdk-version 24 --target-sdk-version 30 --version-code 127 --version-name 0.10.27
javac --release 11 -classpath "$JAR" -d "$B/classes" $(find app/src/main/java "$B/gen" -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/base.apk" "$B/unsigned.apk"; python3 -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1],'a').write(sys.argv[2],'classes.dex')" "$B/unsigned.apk" "$B/dex/classes.dex"
"$BT/zipalign" -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"
KS="${CODEAQUE_KEYSTORE:-$HOME/.android/codeaque-debug.keystore}"
if [ ! -f "$KS" ]; then mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias codeaque -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=CodeAque Debug" >/dev/null 2>&1; fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:${CODEAQUE_KS_PASS:-android}" --ks-key-alias "${CODEAQUE_KS_ALIAS:-codeaque}" --out dist/codeaque-launcher.apk "$B/aligned.apk"
"$BT/apksigner" verify dist/codeaque-launcher.apk && ls -la dist/codeaque-launcher.apk
