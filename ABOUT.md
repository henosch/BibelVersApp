# BibelVers – Projektüberblick

BibelVers ist eine kleine Android-App, die jeden Tag ein zufällig kombiniertes Verspaar aus Altem und Neuem Testament aus einer lokal mitgelieferten Sammlung anzeigt. Zusätzlich bietet sie einen Livestream der Klagemauer (mehrere Kameraansichten) sowie praktische Komfortfunktionen.

## Hauptfunktionen
- **Tagesvers**: Anzeige eines AT/NT-Paares aus der internen Datei `BibelVerse.xml`. Für jedes Kalenderjahr wird eine zufällige Reihenfolge erstellt, damit kein Vers doppelt vorkommt.
- **Livestream**: Auswahl verschiedener Kotel-Kameras und randloses Streaming im Querformat.
- **Push-Benachrichtigungen**: Optionale Erinnerung an den Tagesvers (inkl. Testbutton über den Datenquellen-Hinweis).
- **Navigation**: Wischgesten nach links/rechts blättern zwischen den Tagen; Shabbat-Hinweis wird automatisch eingeblendet.
- **100 % offline**: Es erfolgt kein Download von Drittquellen für die Verse – nur der Livestream benötigt Internet.

## Build & Entwicklung
- Kotlin + Android SDK, Build per `./build.sh` (legt bei Bedarf `local.properties` an und startet `./gradlew assembleDebug`).

Weitere Details siehe `README.md`.
