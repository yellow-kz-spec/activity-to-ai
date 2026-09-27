# Changelog

## Unreleased

- Prepared the repository for GitHub; excluded local build tools, caches, application packages, signing keys, and Garmin network captures.

## 0.2.5

- Fail Android builds when required Strava development credentials are absent instead of producing an APK whose Connect Strava action cannot work.
- Preserve command-line credential injection through ignored `local.properties`, Gradle properties, or environment variables.

- Consolidated Android Strava OAuth on `activitytomd://strava-auth.garminaiexporter.com/callback`, shared by the request and manifest; no callback relay is required.
- Confirmed authorization uses the documented mobile endpoint, retained cryptographic state validation, and added safe categorized OAuth diagnostics.
- Prepared development build 0.2.4 (versionCode 6).
- Prepared development build 0.2.3 (versionCode 5) with automatic descriptive APK naming.
- Added adaptive, round, themed monochrome, and legacy launcher icons from the supplied Activity to MD assets.
- Updated the launcher label and home status heading to Activity to MD, with the build version shown on the existing home screen.
- Product renamed from Garmin to AI to Activity to MD; legacy internal identifiers remain unchanged pending a separate migration.
- Project direction expanded from a Garmin-specific exporter to an Android multi-source activity-to-Markdown converter.
- Garmin remains the current working source; Strava activity export is under development.
- Documented the target separation of source detection/adapters, a normalized activity model with source-specific extensions, and the Markdown exporter.
- Updated developer, storage, Garmin-source, MVP, and testing documentation to distinguish current behavior from the planned multi-source architecture.
- Создан Android-каркас v0.1 на Kotlin.
- Добавлен приём Garmin-ссылок через `ACTION_SEND` для путей `/app/activity` и `/modern/activity`.
- Добавлена загрузка публичных Garmin JSON через скрытый WebView.
- Добавлена рекурсивная фильтрация координат, полилиний и других геополей.
- Добавлены Markdown-экспорт, круги, зоны и числовые временные ряды.
- Добавлены постоянный выбор папки через SAF и стратегия имён `_2`, `_3` без перезаписи.
- Добавлены модульные тесты фильтрации и Markdown-экспорта.
- Зафиксирован share-first сценарий Android MVP.
- Удален главный экран из продуктовой концепции.
- Добавлено автоматическое определение типа тренировки.
- Основным результатом установлен Markdown-файл.
- Добавлен однократный выбор папки через системный механизм Android.
- Added phase-one Strava OAuth connection, Keystore-backed encrypted token storage, automatic refresh support, local disconnect, and a minimal launcher configuration screen.
- Preserved the existing Garmin share/export path without requiring authentication.
