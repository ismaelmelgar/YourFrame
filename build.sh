#!/usr/bin/env bash
# Builds YourFrame.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Adapted from Free My TV's build.sh (MIT, https://github.com/simbasjungles/free-my-tv); see NOTICE.
# Needs a JDK (17+) and the Android SDK command-line tools (build-tools 34+, platform android-34).
set -euo pipefail
cd "$(dirname "$0")"

# --- The app's identity: change it here (and only here) before any publication.
APP_NAME="YourFrame"            # also app_name in app/res/values/strings.xml (the label the TV shows)
APP_ID="io.yourframe.tv"        # the installed package id; the Java package stays io.yourframe.tv
VERSION_CODE=1
VERSION_NAME="0.1"
APK="$APP_NAME.apk"
KEYSTORE="yourframe.keystore"   # created on the first build; keep it (updates need the same key), never commit it

# --- Tools
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" /opt/homebrew/share/android-commandlinetools \
           /usr/local/share/android-commandlinetools /usr/lib/android-sdk; do
    if [ -d "$d/build-tools" ]; then SDK="$d"; break; fi
  done
fi
[ -n "$SDK" ] && [ -d "$SDK/build-tools" ] || { echo "Android SDK not found. Install it and set ANDROID_HOME."; exit 1; }
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
AJ="$(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1 || true)"
[ -f "${AJ:-}" ] || { echo "Android platform not found. Run: sdkmanager \"platforms;android-34\""; exit 1; }
# d8 crashes on OpenJ9: on macOS take a HotSpot JDK 17 instead when JAVA_HOME is unset or OpenJ9.
if { [ -z "${JAVA_HOME:-}" ] || "${JAVA_HOME}/bin/java" -version 2>&1 | grep -q OpenJ9; } && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
# d8 and apksigner are scripts that run the `java` on PATH: make it the same JDK.
[ -n "${JAVA_HOME:-}" ] && export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
command -v "$JAVAC" >/dev/null 2>&1 || { echo "JDK not found. Install JDK 17 and set JAVA_HOME."; exit 1; }
[ -f app/assets/artworks.json ] || { echo "app/assets/artworks.json missing: run tools/make_artlist.py first."; exit 1; }

rm -rf out && mkdir -p out/gen out/classes out/test

# --- Domain tests on the plain JVM: domain + application compiled without android.jar (so they can't use Android).
"$JAVAC" -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -d out/test \
  $(find app/src/io/yourframe/tv/domain app/src/io/yourframe/tv/application app/test -name '*.java')
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp out/test io.yourframe.tv.DomainTest
# The catalogue rules of the data tool (pure Python, no Pillow needed).
if command -v python3 >/dev/null; then python3 -I tools/test_artlist.py; fi
"$BT/aapt2" compile --dir app/res -o out/res.zip
# Fonts uncompressed: Android reads them straight from the APK.
"$BT/aapt2" link -o out/base.apk -I "$AJ" --manifest app/AndroidManifest.xml --java out/gen out/res.zip \
  --rename-manifest-package "$APP_ID" --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --min-sdk-version 26 --target-sdk-version 34 -A app/assets -0 ttf
CPSEP=:; BAT=; case "$(uname -s 2>/dev/null)" in MINGW*|MSYS*|CYGWIN*) CPSEP=";"; BAT=.bat ;; esac
"$JAVAC" -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$AJ$CPSEP$BT/core-lambda-stubs.jar" -classpath "$AJ" \
  -encoding UTF-8 -d out/classes $(find app/src out/gen -name '*.java')
"$BT/d8$BAT" --release --min-api 26 --lib "$AJ" --output out $(find out/classes -name '*.class')
if command -v zip >/dev/null; then (cd out && zip -qj base.apk classes.dex)
else "${JAVA_HOME:+$JAVA_HOME/bin/}jar" -u -M -f out/base.apk -C out classes.dex; fi
"$BT/zipalign" -f -p 4 out/base.apk out/aligned.apk

# --- Sign with this app's own key (created once)
if [ ! -f "$KEYSTORE" ]; then
  "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -alias yourframe -keyalg RSA -keysize 2048 -validity 20000 \
    -storepass yourframe -keypass yourframe -dname "CN=$APP_NAME" >/dev/null 2>&1
fi
"$BT/apksigner$BAT" sign --ks "$KEYSTORE" --ks-pass pass:yourframe --key-pass pass:yourframe --out "$APK" out/aligned.apk
echo "Built $APK ($(du -h "$APK" | cut -f1)). Install: adb install -r $APK"
echo "Screensaver component: $APP_ID/io.yourframe.tv.ArtDream"
