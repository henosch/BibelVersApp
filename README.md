# BibelVers Android App

Eine kleine Android-App, die die tägliche Losung (Herrnhuter Losungen) anzeigt, einen Livestream von der Klagemauer anbietet und optionale Push-Benachrichtigungen versendet.

## Features
- Tageslosung mit Lehrtext aus der offiziellen XML-Datei (lokale Fallback-Ressource + automatischer Download für neue Jahrgänge)
- Livestream-Auswahl (Kotel-Kameras) mit randloser Wiedergabe im Querformat
- Wischgesten (links/rechts) zum Blättern zwischen Tagen
- Benachrichtigungen für die Tageslosung (inkl. Testtrigger über die Quellenangabe)
- Shabbat-Hinweis am Freitagabend/Samstag

## Build
```
./gradlew assembleDebug
```
APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

## Hinweise
- Die App lädt beim Start und beim Wechsel zwischen Jahren automatisch die entsprechenden Losungs-XMLs herunter (unter `filesDir`).
- Für die Livestreams wird ein direkter HLS-Stream über ffplay-kompatible URLs verwendet.
