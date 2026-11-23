#!/bin/bash
# Run the HTTP timeout regression test in MicroEmulator.
#
#   ./sdk/timeouttest.sh
#
# Build first with ./build.sh. The fake server accepts a request and never
# answers; the test requires HTTPQueue to close it within a few seconds.
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
PORT="${FAKE_DISCORD_PORT:-8098}"

if [ ! -f "$APP_JAR" ]; then
  echo "Missing $APP_JAR; run ./build.sh first" >&2
  exit 1
fi

WORK="$(mktemp -d)"
SERVER_PID=""
cleanup() {
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null || true
  rm -rf "$WORK"
}
trap cleanup EXIT

mkdir -p "$WORK/classes"
cp tests/DiscofiTimeoutTest.java "$WORK/DiscofiTimeoutTest.java"

"$JAVAC" -nowarn -source 1.3 -target 1.2 \
  -cp "$APP_JAR:$ME_JAR" -d "$WORK/classes" "$WORK/DiscofiTimeoutTest.java"

cat > "$WORK/MANIFEST.MF" <<'EOF'
MIDlet-1: DiscofiTimeoutTest,,fi.gtrxac.discord.DiscofiTimeoutTest
MIDlet-Name: DiscofiTimeoutTest
MIDlet-Vendor: resymbian
MIDlet-Version: 1.0
MicroEdition-Configuration: CLDC-1.0
MicroEdition-Profile: MIDP-2.0
EOF

mkdir -p "$WORK/app"
(cd "$WORK/app" && unzip -q "$APP_JAR")
cp -r "$WORK/classes/fi" "$WORK/app/"
(cd "$WORK/app" && "$JAR" cfm "$WORK/test.jar" "$WORK/MANIFEST.MF" .)

JAR_SIZE="$(stat -c %s "$WORK/test.jar")"
cat > "$WORK/test.jad" <<EOF
MIDlet-1: DiscofiTimeoutTest,,fi.gtrxac.discord.DiscofiTimeoutTest
MIDlet-Name: DiscofiTimeoutTest
MIDlet-Vendor: resymbian
MIDlet-Version: 1.0
MicroEdition-Configuration: CLDC-1.0
MicroEdition-Profile: MIDP-2.0
MIDlet-Jar-URL: test.jar
MIDlet-Jar-Size: $JAR_SIZE
FakeApiUrl: http://127.0.0.1:$PORT
EOF

python3 tests/fake_discord.py "$PORT" > "$WORK/server.txt" 2>&1 &
SERVER_PID=$!
sleep 1

set +e
timeout 30s "$JAVA" -cp "$ME_JAR" org.microemu.app.Headless \
  "file://$WORK/test.jad" --rms memory --quit > "$WORK/output.txt" 2>&1
STATUS=$?
set -e

echo "--- client ---"
cat "$WORK/output.txt"
echo "--- fake server saw ---"
cat "$WORK/server.txt"

if [ "$STATUS" -ne 0 ]; then
  echo "MicroEmulator exited with status $STATUS (hang or crash)" >&2
  exit 1
fi

if grep -q '^FAIL:' "$WORK/output.txt" || ! grep -q '^TIMEOUT TEST PASSED$' "$WORK/output.txt"; then
  echo "Timeout test failed" >&2
  exit 1
fi

echo "Timeout test passed"
