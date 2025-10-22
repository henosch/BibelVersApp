# Repository Guidelines

## Project Structure & Module Organization
- `app/src/main/java/com/example/bibelvers` holds the Kotlin activities, repositories, and receivers; keep feature-specific helpers near their entry point (e.g., verse parsing stays beside `LosungRepository`).
- Layouts, drawables, and strings live under `app/src/main/res`; name new resources with `snake_case` aligned to screen purpose (e.g., `activity_devotional.xml`).
- Long-lived data such as the yearly Losung ZIP files reside in `app/src/main/assets`; add future years using the `Losung_<YEAR>_XML.zip` pattern so `LosungRepository.ensureYear` continues to resolve them automatically.
- Gradle wrapper files remain in the root; use the provided structure when introducing new modules so `settings.gradle` stays in sync.

## Build, Test, and Development Commands
- `./build.sh` primes `local.properties`, configures `JAVA_HOME`, and triggers a debug build for repeatable local setups.
- `./gradlew assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk` for sideloading.
- `./gradlew installDebug` pushes the debug APK to a connected emulator or device.
- `./gradlew lint` and `./gradlew testDebugUnitTest` run static checks and JVM tests before review; use `./gradlew connectedAndroidTest` for instrumentation on a booted device.

## Coding Style & Naming Conventions
- Kotlin files use 4-space indentation, braces on the same line, and idiomatic constructs (scope functions, coroutines) as already shown in `MainActivity.kt`.
- Name classes and activities in `PascalCase`, properties and methods in `camelCase`, and constants with `ALL_CAPS` inside `companion object`.
- Favor View Binding (`ActivityMainBinding`) over `findViewById`, and keep UI updates on the main thread; document non-obvious coroutines with short comments.
- Resource IDs follow Android defaults: layouts in `snake_case`, string names prefixed by screen (e.g., `main_subtitle`).

## Testing Guidelines
- Place unit tests under `app/src/test/java`; suffix filenames with `Test` (`LosungRepositoryTest`) and cover parsing edge cases, offline fallbacks, and scheduling math.
- Instrumented tests belong in `app/src/androidTest/java` using `AndroidJUnitRunner`; reset shared preferences or files between runs.
- Add regression tests when adjusting date logic or notification timing to guard against locale-specific issues.
- Ensure CI-equivalent checks pass locally via `./gradlew lint testDebugUnitTest` before pushing.

## Commit & Pull Request Guidelines
- Current history uses concise, imperative subjects (e.g., `Initial commit`); keep messages under ~72 characters and describe *what* changes, optionally elaborating in the body.
- Reference issues in the body when applicable, and note any asset or resource updates that require QA.
- Pull requests should outline the motivation, list executed commands (`lint`, `testDebugUnitTest`, emulator smoke tests), and include screenshots or recordings for UI changes.
- Tag reviewers on code-owner areas (UI, repository, scheduling) so domain experts can sign off.

## Configuration Notes
- Keep `local.properties` machine-specific; rely on `ANDROID_HOME`/`ANDROID_SDK_ROOT` variables or edit the file locally without committing secrets.
- `build.sh` pins `temurin-17`; update the script alongside any Gradle or AGP upgrade so contributors share identical toolchains.
- When adding streamed content or external URLs, document firewall or certificate needs in `README.md` and guard secrets via system properties rather than hardcoding.
