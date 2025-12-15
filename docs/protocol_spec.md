# Protocol Specification (UART)

## 1. Transmission Settings
* **Baud Rate:** 115200 bps
* **Data Bits:** 8
* **Parity:** None
* **Stop Bits:** 1
* **Flow Control:** None

## 2. Command Set
The host (PC) controls the device by sending single-byte commands.

| Command | Hex | ASCII | Description |
| :--- | :--- | :--- | :--- |
| **START** | `0x01` | - | Starts the sampling timer and data transmission. |
| **STOP** | `0x02` | - | Stops the sampling timer. |
| **RATE_1KHZ** | `0x10` | - | Sets sampling rate to 1 kHz (Default). |
| **RATE_10KHZ** | `0x11` | - | Sets sampling rate to 10 kHz. |
| **HANDSHAKE** | `0x3F` | `?` | Requests device identification and integrity check. |

### Handshake Response
Upon receiving `0x3F`, the device responds with:
1.  **ID String:** `"OSC_V1\n"` (7 bytes: `O`, `S`, `C`, `_`, `V`, `1`, `\n`)
2.  **Checksum:** 1 Byte (XOR of all 7 bytes in the ID string).

**Verification Algorithm (Python):**
```python
expected_checksum = 0
for byte in b"OSC_V1\n":
    expected_checksum ^= byte
# Compare expected_checksum with the last received byte
```

## 3. Data Format (ADC Stream)
10-bit ADC samples are transmitted as a continuous stream of 2-byte packets.
Bit 7 (MSB) is used for packet synchronization.

### Packet Structure

| Byte | Role | Bit 7 (Sync) | Bit 6 | Bit 5 | Bit 4 | Bit 3 | Bit 2 | Bit 1 | Bit 0 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **High Byte** | Start of Sample | **1** | 0 | 0 | 0 | 0 | D9 | D8 | D7 |
| **Low Byte** | End of Sample | **0** | D6 | D5 | D4 | D3 | D2 | D1 | D0 |

*   **D9...D0**: Raw 10-bit ADC value (0..1023).
*   **Sync Bit (Bit 7)**:
    *   `1`: Indicates the High Byte (Bits 9-7).
    *   `0`: Indicates the Low Byte (Bits 6-0).

### Reconstruction Logic
```c
uint16_t sample = ((HighByte & 0x07) << 7) | (LowByte & 0x7F);
```

## 4. Error Handling
*   **Buffer Overflow:** If the internal UART Ring Buffer is full (e.g., PC is reading too slowly), new data is dropped.
*   **Indicator:** The on-board LED toggles to indicate a buffer overflow event.
*   **Watchdog Timer:** The system is protected by a 2-second Watchdog Timer. If the main loop hangs, the device will reset.
