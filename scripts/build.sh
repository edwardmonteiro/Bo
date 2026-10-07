#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd)
: "${ANDROID_JAR:?Set ANDROID_JAR to Android API 35+ android.jar}"
: "${BUILD_TOOLS:?Set BUILD_TOOLS to Android build-tools directory}"
JAVAC=${JAVAC:-javac}
JAR=${JAR:-jar}
KEYTOOL=${KEYTOOL:-keytool}
OUT="$PROJECT_ROOT/build"
mkdir -p "$OUT/classes" "$OUT/core-classes" "$OUT/dex" "$OUT/generated" "$OUT/aar"
"$BUILD_TOOLS/aapt2" compile --dir "$PROJECT_ROOT/app/src/main/res" -o "$OUT/resources.zip"
"$BUILD_TOOLS/aapt2" link -o "$OUT/base.apk" --manifest "$PROJECT_ROOT/app/src/main/AndroidManifest.xml" -I "$ANDROID_JAR" -A "$PROJECT_ROOT/core/src/main/assets" --java "$OUT/generated" "$OUT/resources.zip"
find "$PROJECT_ROOT/core/src/main/java" -name '*.java' > "$OUT/core-sources.txt"
"$JAVAC" -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR" -d "$OUT/core-classes" @"$OUT/core-sources.txt"
"$JAR" cf "$OUT/aar/classes.jar" -C "$OUT/core-classes" .
cp "$PROJECT_ROOT/core/src/main/AndroidManifest.xml" "$OUT/aar/AndroidManifest.xml"
cp -R "$PROJECT_ROOT/core/src/main/assets" "$OUT/aar/"
"$JAR" cf "$OUT/bo-core-0.1.0.aar" -C "$OUT/aar" .
find "$PROJECT_ROOT/app/src/main/java" "$OUT/generated" -name '*.java' > "$OUT/sources.txt"
"$JAVAC" -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR:$OUT/aar/classes.jar" -d "$OUT/classes" @"$OUT/sources.txt"
"$JAR" cf "$OUT/app-classes.jar" -C "$OUT/classes" .
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 28 --output "$OUT/dex" "$OUT/app-classes.jar" "$OUT/aar/classes.jar"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
python3 - "$OUT" <<'PY'
import zipfile,pathlib,sys
p=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(p/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for f in (p/'dex').glob('*.dex'):z.write(f,f.name)
PY
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
# Development signing only. Production: supply BO_KEYSTORE, BO_STOREPASS and BO_KEY_ALIAS.
BO_KEYSTORE=${BO_KEYSTORE:-"$HOME/.android/bo-development.keystore"}
export BO_STOREPASS=${BO_STOREPASS:-android}
BO_KEY_ALIAS=${BO_KEY_ALIAS:-bo}
if [ ! -f "$BO_KEYSTORE" ]; then
  mkdir -p "$(dirname "$BO_KEYSTORE")"
  "$KEYTOOL" -genkeypair -keystore "$BO_KEYSTORE" -storepass:env BO_STOREPASS -keypass:env BO_STOREPASS -alias "$BO_KEY_ALIAS" -dname 'CN=Bo Development,O=Bo,C=BR' -keyalg RSA -keysize 2048 -validity 10000
  chmod 600 "$BO_KEYSTORE"
fi
"$BUILD_TOOLS/apksigner" sign --ks "$BO_KEYSTORE" --ks-pass env:BO_STOREPASS --ks-key-alias "$BO_KEY_ALIAS" --out "$OUT/Bo-v0.1.0.apk" "$OUT/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$OUT/Bo-v0.1.0.apk"
python3 - "$OUT" <<'PY'
import pathlib,sys,hashlib
p=pathlib.Path(sys.argv[1])
for name in ['Bo-v0.1.0.apk','bo-core-0.1.0.aar']:
 f=p/name
 print(f'{name}: {f.stat().st_size:,} bytes | SHA256 {hashlib.sha256(f.read_bytes()).hexdigest()}')
PY
