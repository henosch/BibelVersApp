#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

OS_NAME="$(uname -s 2>/dev/null || echo Unknown)"

info() { printf '[INFO] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }
error() { printf '[ERROR] %s\n' "$*" >&2; exit 1; }

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

ensure_brew() {
  if ! command_exists brew; then
    warn "Homebrew wird benötigt, um fehlende Abhängigkeiten zu installieren, wurde aber nicht gefunden."
    warn "Installiere Homebrew mit: /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
    exit 1
  fi
}

detect_java_home() {
  local candidates=()

  if [[ -n "${JAVA_HOME:-}" ]]; then
    candidates+=("$JAVA_HOME")
  fi

  case "$OS_NAME" in
    Darwin)
      candidates+=("/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home")
      if command_exists /usr/libexec/java_home; then
        local detected
        detected="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
        if [[ -n "$detected" ]]; then
          candidates+=("$detected")
        fi
      fi
      ;;
    Linux)
      candidates+=("/usr/lib/jvm/java-17-openjdk-amd64" "/usr/lib/jvm/java-17-openjdk" "/usr/lib/jvm/java-17-openjdk-arm64")
      ;;
  esac

  for candidate in "${candidates[@]}"; do
    if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
      JAVA_HOME="$candidate"
      return 0
    fi
  done
  return 1
}

install_java_mac() {
  ensure_brew
  info "Installiere Temurin 17 über Homebrew..."
  if brew list --cask temurin17 >/dev/null 2>&1; then
    info "Temurin 17 bereits installiert."
  else
    if ! brew install --cask temurin17 >/dev/null 2>&1; then
      warn "temurin17 Installation fehlgeschlagen, versuche temurin."
      brew install --cask temurin >/dev/null 2>&1 || error "Konnte Temurin nicht installieren."
    fi
  fi

  if ! detect_java_home; then
    error "JAVA_HOME konnte nach der Installation nicht ermittelt werden."
  fi
}

ensure_java() {
  if detect_java_home; then
    export JAVA_HOME
    export PATH="$JAVA_HOME/bin:$PATH"
    info "JAVA_HOME verwendet: $JAVA_HOME"
    return
  fi

  case "$OS_NAME" in
    Darwin)
      install_java_mac
      export JAVA_HOME
      export PATH="$JAVA_HOME/bin:$PATH"
      ;;
    Linux)
      error "JAVA_HOME konnte nicht ermittelt werden. Bitte OpenJDK 17 installieren (z.B. sudo apt install openjdk-17-jdk) und erneut versuchen."
      ;;
    *)
      error "Nicht unterstütztes Betriebssystem ($OS_NAME). Bitte JAVA_HOME manuell setzen."
      ;;
  esac
}

ensure_gradlew_executable() {
  if [[ ! -x "$SCRIPT_DIR/gradlew" ]]; then
    chmod +x "$SCRIPT_DIR/gradlew" || error "Konnte gradlew nicht ausführbar machen."
  fi
}

ensure_java
ensure_gradlew_executable

if [ ! -f "local.properties" ]; then
  if [ -d "${ANDROID_SDK_ROOT:-}" ]; then
    echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
  elif [ -d "${ANDROID_HOME:-}" ]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
  else
    error "SDK nicht gefunden. Bitte ANDROID_HOME oder ANDROID_SDK_ROOT setzen."
  fi
fi

export GRADLE_USER_HOME="$SCRIPT_DIR/.gradle-tmp"
mkdir -p "$GRADLE_USER_HOME"

info "Starte Build (assembleDebug)..."
"$SCRIPT_DIR/gradlew" assembleDebug
