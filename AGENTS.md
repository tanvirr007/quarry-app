# AGENTS.md — Developer & AI Agent Guidelines for Quarry

Welcome to **Quarry** (`app.quarry.tanvir.info`). This document outlines repository structure, architectural patterns, coding guidelines, and workflow conventions for developers and AI coding agents.

---

## 1. Project Overview & Philosophy

**Quarry** is a modern, privacy-first Android storage analyzer and file manager built with 100% Jetpack Compose and Material 3.

- **100% Offline & Private**: All storage scanning, size calculation, and file operations occur entirely on-device. No telemetry, no external network uploads.
- **Modern Android Native**: Targets Android 12.0+ (API 31+) through Android 16 (API 36), leveraging modern platform APIs, Coroutines, Flow, and Material You theming.

---

## 2. Technical Stack & Dependencies

- **Language & Runtime**: Kotlin `2.0.21`, Java `21`, Gradle Kotlin DSL (`build.gradle.kts`)
- **Android Target**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 31`
- **UI Framework**: Jetpack Compose (BOM `2024.12.01`), Material 3, Compose Navigation `2.8.5`
- **Local Persistence**:
  - **Room Database** `2.6.1` (with KSP `2.0.21-1.0.28`) for file metadata indexing and cache.
  - **DataStore Preferences** `1.1.1` for user settings (theme, sort preferences, onboarding flags).
- **Background Processing**: Jetpack **WorkManager** `2.10.0` + Kotlinx Coroutines `1.9.0`
- **Security**: AndroidX **Biometric** `1.2.0-alpha05` for biometric / PIN protection.
- **CI/CD**: GitHub Actions with live Telegram bot build monitoring & release dispatch.

---

## 3. Architecture & Codebase Structure

Quarry follows **Clean Architecture** and **Unidirectional Data Flow (UDF)**:

```
app/src/main/java/app/quarry/tanvir/info/
├── MainActivity.kt               # Single-activity container, theme/onboarding root
├── QuarryApp.kt                  # Application class
├── data/                         # Repositories, Room DAOs, DataStore, file scanners
│   ├── database/                 # Room database, entities, DAOs
│   ├── model/                    # Data models, category enums, file items
│   ├── preferences/              # DataStore user preferences & theme management
│   └── repository/               # Repository implementations
├── domain/                       # Use cases and domain business logic
├── ui/                           # Jetpack Compose UI layer
│   ├── cleanup/                  # Storage cleaner & duplicate/large file screens
│   ├── components/               # Reusable UI widgets (cards, charts, buttons, dialogs)
│   ├── explore/                  # File manager / category browsing screens
│   ├── home/                     # Dashboard, storage breakdown, visual graphs
│   ├── navigation/               # NavHost, screens, bottom navigation bar
│   ├── onboarding/               # First-run permission & onboarding dialogs
│   ├── settings/                 # App settings, theme selector, security options
│   └── theme/                    # Material 3 ColorScheme, Typography, Theme setup
└── worker/                       # WorkManager workers (background scanning/cleanup)
```

---

## 4. Coding Standards & Conventions

### Jetpack Compose & UI
- **Stateless Composables**: Keep UI components decoupled from ViewModel instances by hoisting state and passing event lambdas.
- **State Collection**: Use `collectAsStateWithLifecycle()` when collecting flows in UI composables to stay lifecycle-aware.
- **Material 3 Tokens**: Always use `MaterialTheme.colorScheme` and `MaterialTheme.typography` instead of hardcoded hex colors or direct text styling.
- **Responsive Layout**: Support dynamic window sizing, edge-to-edge system insets (`WindowInsets`, `Scaffold` padding), and dark/light system adaptation.

### Asynchronous Operations & Coroutines
- **Dispatchers**: Always offload disk I/O, file traversal, and database queries to `Dispatchers.IO`. Keep UI logic on `Dispatchers.Main`.
- **Cancellation**: Ensure recursive directory traversals and heavy scanner operations respect coroutine cancellation (`yield()` / `isActive`).

### Storage & Permissions
- Follow Android 12+ scoped storage and `MANAGE_EXTERNAL_STORAGE` guidelines with transparent, in-app rationale prior to requesting system prompts.

---

### General Guidelines & Tone
- **No Emojis**: Do not use emojis in commit messages, documentation, logs, or UI strings. Keep documentation clean, clear, and professional.
- **Issue Tracking**: When addressing bugs or feature requests, consult the structured issue forms in `.github/ISSUE_TEMPLATE/` to ensure all necessary device, OS, and permission diagnostics are addressed.

---

## 5. Build, Test & Release Workflow

### Local Commands
- **Compile / Check**: `./gradlew assembleDebug`
- **Unit Tests**: `./gradlew test`
- **Release APK**: `./gradlew assembleRelease`
- **Query Version**: `./gradlew -q printVersionName`

### Git Commit Conventions
Follow the repository commit guidelines:
1. **Title**: Short, imperative mood with standard prefix (e.g. `feat: ...`, `fix: ...`, `chore: ...`, `ci: ...`), max 40 characters.
2. **Body**: Bullet points with `-` prefix, followed by `TEST:` section delimited by divider lines.
3. **Change-Id & Signoff**: Include `Change-Id` footer and always commit with `-s` (`git commit -s`).

### Continuous Integration (GitHub Actions)
- Pushing to `main` triggers `.github/workflows/build.yml`.
- The CI calculates semantic versions dynamically, updates `app/build.gradle.kts`, executes `./gradlew assembleRelease`, signs the APK (if keystore secrets are present), generates changelog notes from Git commits, creates a GitHub Release, and notifies Telegram with real-time build logs.
