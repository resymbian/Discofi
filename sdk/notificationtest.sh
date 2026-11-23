#!/bin/bash
# Run the native-notification bridge protocol test in MicroEmulator.
#
#   ./sdk/notificationtest.sh
#
# Build first with ./build.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ ! -f sdk/microemulator.jar ] || ! ls -d sdk/jdk*/bin/javac >/dev/null 2>&1; then
  ./sdk/fetch-tools.sh
fi

JDK_BIN="$(ls -d sdk/jdk*/bin | head -1)"
JAVAC="$PWD/$JDK_BIN/javac"
JAVA="$PWD/$JDK_BIN/java"
JAR="$PWD/$JDK_BIN/jar"
ME_JAR="$PWD/sdk/microemulator.jar"
APP_JAR="$PWD/bin/discofi_debug.jar"

if [ ! -f "$APP_JAR" ]; then
  echo "Missing $APP_JAR; run ./build.sh first" >&2
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$WORK/classes" "$WORK/app"
cp tests/DiscofiNotificationTest.java "$WORK/DiscofiNotificationTest.java"

"$JAVAC" -nowarn -source 1.3 -target 1.2 \
  -cp "$APP_JAR:$ME_JAR" -d "$WORK/classes" "$WORK/DiscofiNotificationTest.java"

cat > "$WORK/MANIFEST.MF" <<'EOF'
MIDlet-1: DiscofiNotificationTest,,fi.gtrxac.discord.DiscofiNotificationTest
MIDlet-Name: DiscofiNotificationTest
MIDlet-Vendor: resymbian
MIDlet-Version: 1.0
MicroEdition-Configuration: CLDC-1.0
MicroEdition-Profile: MIDP-2.0
EOF

(cd "$WORK/app" && unzip -q "$APP_JAR")
cp -r "$WORK/classes/fi" "$WORK/app/"
(cd "$WORK/app" && "$JAR" cfm "$WORK/test.jar" "$WORK/MANIFEST.MF" .)

JAR_SIZE="$(stat -c %s "$WORK/test.jar")"
cat > "$WORK/test.jad" <<EOF
MIDlet-1: DiscofiNotificationTest,,fi.gtrxac.discord.DiscofiNotificationTest
MIDlet-Name: DiscofiNotificationTest
MIDlet-Vendor: resymbian
MIDlet-Version: 1.0
MicroEdition-Configuration: CLDC-1.0
MicroEdition-Profile: MIDP-2.0
MIDlet-Jar-URL: test.jar
MIDlet-Jar-Size: $JAR_SIZE
EOF

set +e
timeout 30s "$JAVA" -cp "$ME_JAR" org.microemu.app.Headless \
  "file://$WORK/test.jad" --rms memory --quit > "$WORK/output.txt" 2>&1
STATUS=$?
set -e

cat "$WORK/output.txt"

if [ "$STATUS" -ne 0 ]; then
  echo "MicroEmulator exited with status $STATUS (hang or crash)" >&2
  exit 1
fi

if grep -q '^FAIL:' "$WORK/output.txt" || ! grep -q '^NOTIFICATION TEST PASSED$' "$WORK/output.txt"; then
  echo "Notification test failed" >&2
  exit 1
fi

echo "Notification test passed"
