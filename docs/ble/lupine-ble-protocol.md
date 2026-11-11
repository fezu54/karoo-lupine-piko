# Lupine Light Control BLE Protocol Specification

---

## 1. GATT Service & Characteristic UUIDs

| Name                          | UUID                                   | ASCII Suffix  | Properties / Usage                                                                                   |
|-------------------------------|----------------------------------------|---------------|------------------------------------------------------------------------------------------------------|
| `ServiceLupineUUID`           | `B29298B2-D595-41C6-5331-4C7570696E65` | `"S1Lupine"`  | Primary Lupine BLE Service                                                                           |
| `CharacteristicNotifyUUID`    | `CB432D3F-5CB9-815B-4342-4C7570696E65` | `"CBLupine"`  | **Write** live commands (`SendCommand`: status poll, button switch, direct mode/brightness override) |
| `CharacteristicConfigUUID`    | `CC432D3F-5CB9-815B-4343-4C7570696E65` | `"CCLupine"`  | **Read / Write** 16-byte button/profile configuration packets (`SendConfig` / `ReadConfig`)          |
| `CharacteristicDataUUID`      | `CD432D3F-5CB9-815B-4344-4C7570696E65` | `"CDLupine"`  | **Notify** 16-byte status & telemetry packets (`DataCharacteristic_ValueUpdated`)                    |
| `ClientConfigDescriptionUUID` | `00002902-0000-1000-8000-00805F9B34FB` | Standard CCCD | Enable notifications on `CharacteristicDataUUID`                                                     |

---

## 2. Device Identification (`HwId` Byte in Status Packet)

When subscribed to `CharacteristicDataUUID` (`CD432D3F-...`) and after sending the status poll command, the lamp sends a 16-byte status notification where byte `[1]` is `HwId`.
`HwId` is split into two 4-bit nibbles (`High = (HwId >> 4) & 0x0F`, `Low = HwId & 0x0F`):

| `HwId` High Nibble | `HwId` Low Nibble (`HardwareRevision`) | `DeviceType` Enum | Model                                           |
|--------------------|----------------------------------------|-------------------|-------------------------------------------------|
| `2` (`0x2_`)       | `!= 6`                                 | `Piko (2)`        | Lupine Piko R (earlier BLE generation, 20W max) |
| `2` (`0x26`)       | `== 6`                                 | `Piko_R_2019 (7)` | **Lupine Piko R (2019+, 22W max)**              |
| `5` (`0x5_`)       | `0`                                    | `Blika (5)`       | Lupine Blika (20W max, handled by `DevicePeak`) |
| `5` (`0x5_`)       | `1`                                    | `Blika2 (8)`      | Lupine Blika 2 (2019+, 23W max)                 |
| `6` (`0x6_`)       | any                                    | `Alpha (6)`       | Lupine Alpha (70W max)                          |

---

## 3. Live Commands (`CharacteristicNotifyUUID`: `CB432D3F-5CB9-815B-4342-4C7570696E65`)

All live commands are written to `CharacteristicNotifyUUID` (`CB432D3F-5CB9-815B-4342-4C7570696E65`).

### 3.1 Status Poll Command (`StatusMessage`)
Requests an immediate 16-byte status notification on `CharacteristicDataUUID` (polled every ~2 seconds while connected):
- **Bytes (2 bytes)**: `[0x74, 0x00]` (`'t'`, `0x00`)

### 3.2 Virtual Button Switch Commands (`DeviceButtonProtocol`)
Simulates pressing the physical remote/lamp buttons (UpSide = top/main button, DownSide = bottom/secondary button):

| Command                     | Byte Array (Hex) | ASCII / Decimal      | Description                                            |
|-----------------------------|------------------|----------------------|--------------------------------------------------------|
| **UpSide Switch / Cycle**   | `[0x74, 0x11]`   | `['t', 17]`          | Short press on main/top button (turn on / cycle level) |
| **UpSide Switch Off**       | `[0x74, 0x31]`   | `['t', 49]` (`"t1"`) | Turn off main lamp / exit live preview                 |
| **UpSide Switch Up**        | `[0x74, 0x41]`   | `['t', 65]` (`"tA"`) | Step brightness level **up**                           |
| **UpSide Switch Down**      | `[0x74, 0x42]`   | `['t', 66]` (`"tB"`) | Step brightness level **down**                         |
| **DownSide Switch / Cycle** | `[0x74, 0x12]`   | `['t', 18]`          | Short press on secondary/bottom button                 |
| **DownSide Switch Off**     | `[0x74, 0x32]`   | `['t', 50]` (`"t2"`) | Turn off secondary button mode                         |

