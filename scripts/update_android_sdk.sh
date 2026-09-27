#!/usr/bin/env bash
set -euo pipefail

info() { printf '[INFO] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }
error() { printf '[ERROR] %s\n' "$*" >&2; exit 1; }

OS_NAME="$(uname -s 2>/dev/null || echo Unknown)"

case "$OS_NAME $(uname -m)" in
  "Darwin arm64")  DEFAULT_SDK_ROOT="${HOME}/Library/Android/sdk"; CLI_OS="darwin_arm64" ;;
  "Darwin x86_64") DEFAULT_SDK_ROOT="${HOME}/Library/Android/sdk"; CLI_OS="darwin_x86_64" ;;
  "Linux x86_64")  DEFAULT_SDK_ROOT="${HOME}/Android/Sdk";         CLI_OS="linux_x86_64" ;;
  *) error "Nicht unterstütztes System: $OS_NAME $(uname -m)" ;;
esac

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$DEFAULT_SDK_ROOT}}"
CLI_URL="https://dl.google.com/android/cli/latest/${CLI_OS}/android"
CLI_INSTALL_DIR="${HOME}/.local/bin"

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# Das `android`-CLI ersetzt sdkmanager (deprecated) und wird für Update und
# Neuinstallation gebraucht. Fehlt es, wird es wie vom offiziellen install.sh
# nach ~/.local/bin geladen (ohne Shell-Profile anzufassen).
ensure_android_cli() {
  if command_exists android; then
    ANDROID_CLI="$(command -v android)"
    info "Aktualisiere android-CLI ($ANDROID_CLI) ..."
    "$ANDROID_CLI" update || warn "android update schlug fehl – fahre mit dem SDK-Update fort."
    return
  fi
  if [[ -x "$CLI_INSTALL_DIR/android" ]]; then
    ANDROID_CLI="$CLI_INSTALL_DIR/android"
    warn "$CLI_INSTALL_DIR ist nicht im PATH."
    "$ANDROID_CLI" update || warn "android update schlug fehl – fahre mit dem SDK-Update fort."
    return
  fi

  info "android-CLI fehlt – lade $CLI_URL ..."
  mkdir -p "$CLI_INSTALL_DIR"
  local tmp
  tmp="$(mktemp)"
  curl -fsSL "$CLI_URL" -o "$tmp" || { rm -f "$tmp"; error "Download des android-CLI fehlgeschlagen."; }
  mv "$tmp" "$CLI_INSTALL_DIR/android"
  chmod +x "$CLI_INSTALL_DIR/android"
  ANDROID_CLI="$CLI_INSTALL_DIR/android"
  info "android-CLI installiert: $ANDROID_CLI"
  [[ ":$PATH:" == *":$CLI_INSTALL_DIR:"* ]] || warn "$CLI_INSTALL_DIR in den PATH aufnehmen, um 'android' direkt aufzurufen."
}

run_android_sdk() {
  "$ANDROID_CLI" --sdk="$SDK_ROOT" sdk "$@"
}

# Liefert die Paketliste (Name je Zeile) aus einem Abschnitt von `sdk list --all`.
list_packages() {
  local section="$1"
  awk -v s="$section" '
    /^Installed packages:/ {cur="installed"; next}
    /^Available packages:/ {cur="available"; next}
    cur==s && NF {print $1}
  ' "$SDK_LIST"
}

# Neueste stabile Version eines Präfixes (ohne rc/beta/ext).
latest_stable() {
  local prefix="$1"
  list_packages available \
    | grep -E "^${prefix}[0-9]+(\.[0-9]+)*$" \
    | sed "s#^${prefix}##" | sort -V | tail -n1
}

install_if_missing() {
  local package="$1"
  if list_packages installed | grep -qxF "$package"; then
    info "$package bereits installiert."
  else
    info "Installiere $package ..."
    run_android_sdk install "$package"
  fi
}

ensure_android_cli
info "Verwende android-CLI $("$ANDROID_CLI" --version 2>/dev/null | tail -n1), SDK: $SDK_ROOT"
mkdir -p "$SDK_ROOT"

if [[ -d "$SDK_ROOT/platforms" || -d "$SDK_ROOT/build-tools" ]]; then
  info "Aktualisiere vorhandene Pakete ..."
  run_android_sdk update || error "android sdk update schlug fehl."
else
  info "Leeres SDK – Neuinstallation."
fi

SDK_LIST="$(mktemp)"
trap 'rm -f "$SDK_LIST"' EXIT
run_android_sdk list --all >"$SDK_LIST" 2>/dev/null || error "android sdk list schlug fehl."

platform="$(latest_stable 'platforms/android-')"
if [[ -n "$platform" ]]; then
  install_if_missing "platforms/android-$platform"
else
  warn "Keine stabile Plattform im Katalog gefunden."
fi

for package in platform-tools cmdline-tools/latest; do
  install_if_missing "$package"
done

build_tools="$(latest_stable 'build-tools/')"
if [[ -n "$build_tools" ]]; then
  install_if_missing "build-tools/$build_tools"
else
  warn "Keine stabilen Build-Tools im Katalog gefunden."
fi

info "SDK-Update abgeschlossen. Verfügbare Plattformen:"
ls "$SDK_ROOT/platforms" 2>/dev/null || warn "Keine Plattformverzeichnisse gefunden."
