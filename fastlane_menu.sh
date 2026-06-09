#!/bin/bash

# BibelVers Fastlane Manager
# Interaktives Menü für Play Store Deployments

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
FASTLANE="fastlane"

cd "$PROJECT_DIR"

# ============================================================
# NAS-MOUNT
# ============================================================
PLAYSTORE_MOUNT="/Volumes/playstore"
PLAYSTORE_KEY_DIR="$PLAYSTORE_MOUNT/upload-playstore-key"
PLAYSTORE_SMB="smb://mike@192.168.11.150/playstore"

is_nas_mounted() {
  [[ -d "$PLAYSTORE_KEY_DIR" ]] && \
  [[ -f "$PLAYSTORE_KEY_DIR/upload-keystore-new.jks" ]] && \
  [[ -f "$PLAYSTORE_KEY_DIR/carmentv-b29f85067d53.json" ]]
}

ensure_nas_mount() {
  if is_nas_mounted; then
    return 0
  fi
  echo -e "${YELLOW}Mounte NAS: $PLAYSTORE_SMB${NC}"
  open "$PLAYSTORE_SMB" >/dev/null 2>&1 || true
  local waited=0
  while (( waited < 60 )); do
    is_nas_mounted && break
    sleep 2
    (( waited += 2 ))
  done
  if ! is_nas_mounted; then
    echo -e "${RED}❌ NAS-Mount fehlgeschlagen: $PLAYSTORE_SMB${NC}"
    echo "   Bitte NAS manuell mounten und erneut versuchen."
    exit 1
  fi
  echo -e "${GREEN}✓ NAS gemountet: $PLAYSTORE_MOUNT${NC}"
}

# Farben
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

ensure_nas_mount

show_header() {
    clear
    echo -e "${CYAN}=========================================${NC}"
    echo -e "${CYAN}     BibelVers Fastlane Manager         ${NC}"
    echo -e "${CYAN}=========================================${NC}"
    echo ""
}

show_status() {
    echo -e "${BLUE}Projekt:${NC}  $PROJECT_DIR"
    echo -e "${BLUE}Package:${NC}  de.henosch.bibelvers"
    echo ""
}

run_command() {
    local cmd="$1"
    local description="$2"

    echo ""
    echo -e "${YELLOW}▶ $description${NC}"
    echo -e "${CYAN}Befehl: $cmd${NC}"
    echo ""
    read -p "Fortfahren? (j/n): " confirm
    if [[ "$confirm" =~ ^[Jj]$ ]]; then
        echo ""
        eval "$cmd"
        echo ""
        echo -e "${GREEN}✅ Abgeschlossen!${NC}"
    else
        echo -e "${YELLOW}⏭️  Übersprungen${NC}"
    fi
    echo ""
    read -p "Enter drücken zum Fortfahren..."
}

# ======================
# HAUPTMENÜ
# ======================
while true; do
    show_header
    show_status

    echo "===== BUILD ====="
    echo "1)  Debug APK bauen              (fastlane build_debug)"
    echo "2)  Release AAB bauen            (fastlane build_release)"
    echo ""
    echo "===== VALIDATION ====="
    echo "3)  Lint ausführen               (fastlane lint)"
    echo "4)  Unit Tests ausführen         (fastlane test)"
    echo "5)  Alles validieren             (fastlane validate)"
    echo ""
    echo "===== DEPLOY ====="
    echo "6)  Deploy Internal Track        (fastlane deploy_internal)"
    echo "7)  Deploy Alpha (Closed Test)   (fastlane deploy_alpha)"
    echo "8)  Deploy Production (Draft)    (fastlane deploy_production)"
    echo "9)  Deploy Production (Staged)    (fastlane deploy_staged)"
    echo ""
    echo "===== PROMOTE ====="
    echo "10) Promote Internal → Alpha     (fastlane promote_internal_to_alpha)"
    echo "11) Promote Alpha → Production   (fastlane promote_alpha_to_production)"
    echo ""
    echo "===== METADATEN ====="
    echo "12) Metadaten hochladen          (fastlane upload_metadata)"
    echo "13) Metadaten vom Store laden    (fastlane supply init)"
    echo ""
    echo "===== SONSTIGES ====="
    echo "14) Screenshots erstellen        (fastlane screenshots)"
    echo "15) Version erhöhen + Git Tag    (fastlane bump_version)"
    echo ""
    echo "===== CI WORKFLOWS ====="
    echo "16) Full CI → Internal           (fastlane ci_internal)"
    echo "17) Full CI → Production         (fastlane ci_production)"
    echo ""
    echo "===== BEENDEN ====="
    echo "0)  Exit"
    echo ""

    read -p "Wähle eine Option: " choice

    case $choice in
        1)
            run_command "$FASTLANE build_debug" "Debug APK bauen"
            ;;
        2)
            run_command "$FASTLANE build_release" "Release AAB bauen"
            ;;
        3)
            run_command "$FASTLANE lint" "Lint Checks ausführen"
            ;;
        4)
            run_command "$FASTLANE test" "Unit Tests ausführen"
            ;;
        5)
            run_command "$FASTLANE validate" "Lint + Tests + Debug Build"
            ;;
        6)
            run_command "$FASTLANE deploy_internal" "Deploy auf Internal Track"
            ;;
        7)
            run_command "$FASTLANE deploy_alpha" "Deploy auf Alpha Track (Closed Testing)"
            ;;
        8)
            run_command "$FASTLANE deploy_production" "Deploy auf Production (als Draft)"
            ;;
        9)
            echo ""
            read -p "Rollout Prozentsatz (z.B. 0.1 für 10%): " rollout
            run_command "$FASTLANE deploy_staged rollout:$rollout" "Staged Rollout auf Production ($rollout)"
            ;;
        10)
            run_command "$FASTLANE promote_internal_to_alpha" "Promote Internal zu Alpha"
            ;;
        11)
            run_command "$FASTLANE promote_alpha_to_production" "Promote Alpha zu Production"
            ;;
        12)
            run_command "$FASTLANE upload_metadata" "Nur Metadaten hochladen"
            ;;
        13)
            run_command "$FASTLANE supply init" "Metadaten vom Play Store herunterladen"
            ;;
        14)
            run_command "$FASTLANE screenshots" "Screenshots automatisch erstellen"
            ;;
        15)
            run_command "$FASTLANE bump_version" "Version Code erhöhen + Git Tag"
            ;;
        16)
            run_command "$FASTLANE ci_internal" "Full CI: Validate → Build → Deploy Internal"
            ;;
        17)
            run_command "$FASTLANE ci_production" "Full CI: Validate → Build → Deploy Production"
            ;;
        0)
            echo -e "${GREEN}👋 Bis zum nächsten Mal!${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}❌ Ungültige Auswahl!${NC}"
            sleep 1
            ;;
    esac
done