### 3.3 Direct Brightness & LED Override (`GetModeMessage`)
Directly sets the lamp's active LEDs and brightness power level **immediately without changing the saved profile** (used by the Lupine app for Live Preview and GPS Speed Control).
**This is ideal for instant High-Beam ↔ Dimmed Low-Beam toggling on the Karoo!**

#### A. `Piko_R_2019` (`DeviceType == 7`, 5-byte payload)
- **Format**: `[0x6D, 0x26, swRev, powerValue, ledMask]`
  - `byte[0]` = `0x6D` (`109`, ASCII `'m'` = `ModeId`)
  - `byte[1]` = `0x26` (`38`, device identifier for Piko R 2019)
  - `byte[2]` = `CurrentStatus.SoftwareRevision` (`SwId` from status byte `[2]`, or `0x00` if unknown)
  - `byte[3]` = `powerValue`:
    - For **Main LED (`ledMask = 0x41`)**: `3` (`0x03`, ~0.3W minimum dimmed) to `232` (`0xE8`, 22W / 20W full high beam).
      - Default factory levels on Piko R 2019: `232` (`0xE8`), `96` (`0x60`), `24` (`0x18`).
    - For **Diffuse LED (`ledMask = 0x42`)**: `6` (`0x06`, 0.5W) to `30` (`0x1E`, 2.1W).
  - `byte[4]` = `ledMask` (`0x40 | ledId`):
    - `0x41` (`65`): Main LED (`0x40 | 0x01`)
    - `0x42` (`66`): Diffuse reading light (`0x40 | 0x02`)
    - `0x64` (`100`): SOS (`0x40 | 0x24`)
    - `0x70` (`112`): Alpine SOS (`0x40 | 0x30`)
    - `0x68` (`104`): RVLR Flash (`0x40 | 0x28`)

#### B. Classic `Piko` / `Blika` (`DevicePeak`, 6-byte payload)
- **Format**: `[0x6D, 0x50, swRev, powerValue, ledMask, 0x00]`
  - `byte[0]` = `0x6D` (`109`, ASCII `'m'`)
  - `byte[1]` = `0x50` (`80`, ASCII `'P'` for `DevicePeak`; `0x51` / `'Q'` for `Blika2`; `0x60` for `Alpha` / Speed Control)
  - `byte[2]` = `CurrentStatus.SoftwareRevision` (or `0x00`)
  - `byte[3]` = `powerValue` (`15` / `0x0F` min ~0.3W up to `232` / `0xE8` max 20W)
  - `byte[4]` = `ledMask` (`0x40 | ledId`, where `MainLed = 1` -> `0x41`, `RedLed = 2`, `GreenLed = 4`, `SpotLed = 8`, `DefuseLed = 16`)
  - `byte[5]` = `0x00`

#### C. Generic Speed-Control Direct Brightness Command (6-byte payload)
Used in `HomeViewModel::StartSpeedControl` and `Geolocator_PositionChanged`:
- **Format**: `[0x6D, 0x60, 0x00, powerValue, 0x01, 0x00]`
  - `powerValue` = `(byte)(232.0 * percent)` where `percent` is in `0.1 .. 1.0` (`23` to `232`).

---

## 4. Status & Telemetry Notification Packet (`CharacteristicDataUUID`: `CD432D3F-5CB9-815B-4344-4C7570696E65`)

Notifications on `CharacteristicDataUUID` are **16 bytes long** (`Peak.Models.Status`):

