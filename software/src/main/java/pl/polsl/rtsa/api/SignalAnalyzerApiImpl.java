package pl.polsl.rtsa.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.polsl.rtsa.api.dto.*;
import pl.polsl.rtsa.api.exception.ConnectionException;
import pl.polsl.rtsa.api.exception.DeviceException;
import pl.polsl.rtsa.config.AppConfig;
import pl.polsl.rtsa.hardware.*;
import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;
import pl.polsl.rtsa.service.SignalProcessingService;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Production implementation of the SignalAnalyzerApi.
 * <p>
 * This class bridges the low-level hardware communication with the high-level
 * API used by the JavaFX frontend. It handles:
 * <ul>
 *   <li>Connection lifecycle management</li>
 *   <li>State tracking and synchronization</li>
 *   <li>Data transformation from raw to DTOs</li>
 *   <li>Callback dispatch to UI layer</li>
 * </ul>
 * </p>
 * 
 * <h2>Thread Safety:</h2>
 * This implementation is fully thread-safe. All state is protected by
 * atomic references and the underlying DeviceClient handles its own threading.
 */
public class SignalAnalyzerApiImpl implements SignalAnalyzerApi, DataListener {

    private static final Logger logger = LoggerFactory.getLogger(SignalAnalyzerApiImpl.class);

    // Core dependencies
    private final DeviceClient deviceClient;
    private final SignalProcessingService processingService;
    private final AppConfig config;

    // State tracking (thread-safe)
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean acquiring = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicReference<String> connectedPort = new AtomicReference<>(null);
    private final AtomicReference<String> deviceInfo = new AtomicReference<>(null);
    private final AtomicReference<Double> currentSampleRate = new AtomicReference<>(1000.0);
    private final AtomicReference<SignalData> lastSignalData = new AtomicReference<>(null);

    // Callbacks (thread-safe)
    private final AtomicReference<Consumer<SignalData>> dataCallback = new AtomicReference<>(null);
    private final AtomicReference<Consumer<String>> errorCallback = new AtomicReference<>(null);
    private final AtomicReference<Consumer<ConnectionStatus>> connectionCallback = new AtomicReference<>(null);

    // Cached port list
    private final AtomicReference<AvailablePorts> cachedPorts = new AtomicReference<>(AvailablePorts.empty());

    /**
     * Creates a new API implementation.
     *
     * @param useMock If true, uses MockDeviceClient for testing.
     */
    public SignalAnalyzerApiImpl(boolean useMock) {
        this.config = AppConfig.getInstance();
        this.processingService = new SignalProcessingService(config);
        this.deviceClient = useMock ? new MockDeviceClient() : new RealDeviceClient();
        this.deviceClient.addListener(this);
        
        logger.info("SignalAnalyzerApi initialized (mock={})", useMock);
    }

    // ==================== Connection Management ====================

    @Override
    public AvailablePorts getAvailablePorts() {
        AvailablePorts cached = cachedPorts.get();
        // Refresh if cache is older than 5 seconds
        if (System.currentTimeMillis() - cached.lastRefreshTime() > 5000) {
            return refreshPorts();
        }
        return cached;
    }

    @Override
    public AvailablePorts refreshPorts() {
        checkNotShutdown();
        List<String> ports = deviceClient.getAvailablePorts();
        AvailablePorts newPorts = new AvailablePorts(ports, System.currentTimeMillis());
        cachedPorts.set(newPorts);
        logger.debug("Refreshed port list: {}", ports);
        return newPorts;
    }

    @Override
    public void connect(String portName) {
        checkNotShutdown();
        
        if (connected.get()) {
            logger.warn("Already connected. Disconnecting first.");
            disconnect();
        }

        if (portName == null || portName.isBlank()) {
            throw new ConnectionException(DeviceException.ErrorCode.INVALID_PORT, 
                    "Port name cannot be null or empty");
        }

        logger.info("Connecting to port: {}", portName);
        
        boolean success = deviceClient.connect(portName);
        
        if (!success) {
            throw new ConnectionException("Failed to connect to port: " + portName);
        }

        connected.set(true);
        connectedPort.set(portName);
        deviceInfo.set("OSC_V1"); // Set from handshake
        
        logger.info("Successfully connected to {}", portName);
        notifyConnectionChange();
    }

    @Override
    public void disconnect() {
        if (!connected.get()) {
            logger.debug("Disconnect called but not connected");
            return;
        }

        logger.info("Disconnecting...");
        
        // Stop acquisition first
        if (acquiring.get()) {
            stopAcquisition();
        }

        deviceClient.disconnect();
        
        connected.set(false);
        connectedPort.set(null);
        deviceInfo.set(null);
        
        logger.info("Disconnected");
        notifyConnectionChange();
    }

