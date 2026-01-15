package pl.polsl.rtsa.api;

import pl.polsl.rtsa.api.dto.*;

import java.util.function.Consumer;

/** 
 *|     .-.
 *|    /   \         .-.
 *|   /     \       /   \       .-.     .-.     _   _
 *+--/-------\-----/-----\-----/---\---/---\---/-\-/-\/\/---
 *| /         \   /       \   /     '-'     '-'
 *|/           '-'         '-'
 */

/**
 * Main API interface for the Signal Analyzer backend.
 * <p>
 * This interface defines the complete contract between the JavaFX frontend
 * and the backend services. All methods are designed to be called from the
 * UI thread and handle threading internally.
 * </p>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * SignalAnalyzerApi api = SignalAnalyzerApi.create();
 * 
 * // Subscribe to data updates
 * api.setDataCallback(data -> Platform.runLater(() -> updateCharts(data)));
 * 
 * // Connect and start
 * api.connect("/dev/ttyACM0");
 * api.startAcquisition();
 * 
 * // Cleanup
 * api.shutdown();
 * }</pre>
 * 
 * <h2>Thread Safety:</h2>
 * <ul>
 *   <li>All public methods are thread-safe</li>
 *   <li>Callbacks are invoked on a background thread</li>
 *   <li>Use {@code Platform.runLater()} for UI updates in callbacks</li>
 * </ul>
 */
public interface SignalAnalyzerApi {

    // ==================== Factory Methods ====================

    /**
     * Creates a new API instance with real hardware support.
     *
     * @return A new SignalAnalyzerApi instance.
     */
    static SignalAnalyzerApi create() {
        return new SignalAnalyzerApiImpl(false);
    }

    /**
     * Creates a new API instance with mock hardware for testing.
     *
     * @return A new SignalAnalyzerApi instance using mock data.
     */
    static SignalAnalyzerApi createMock() {
        return new SignalAnalyzerApiImpl(true);
    }

    // ==================== Connection Management ====================

    /**
     * Gets the list of available serial ports.
     *
     * @return AvailablePorts containing port names and metadata.
     */
    AvailablePorts getAvailablePorts();

    /**
     * Refreshes the list of available serial ports.
     * Useful after plugging/unplugging devices.
     *
     * @return Updated AvailablePorts.
     */
    AvailablePorts refreshPorts();

    /**
     * Connects to a device on the specified port.
     * <p>
     * This method performs handshake validation and will throw
     * if the device does not respond correctly.
     * </p>
     *
     * @param portName The system port name (e.g., "COM3", "/dev/ttyACM0").
     * @throws pl.polsl.rtsa.api.exception.ConnectionException if connection fails.
     */
    void connect(String portName);

    /**
     * Disconnects from the currently connected device.
     * Safe to call even if not connected.
     */
    void disconnect();

    /**
     * Gets the current connection status.
     *
     * @return ConnectionStatus with connection details.
     */
    ConnectionStatus getConnectionStatus();

    /**
     * Checks if a device is currently connected.
     *
     * @return true if connected, false otherwise.
     */
    boolean isConnected();

    // ==================== Acquisition Control ====================

    /**
     * Starts data acquisition.
     * Must be connected first.
     *
     * @throws pl.polsl.rtsa.api.exception.DeviceException if not connected or command fails.
     */
    void startAcquisition();

    /**
     * Stops data acquisition.
     * Safe to call even if not acquiring.
     */
    void stopAcquisition();

    /**
     * Checks if acquisition is currently active.
     *
     * @return true if acquiring data, false otherwise.
     */
    boolean isAcquiring();

    /**
     * Gets the current acquisition configuration.
     *
     * @return AcquisitionConfig with current settings.
     */
    AcquisitionConfig getAcquisitionConfig();

    // ==================== Sample Rate Control ====================

    /**
     * Sets the sample rate to 1 kHz.
     */
    void setSampleRate1kHz();

    /**
     * Sets the sample rate to 10 kHz.
     */
    void setSampleRate10kHz();

    /**
     * Sets the sample rate to 20 kHz (Turbo Mode).
     * <p>
     * Note: Turbo mode may have reduced impedance tolerance.
     * </p>
     */
    void setSampleRate20kHz();

    /**
     * Gets the current sample rate.
     *
     * @return Sample rate in Hz.
     */
    double getCurrentSampleRate();

    // ==================== Data Callbacks ====================

    /**
     * Sets the callback for receiving signal data updates.
     * <p>
     * The callback is invoked on a background thread. Use
     * {@code Platform.runLater()} for UI updates.
     * </p>
     *
     * @param callback Consumer that receives SignalData updates, or null to clear.
     */
    void setDataCallback(Consumer<SignalData> callback);

    /**
     * Sets the callback for receiving error notifications.
     * <p>
     * The callback is invoked on a background thread.
     * </p>
     *
     * @param callback Consumer that receives error messages, or null to clear.
     */
    void setErrorCallback(Consumer<String> callback);

    /**
     * Sets the callback for connection state changes.
     *
     * @param callback Consumer that receives ConnectionStatus updates, or null to clear.
     */
    void setConnectionCallback(Consumer<ConnectionStatus> callback);

    // ==================== Signal Processing ====================

    /**
     * Gets the last received signal data.
     * Returns null if no data has been received yet.
     *
     * @return The most recent SignalData, or null.
     */
    SignalData getLastSignalData();

    /**
     * Computes FFT for the given voltage data.
     * Useful for processing external data.
     *
     * @param voltageData Time-domain voltage samples.
     * @param sampleRate  Sample rate used to capture the data.
     * @return FFT magnitude spectrum.
     */
    double[] computeFFT(double[] voltageData, double sampleRate);

    /**
     * Converts raw ADC values to voltage.
     *
     * @param rawData Raw 10-bit ADC values (0-1023).
     * @return Voltage values (0.0-5.0V).
     */
    double[] convertToVoltage(int[] rawData);

    /**
     * Computes signal statistics for the given data.
     *
     * @param voltageData Time-domain voltage samples.
     * @param freqData    Frequency-domain magnitude data.
     * @param sampleRate  Sample rate in Hz.
     * @return Computed SignalStatistics.
     */
    SignalStatistics computeStatistics(double[] voltageData, double[] freqData, double sampleRate);

    // ==================== Lifecycle ====================

    /**
     * Shuts down the API and releases all resources.
     * <p>
     * This method should be called when the application is closing.
     * It stops acquisition, disconnects, and shuts down all background threads.
     * </p>
     */
    void shutdown();

    /**
     * Checks if the API has been shut down.
     *
     * @return true if shutdown() has been called.
     */
    boolean isShutdown();
}