| Byte Index | Field Name   | Formula / Meaning                                                                                                                                                                                                                      |
|------------|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `[0]`      | `TYPE`       | ASCII char message type (e.g. `'t'` / `0x74`)                                                                                                                                                                                          |
| `[1]`      | `HwId`       | High nibble `(b >> 4)` = Device family (`2` = Piko, `5` = Blika, `6` = Alpha); Low nibble `(b & 0x0F)` = `HardwareRevision` (`6` = Piko R 2019)                                                                                        |
| `[2]`      | `SwId`       | `SoftwareRevision` (unsigned byte)                                                                                                                                                                                                     |
| `[3]`      | `LAMP_FLAG`  | Bitmask (`FL_04=0x01, FL_1=0x02, FL_2=0x04, FL_4=0x08, FL_7=0x10, FL_13=0x20, FL_OFF=0x80`)                                                                                                                                            |
| `[4]`      | `LED_FLAG`   | Status LEDs (`B_GRN_ON=0x01, B_RED_ON=0x02, B_BLU_ON=0x04, B_GRN_BLINK=0x08, B_RED_BLINK=0x10, B_BLU_BLINK=0x20`)                                                                                                                      |
| `[5]`      | `AH_CNT_H`   | Consumed capacity: `AH_CNT_H * 0.013` Ah                                                                                                                                                                                               |
| `[6]`      | `AH_CNT_L`   | Low Ah counter byte                                                                                                                                                                                                                    |
| `[7]`      | `AD_U_AKKU`  | **Battery Voltage (V)**: `AD_U_AKKU * 0.09` V (on Piko R 2019) or `AD_U_AKKU * 0.059` V (on classic Peak)                                                                                                                              |
| `[8]`      | `AD_I_LED`   | **Current LED Current (A)**: `(AD_I_LED / 232.0) * 3.0` A                                                                                                                                                                              |
| `[9]`      | *(unused)*   | Reserved                                                                                                                                                                                                                               |
| `[10]`     | `AD_TEMP`    | **Temperature (°C)**: Piecewise linear scale (`<=125 -> b/125*20°C`, `<=154 -> b/154*40°C`, `<=186 -> b/186*50°C`, `<=197 -> b/197*60°C`, `<=209 -> b/209*70°C`, `<=221 -> b/221*80°C`, `<=226 -> b/226*90°C`, `<=230 -> b/230*100°C`) |
| `[11]`     | `SOS_TYPE`   | Active SOS flags                                                                                                                                                                                                                       |
| `[12]`     | `M_KEY_FNCT` | Active key function / mode index                                                                                                                                                                                                       |
| `[13]`     | `M_KEY_I`    | **Max / Target LED Current (A)**: `(M_KEY_I / 232.0) * 3.0` A                                                                                                                                                                          |
| `[14]`     | `BYTE_14`    | Extra status byte 14                                                                                                                                                                                                                   |
| `[15]`     | `BYTE_15`    | Extra status byte 15                                                                                                                                                                                                                   |

---

## 5. Button / Profile Configuration (`CharacteristicConfigUUID`: `CC432D3F-5CB9-815B-4343-4C7570696E65`)

### Reading Current Device Configuration
1. Write 1 byte `[GetConfigId]` to `CharacteristicConfigUUID`:
   - UpSide Button `GetConfigId` = `0x75` (`117`, ASCII `'u'`)
   - DownSide Button `GetConfigId` = `0x64` (`100`, ASCII `'d'`)
2. Wait `300 ms`.
3. Read 16-byte array from `CharacteristicConfigUUID`.

### Writing Button Configuration (16 bytes)
Write 16 bytes to `CharacteristicConfigUUID` (wait `6000 ms` between UpSide and DownSide configs if writing both):
- `byte[0]`: `SetConfigId` (`0x76` / `'v'` for UpSide Button; `0x65` / `'e'` for DownSide Button)
- `byte[1]`: `BluetoothTimeout` (default `12` on UpSide)
- `byte[2]`: Reserved (`24` on Piko R 2019 UpSide, `48` on Peak UpSide)
- `byte[3]`:
  - UpSide (Piko R 2019): `SosType` (or `SosType | 0x20`)
  - DownSide: `BatteryWarningL1` (`round(voltage / 0.061)`, default `73` -> ~4.45V on Piko R 2019)
- `byte[4]`:
  - UpSide: `Modes` bitmask (`STEALTH=0x02, DOUBLECLICK=0x04, M_OFF_SEL=0x08, SAVE_FNCT=0x10, INIT_100_DIMM=0x20, NO_DIFUS=0x40, HEAD_OFF=0x80`)
  - DownSide: `BatteryWarningL2` (`round(voltage / 0.061)`, default `67` -> ~4.09V)
- `byte[5]`:
  - UpSide: `0x00` (Piko R 2019) or `SosType | 0x80` (Peak)
  - DownSide: `BatteryWarningL3` (`round(voltage / 0.061)`, default `63` -> ~3.84V)
- `byte[6..9]`: `PowerValue` for Level 0..3 (`0` if unused, `3..232` for Main LED)
- `byte[10..13]`: `LedId` bitmask for Level 0..3 (`1` = Main LED, `2` = Diffuse LED, etc.; on classic Peak UpSide, `0x40` is ORed)
- `byte[14]`: Reserved (`0` on UpSide)
- `byte[15]`: Check/trailer byte (`58` / `0x3A` on UpSide)
