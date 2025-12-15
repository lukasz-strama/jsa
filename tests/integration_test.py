import unittest
import serial
import serial.tools.list_ports
import time
import sys

def find_arduino_port():
    """
    Auto-detects the serial port of the Arduino.
    Returns the port name (e.g., '/dev/ttyACM0' or 'COM3').
    Raises Exception if not found.
    """
    ports = list(serial.tools.list_ports.comports())
    for p in ports:
        # Check for common Arduino identifiers in the description or manufacturer
        # Note: "USB Serial" is common for CH340 clones
        if "Arduino" in p.description or "CH340" in p.description or "USB Serial" in p.description or "ACM" in p.device:
            print(f"Auto-detected Arduino on port: {p.device} ({p.description})")
            return p.device
    
    # Fallback/Error
    print("Available ports:")
    for p in ports:
        print(f" - {p.device}: {p.description}")
    raise Exception("Could not find Arduino. Please specify port manually using --port")

# Default configuration
# If --port is not passed, try to auto-detect
if '--port' not in sys.argv:
    try:
        SERIAL_PORT = find_arduino_port()
    except Exception as e:
        print(e)
        # Fallback to a default if detection fails, but likely won't work
        SERIAL_PORT = '/dev/ttyACM0' 
else:
    # Placeholder, will be overwritten in __main__ block
    SERIAL_PORT = '' 

BAUD_RATE = 2000000

