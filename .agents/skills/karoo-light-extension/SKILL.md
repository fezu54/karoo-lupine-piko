---
name: karoo-light-extension
description: Guidelines for integrating with the Hammerhead karoo-ext SDK (KarooExtension), exposing interactive graphical data fields (RemoteViews), BonusActions, and lightweight state persistence.
---

# Karoo Extension & In-Ride Control Integration

## Overview
Coordinates the Hammerhead `karoo-ext` service (`LupinePikoExtension`), Karoo device events, interactive in-ride graphical data fields (`RemoteViews`), hardware/Di2 `BonusAction` triggers, and `SharedPreferences` persistence.

---

## Architecture Placement
- **Extension Service**: `org.happycode.karoo.lupinepiko.extension.LupinePikoExtension`
- **Device Adapter**: `org.happycode.karoo.lupinepiko.extension.LupineKarooAdapter`
- **Graphical DataTypes**: `org.happycode.karoo.lupinepiko.extension.LupineControlDataType`
- **Preferences Store**: `org.happycode.karoo.lupinepiko.adapters.storage.SharedPrefsLightConfigRepository`
- **SDK**: `io.hammerhead:karoo-ext`

---

## 1. Interactive In-Ride Data Fields (`DataTypeImpl`)
Provide graphical data fields (`RemoteViews`) for the Karoo ride pages so the rider can see the current light state and switch modes with a single tap during a ride:
- **Quick-Dim / High-Beam Data Field**:
  - Displays current mode (e.g., `MAX 22W` vs `DIM 0.3W`), battery status, and a prominent visual indicator when high-beam is active.
  - Tapping the data field triggers `QuickDimToggleUseCase` via a `BroadcastReceiver` / `PendingIntent` or `CommandBus`.
- **Mode Selector / Cycle Data Field**:
  - Displays active mode and allows tapping to cycle modes (`CycleLightModeUseCase`) or step up/down.

---

## 2. Karoo Bonus Actions (Hardware / Di2 / Lap Button Control)
Register Karoo `BonusAction` entries in `extension_info.xml` and handle `performBonusAction(actionId)` in `LupinePikoExtension` so the rider can trigger light actions without touching the screen:
- `action_toggle_dim`: Immediately toggles between high-beam and dimmed mode (`QuickDimToggleUseCase`).
- `action_cycle_mode`: Advances to the next configured light mode (`CycleLightModeUseCase`).
- `action_toggle_power`: Turns the light on/off.

---

## 3. Minimal Local State Management
Prefer `SharedPreferences` over Room/SQLite unless relational historical data is required:
- `pref_high_beam_mode`: Default bright forest mode (e.g., `MAX`).
- `pref_dimmed_mode`: Default oncoming-traffic dimmed mode (e.g., `DIMMED`).
- `pref_enabled_cycle_modes`: Ordered set/list of modes included in sequential cycling.
- `pref_locked_mac_address`: Optional locked BLE MAC address.
