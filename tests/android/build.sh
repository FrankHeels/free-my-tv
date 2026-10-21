#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${ANDROID_HOME:?Set ANDROID_HOME}" "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
BT="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)"
AJ="$(ls -d "$ANDROID_HOME"/platforms/android-*/android.jar | sort -V | tail -1)"
TEST_OUT=out/android-tests
mkdir -p "$TEST_OUT/classes" "$TEST_OUT/gen"
"$BT/aapt2" link -I "$AJ" --manifest tests/android/AndroidManifest.xml --min-sdk-version 28 --target-sdk-version 33 -o "$TEST_OUT/base.apk"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -encoding UTF-8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" -d "$TEST_OUT/classes" \
  app/src/io/freemytv/Continue{Model,Prefs,Watching,Images,Launch,Text}.java tests/android/src/io/freemytv/*.java
"$BT/d8" --release --min-api 28 --lib "$AJ" --output "$TEST_OUT" $(find "$TEST_OUT/classes" -name '*.class')
(cd "$TEST_OUT" && zip -qj base.apk classes.dex)
"$BT/zipalign" -f 4 "$TEST_OUT/base.apk" "$TEST_OUT/aligned.apk"
if [ ! -f "$TEST_OUT/test.keystore" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$TEST_OUT/test.keystore" -alias test -keyalg RSA -keysize 2048 -validity 30 -storepass testpass -keypass testpass -dname 'CN=Local tests' >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$TEST_OUT/test.keystore" --ks-pass pass:testpass --out "$TEST_OUT/tests.apk" "$TEST_OUT/aligned.apk"
echo "Built $TEST_OUT/tests.apk"
