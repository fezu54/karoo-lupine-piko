---
name: lupine-ble-protocol
description: Specifications and instructions for Lupine Piko BLE discovery, GATT connection management via Kable, low-latency command encoding, telemetry parsing, and auto-reconnect handling.
---

# Lupine BLE Protocol Skill

Use this skill when implementing or modifying Bluetooth Low Energy (BLE) discovery, connection lifecycle, GATT characteristic reads/writes/notifications, or packet encoding/decoding for the Lupine Piko lamp.

Detailed reverse-engineered protocol reference: [`docs/ble/lupine-ble-protocol.md`](../../../docs/ble/lupine-ble-protocol.md).

## Architectural Placement (Onion Architecture)

1. **Domain (`domain/ble` or `domain/light`)**:
   - Pure Kotlin value objects and entities: `BleConnectionState`, `LightTelemetry` (battery voltage, LED current, temperature, hardware/software revision), `LightCommand`, `BleDeviceAddress`.
   - Zero Android or Kable imports.
2. **Application (`application/port`)**:
   - Gateway interfaces: `LupineBleGateway` exposing `connectionState: Flow<BleConnectionState>`, `telemetry: Flow<LightTelemetry>`, and `suspend fun sendCommand(command: LightCommand): Result<Unit>`.
3. **Adapters (` adapter/ble` or `data/ble`)**:
   - Kable-based implementation of `LupineBleGateway`.
   - GATT service/characteristic lookup, packet framing, retry/backoff logic.

## GATT Service & Characteristic UUIDs

All Lupine UUIDs end with ASCII `"Lupine"` (`4C7570696E65`):
- **Primary Service (`ServiceLupineUUID`)**: `B29298B2-D595-41C6-5331-4C7570696E65` (`"S1Lupine"`)
- **Command Write (`CharacteristicNotifyUUID`)**: `CB432D3F-5CB9-815B-4342-4C7570696E65` (`"CBLupine"`)
- **Config Read/Write (`CharacteristicConfigUUID`)**: `CC432D3F-5CB9-815B-4343-4C7570696E65` (`"CCLupine"`)
- **Telemetry Notify (`CharacteristicDataUUID`)**: `CD432D3F-5CB9-815B-4344-4C7570696E65` (`"CDLupine"`)
- **CCCD**: `00002902-0000-1000-8000-00805F9B34FB`

## Live Command Payloads (Write to `CharacteristicNotifyUUID`)

1. **Poll Status**:
   - `byteArrayOf(0x74, 0x00)` (`'t'`, `0x00`) — triggers a 16-byte notification on `CharacteristicDataUUID`.
2. **Virtual Button Commands**:
   - **Switch / Cycle UpSide (Main) Button**: `byteArrayOf(0x74, 0x11)`
   - **Switch Off UpSide (Main) Button**: `byteArrayOf(0x74, 0x31)` (`"t1"`)
   - **Step Brightness Level Up**: `byteArrayOf(0x74, 0x41)` (`"tA"`)
   - **Step Brightness Level Down**: `byteArrayOf(0x74, 0x42)` (`"tB"`)
   - **Switch / Cycle DownSide Button**: `byteArrayOf(0x74, 0x12)`
   - **Switch Off DownSide Button**: `byteArrayOf(0x74, 0x32)` (`"t2"`)
3. **Direct Brightness / Mode Override (`GetModeMessage`)**:
   - **Piko R 2019 (`DeviceType == 7`, 5 bytes)**:
     `byteArrayOf(0x6D, 0x26, swRev, powerValue, ledMask)`
     - `powerValue`: `3` (`0x03`, ~0.3W min dimmed) to `232` (`0xE8`, 22W max high beam) for Main LED (`ledMask = 0x41`); `6..30` for Diffuse LED (`ledMask = 0x42`).
   - **Classic Piko / Peak (`DeviceType == 2`, 6 bytes)**:
     `byteArrayOf(0x6D, 0x50, swRev, powerValue, ledMask, 0x00)`
     - `powerValue`: `15` (`0x0F`, ~0.3W) to `232` (`0xE8`, 20W) for Main LED (`ledMask = 0x41`).
   - **Universal Speed-Control Brightness Override (6 bytes)**:
     `byteArrayOf(0x6D, 0x60, 0x00, powerValue, 0x01, 0x00)`

## Status Notification Parsing (`CharacteristicDataUUID`, 16 bytes)

- `bytes[0]`: `TYPE` (`0x74` / `'t'`)
- `bytes[1]`: `HwId` (`(b >> 4) == 2 && (b & 0x0F) == 6` -> Piko R 2019; `(b >> 4) == 2` -> Piko)
- `bytes[2]`: `SwId` (`SoftwareRevision`)
- `bytes[3]`: `LAMP_FLAG` (`0x80` = `FL_OFF`)
- `bytes[4]`: `LED_FLAG` (button LED indicators)
- `bytes[5]`: `AH_CNT_H` (`consumedAh = b * 0.013`)
- `bytes[7]`: `AD_U_AKKU` (`voltage = b * 0.09` V for Piko R 2019, or `b * 0.059` V for classic Piko)
- `bytes[8]`: `AD_I_LED` (`ledCurrentA = (b / 232.0) * 3.0`)
- `bytes[10]`: `AD_TEMP` (temperature ADC value)
- `bytes[12]`: `M_KEY_FNCT` (current key function)
- `bytes[13]`: `M_KEY_I` (`maxCurrentA = (b / 232.0) * 3.0`)

## Key Implementation Rules

- **Low-Latency Command Execution**:
  - Prioritize rapid command delivery for the oncoming-traffic dim toggle (`< 100ms` dispatch).
  - Conflate rapid sequential brightness commands or guard with a mutex so stale queued commands do not overwrite the latest user intent.
- **Resilient Auto-Reconnect**:
  - Maintain connection state reactively using Kotlin `Flow`.
  - When the Piko enters standby or goes out of range, automatically reconnect when it advertises again during an active ride.
- **Deterministic Unit Testing**:
  - Do not depend on real Android Bluetooth adapters in unit tests.
  - Provide an in-memory fake `LupineBleGateway` and unit test packet encoders/decoders with Kotest `ShouldSpec`.
