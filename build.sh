#!/bin/bash

# Path to Java JDK. The JDK listed here must support targeting Java 1.2 (J2ME).
# For example OpenJDK 8 (https://adoptium.net/temurin/releases/?version=8)
if [ -z "$JAVA_HOME" ]; then
  export JAVA_HOME=sdk/jdk*
fi

if ! command -v node > /dev/null; then
  echo "Please install Node.js"
  exit 1
fi

node sdk/compile_all.js