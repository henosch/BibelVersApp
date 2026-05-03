#!/usr/bin/env bash
# Vorlage für lokales build.sh – nach `cp build.template.sh build.sh` anpassen.
# build.sh ist in .gitignore und enthält ggf. sensitive Werte (Keystore-Passwort etc.).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

OS_NAME="$(uname -s 2>/dev/null || echo Unknown)"

info() { printf '[INFO] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }
error() { printf '[ERROR] %s\n' "$*" >&2; exit 1; }

command_exists() { command -v "$1" >/dev/null 2>&1; }

# --- JDK 17 finden ---------------------------------------------------------
detect_java_home() {
  local candidates=()
  [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME")
  case "$OS_NAME" in
    Darwin)
      candidates+=("/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home")
      if command_exists /usr/libexec/java_home; then
        local detected
        detected="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
        [[ -n "$detected" ]] && candidates+=("$detected")
      fi
      ;;
    Linux)
      candidates+=("/usr/lib/jvm/java-17-openjdk-amd64" "/usr/lib/jvm/java-17-openjdk" "/usr/lib/jvm/java-17-openjdk-arm64")
      ;;
  esac
  for c in "${candidates[@]}"; do
    [[ -n "$c" && -x "$c/bin/java" ]] && { JAVA_HOME="$c"; return 0; }
  done
  return 1
}

if detect_java_home; then
  export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
  info "JAVA_HOME: $JAVA_HOME"
else
  error "JDK 17 nicht gefunden. Bitte JAVA_HOME setzen oder Temurin 17 installieren."
fi

[[ -x "$SCRIPT_DIR/gradlew" ]] || chmod +x "$SCRIPT_DIR/gradlew"

# --- local.properties ggf. anlegen -----------------------------------------
if [[ ! -f local.properties ]]; then
  if   [[ -d "${ANDROID_SDK_ROOT:-}" ]]; then echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
  elif [[ -d "${ANDROID_HOME:-}"     ]]; then echo "sdk.dir=$ANDROID_HOME"     > local.properties
  elif [[ -d "$HOME/Library/Android/sdk" ]]; then echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
  else error "Android-SDK nicht gefunden. Bitte ANDROID_HOME / ANDROID_SDK_ROOT setzen."
  fi
fi

export GRADLE_USER_HOME="$SCRIPT_DIR/.gradle-tmp"
mkdir -p "$GRADLE_USER_HOME"

# --- Konfiguration: Privat-Gerät & Keystore --------------------------------
# IP-Präfix des Privat-Test-Geräts (ohne ADB-Port; der wird via mdns ermittelt).
PRIVATE_IP_PREFIX="${PRIVATE_IP_PREFIX:-192.168.1.10}"
# User-ID des Profils, in das installiert werden soll (0 = Hauptprofil).
PRIVATE_USER="${PRIVATE_USER:-0}"

KEYSTORE_FILE="$SCRIPT_DIR/upload-keystore.jks"
# WICHTIG: Passwörter und Alias bei Bedarf hier eintragen – build.sh ist in .gitignore.
KEYSTORE_PASS="${KEYSTORE_PASS:-CHANGE_ME}"
KEY_ALIAS="${KEY_ALIAS:-upload}"

# --- Discovery & Install via adb mdns -------------------------------------
discover_private_device() {
  command_exists adb || error "adb nicht im PATH gefunden."
  local connected
  connected="$(adb devices | awk -v p="$PRIVATE_IP_PREFIX:" 'NR>1 && $2=="device" && index($1,p)==1 {print $1}' | head -1)"
  if [[ -n "$connected" ]]; then PRIVATE_DEVICE="$connected"; info "Bereits verbunden: $PRIVATE_DEVICE"; return 0; fi
  info "Suche Privat-Gerät via mdns ($PRIVATE_IP_PREFIX)..."
  local candidates
  candidates="$(adb mdns services 2>/dev/null \
    | awk -v p="$PRIVATE_IP_PREFIX:" '$2=="_adb-tls-connect._tcp" {for(i=3;i<=NF;i++) if(index($i,p)==1) print $i}' \
    | sort -u)"
  [[ -n "$candidates" ]] || error "Kein adb-mdns-Service mit IP $PRIVATE_IP_PREFIX gefunden."
  local ep
  while IFS= read -r ep; do
    [[ -z "$ep" ]] && continue
    if adb connect "$ep" 2>&1 | grep -qE "connected to|already connected"; then
      adb -s "$ep" get-state >/dev/null 2>&1 && { PRIVATE_DEVICE="$ep"; info "Verbunden: $ep"; return 0; }
    fi
  done <<< "$candidates"
  error "Konnte mit keinem mdns-Endpunkt unter $PRIVATE_IP_PREFIX verbinden."
}

