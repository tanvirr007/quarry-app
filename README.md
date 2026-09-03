<div align="center">

![Quarry Banner](assets/update.png)

<br/>

[![Android](https://img.shields.io/badge/Android-12.0%2B%20(API%2031%2B)-2E7D32?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2026.06.01-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material_3-M3-6750A4?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](LICENSE)
![Visitors](https://visitor-badge.laobi.icu/badge?page_id=tanvirr007.quarry-app)

</div>

---

## Index

- [Overview](#overview)
- [Why Choose Quarry?](#why-choose-quarry)
- [Screenshots](#screenshots)
- [Download](#download)
- [Features](#features)
- [Tech Stack & Architecture](#tech-stack--architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [CI & Releases](#continuous-integration--releases)
- [Reporting Issues & Contributing](#reporting-issues--contributing)
- [License](#license)

---

## Overview

**Quarry** is an offline storage analyzer and visual disk cleanup utility for Android devices.

> [!NOTE]
> Quarry is not a dedicated file manager app.

---

## Why Choose Quarry?

| Others | Quarry |
| :---: | :---: |
| Inaccurate sizes on newer Android versions | **Accurate and fully optimized for Android 12–16** |
| Tiny files are hard to see and tap | **Clear, easy-to-tap visual tiles with minimum size floors** |
| Ads, background trackers, and network usage | **Offline with zero ads and complete privacy** |
| Complex menus and unnecessary booster gimmicks | **Simple, honest cleanup for duplicates and large files** |
| Outdated or cluttered design | **Modern, smooth, and clean Material 3 experience** |

---

## Screenshots

<div align="center">

<img src="assets/ui/home.png" width="24%" alt="Home Screen" />
<img src="assets/ui/explore.png" width="24%" alt="Explore Screen" />
<img src="assets/ui/cleanup.png" width="24%" alt="Cleanup Screen" />
<img src="assets/ui/settings.png" width="24%" alt="Settings Screen" />

</div>

---

## Download

[![Download Now](https://img.shields.io/badge/Download-Now-blue?style=for-the-badge&logo=github&logoColor=white)](https://github.com/tanvirr007/quarry-app/releases/latest/download/Quarry.apk)
[![Latest Release](https://img.shields.io/github/v/release/tanvirr007/quarry-app?color=blue&label=&logo=android&logoColor=white&style=for-the-badge)](https://github.com/tanvirr007/quarry-app/releases/latest)

<br/>

> [!WARNING]
> Always download official builds directly from the official [tanvirr007/quarry-app](https://github.com/tanvirr007/quarry-app/releases) releases.

---

## Features

- **Interactive Treemaps**: Hardware-accelerated squarified treemaps with minimum tile size floors and single-tap folder exploration.
- **Versatile Explorer**: Browse by treemap, hierarchical list, largest files, categories, and folders with instant search and sorting.
- **Smart Cleanup Hub**: Detect duplicate files, locate large files, find empty directories or obsolete APKs, and manage Trash safely.
- **Installed App Analyzer**: Inspect storage used by installed applications (APK size, data, cache) with quick system shortcuts.
- **Multi-Volume & SD Cards**: Real-time storage stats and mount inspection for internal storage, SD cards, and USB OTG.
- **Scan Exclusions**: Custom folder whitelist to exclude specific directories from scans and cleanup recommendations.
- **Native Thumbnails**: Zero-dependency, offline preview generator for images, video frames, and APK badges.
- **Customizable Dashboard**: Curate category cards, toggle Quick Insights, and monitor storage status at a glance.
- **Biometric Security**: Protect file browsing and storage details with fingerprint authentication or device PIN.
- **Haptics & Appearance**: Granular vibration strength control, dynamic Material You colors, and full dark theme support.
- **Offline & Private**: Zero analytics, zero network requests, and complete on-device privacy.

---

## Tech Stack & Architecture

Quarry is built following **Clean Architecture** and **Unidirectional Data Flow (UDF)** with modern Android development standards:

| Component | Technology |
| :--- | :--- |
| **Language** | [Kotlin 2.2.21](https://kotlinlang.org/) |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) (BOM `2026.06.01`) + Material 3 |
| **Typography** | [Google Sans Rounded](https://fonts.google.com/) (Embedded `res/font/`) |
| **Navigation** | [Compose Navigation](https://developer.android.com/guide/navigation/navigation-compose) |
| **Local Database** | [Room Database 2.8.4](https://developer.android.com/training/data-storage/room) with [KSP](https://kotlinlang.org/docs/ksp-overview.html) |
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
│       │   ├── MainViewModel.kt  # App UI state & global preference holder
│       │   ├── QuarryApp.kt      # Application initialization
│       │   ├── data/             # Database, preferences, filesystem, MediaStore & SAF
│       │   ├── domain/           # Analyzer, cleanup, duplicates, file, haptics, media, treemap, volumes
│       │   ├── ui/               # Compose screens & components (cleanup, components, explore, home, settings)
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

---

## Reporting Issues & Contributing

Contributions and bug reports are welcome to make Quarry better.

- **[Report a Bug](https://github.com/tanvirr007/quarry-app/issues/new?template=bug_report.yml)**
- **[Request a Feature](https://github.com/tanvirr007/quarry-app/issues/new?template=feature_request.yml)**
- **[Browse All Issues](https://github.com/tanvirr007/quarry-app/issues)**

### Reporting a Bug
- **Bug Reports**: Submit a [Bug Report](https://github.com/tanvirr007/quarry-app/issues/new?template=bug_report.yml) via the issue template and fill in the requested details.

### Requesting a Feature
- **Feature Requests**: Submit a [Feature Request](https://github.com/tanvirr007/quarry-app/issues/new?template=feature_request.yml) describing your use case, proposed idea, and any mockups or alternatives.

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