    @Override
    public ConnectionStatus getConnectionStatus() {
        if (connected.get()) {
            return ConnectionStatus.connected(connectedPort.get(), deviceInfo.get());
        }
        return ConnectionStatus.disconnected();
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    // ==================== Acquisition Control ====================

    @Override
    public void startAcquisition() {
        checkNotShutdown();
        checkConnected();

        if (acquiring.get()) {
            logger.warn("Acquisition already active");
            return;
        }

        logger.info("Starting acquisition at {} Hz", currentSampleRate.get());
        deviceClient.sendCommand(DeviceCommand.START_ACQUISITION);
        acquiring.set(true);
    }

    @Override
    public void stopAcquisition() {
        if (!acquiring.get()) {
            logger.debug("Stop acquisition called but not acquiring");
            return;
        }

        logger.info("Stopping acquisition");
        deviceClient.sendCommand(DeviceCommand.STOP_ACQUISITION);
        acquiring.set(false);
    }

    @Override
    public boolean isAcquiring() {
        return acquiring.get();
    }

    @Override
    public AcquisitionConfig getAcquisitionConfig() {
        return new AcquisitionConfig(
                currentSampleRate.get(),
                config.getBufferSize(),
                acquiring.get()
        );
    }

    // ==================== Sample Rate Control ====================

    @Override
    public void setSampleRate1kHz() {
        setSampleRate(DeviceCommand.SET_RATE_1KHZ, AcquisitionConfig.RATE_1KHZ);
    }

    @Override
    public void setSampleRate10kHz() {
        setSampleRate(DeviceCommand.SET_RATE_10KHZ, AcquisitionConfig.RATE_10KHZ);
    }

    @Override
    public void setSampleRate20kHz() {
        setSampleRate(DeviceCommand.SET_RATE_20KHZ, AcquisitionConfig.RATE_20KHZ);
    }

    private void setSampleRate(DeviceCommand command, double rate) {
        checkNotShutdown();
        
        if (connected.get()) {
            deviceClient.sendCommand(command);
        }
        
        currentSampleRate.set(rate);
        logger.info("Sample rate set to {} Hz", rate);
    }

    @Override
    public double getCurrentSampleRate() {
        return currentSampleRate.get();
    }

    // ==================== Data Callbacks ====================

    @Override
    public void setDataCallback(Consumer<SignalData> callback) {
        this.dataCallback.set(callback);
    }

    @Override
    public void setErrorCallback(Consumer<String> callback) {
        this.errorCallback.set(callback);
    }

    @Override
    public void setConnectionCallback(Consumer<ConnectionStatus> callback) {
        this.connectionCallback.set(callback);
    }

    // ==================== Signal Processing ====================

    @Override
    public SignalData getLastSignalData() {
        return lastSignalData.get();
    }

    @Override
    public double[] computeFFT(double[] voltageData, double sampleRate) {
        return processingService.computeFFT(voltageData);
    }

    @Override
    public double[] convertToVoltage(int[] rawData) {
        return processingService.convertToVoltage(rawData);
    }

    @Override
    public SignalStatistics computeStatistics(double[] voltageData, double[] freqData, double sampleRate) {
        return processingService.computeStatistics(voltageData, freqData, sampleRate);
    }

    // ==================== Lifecycle ====================

    @Override
    public void shutdown() {
        if (shutdown.getAndSet(true)) {
            logger.debug("Already shut down");
            return;
        }

        logger.info("Shutting down SignalAnalyzerApi...");
        
        try {
            disconnect();
        } catch (Exception e) {
            logger.warn("Error during shutdown disconnect: {}", e.getMessage());
        }

        // Clear callbacks
        dataCallback.set(null);
        errorCallback.set(null);
        connectionCallback.set(null);

        logger.info("SignalAnalyzerApi shut down complete");
    }

    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    // ==================== DataListener Implementation ====================

    @Override
    public void onNewData(SignalResult result) {
        if (shutdown.get()) {
            return;
        }

        // Transform SignalResult to SignalData with statistics
        SignalStatistics stats = processingService.computeStatistics(
                result.timeDomainData(),
                result.freqDomainData(),
                result.sampleRate()
        );

        SignalData signalData = new SignalData(
                Instant.now(),
                result.timeDomainData(),
                result.freqDomainData(),
                result.sampleRate(),
                stats
        );

        lastSignalData.set(signalData);

        // Dispatch to callback
        Consumer<SignalData> callback = dataCallback.get();
        if (callback != null) {
            try {
                callback.accept(signalData);
            } catch (Exception e) {
                logger.error("Error in data callback", e);
            }
        }
    }

    @Override
    public void onError(String message) {
        logger.error("Device error: {}", message);

        Consumer<String> callback = errorCallback.get();
        if (callback != null) {
            try {
                callback.accept(message);
            } catch (Exception e) {
                logger.error("Error in error callback", e);
            }
        }
    }

    // ==================== Helper Methods ====================

    private void checkNotShutdown() {
        if (shutdown.get()) {
            throw new IllegalStateException("API has been shut down");
        }
    }

    private void checkConnected() {
        if (!connected.get()) {
            throw new DeviceException(DeviceException.ErrorCode.NOT_CONNECTED,
                    "No device connected. Call connect() first.");
        }
    }

    private void notifyConnectionChange() {
        Consumer<ConnectionStatus> callback = connectionCallback.get();
        if (callback != null) {
            try {
                callback.accept(getConnectionStatus());
            } catch (Exception e) {
                logger.error("Error in connection callback", e);
            }
        }
    }
}
