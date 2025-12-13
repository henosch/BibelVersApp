#!/usr/bin/env bash
set -euo pipefail

# Captures light and dark screenshots for the BibelVers app.
# The script copies full-resolution PNGs to $HOME/Desktop/playstore
# and writes resized (height 720 px) variants into docs/.
# Optional env vars:
#   PLAYSTORE_DIR: override destination for full-res files
#   SETTINGS_TAP:  "<x> <y>" tap coordinates for the settings button
#   RESIZE_HEIGHT: target height for docs screenshots (default 720)

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCS_DIR="$REPO_ROOT/docs"
PLAYSTORE_DIR="${PLAYSTORE_DIR:-$HOME/Desktop/playstore}"
PKG="de.henosch.bibelvers"
MAIN_ACTIVITY="$PKG/.MainActivity"
SETTINGS_ACTIVITY="$PKG/.SettingsActivity"
SETTINGS_TAP="${SETTINGS_TAP:-932 270}"
# Spinner-Koordinaten (Defaults für Pixel 10 Pro XL)
THEME_SPINNER_TAP="${THEME_SPINNER_TAP:-200 1080}"
RESIZE_HEIGHT="${RESIZE_HEIGHT:-720}"
TMP_DIR="$(mktemp -d)"

cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing required command: $1" >&2
        exit 1
    fi
}

require_device() {
    if ! adb get-state >/dev/null 2>&1; then
        if adb devices 2>&1 | grep -q "more than one device"; then
            echo "Mehrere Geräte gefunden. Setze z.B. export ANDROID_SERIAL=192.168.11.130:PORT" >&2
        else
            echo "No adb device connected" >&2
        fi
        exit 1
    fi
}

current_focus() {
    adb shell dumpsys window displays | grep -F "mCurrentFocus" || true
}

wait_for_activity() {
    local activity
    activity="$(canonical_component "$1")"
    local attempts=0
    while (( attempts < 5 )); do
        if current_focus | grep -Fq "$activity"; then
            return 0
        fi
        sleep 1
        ((attempts++))
    done
    return 1
}

canonical_component() {
    local component="$1"
    local pkg="${component%%/*}"
    local clazz="${component##*/}"
    if [[ "$clazz" == .* ]]; then
        clazz="${pkg}${clazz}"
    elif [[ "$clazz" != *.* ]]; then
        clazz="${pkg}.${clazz}"
    fi
    echo "${pkg}/${clazz}"
}

switch_to_activity() {
    local activity="$1"
    adb shell am start -n "$activity" >/dev/null
    if ! wait_for_activity "$activity"; then
        echo "Could not switch to $activity" >&2
        exit 1
    fi
}

scroll_to_theme_spinner() {
    # Scroll leicht nach unten, um sicherzustellen, dass der Spinner sichtbar ist
    adb shell input swipe 500 1600 500 900 >/dev/null 2>&1 || true
    sleep 1
}

select_theme_spinner() {
    local mode="$1"
    scroll_to_theme_spinner
    adb shell input tap $THEME_SPINNER_TAP >/dev/null
    sleep 1
    # springe zum Anfang der Liste
    adb shell input keyevent KEYCODE_MOVE_HOME >/dev/null
    sleep 0.5
    case "$mode" in
        system)
            ;;
        light)
            adb shell input keyevent KEYCODE_DPAD_DOWN >/dev/null
            ;;
        dark)
            adb shell input keyevent KEYCODE_DPAD_DOWN >/dev/null
            adb shell input keyevent KEYCODE_DPAD_DOWN >/dev/null
            ;;
        *)
            echo "Unknown theme mode: $mode" >&2
            exit 1
            ;;
    esac
    adb shell input keyevent KEYCODE_ENTER >/dev/null
    sleep 1
    wait_for_activity "$SETTINGS_ACTIVITY" || true
}

ensure_theme_system() {
    set_theme_mode system
}

restart_app() {
    adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
    switch_to_activity "$MAIN_ACTIVITY"
    sleep 1
}

open_settings() {
    local attempts=0
    while (( attempts < 5 )); do
        adb shell input tap $SETTINGS_TAP >/dev/null
        sleep 1
        if wait_for_activity "$SETTINGS_ACTIVITY"; then
            return 0
        fi
        ((attempts++))
    done
    echo "Could not open settings; adjust SETTINGS_TAP if layout differs." >&2
    exit 1
}

return_to_main() {
    adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    sleep 1
    if wait_for_activity "$MAIN_ACTIVITY"; then
        return 0
    fi
    switch_to_activity "$MAIN_ACTIVITY"
}

capture_screen() {
    local name="$1"
    local tmp_png="$TMP_DIR/${name}.png"

    mkdir -p "$DOCS_DIR" "$PLAYSTORE_DIR"
    adb exec-out screencap -p > "$tmp_png"
    cp "$tmp_png" "$PLAYSTORE_DIR/${name}.png"
    sips -Z "$RESIZE_HEIGHT" "$tmp_png" --out "$DOCS_DIR/${name}.png" >/dev/null
}

set_theme_mode() {
    local mode="$1"
    return_to_main
    open_settings
    select_theme_spinner "$mode"
    return_to_main
}

main() {
    require_cmd adb
    require_cmd sips
    require_device
    ensure_theme_system

    # Light mode
    set_theme_mode light
    restart_app
    capture_screen "pixel_main"
    open_settings
    capture_screen "pixel_settings"
    return_to_main

    # Dark mode
    set_theme_mode dark
    restart_app
    capture_screen "pixel_dark_main"
    open_settings
    capture_screen "pixel_dark_settings"
    return_to_main

    echo "Screenshots updated under $DOCS_DIR and $PLAYSTORE_DIR."
}

main "$@"
