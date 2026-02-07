package pl.polsl.rtsa.hardware;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.polsl.rtsa.config.AppConfig;
import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;
import pl.polsl.rtsa.service.SignalProcessingService;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Implementation of {@link DeviceClient} for the real hardware device via UART.
 * <p>
 * Handles serial communication, data buffering, and FFT processing.
 * </p>
 */
public class RealDeviceClient implements DeviceClient {

    private static final Logger logger = LoggerFactory.getLogger(RealDeviceClient.class);
    private final AppConfig config = AppConfig.getInstance();

    private SerialPort serialPort;
    private final List<DataListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread readerThread;
    private double currentSampleRate;
    private final SignalProcessingService dspService = new SignalProcessingService();

    // Processing Executor to prevent blocking the reader thread
    private final ExecutorService processingExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DSP-Worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    // Ring Buffer Fields
    private final int[] ringBuffer;
    private int headIndex = 0;
    private int samplesPerFrame;
    private int samplesSinceLastUpdate = 0;

    public RealDeviceClient() {
        this.currentSampleRate = config.getSampleRate();
        this.ringBuffer = new int[config.getBufferSize()];
        recalculateSamplesPerFrame();
    }

    /**
     * Recalculates {@link #samplesPerFrame} so that UI updates happen at ~30 FPS
     * for the current sample rate.
     */
    private void recalculateSamplesPerFrame() {
        // Target 30 FPS
        this.samplesPerFrame = (int) (currentSampleRate / 30.0);
        if (this.samplesPerFrame < 1) {
            this.samplesPerFrame = 1;
        }
        logger.debug("Samples per frame updated to: {} (Rate: {})", samplesPerFrame, currentSampleRate);
    }

    @Override
    public boolean connect(String portName) {
        logger.info("Connecting to port: {}", portName);
        try {
            serialPort = SerialPort.getCommPort(portName);
            serialPort.setBaudRate(config.getBaudRate());
            serialPort.setNumDataBits(8);
            serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
            serialPort.setParity(SerialPort.NO_PARITY);
            serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, config.getReadTimeout(), 0);

            if (!serialPort.openPort()) {
                String msg = "Failed to open serial port: " + portName;
                logger.error(msg);
                notifyError(msg);
                return false;
            }

            // Allow Arduino to reset
            try {
                logger.debug("Waiting {}ms for device reset...", config.getAutoResetDelay());
                Thread.sleep(config.getAutoResetDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Reset wait interrupted");
            }

            if (!performHandshake()) {
                serialPort.closePort();
                String msg = "Handshake failed with device on " + portName;
                logger.error(msg);
                notifyError(msg);
                return false;
            }

            logger.info("Connected successfully to {}", portName);
            startReading();
            return true;

        } catch (SerialPortInvalidPortException e) {
            String msg = "Invalid Port: " + e.getMessage();
            logger.error(msg, e);
            notifyError(msg);
            return false;
        } catch (Exception e) {
            String msg = "Connection Error: " + e.getMessage();
            logger.error(msg, e);
            notifyError(msg);
            return false;
        }
    }

    /**
     * Performs a handshake with the device to verify protocol compatibility.
     * Sends '?' and expects "OSC_V1".
     *
     * @return true if handshake succeeds.
     */
    private boolean performHandshake() {
        logger.debug("Performing handshake...");
        try {
            while (serialPort.bytesAvailable() > 0) {
                byte[] sink = new byte[serialPort.bytesAvailable()];
                serialPort.readBytes(sink, sink.length);
            }

            byte[] cmd = new byte[] { 0x3F };
            serialPort.writeBytes(cmd, 1);

            byte[] buffer = new byte[32];
            int len = serialPort.readBytes(buffer, buffer.length);

            if (len > 0) {
                String response = new String(buffer, 0, len);
                logger.debug("Handshake response: {}", response);
                if (response.contains("OSC_V1")) {
                    return true;
                } else {
                    logger.warn("Handshake mismatch. Received: {}", response);
                    notifyError("Handshake mismatch. Received: " + response);
                    return false;
                }
            }
            logger.warn("Handshake timeout. No response.");
            notifyError("Handshake timeout. No response.");
            return false;

        } catch (Exception e) {
            logger.error("Handshake exception", e);
            return false;
        }
    }

