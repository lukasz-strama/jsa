"""Sampling-rate benchmark for the JSignalAnalysis ATmega328P firmware.

Measures the actual sample throughput at each supported rate (1/10/20 kHz)
over a configurable duration and reports the deviation from the target.

Usage::

    python benchmark.py                   # auto-detect Arduino port
    python benchmark.py --port=/dev/ttyACM0
"""

import serial
import serial.tools.list_ports
import time
import sys

# Configuration
BAUD_RATE = 2000000
MEASURE_DURATION = 10.0  # Seconds

TEST_CASES = [
    {"name": "1 kHz",  "cmd": b'\x10', "target": 1000},
    {"name": "10 kHz", "cmd": b'\x11', "target": 10000},
    {"name": "20 kHz", "cmd": b'\x12', "target": 20000},
]

def find_arduino_port():
    """
    Auto-detects the serial port of the Arduino.
    Returns the port name (e.g., '/dev/ttyACM0' or 'COM3').
    Raises Exception if not found.
    """
    ports = list(serial.tools.list_ports.comports())
    for p in ports:
        # Check for common Arduino identifiers
        if "Arduino" in p.description or "CH340" in p.description or "USB Serial" in p.description or "ACM" in p.device:
            print(f"Auto-detected Arduino on port: {p.device} ({p.description})")
            return p.device
    
    # Fallback/Error
    print("Available ports:")
    for p in ports:
        print(f" - {p.device}: {p.description}")
    raise Exception("Could not find Arduino. Please specify port manually using --port")

def run_benchmark():
    """Execute the sampling-rate benchmark across all configured test cases.

    Steps:
        1. Detect (or accept) a serial port.
        2. For each rate in ``TEST_CASES``: set the rate, start acquisition,
           count received bytes over ``MEASURE_DURATION`` seconds, stop, and
           compute the actual Hz and error percentage.
        3. Print a summary table to stdout.
    """
    # 1. Detect Port
    port = ""
    if len(sys.argv) > 1 and sys.argv[1].startswith("--port="):
        port = sys.argv[1].split("=")[1]
    else:
        try:
            port = find_arduino_port()
        except Exception as e:
            print(f"Error: {e}")
            sys.exit(1)

    # 2. Open Connection
    try:
        ser = serial.Serial(port, BAUD_RATE, timeout=0.1)
        time.sleep(2.0) # Wait for Arduino reset
    except Exception as e:
        print(f"Failed to open port {port}: {e}")
        sys.exit(1)

    print(f"\nStarting Benchmark on {port} @ {BAUD_RATE} baud")
    print(f"Duration per mode: {MEASURE_DURATION} seconds\n")
    
    # Header
    print(f"{'Mode':<10} | {'Target Hz':<10} | {'Actual Hz':<10} | {'Samples':<10} | {'Error %':<10}")
    print("-" * 65)

    for test in TEST_CASES:
        # Stop first to ensure clean state
        ser.write(b'\x02') 
        time.sleep(0.1)
        ser.reset_input_buffer()

        # Set Rate
        ser.write(test["cmd"])
        time.sleep(0.05)

        # Start Transmission
        ser.write(b'\x01')
        
        # Warm-up (discard initial data)
        time.sleep(0.2)
        ser.reset_input_buffer()

        # Measurement Loop
        total_bytes = 0
        start_time = time.perf_counter()
        
        while (time.perf_counter() - start_time) < MEASURE_DURATION:
            if ser.in_waiting:
                data = ser.read(ser.in_waiting)
                total_bytes += len(data)
            else:
                time.sleep(0.001) # Prevent CPU spin
        
        end_time = time.perf_counter()
        
        # Stop Transmission
        ser.write(b'\x02')

        # Calculations
        actual_duration = end_time - start_time
        total_samples = total_bytes / 2
        actual_hz = total_samples / actual_duration
        error_percent = abs(test["target"] - actual_hz) / test["target"] * 100

        # Output Row
        print(f"{test['name']:<10} | {test['target']:<10} | {actual_hz:<10.2f} | {int(total_samples):<10} | {error_percent:<10.2f}%")

    ser.close()
    print("\nBenchmark Complete.")

if __name__ == "__main__":
    run_benchmark()
