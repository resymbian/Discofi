#!/bin/bash
# Fetch the exact build tools used by CI. Every download is pinned by SHA-256.
set -euo pipefail

JDK_VERSION="jdk8u514-b01"
JDK_URL="https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u514-b01/OpenJDK8U-jdk_x64_linux_hotspot_8u514b01.tar.gz"
JDK_SHA256="27eda854481d20806d918b30cdd417c6bed4fd15882e24f3595dc8ae7fb5d095"

PROGUARD_URL="https://repo1.maven.org/maven2/net/sf/proguard/proguard-base/6.2.2/proguard-base-6.2.2.jar"
PROGUARD_SHA256="7587a3a415d6aea1946f597f4babe710a245f085bac4dc5e7cda653acfc39b03"

cd "$(dirname "$0")/.."
mkdir -p sdk

if [ ! -x "sdk/$JDK_VERSION/bin/javac" ]; then
  echo "Downloading Temurin $JDK_VERSION"
  tmp="$(mktemp)"
  curl --fail --location --retry 3 --output "$tmp" "$JDK_URL"
  echo "$JDK_SHA256  $tmp" | sha256sum --check -
  tar -xzf "$tmp" -C sdk
  rm -f "$tmp"
fi

if [ ! -f sdk/proguard.jar ]; then
  echo "Downloading ProGuard 6.2.2"
  curl --fail --location --retry 3 --output sdk/proguard.jar "$PROGUARD_URL"
  echo "$PROGUARD_SHA256  sdk/proguard.jar" | sha256sum --check -
fi

echo "JDK: $(sdk/$JDK_VERSION/bin/java -version 2>&1 | head -1)"
echo "ProGuard: $(sdk/$JDK_VERSION/bin/java -jar sdk/proguard.jar 2>&1 | head -1)"