    @Override
    public void disconnect() {
        logger.info("Disconnecting...");
        running.set(false);

        // Close serial port FIRST to unblock any blocking native read
        if (serialPort != null && serialPort.isOpen()) {
            try {
                sendCommand(DeviceCommand.STOP_ACQUISITION);
            } catch (Exception e) {
                logger.debug("Error sending stop during disconnect: {}", e.getMessage());
            }
            serialPort.closePort();
        }

        // Now the reader thread should exit quickly since the port is closed
        if (readerThread != null) {
            readerThread.interrupt();
            try {
                readerThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown executor and wait briefly
        processingExecutor.shutdownNow();
        try {
            processingExecutor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Disconnected");
    }

    @Override
    public void sendCommand(DeviceCommand cmd) {
        if (serialPort == null || !serialPort.isOpen())
            return;

        logger.debug("Sending command: {}", cmd);
        byte byteCmd = 0;
        switch (cmd) {
            case START_ACQUISITION -> byteCmd = 0x01;
            case STOP_ACQUISITION -> byteCmd = 0x02;
            case SET_RATE_1KHZ -> {
                byteCmd = 0x10;
                currentSampleRate = 1000.0;
                recalculateSamplesPerFrame();
            }
            case SET_RATE_10KHZ -> {
                byteCmd = 0x11;
                currentSampleRate = 10000.0;
                recalculateSamplesPerFrame();
            }
            case SET_RATE_20KHZ -> {
                byteCmd = 0x12;
                currentSampleRate = 20000.0;
                recalculateSamplesPerFrame();
            }
        }
        serialPort.writeBytes(new byte[] { byteCmd }, 1);
    }

    @Override
    public void addListener(DataListener listener) {
        listeners.add(listener);
    }

    @Override
    public List<String> getAvailablePorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName)
                .collect(Collectors.toList());
    }

    /**
     * Spawns the daemon reader thread that decodes the serial byte stream.
     */
    private void startReading() {
        running.set(true);
        readerThread = new Thread(this::readLoop, "Serial-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Main read loop — runs on the reader thread.
     * <p>
     * Decodes the 2-byte sync protocol (high byte bit 7 = 1, low byte bit 7 = 0)
     * into 10-bit ADC values and writes them into the ring buffer. Every
     * {@link #samplesPerFrame} samples, triggers a UI update.
     * </p>
     */
    private void readLoop() {
        int bufferSize = ringBuffer.length;
        InputStream in = serialPort.getInputStream();
        byte[] transferBuffer = new byte[2048];
        int highByte = -1;

        logger.debug("Starting optimized read loop with ring buffer size: {}", bufferSize);

        try {
            while (running.get()) {
                int available = in.available();
                if (available > 0) {
                    int bytesToRead = Math.min(available, transferBuffer.length);
                    int readCount = in.read(transferBuffer, 0, bytesToRead);

                    for (int i = 0; i < readCount; i++) {
                        int b = transferBuffer[i] & 0xFF;

                        if ((b & 0x80) != 0) {
                            // High Byte (Bit 7 set)
                            highByte = b;
                        } else {
                            // Low Byte (Bit 7 clear)
                            if (highByte != -1) {
                                int raw = ((highByte & 0x07) << 7) | (b & 0x7F);

                                // Ring Buffer Logic
                                ringBuffer[headIndex] = raw;
                                headIndex = (headIndex + 1) % bufferSize;
                                samplesSinceLastUpdate++;

                                // FPS Limiter Logic
                                if (samplesSinceLastUpdate >= samplesPerFrame) {
                                    updateUI();
                                    samplesSinceLastUpdate = 0;
                                }
                                highByte = -1; // Reset
                            }
                        }
                    }
                } else {
                    Thread.sleep(1);
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                String msg = "Read Error: " + e.getMessage();
                logger.error(msg, e);
                notifyError(msg);
            }
        }
    }

    /**
     * Snapshots the most recent samples from the ring buffer and submits
     * them to the DSP executor for voltage conversion, FFT, and listener
     * dispatch. Skipped if a previous DSP job is still running.
     */
    private void updateUI() {
        // If DSP is busy, skip this frame to avoid backpressure on the reader thread
        if (isProcessing.get()) {
            return;
        }

        // Determine processing window size (Target ~1.5s of history)
        // 1kHz -> 1500, 10kHz -> 15000, 20kHz -> 30000
        int targetHistory = (int) (currentSampleRate * 1.5);
        int len = Math.min(targetHistory, ringBuffer.length);
        // Ensure minimum size for stability
        len = Math.max(len, 1024);

        int[] processingBuffer = new int[len];

        // Extract the LAST 'len' samples from the ring buffer
        // The newest sample is at (headIndex - 1)
        int startIndex = (headIndex - len);
        if (startIndex < 0) {
            startIndex += ringBuffer.length;
        }

        // Copy first chunk (startIndex -> end of array)
        int firstChunkLen = Math.min(len, ringBuffer.length - startIndex);
        System.arraycopy(ringBuffer, startIndex, processingBuffer, 0, firstChunkLen);

        // Copy second chunk (start of array -> rest) if wrapped
        if (firstChunkLen < len) {
            int secondChunkLen = len - firstChunkLen;
            System.arraycopy(ringBuffer, 0, processingBuffer, firstChunkLen, secondChunkLen);
        }

        // Offload processing to separate thread
        isProcessing.set(true);
        processingExecutor.submit(() -> {
            try {
                processBuffer(processingBuffer);
            } finally {
                isProcessing.set(false);
            }
        });
    }

    /**
     * Converts raw ADC data to voltage, computes the FFT, and notifies
     * all registered listeners with a {@link SignalResult}.
     *
     * @param rawData the raw 10-bit ADC values
     */
    private void processBuffer(int[] rawData) {
        // 1. Convert to Voltage
        double[] voltageData = dspService.convertToVoltage(rawData);

        // 2. Compute FFT (includes Windowing)
        double[] fftData = dspService.computeFFT(voltageData);

        // 3. Create Result
        SignalResult result = new SignalResult(
                voltageData,
                fftData,
                currentSampleRate);

        // 4. Notify Listeners
        notifyListeners(result);
    }

    /**
     * Dispatches a {@link SignalResult} to all registered listeners.
     *
     * @param result the processed signal result
     */
    private void notifyListeners(SignalResult result) {
        for (DataListener l : listeners) {
            l.onNewData(result);
        }
    }

    /**
     * Dispatches an error message to all registered listeners.
     *
     * @param msg the error description
     */
    private void notifyError(String msg) {
        for (DataListener l : listeners) {
            l.onError(msg);
        }
    }
}
