#!/bin/bash
# Fetch the exact build tools used by CI. Every download is pinned by SHA-256.
set -euo pipefail

JDK_VERSION="jdk8u514-b01"
JDK_URL="https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u514-b01/OpenJDK8U-jdk_x64_linux_hotspot_8u514b01.tar.gz"
JDK_SHA256="27eda854481d20806d918b30cdd417c6bed4fd15882e24f3595dc8ae7fb5d095"

PROGUARD_URL="https://repo1.maven.org/maven2/net/sf/proguard/proguard-base/6.2.2/proguard-base-6.2.2.jar"
PROGUARD_SHA256="7587a3a415d6aea1946f597f4babe710a245f085bac4dc5e7cda653acfc39b03"

MICROEMU_URL="https://repo1.maven.org/maven2/org/microemu/microemulator/2.0.4/microemulator-2.0.4.jar"
MICROEMU_SHA256="dbd5f3eb8365d3e839d6a203149e0e3776fc1a0585e16ac1fc23f76c9fcae1c6"

cd "$(dirname "$0")/.."
mkdir -p sdk

if [ ! -x "sdk/$JDK_VERSION/bin/javac" ]; then
  echo "Downloading Temurin $JDK_VERSION"
  tmp="$(mktemp)"
  verified=0

  # GitHub release assets occasionally return a small HTML error page with a
  # 200 status. Retry the whole download when the pinned hash does not match.
  for attempt in 1 2 3; do
    if curl --fail --location --retry 3 --retry-all-errors \
        --connect-timeout 20 --output "$tmp" "$JDK_URL" &&
       echo "$JDK_SHA256  $tmp" | sha256sum --check --status -; then
      verified=1
      break
    fi
    echo "JDK download attempt $attempt failed; retrying"
    sleep 5
  done

  if [ "$verified" -ne 1 ]; then
    rm -f "$tmp"
    echo "Could not download a JDK archive matching $JDK_SHA256" >&2
    exit 1
  fi

  tar -xzf "$tmp" -C sdk
  rm -f "$tmp"
fi

if [ ! -f sdk/proguard.jar ]; then
  echo "Downloading ProGuard 6.2.2"
  curl --fail --location --retry 3 --output sdk/proguard.jar "$PROGUARD_URL"
  echo "$PROGUARD_SHA256  sdk/proguard.jar" | sha256sum --check -
fi

if [ ! -f sdk/microemulator.jar ]; then
  echo "Downloading MicroEmulator 2.0.4"
  curl --fail --location --retry 3 --retry-all-errors \
    --output sdk/microemulator.jar "$MICROEMU_URL"
  echo "$MICROEMU_SHA256  sdk/microemulator.jar" | sha256sum --check -
fi

echo "JDK: $(sdk/$JDK_VERSION/bin/java -version 2>&1 | head -1)"
echo "ProGuard: $(sdk/$JDK_VERSION/bin/java -jar sdk/proguard.jar 2>&1 | head -1)"
echo "MicroEmulator: $MICROEMU_SHA256"
