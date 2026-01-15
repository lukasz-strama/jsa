# JSignalAnalysis Backend API Documentation

## Overview

This document describes the backend API for the JSignalAnalysis real-time FFT analyzer. The API provides a clean, thread-safe interface for JavaFX frontend development.

## Quick Start

```java
import pl.polsl.rtsa.api.SignalAnalyzerApi;
import pl.polsl.rtsa.api.dto.*;
import javafx.application.Platform;

public class MainController {
    private final SignalAnalyzerApi api = SignalAnalyzerApi.create();
    
    public void initialize() {
        // Set up data callback (runs on background thread)
        api.setDataCallback(data -> Platform.runLater(() -> {
            updateOscilloscope(data.timeDomainData());
            updateSpectrum(data.freqDomainData());
            updateStats(data.statistics());
        }));
        
        // Set up error callback
        api.setErrorCallback(msg -> Platform.runLater(() -> 
            showError(msg)
        ));
        
        // Set up connection callback
        api.setConnectionCallback(status -> Platform.runLater(() ->
            updateConnectionUI(status)
        ));
    }
    
    public void onConnectClicked() {
        String port = portComboBox.getValue();
        try {
            api.connect(port);
            api.startAcquisition();
        } catch (ConnectionException e) {
            showError("Connection failed: " + e.getMessage());
        }
    }
    
    public void onDisconnectClicked() {
        api.stopAcquisition();
        api.disconnect();
    }
    
    public void onShutdown() {
        api.shutdown();
    }
}
```

## API Reference

### Factory Methods

```java
// For real hardware
SignalAnalyzerApi api = SignalAnalyzerApi.create();

// For testing with mock data (generates 50Hz sine wave)
SignalAnalyzerApi api = SignalAnalyzerApi.createMock();
```

### Connection Management

| Method | Description |
|--------|-------------|
| `AvailablePorts getAvailablePorts()` | Get cached list of serial ports |
| `AvailablePorts refreshPorts()` | Force refresh of port list |
| `void connect(String portName)` | Connect to device (throws `ConnectionException`) |
| `void disconnect()` | Disconnect (safe to call anytime) |
| `ConnectionStatus getConnectionStatus()` | Get current connection state |
| `boolean isConnected()` | Quick connection check |

### Acquisition Control

| Method | Description |
|--------|-------------|
| `void startAcquisition()` | Start data stream (throws if not connected) |
| `void stopAcquisition()` | Stop data stream |
| `boolean isAcquiring()` | Check if acquiring |
| `AcquisitionConfig getAcquisitionConfig()` | Get current config |

### Sample Rate Control

| Method | Rate | Notes |
|--------|------|-------|
| `setSampleRate1kHz()` | 1,000 Hz | Default, best for low-frequency signals |
| `setSampleRate10kHz()` | 10,000 Hz | Mid-range, good for audio |
| `setSampleRate20kHz()` | 20,000 Hz | Turbo mode, reduced impedance tolerance |
| `getCurrentSampleRate()` | - | Returns current rate in Hz |

### Data Callbacks

```java
// Signal data (called ~30 times/second during acquisition)
api.setDataCallback(data -> {
    double[] time = data.timeDomainData();      // Voltage samples
    double[] freq = data.freqDomainData();      // FFT magnitude
    SignalStatistics stats = data.statistics(); // Pre-computed stats
    double rate = data.sampleRate();            // Current sample rate
    Instant ts = data.timestamp();              // When captured
});

// Errors
api.setErrorCallback(message -> {
    // Handle device errors
});

// Connection state changes
api.setConnectionCallback(status -> {
    if (status.connected()) {
        // Connected to status.portName()
    } else {
        // Disconnected
    }
});
```

### Signal Processing Utilities

```java
// Convert raw ADC (0-1023) to voltage (0-5V)
double[] voltage = api.convertToVoltage(rawData);

// Compute FFT on external data
double[] fft = api.computeFFT(voltageData, sampleRate);

// Compute statistics
SignalStatistics stats = api.computeStatistics(voltageData, fftData, sampleRate);
```

### Lifecycle

```java
// Call on application shutdown
api.shutdown();

// Check if shut down
boolean closed = api.isShutdown();
```

## Data Transfer Objects (DTOs)

### SignalData

The main data container received in data callbacks.

```java
record SignalData(
    Instant timestamp,          // When captured
    double[] timeDomainData,    // Voltage samples (V)
    double[] freqDomainData,    // FFT magnitude
    double sampleRate,          // Hz
    SignalStatistics statistics // Pre-computed stats
) {
    double getDurationSeconds();      // Time span of data
    double getFrequencyResolution();  // Hz per FFT bin
    double getNyquistFrequency();     // Max frequency (rate/2)
    int getSampleCount();             // Time domain length
    int getFrequencyBinCount();       // Spectrum length
}
```

### SignalStatistics

Pre-computed signal analysis results.

