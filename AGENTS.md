# Richtlinien für das Repository

## Projektstruktur und Modulorganisation
- `app/src/main/java/de/henosch/bibelvers` enthält Activities, Repositories und Broadcast-Receiver. Feature-spezifische Helfer sollten nah am Einstiegspunkt bleiben (z. B. Parsing weiterhin in `BibelVersRepository`).
- Layouts, Drawables und Strings befinden sich unter `app/src/main/res`. Neue Ressourcen erhalten Namen im `snake_case`, passend zum Verwendungszweck (z. B. `activity_devotional.xml`).
- Die komplette Verssammlung liegt als `BibelVerse.xml` unter `app/src/main/assets`. Anpassungen erfolgen über die Skripte im Ordner `scripts/`.
- Gradle-Wrapper-Dateien bleiben im Projektstamm. Bei neuen Modulen die vorhandene Struktur übernehmen, damit `settings.gradle` konsistent bleibt.

## Build-, Test- und Entwicklungsbefehle
- `./build.sh` bereitet `local.properties` vor, setzt `JAVA_HOME` und startet den Debug-Build für reproduzierbare lokale Setups.
- `./gradlew assembleDebug` erzeugt `app/build/outputs/apk/debug/app-debug.apk` zum Sideloaden.
- `./gradlew installDebug` installiert die Debug-APK auf einem verbundenen Emulator oder Gerät.
- `./gradlew lint` und `./gradlew testDebugUnitTest` führen statische Prüfungen sowie JVM-Tests vor dem Review aus. Für instrumentierte Tests auf einem laufenden Gerät `./gradlew connectedAndroidTest` verwenden.

## Coding-Style und Namenskonventionen
- Kotlin-Dateien nutzen eine Einrückung von vier Leerzeichen, geschweifte Klammern bleiben in derselben Zeile. Setze idiomatische Konstrukte wie Scope-Funktionen und Coroutines ein (siehe `MainActivity.kt`).
- Klassen und Activities erhalten Namen im `PascalCase`, Eigenschaften und Methoden im `camelCase`, Konstanten innerhalb eines `companion object` in `ALL_CAPS`.
- View Binding (`ActivityMainBinding`) bevorzugen statt `findViewById`, UI-Aktualisierungen auf dem Main-Thread halten. Nicht offensichtliche Coroutines kurz kommentieren.
- Ressourcen-IDs folgen den Android-Standards: Layouts in `snake_case`, Strings mit Präfix nach Bildschirm (z. B. `main_subtitle`).

## Test-Richtlinien
- Unit-Tests liegen unter `app/src/test/java`; Dateinamen enden auf `Test`. Bei Änderungen an Zufallslogik oder Datumsermittlung Regressionstests ergänzen.
- Instrumentierte Tests gehören nach `app/src/androidTest/java` (Runner `AndroidJUnitRunner`). Nach jedem Lauf Dateien oder Preferences aufräumen, die durch Tests angelegt wurden.
- Bei Änderungen an Datum, Benachrichtigungs-Timing oder der BibelVerse-Auswahl Regressionstests ergänzen.
- Vor jedem Push mindestens `./gradlew lint testDebugUnitTest` ausführen; bei Bedarf `./gradlew connectedAndroidTest` für Gerätetests starten.

## Commit- und Pull-Request-Richtlinien
- Die Historie nutzt kurze imperative Betreffzeilen (z. B. `Initial commit`). Betreff unter ca. 72 Zeichen halten und beschreiben, *was* geändert wurde; Details optional im Body ergänzen.
- Issues im Commit-Body referenzieren, wenn vorhanden, und Nutzer auf geänderte Assets oder Ressourcen hinweisen, die QA benötigen.
- Pull Requests erklären Motivation, listen ausgeführte Befehle (`lint`, `testDebugUnitTest`, Emulator-Smoke-Tests) und enthalten bei UI-Änderungen Screenshots oder Aufzeichnungen.
- Reviewer aus den zuständigen Bereichen (UI, Repositories, Scheduling) taggen, damit Domain-Experten freigeben können.

## Konfigurationshinweise
- `local.properties` bleibt maschinenspezifisch. Auf `ANDROID_HOME`/`ANDROID_SDK_ROOT` vertrauen oder die Datei lokal anpassen, ohne Secrets einzuchecken.
- `build.sh` pinnt `temurin-17`. Bei Anpassungen an Gradle oder dem Android-Gradle-Plugin das Skript mit aktualisieren, damit alle denselben Toolchain-Stand teilen.
- Bei gestreamten Inhalten oder externen URLs Firewall- oder Zertifikatsanforderungen in `README.md` dokumentieren und Secrets über System-Properties statt Hard-Coding schützen.
