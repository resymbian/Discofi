#!/bin/bash

# Path to Java JDK. The JDK listed here must support targeting Java 1.2 (J2ME).
# For example OpenJDK 8 (https://adoptium.net/temurin/releases/?version=8)
# Always prefer the pinned JDK extracted by sdk/fetch-tools.sh. GitHub
# Actions exports its own JAVA_HOME, which must not leak into this build.
JDK_DIR=$(ls -d sdk/jdk* 2>/dev/null | head -1)
if [ -n "$JDK_DIR" ]; then
  export JAVA_HOME="$PWD/$JDK_DIR"
fi

if ! command -v node > /dev/null; then
  echo "Please install Node.js"
  exit 1
fi

node sdk/compile_all.js