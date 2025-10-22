#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f "local.properties" ]; then
  if [ -d "$ANDROID_SDK_ROOT" ]; then
    echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
  elif [ -d "$ANDROID_HOME" ]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
  else
    echo "SDK nicht gefunden. Bitte ANDROID_HOME oder ANDROID_SDK_ROOT setzen." >&2
    exit 1
  fi
fi

export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$SCRIPT_DIR/.gradle-tmp"
mkdir -p "$GRADLE_USER_HOME"

./gradlew assembleDebug
