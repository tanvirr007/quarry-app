# AGENTS.md — Developer & AI Agent Guidelines for Quarry

Welcome to **Quarry** (`app.quarry.tanvir.info`). This document outlines repository structure, architectural patterns, coding guidelines, and workflow conventions for developers and AI coding agents.

---

## 1. Project Overview & Philosophy

**Quarry** is a modern, privacy-first Android storage analyzer and file manager built with 100% Jetpack Compose and Material 3.

- **100% Offline & Private**: All storage scanning, size calculation, and file operations occur entirely on-device. No telemetry, no external network uploads.
- **Modern Android Native**: Targets Android 12.0+ (API 31+) through Android 16 (API 36), leveraging modern platform APIs, Coroutines, Flow, and Material You theming.
- **Visual Storage Breakdown**: Hardware-accelerated squarified treemaps with guaranteed minimum tile visibility, list explorers, and duplicate/large file cleaners.

---

## 2. Technical Stack & Dependencies

- **Language & Runtime**: Kotlin `2.2.21`, Java `21`, Gradle Kotlin DSL (`build.gradle.kts`)
- **Android Target**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 31`
- **UI Framework**: Jetpack Compose (BOM `2026.06.01`), Material 3, Compose Navigation `2.8.5`
- **Typography**: Google Sans Rounded embedded font family (`res/font/google_sans_rounded.ttf`)
- **Local Persistence**:
  - **Room Database** `2.8.4` (with KSP `2.2.21-2.0.5`) for file metadata indexing and cache.
  - **DataStore Preferences** `1.1.1` for user settings (theme, sort preferences, onboarding flags, scan exclusions).
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
│   ├── database/                 # Room database, entities, DAOs (QuarryDatabase, FileDao, FileEntity)
│   ├── model/                    # Data models, category enums, file items
│   ├── preferences/              # DataStore user preferences, theme, haptics, keep-screen-on, category visibility
│   └── repository/               # Repository implementations
├── domain/                       # Use cases and domain business logic
│   ├── file/                     # File operations (trash, delete, rename, batch actions)
│   ├── haptics/                  # Centralized vibration helper (QuarryHaptics, strength mapping, VibratorManager)
│   ├── model/                    # Domain models (StorageCategory, StorageFormatter)
│   ├── scanner/                  # Local storage scanners & metadata indexing
│   ├── security/                 # Biometric authentication management
│   ├── treemap/                  # Treemap layout engine (squarified algorithm, node hierarchy)
│   └── volume/                   # Storage volume manager (internal & external volumes)
├── ui/                           # Jetpack Compose UI layer
│   ├── cleanup/                  # Storage cleaner & duplicate/large file screens
│   ├── components/               # Reusable UI widgets (cards, charts, buttons, dialogs)
│   ├── explore/                  # Treemap canvas, file lists, category explorer, breadcrumb navigation
│   ├── home/                     # Dashboard, storage breakdown, visual category charts (Quick Insights gating, filtered categories)
│   ├── navigation/               # NavHost, screens, bottom navigation bar
│   ├── onboarding/               # First-run permission & onboarding dialogs
│   ├── settings/                 # Settings + dialogs (Appearance, Exclusions, Storage Volumes, CategoryVisibilityDialog, MiscellaneousDialog)
│   └── theme/                    # Material 3 ColorScheme, Typography, Theme setup
└── worker/                       # WorkManager workers (background scanning/cleanup)
```

---

## 4. Key Architectural Patterns

### Treemap Engine & Visualization
- **Squarified Treemap Algorithm**: `TreemapEngine` organizes directory hierarchies into squarified tiles maintaining optimal aspect ratios.
- **Guaranteed Visible Floor**: Small files and directories are assigned a balanced minimum visual area floor (~3.5% canvas area) to prevent collapsing into invisible or unclickable 1px slivers.
- **Proximity-Aware Hit Detection**: `TreemapCanvas` evaluates touch events with distance tolerances to ensure direct, effortless tapping for both small and large tiles.
- **Color Harmonization**: Every file and directory is rendered with distinct, harmonized HSL gradient tiles and high-contrast labels.

### Jetpack Compose & UI
- **Stateless Composables**: Keep UI components decoupled from ViewModel instances by hoisting state and passing event lambdas.
- **State Collection**: Use `collectAsStateWithLifecycle()` when collecting flows in UI composables to stay lifecycle-aware.
- **Material 3 Tokens**: Always use `MaterialTheme.colorScheme` and `MaterialTheme.typography` instead of hardcoded hex colors or direct text styling.
- **Responsive Layout**: Support dynamic window sizing, edge-to-edge system insets (`WindowInsets`, `Scaffold` padding), and dark/light system adaptation.

### Asynchronous Operations & Coroutines
- **Dispatchers**: Always offload disk I/O, file traversal, and database queries to `Dispatchers.IO`. Keep UI logic on `Dispatchers.Main`.
- **Cancellation**: Ensure recursive directory traversals and heavy scanner operations respect coroutine cancellation (`yield()` / `isActive`).

### Miscellaneous Settings
- **Quick Insights toggle**: DataStore `quick_insights_enabled` (default true); `HomeViewModel` filters `StorageOverviewData.quickInsights` → `visibleQuickInsights`.
- **Storage Categories visibility**: DataStore `enabled_categories` seeded with all 8 `StorageCategory` entries; `SettingsViewModel.toggleCategory()` enforces minimum 4 visible, Home falls back to full list if <4; managed via `CategoryVisibilityDialog`.
- **Haptics**: `domain/haptics/QuarryHaptics.kt` wraps `VibratorManager`/`VibrationEffect.createOneShot(amplitude=strength*255/100, duration~20-55ms)` gated by `haptics_enabled` + `haptic_strength (1-100)`; `rememberQuarryHaptic()` composes `LocalHapticFeedback.LongPress` + `performQuarryHaptic()`; permission `VIBRATE` (normal) in manifest; no-op when `hasVibrator()==false`.
- **Keep Screen On**: DataStore `keep_screen_on` (default false); `MainViewModel` exposes in `MainUiState.Success`; `MainActivity` applies `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` via `DisposableEffect`, cleared on dispose/background.

### Storage & Permissions
- Follow Android 12+ scoped storage and `MANAGE_EXTERNAL_STORAGE` guidelines with transparent, in-app rationale prior to requesting system prompts.
- `VIBRATE` is normal permission, no runtime prompt; `FLAG_KEEP_SCREEN_ON` requires no permission.

---

## 5. General Guidelines & Tone

- **No Emojis**: Do not use emojis in commit messages, documentation, logs, or UI strings. Keep documentation clean, clear, and professional.
- **Issue Tracking**: When addressing bugs or feature requests, consult the structured issue forms:
  - [Bug Report Template](.github/ISSUE_TEMPLATE/bug_report.yml) ([New Bug on GitHub](https://github.com/tanvirr007/quarry-app/issues/new?template=bug_report.yml))
  - [Feature Request Template](.github/ISSUE_TEMPLATE/feature_request.yml) ([New Feature on GitHub](https://github.com/tanvirr007/quarry-app/issues/new?template=feature_request.yml))
  - [Issue Tracker](https://github.com/tanvirr007/quarry-app/issues)
  Ensure all necessary device, OS, and permission diagnostics are addressed when working with issues.

---

## 6. Build, Test & Release Workflow

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
