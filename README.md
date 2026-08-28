<div align="center">

# Quarry

**See where your device storage really goes**

[![Android](https://img.shields.io/badge/Android-12.0%2B%20(API%2031%2B)-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12.01-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%20Design-3-6750A4?style=flat&logo=materialdesign&logoColor=white)](https://m3.material.io)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

<br/>

</div>

---

## Overview

**Quarry** is a modern, fast, and privacy-first Android storage analyzer and file manager. Designed with **Jetpack Compose** and **Material 3**, Quarry provides deep visibility into your device's internal storage, categorizes your files, helps identify redundant data, and cleans up space safely—all processed 100% locally on your device.

---

## Features

- **Deep Storage Visualization**: Interactive breakdown of internal storage usage by category (Videos, Images, Apps, Documents, Audio, Archives, APKs, and more).
- **File Explorer**: Browse and inspect files, directories, and file metadata with high performance.
- **Smart Cleanup**:
  - Identify and safely remove duplicate files and residual junk.
  - Discover large files occupying significant space.
  - Locate obsolete APK packages, empty folders, and orphaned data.
- **Biometric Security**: Protect file browsing and sensitive storage details using device biometric authentication / PIN lock.
- **Material You & Theming**: Dynamic color theming with complete support for System, Dark, and Light modes.
- **100% Offline & Private**: Zero tracking, zero analytics, zero network data transmission. Everything stays on your phone.

---

## Tech Stack & Architecture

Quarry is built following **Clean Architecture** and **Unidirectional Data Flow (UDF)** with modern Android development standards:

| Component | Technology |
| :--- | :--- |
| **Language** | [Kotlin 2.0.21](https://kotlinlang.org/) |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) (BOM `2024.12.01`) + Material 3 |
| **Navigation** | [Compose Navigation](https://developer.android.com/guide/navigation/navigation-compose) |
| **Local Database** | [Room Database 2.6.1](https://developer.android.com/training/data-storage/room) with [KSP](https://kotlinlang.org/docs/ksp-overview.html) |
| **User Preferences** | [Jetpack DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore) |
| **Background Tasks** | [Jetpack WorkManager 2.10.0](https://developer.android.com/topic/libraries/architecture/workmanager) |
| **Concurrency** | [Kotlinx Coroutines & Flow](https://github.com/Kotlin/kotlinx.coroutines) |
| **Security** | [AndroidX Biometric 1.2.0](https://developer.android.com/jetpack/androidx/releases/biometric) |
| **Build & CI/CD** | Gradle Kotlin DSL (`build.gradle.kts`), Java 21, GitHub Actions CI + Telegram Release Bot |

---

## Project Structure

```
quarry-app/
├── .github/
│   ├── scripts/bot.py            # CI Telegram bot & release manager
│   └── workflows/build.yml       # GitHub Actions build & release pipeline
├── app/
│   ├── build.gradle.kts          # Module build configuration
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/app/quarry/tanvir/info/
│       │   ├── MainActivity.kt   # App root & theme provider
│       │   ├── QuarryApp.kt      # Application initialization
│       │   ├── data/             # Database, preferences & repositories
│       │   ├── domain/           # Use cases & business logic
│       │   ├── ui/               # Compose screens & components
│       │   │   ├── cleanup/      # Storage cleanup UI
│       │   │   ├── explore/      # File explorer UI
│       │   │   ├── home/         # Storage dashboard UI
│       │   │   ├── navigation/   # Navigation bar & routing
│       │   │   ├── onboarding/   # Permissions & onboarding dialogs
│       │   │   ├── settings/     # Settings UI
│       │   │   └── theme/        # Material 3 theme & color system
│       │   └── worker/           # Background workers
│       └── res/                  # Drawables, strings, mipmaps
└── AGENTS.md                     # Agent & developer guidelines
```

---

## Getting Started

### Prerequisites
- **JDK 21** or later
- **Android Studio** (Ladybug / Meerkat or newer recommended)
- Android SDK with Platform `android-36` installed

### Build & Run Locally
1. Clone the repository:
   ```bash
   git clone https://github.com/tanvirr007/quarry-app.git
   cd quarry-app
   ```
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run unit tests:
   ```bash
   ./gradlew test
   ```

---

## Continuous Integration & Releases

Automated builds and GitHub Releases are powered by GitHub Actions ([`.github/workflows/build.yml`](.github/workflows/build.yml)):
- **Semantic Versioning**: Automatically calculates version tags based on previous releases and Gradle configuration.
- **Signed APKs**: Automatically signs release builds when signing secrets are configured.
- **Telegram Notifications**: Real-time compilation status and release dispatches via Telegram bot integration.

## Reporting Issues & Contributing

Contributions and bug reports are welcome to make Quarry better.

### Reporting a Bug
When reporting a bug, please use the GitHub Bug Report template and provide:
- **Quarry Version**: The version number and build number (e.g. `v1.0.0`).
- **Device Details**: Device model, manufacturer, and Android OS version / custom ROM.
- **Permission State**: Whether "All Files Access" (`MANAGE_EXTERNAL_STORAGE`) was granted.
- **Steps to Reproduce**: Clear, numbered step-by-step instructions to reproduce the issue.
- **Observed vs. Expected**: What happened versus what you expected to happen.
- **Logs / Screenshots**: Logcat output or crash stack traces if applicable.

Before opening a new issue, please search existing issues to avoid duplicates.

### Requesting Features
To suggest new features or enhancements, please submit a Feature Request describing your use case, proposed solution, and any alternatives considered.

---

## License

```
Copyright 2026 Tanvir Hasan

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```