#!/usr/bin/env bash
set -euo pipefail

info() { printf '[INFO] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }
error() { printf '[ERROR] %s\n' "$*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"
cd "$REPO_ROOT"

# JAVA_HOME automatisch setzen, falls möglich (z. B. macOS).
if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  if CANDIDATE="$(/usr/libexec/java_home -v 17 2>/dev/null)"; then
    export JAVA_HOME="$CANDIDATE"
    info "JAVA_HOME automatisch auf ${JAVA_HOME} gesetzt."
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  warn "JAVA_HOME ist nicht gesetzt. Bitte Pfad zu einem JDK 17 konfigurieren."
else
  info "JAVA_HOME=${JAVA_HOME}"
fi

LOCAL_PROPERTIES="${REPO_ROOT}/local.properties"
if [[ ! -f "$LOCAL_PROPERTIES" ]]; then
  SDK_PATH=""
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    SDK_PATH="${ANDROID_SDK_ROOT}"
  elif [[ -n "${ANDROID_HOME:-}" ]]; then
    SDK_PATH="${ANDROID_HOME}"
  elif [[ "$(uname -s)" == "Darwin" ]]; then
    DEFAULT_SDK="${HOME}/Library/Android/sdk"
    [[ -d "$DEFAULT_SDK" ]] && SDK_PATH="$DEFAULT_SDK"
  fi

  if [[ -n "$SDK_PATH" ]]; then
    info "Erzeuge local.properties mit sdk.dir=${SDK_PATH}"
    cat > "$LOCAL_PROPERTIES" <<EOF
sdk.dir=${SDK_PATH}
EOF
  else
    warn "Keine Android-SDK-Umgebung gefunden. Bitte local.properties manuell anlegen."
  fi
else
  info "Nutze bestehende local.properties"
fi

GRADLEW="${REPO_ROOT}/gradlew"
[[ -x "$GRADLEW" ]] || error "Gradle Wrapper (${GRADLEW}) ist nicht ausführbar."

remove_ds_store() {
  if command -v rg >/dev/null 2>&1; then
    if rg --files -g '.DS_Store' >/dev/null 2>&1; then
      warn ".DS_Store gefunden – entferne vor dem Build."
      rg --files -g '.DS_Store' | while IFS= read -r file; do
        rm -f "$file"
      done
    fi
  else
    find "$REPO_ROOT" -name '.DS_Store' -print0 2>/dev/null | while IFS= read -r -d '' file; do
      warn "Entferne $file"
      rm -f "$file"
    done
  fi
}

remove_ds_store

GRADLE_ARGS=(--no-daemon --stacktrace)

info "Baue Debug-APK (assembleDebug)"
"$GRADLEW" "${GRADLE_ARGS[@]}" assembleDebug

info "Starte Lint- und Unit-Tests"
"$GRADLEW" "${GRADLE_ARGS[@]}" lint testDebugUnitTest

info "Build abgeschlossen. APK: app/build/outputs/apk/debug/app-debug.apk"
