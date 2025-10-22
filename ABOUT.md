# BibelVers – Projektüberblick

BibelVers ist eine kleine Android-App, die täglich die Herrnhuter Losungen anzeigt. Zusätzlich bietet sie einen Livestream der Klagemauer (mehrere Kameraansichten) sowie praktische Komfortfunktionen.

## Hauptfunktionen
- **Tageslosung**: Anzeige der Losung und des Lehrtextes auf Basis der offiziellen XML-Datei.
- **Livestream**: Auswahl verschiedener Kotel-Kameras und randloses Streaming im Querformat.
- **Push-Benachrichtigungen**: Optionale Erinnerung an die Tageslosung (inkl. Testbutton über den Quellen-Hinweis).
- **Navigation**: Wischgesten nach links/rechts blättern zwischen den Tagen; Shabbat-Hinweis wird automatisch eingeblendet.
- **Automatischer Jahreswechsel**: Beim Start bzw. beim Wechsel in ein neues Jahr lädt die App die passende Losungs-XML herunter.

## Build & Entwicklung
- Kotlin + Android SDK, Build per `./build.sh` (legt bei Bedarf `local.properties` an und startet `./gradlew assembleDebug`).

Weitere Details siehe `README.md`.