install_apk_private() {
  discover_private_device
  adb -s "$PRIVATE_DEVICE" shell pm list users | grep -qE "UserInfo\{$PRIVATE_USER:" \
    || error "User $PRIVATE_USER existiert nicht auf $PRIVATE_DEVICE."
  info "Installiere $(basename "$1") auf $PRIVATE_DEVICE (User $PRIVATE_USER)..."
  adb -s "$PRIVATE_DEVICE" install -r --user "$PRIVATE_USER" "$1"
}

# --- APK-Signatur ---------------------------------------------------------
find_apksigner() {
  local sdk_dir
  sdk_dir="$(grep -E '^sdk\.dir=' local.properties 2>/dev/null | cut -d'=' -f2-)"
  sdk_dir="${sdk_dir:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}}"
  ls -1 "$sdk_dir"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1
}

sign_release_apk() {
  [[ -f "$KEYSTORE_FILE" ]] || error "Keystore $KEYSTORE_FILE fehlt."
  [[ "$KEYSTORE_PASS" != "CHANGE_ME" ]] || error "KEYSTORE_PASS noch nicht gesetzt."
  local apksigner; apksigner="$(find_apksigner)"
  [[ -n "$apksigner" ]] || error "apksigner nicht gefunden."
  "$apksigner" sign --ks "$KEYSTORE_FILE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$KEYSTORE_PASS" --key-pass "pass:$KEYSTORE_PASS" \
    --out "$2" "$1"
  "$apksigner" verify "$2" >/dev/null
  info "✓ APK signiert: $2"
}

print_help() {
  cat <<EOF
Verwendung: ./build.sh [befehl]

Befehle:
  debug             Erzeugt die Debug-APK (assembleDebug).
  release           Erstellt + signiert Release-Bundle (Play Store) und Release-APK.
  install           Baut Debug-APK und installiert via mdns auf $PRIVATE_IP_PREFIX, User $PRIVATE_USER.
  install-release   Wie install, aber mit signierter Release-APK.
  lint              ./gradlew lint
  test              ./gradlew testDebugUnitTest
  clean             ./gradlew clean

Umgebungsvariablen (überschreiben Defaults):
  PRIVATE_IP_PREFIX  IP-Präfix des Privat-Geräts
  PRIVATE_USER       User-ID des Zielprofils
  KEYSTORE_PASS      Keystore-Passwort
  KEY_ALIAS          Key-Alias (Default: upload)
EOF
}

case "${1:-debug}" in
  help|--help|-h) print_help ;;
  debug)
    "$SCRIPT_DIR/gradlew" assembleDebug
    info "✓ APK: app/build/outputs/apk/debug/app-debug.apk" ;;
  release)
    "$SCRIPT_DIR/gradlew" clean bundleRelease assembleRelease
    BUNDLE="$SCRIPT_DIR/app/build/outputs/bundle/release/app-release.aab"
    APK_IN="$SCRIPT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
    APK_OUT="$SCRIPT_DIR/app/build/outputs/apk/release/app-release-signed.apk"
    if [[ -f "$KEYSTORE_FILE" && "$KEYSTORE_PASS" != "CHANGE_ME" ]]; then
      jarsigner -sigalg SHA256withRSA -digestalg SHA-256 \
        -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
        "$BUNDLE" "$KEY_ALIAS"
      info "✓ Bundle signiert: $BUNDLE"
      sign_release_apk "$APK_IN" "$APK_OUT"
    else
      warn "Keystore oder KEYSTORE_PASS fehlt – Artefakte bleiben unsigniert."
    fi ;;
  install)
    "$SCRIPT_DIR/gradlew" assembleDebug
    install_apk_private "$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk" ;;
  install-release)
    "$SCRIPT_DIR/gradlew" assembleRelease
    APK_IN="$SCRIPT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
    APK_OUT="$SCRIPT_DIR/app/build/outputs/apk/release/app-release-signed.apk"
    sign_release_apk "$APK_IN" "$APK_OUT"
    install_apk_private "$APK_OUT" ;;
  lint)  "$SCRIPT_DIR/gradlew" lint ;;
  test)  "$SCRIPT_DIR/gradlew" testDebugUnitTest ;;
  clean) "$SCRIPT_DIR/gradlew" clean ;;
  *) warn "Unbekannter Befehl: $1"; print_help; exit 1 ;;
esac
