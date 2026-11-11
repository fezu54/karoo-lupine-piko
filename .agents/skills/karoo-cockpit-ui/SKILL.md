---
name: karoo-cockpit-ui
description: Design and implementation guidelines for the Karoo companion configuration and live-control UI using Jetpack Compose, glove-friendly touch targets, and English/German localization.
---

# Karoo Cockpit & Configuration UI

## Overview
Provides the Jetpack Compose user interface (`ui.main`) optimized for the Hammerhead Karoo display (480x800 compact screen, outdoor/night readability, and glove-friendly touch targets) for configuring light presets and controlling the Lupine Piko directly from the app.

---

## Architecture Placement
- **Entry Activity**: `org.happycode.karoo.lupinepiko.MainActivity`
- **Main Screen & Components**: `org.happycode.karoo.lupinepiko.ui.main.*`
- **Theme**: `org.happycode.karoo.lupinepiko.theme.AppTheme`
- **String Resources**:
  - `app/src/main/res/values/strings.xml` (English)
  - `app/src/main/res/values-de/strings.xml` (German)

---

## UI Design Rules for Karoo & Night Riding

### 1. Fast High-Beam / Dimmed Cockpit Card
- Place the **Quick Dim / High-Beam Toggle** at the top of the main screen with a large, full-width, high-contrast button (minimum `64.dp` height) so it can be hit effortlessly with winter/MTB gloves.
- Clearly distinguish between **Bright (Forest High-Beam)** and **Dimmed (Oncoming Traffic)** states using semantic colors from `MaterialTheme.lupinePikoColors`.

### 2. Mode Selection & Preset Configuration
- Provide a clear grid or list of available `LightMode` buttons to jump directly to any brightness level.
- Allow the user to configure:
  - Which mode is used as the **Bright (High-Beam)** preset.
  - Which mode is used as the **Dimmed** preset.
  - Which modes are included when cycling modes.

### 3. Testability & Pure UI Separation
- Split screens into a stateful wrapper (connecting to `KarooSystemService` / stores) and a stateless `@Composable fun MainScreenContent(...)` that takes primitive/immutable state and lambda callbacks.
- Keep business and state-transition logic out of `@Composable` functions; delegate all mode transitions to domain/application use cases.

### 4. Mandatory Bilingual Localization
- Every user-facing string added to `res/values/strings.xml` **must** simultaneously be added in German to `res/values-de/strings.xml` (see `.agents/rules/translations.md`).