class TestFirmwareProtocol(unittest.TestCase):
    """
    Integration tests for ATmega328P Firmware via UART.
    """

    def setUp(self):
        """
        Setup method run before EACH test.
        Opens the serial port and waits for the board to reset.
        """
        try:
            self.ser = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=0.5)
            # Opening the port on Arduino usually triggers a reset (DTR toggle).
            # Wait for bootloader to finish and firmware to start.
            time.sleep(2.0)
            self.ser.reset_input_buffer()
            self.ser.reset_output_buffer()
        except serial.SerialException as e:
            self.fail(f"Could not open serial port {SERIAL_PORT}: {e}")

    def tearDown(self):
        """
        Teardown method run after EACH test.
        Stops sampling and closes the port.
        """
        if self.ser and self.ser.is_open:
            # Try to stop sampling to leave device in clean state
            self.ser.write(b'\x02') 
            self.ser.close()

    def test_connection_and_silence_on_boot(self):
        """
        Test 1: Verify that NO data is received initially (firmware should wait for START command).
        """
        print("\n[Test] Connection and Silence on Boot")
        
        # Read for a short period
        data = self.ser.read(10)
        
        # Assert that buffer is empty (timeout occurred and no bytes read)
        self.assertEqual(len(data), 0, f"Received unexpected data on boot: {data.hex()}")
        print("PASS: Device is silent on boot.")

    def test_start_command_and_data_flow(self):
        """
        Test 2: Send START command and verify data starts arriving.
        """
        print("\n[Test] Start Command and Data Flow")
        
        # Send START command
        self.ser.write(b'\x01')
        
        # Read a chunk of data
        # We expect data to flow immediately. 
        # At 1kHz, we get ~2000 bytes/sec. Reading 100 bytes should be fast.
        data = self.ser.read(100)
        
        self.assertGreater(len(data), 0, "No data received after START command.")
        print(f"Received data sample: {data[:20].hex()}")
        self.assertEqual(len(data), 100, "Timeout while reading data stream.")
        print(f"PASS: Received {len(data)} bytes after START.")

    def test_packet_integrity_and_sync(self):
        """
        Test 3: Analyze stream for protocol compliance (High/Low byte markers).
        """
        print("\n[Test] Packet Integrity and Sync")
        
        self.ser.write(b'\x01') # START
        time.sleep(0.1) # Buffer some data
        
        data = self.ser.read(200)
        self.assertTrue(len(data) > 50, "Not enough data for analysis.")
        
        valid_packets = 0
        sync_errors = 0
        
        # We need to find the first High Byte to start parsing
        start_index = -1
        for i in range(len(data)):
            if (data[i] & 0x80) == 0x80:
                start_index = i
                break
        
        self.assertNotEqual(start_index, -1, "No High Byte (Sync bit) found in stream.")
        
        # Iterate through pairs
        i = start_index
        while i < len(data) - 1:
            high = data[i]
            low = data[i+1]
            
            # Check High Byte Marker
            if (high & 0x80) != 0x80:
                # Lost sync or garbage data
                sync_errors += 1
                i += 1 # Try to resync next byte
                continue
                
            # Check Low Byte Marker
            if (low & 0x80) != 0x00:
                # The byte following a high byte was not a low byte.
                # It might be another high byte (missed low byte?)
                sync_errors += 1
                i += 1 # Treat current 'low' as potential 'high' for next iter
                continue
            
            # If we got here, we have a valid pair
            valid_packets += 1
            i += 2
            
        print(f"Analyzed {len(data)} bytes. Valid Packets: {valid_packets}, Sync Errors: {sync_errors}")
        self.assertGreater(valid_packets, 0, "No valid packets found.")
        self.assertLess(sync_errors, 5, "Too many sync errors, unreliable connection.")
        print("PASS: Packet structure is valid.")

    def test_value_reconstruction_range(self):
        """
        Test 4: Reassemble 10-bit integer and verify range [0, 1023].
        """
        print("\n[Test] Value Reconstruction and Range")
        
        self.ser.write(b'\x01') # START
        time.sleep(0.1)
        data = self.ser.read(100)
        
        # Find sync
        start_index = 0
        while start_index < len(data) and (data[start_index] & 0x80) == 0:
            start_index += 1
            
        reconstructed_values = []
        
        for i in range(start_index, len(data)-1, 2):
            high = data[i]
            low = data[i+1]
            
            if (high & 0x80) == 0x80 and (low & 0x80) == 0x00:
                # Reassemble
                # High byte: 1 0 0 0 0 D9 D8 D7 (Bits 0-2 are data)
                # Low byte:  0 D6 D5 D4 D3 D2 D1 D0 (Bits 0-6 are data)
                
                val = ((high & 0x07) << 7) | (low & 0x7F)
                reconstructed_values.append(val)
                
                self.assertTrue(0 <= val <= 1023, f"Value out of range: {val}")
        
        self.assertGreater(len(reconstructed_values), 0, "No values reconstructed.")
        print(f"PASS: Checked {len(reconstructed_values)} samples. All within [0, 1023].")
        print(f"Sample values: {reconstructed_values[:10]}...")

    def test_handshake_and_checksum(self):
        """
        Test 6: Verify Handshake command and Checksum.
        Command: '?' (0x3F)
        Expected: "OSC_V1\\n" + 1 byte XOR checksum.
        """
        print("\n[Test] Handshake and Checksum")
        
        # Flush buffers
        self.ser.reset_input_buffer()
        
        # Send Handshake
        self.ser.write(b'?')
        
        # Read response
        # "OSC_V1\n" is 7 bytes. + 1 byte checksum = 8 bytes total.
        response = self.ser.read(8)
        
        self.assertEqual(len(response), 8, f"Timeout or incomplete handshake response. Received: {response}")
        
        # Split string and checksum
        id_string = response[:-1]
        received_checksum = response[-1]
        
        # Assertion 1: Verify ID String
        expected_string = b"OSC_V1\n"
        self.assertEqual(id_string, expected_string, f"Invalid ID string. Expected {expected_string}, got {id_string}")
        
        # Assertion 2: Verify Checksum
        expected_checksum = 0
        for byte in expected_string:
            expected_checksum ^= byte
            
        self.assertEqual(received_checksum, expected_checksum, 
                         f"Checksum mismatch. Calc: {hex(expected_checksum)}, Recv: {hex(received_checksum)}")
        
        print(f"PASS: Handshake verified. ID: {id_string.strip()}, Checksum: {hex(received_checksum)}")

    def test_robustness_invalid_commands(self):
        """
        Test 7: Send garbage commands and verify device stability.
        """
        print("\n[Test] Robustness - Invalid Commands")
        
        # Send garbage
        garbage = b'\xFF\xAB\x00\xCA\xFE'
        self.ser.write(garbage)
        
        # Wait a moment to ensure no crash/reset loop triggered immediately
        time.sleep(0.5)
        
        # Verify device is still responsive by sending a valid START command
        self.ser.write(b'\x01')
        
        # Read data
        data = self.ser.read(50)
        
        self.assertGreater(len(data), 0, "Device unresponsive after garbage input.")
        print("PASS: Device survived garbage input and resumed operation.")

    def test_stop_command(self):
        """
        Test 5: Send STOP command and verify silence.
        """
        print("\n[Test] Stop Command")
        
        # First start and ensure data is flowing
        self.ser.write(b'\x01')
        self.ser.read(100) # Clear some data
        
        # Send STOP
        self.ser.write(b'\x02')
        
        # Allow time for firmware to process and buffer to drain
        time.sleep(0.1)
        self.ser.reset_input_buffer()
        
        # Wait a bit more to ensure no new data is generated
        time.sleep(0.5)
        
        # Try to read
        data = self.ser.read(10)
        
        self.assertEqual(len(data), 0, f"Received data after STOP: {data.hex()}")
        print("PASS: Device stopped transmission.")

    def test_turbo_mode_20khz(self):
        """
        Test 4: Verify 20kHz Turbo Mode.
        """
        print("\n[Test] Turbo Mode (20kHz)")
        
        # Set Rate to 20kHz
        self.ser.write(b'\x12')
        time.sleep(0.1)
        
        # Start
        self.ser.write(b'\x01')
        
        # Read data
        # At 20kHz, we get ~40000 bytes/sec.
        # Read for 0.1s -> ~4000 bytes
        data = self.ser.read(4000)
        
        self.assertGreater(len(data), 100, "Insufficient data received in Turbo Mode.")
        print(f"Received {len(data)} bytes in ~0.1s (Expected ~4000)")
        
        # Stop
        self.ser.write(b'\x02')

if __name__ == '__main__':
    # Parse custom arguments
    if '--port' in sys.argv:
        idx = sys.argv.index('--port')
        if idx + 1 < len(sys.argv):
            SERIAL_PORT = sys.argv[idx+1]
            # Remove arguments so unittest doesn't complain
            del sys.argv[idx:idx+2]
        else:
            print("Error: --port requires an argument")
            sys.exit(1)

    unittest.main()
