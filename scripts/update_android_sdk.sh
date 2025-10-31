#!/usr/bin/env bash
set -euo pipefail

info() { printf '[INFO] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }
error() { printf '[ERROR] %s\n' "$*" >&2; exit 1; }

OS_NAME="$(uname -s 2>/dev/null || echo Unknown)"

case "$OS_NAME" in
  Darwin)
    DEFAULT_SDK_ROOT="${HOME}/Library/Android/sdk"
    CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
    ;;
  Linux)
    DEFAULT_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${HOME}/Android/Sdk}}"
    CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    ;;
  *)
    error "Nicht unterstütztes Betriebssystem: $OS_NAME"
    ;;
esac

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$DEFAULT_SDK_ROOT}}"
[[ -d "$SDK_ROOT" ]] || error "Android SDK nicht gefunden. Bitte ANDROID_SDK_ROOT oder ANDROID_HOME setzen."

CMDLINE_DIR="$SDK_ROOT/cmdline-tools/latest"
SDKMANAGER="$CMDLINE_DIR/bin/sdkmanager"

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

sha256_file() {
  if command_exists shasum; then
    shasum -a 256 "$1" | awk '{print $1}'
  elif command_exists sha256sum; then
    sha256sum "$1" | awk '{print $1}'
  else
    warn "Keine SHA-256 Prüfsummen-Utility gefunden – Prüfsummenprüfung wird übersprungen."
    return 1
  fi
}

ensure_cmdline_tools() {
  if [[ -x "$SDKMANAGER" ]]; then
    info "Command-line Tools bereits vorhanden."
    return
  fi

  info "Installiere Android Command-line Tools nach $CMDLINE_DIR ..."
  local tmpdir
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' RETURN

  curl -L "$CMDLINE_URL" -o "$tmpdir/cmdline-tools.zip"

  local checksum
  checksum="$(sha256_file "$tmpdir/cmdline-tools.zip" || true)"
  if [[ -n "$checksum" ]]; then
    info "Prüfe SHA-256 Prüfsumme der Command-line Tools ..."
    local expected
    case "$OS_NAME" in
      Darwin) expected="fd706ccb60edcc9776d274373c3c611f30c362a6fd844120809ec8210ee55411" ;;
      Linux) expected="f18896cf734b39a0944b05b3364ee3c275f2d7475744bc1e050b28bbcf1bdb38" ;;
    esac
    if [[ -n "${expected:-}" && "$checksum" != "$expected" ]]; then
      error "SHA-256 Prüfsumme stimmt nicht überein (erwartet $expected, erhalten $checksum)."
    fi
  fi

  unzip -q "$tmpdir/cmdline-tools.zip" -d "$tmpdir"

  mkdir -p "$SDK_ROOT/cmdline-tools"
  rm -rf "$CMDLINE_DIR"
  mv "$tmpdir/cmdline-tools" "$CMDLINE_DIR"

  info "Command-line Tools installiert."
  trap - RETURN
}

run_sdkmanager() {
  "$SDKMANAGER" --sdk_root="$SDK_ROOT" "$@"
}

package_available() {
  local package="$1"
  run_sdkmanager --list | grep -E "^[[:space:]]*${package}($|[[:space:]])" >/dev/null 2>&1
}

install_package_if_available() {
  local package="$1"
  if package_available "$package"; then
    info "Installiere $package ..."
    yes | run_sdkmanager "$package" >/dev/null
  else
    warn "$package nicht im Katalog. Überspringe Installation."
  fi
}

ensure_cmdline_tools

info "Aktualisiere vorhandene Pakete ..."
if ! yes | run_sdkmanager --update >/dev/null; then
  rc=$?
  if [[ $rc -eq 141 ]]; then
    warn "sdkmanager wurde mit SIGPIPE (Exit 141) beendet – vermutlich hat 'yes' den Stream früh geschlossen."
  else
    error "sdkmanager --update schlug fehl (Exit $rc)."
  fi
fi

install_package_if_available "platforms;android-37"

if package_available "build-tools;37.0.0"; then
  install_package_if_available "build-tools;37.0.0"
else
  latest_build_tools="$(run_sdkmanager --list | awk '/build-tools;/{print $1}' | sort -V | tail -n1)"
  if [[ -n "$latest_build_tools" ]]; then
    info "Installiere neuestes verfügbares Build-Tools-Paket ($latest_build_tools) ..."
    yes | run_sdkmanager "$latest_build_tools" >/dev/null
  else
    warn "Keine Build-Tools im Katalog gefunden."
  fi
fi

info "SDK-Update abgeschlossen. Verfügbare Plattformen:"
ls "$SDK_ROOT/platforms" 2>/dev/null || warn "Keine Plattformverzeichnisse gefunden."