```java
record SignalStatistics(
    double rmsVoltage,      // RMS voltage (V)
    double peakToPeak,      // Peak-to-peak voltage (V)
    double minVoltage,      // Minimum voltage (V)
    double maxVoltage,      // Maximum voltage (V)
    double dcOffset,        // DC offset / mean (V)
    double dominantFreq,    // Strongest frequency (Hz)
    double dominantFreqMag  // Magnitude of dominant freq
) {}
```

### ConnectionStatus

```java
record ConnectionStatus(
    boolean connected,   // Is connected
    String portName,     // e.g., "/dev/ttyACM0"
    String deviceInfo    // e.g., "OSC_V1"
) {
    static ConnectionStatus disconnected();
    static ConnectionStatus connected(String port, String info);
}
```

### AcquisitionConfig

```java
record AcquisitionConfig(
    double sampleRate,       // Hz
    int bufferSize,          // Samples per buffer
    boolean acquisitionActive
) {
    static final double RATE_1KHZ = 1000.0;
    static final double RATE_10KHZ = 10000.0;
    static final double RATE_20KHZ = 20000.0;
    
    static AcquisitionConfig defaultConfig();
    AcquisitionConfig withSampleRate(double rate);
    AcquisitionConfig withAcquisitionActive(boolean active);
}
```

### AvailablePorts

```java
record AvailablePorts(
    List<String> ports,      // Port names
    long lastRefreshTime     // Unix timestamp
) {
    boolean hasAvailablePorts();
    int count();
}
```

## Exception Handling

### Exception Hierarchy

```
DeviceException (base)
└── ConnectionException
    └── INVALID_PORT, PORT_BUSY, HANDSHAKE_FAILED, COMMUNICATION_ERROR, TIMEOUT
```

### Error Codes

| Code | Description |
|------|-------------|
| `CONNECTION_FAILED` | Generic connection failure |
| `NOT_CONNECTED` | Operation requires connection |
| `HANDSHAKE_FAILED` | Device didn't respond correctly |
| `COMMUNICATION_ERROR` | I/O error during communication |
| `INVALID_PORT` | Port name is null or invalid |
| `PORT_BUSY` | Port is used by another app |
| `TIMEOUT` | Operation timed out |
| `CONFIGURATION_ERROR` | Configuration issue |

### Usage Example

```java
try {
    api.connect(portName);
} catch (ConnectionException e) {
    switch (e.getErrorCode()) {
        case INVALID_PORT -> showError("Invalid port selected");
        case HANDSHAKE_FAILED -> showError("Device not recognized");
        default -> showError("Connection failed: " + e.getMessage());
    }
}
```

## Thread Safety

- **All public API methods are thread-safe**
- **Callbacks run on background threads** - use `Platform.runLater()` for UI updates
- Data is delivered as immutable DTOs (records) - safe to pass between threads

## Configuration

Settings in `src/main/resources/config.properties`:

```properties
# Serial
serial.baudrate=2000000      # 2 Mbps
serial.timeout.read=2000     # 2 seconds
serial.auto_reset_delay=2000 # Arduino reset delay

# DSP
dsp.sample_rate=1000         # Default rate
dsp.buffer_size=32768        # Ring buffer size
dsp.v_ref=5.0                # Reference voltage
dsp.adc_resolution=1024      # 10-bit ADC
dsp.target_fps=30            # UI update rate

# App
app.use_mock=false           # Use mock device
```

## Best Practices

1. **Always shutdown on close:**
   ```java
   @Override
   public void stop() {
       api.shutdown();
   }
   ```

2. **Handle disconnections gracefully:**
   ```java
   api.setErrorCallback(msg -> {
       api.disconnect();
       Platform.runLater(() -> showReconnectDialog());
   });
   ```

3. **Use mock for development:**
   ```java
   SignalAnalyzerApi api = isDevelopment 
       ? SignalAnalyzerApi.createMock() 
       : SignalAnalyzerApi.create();
   ```

4. **Downsample for display:**
   For large datasets, the backend's SignalProcessingService provides:
   ```java
   // In your controller, you can inject the service if needed
   processingService.downsampleForDisplay(data, maxPoints);
   ```

## Frequency Axis Calculation

To draw the X-axis for FFT spectrum:

```java
SignalData data = ...;
double nyquist = data.getNyquistFrequency();  // Max frequency
double resolution = data.getFrequencyResolution();  // Hz per bin

// For each FFT bin index 'i':
double frequency = i * resolution;

// Or use the service method:
double[] freqAxis = processingService.computeFrequencyAxis(
    data.getFrequencyBinCount(), 
    data.sampleRate()
);
```

## Module Exports

The module exports these packages for use in JavaFX:

```java
exports pl.polsl.rtsa.api;           // Main API
exports pl.polsl.rtsa.api.dto;       // Data transfer objects
exports pl.polsl.rtsa.api.exception; // Exceptions
exports pl.polsl.rtsa.service;       // Processing utilities
exports pl.polsl.rtsa.config;        // Configuration
```
