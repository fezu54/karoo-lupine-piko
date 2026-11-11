---
name: light-mode-control
description: Domain and application rules for Lupine Piko light modes, mode cycling, and instant high-beam to dimmed toggle (dazzle protection) with previous-mode restoration.
---

# Light Mode Control & Quick-Dim State Machine

## Overview
Defines the pure Kotlin **Domain** (`domain`) and **Application** (`application`) logic for managing Lupine Piko brightness levels, cycling through configured modes, and executing the instant high-beam to dimmed switch when encountering oncoming riders or pedestrians in the forest.

---

## Architecture Placement
- **Domain Entities & Value Objects**:
  - `org.happycode.karoo.lupinepiko.domain.LightMode`
  - `org.happycode.karoo.lupinepiko.domain.LightState`
  - `org.happycode.karoo.lupinepiko.domain.QuickDimConfig`
- **Application Use Cases**:
  - `org.happycode.karoo.lupinepiko.application.SetLightModeUseCase`
  - `org.happycode.karoo.lupinepiko.application.CycleLightModeUseCase`
  - `org.happycode.karoo.lupinepiko.application.QuickDimToggleUseCase`

---

## Domain Model & State Machine Rules

### 1. Light Modes
Model Lupine Piko output levels as a sealed hierarchy or enum representing wattage/lumen steps and special modes (e.g., `Off`, `Dimmed` / `Eco`, `Low`, `Medium`, `High`, `Max` / `ForestHighBeam`, `Sos`, `AlpineEmergency`):
```kotlin
enum class LightMode(val labelKey: String, val watts: Double, val isBright: Boolean) {
    OFF(labelKey = "mode_off", watts = 0.0, isBright = false),
    DIMMED(labelKey = "mode_dimmed", watts = 0.3, isBright = false),
    LOW(labelKey = "mode_low", watts = 1.5, isBright = false),
    MEDIUM(labelKey = "mode_medium", watts = 6.0, isBright = true),
    HIGH(labelKey = "mode_high", watts = 12.0, isBright = true),
    MAX(labelKey = "mode_max", watts = 22.0, isBright = true),
}
```

### 2. Fast High-Beam / Dimmed Toggle (`QuickDimToggleUseCase`)
The primary rider workflow in the forest is instant dazzle protection:
1. **When currently in any bright mode (`currentMode != dimmedMode` and `currentMode != OFF`)**:
   - Store `previousBrightMode = currentMode` in state.
   - Immediately transition to the configured `dimmedMode` (default `LightMode.DIMMED`).
2. **When currently in `dimmedMode`**:
   - Restore `previousBrightMode` (or fallback to configured `highBeamMode`, default `LightMode.MAX`).
3. **When currently `OFF`**:
   - Turn on directly to `dimmedMode` or `highBeamMode` based on action intent.

### 3. Mode Cycling (`CycleLightModeUseCase`)
- Cycles sequentially through the user-enabled subset of `LightMode` entries (e.g., `DIMMED -> LOW -> MEDIUM -> HIGH -> MAX -> DIMMED`).
- Explicitly setting or cycling to a bright mode updates `previousBrightMode` so the next quick-dim toggle always returns to the latest active riding mode.

---

## Testing Expectations
- All state transitions in `QuickDimToggleUseCase` and `CycleLightModeUseCase` must be 100% unit-tested using Kotest `ShouldSpec` and in-memory fakes/mocks without Android dependencies.
