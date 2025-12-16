package pl.polsl.rtsa.hardware;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;
import pl.polsl.rtsa.service.SignalProcessingService;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Implementation of {@link DeviceClient} for the real hardware device via UART.
 * <p>
 * Handles serial communication, data buffering, and FFT processing.
 * </p>
 */
public class RealDeviceClient implements DeviceClient {

    private static final int BAUD_RATE = 2_000_000;
    private static final int BUFFER_SIZE = 1024;

    private SerialPort serialPort;
    private final List<DataListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread readerThread;
    private double currentSampleRate = 1000.0;
    private final SignalProcessingService dspService = new SignalProcessingService();

    @Override
    public boolean connect(String portName) {
        try {
            serialPort = SerialPort.getCommPort(portName);
            serialPort.setBaudRate(BAUD_RATE);
            serialPort.setNumDataBits(8);
            serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
            serialPort.setParity(SerialPort.NO_PARITY);
            serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 2000, 0);

            if (!serialPort.openPort()) {
                notifyError("Failed to open serial port.");
                return false;
            }

            // Allow Arduino to reset
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!performHandshake()) {
                serialPort.closePort();
                notifyError("Handshake failed.");
                return false;
            }

            startReading();
            return true;

        } catch (SerialPortInvalidPortException e) {
            notifyError("Invalid Port: " + e.getMessage());
            return false;
        } catch (Exception e) {
            notifyError("Connection Error: " + e.getMessage());
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
        try {
            while (serialPort.bytesAvailable() > 0) {
                byte[] sink = new byte[serialPort.bytesAvailable()];
                serialPort.readBytes(sink, sink.length);
            }

            byte[] cmd = new byte[]{0x3F};
            serialPort.writeBytes(cmd, 1);

            byte[] buffer = new byte[32];
            int len = serialPort.readBytes(buffer, buffer.length);
            
            if (len > 0) {
                String response = new String(buffer, 0, len);
                if (response.contains("OSC_V1")) {
                    return true;
                } else {
                    notifyError("Handshake mismatch. Received: " + response);
                    return false;
                }
            }
            notifyError("Handshake timeout. No response.");
            return false;

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void disconnect() {
        running.set(false);
        if (readerThread != null) {
            try {
                readerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (serialPort != null && serialPort.isOpen()) {
            sendCommand(DeviceCommand.STOP_ACQUISITION);
            serialPort.closePort();
        }
    }

    @Override
    public void sendCommand(DeviceCommand cmd) {
        if (serialPort == null || !serialPort.isOpen()) return;

        byte byteCmd = 0;
        switch (cmd) {
            case START_ACQUISITION -> byteCmd = 0x01;
            case STOP_ACQUISITION -> byteCmd = 0x02;
            case SET_RATE_1KHZ -> {
                byteCmd = 0x10;
                currentSampleRate = 1000.0;
            }
            case SET_RATE_10KHZ -> {
                byteCmd = 0x11;
                currentSampleRate = 10000.0;
            }
            case SET_RATE_20KHZ -> {
                byteCmd = 0x12;
                currentSampleRate = 20000.0;
            }
        }
        serialPort.writeBytes(new byte[]{byteCmd}, 1);
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

    private void startReading() {
        running.set(true);
        readerThread = new Thread(this::readLoop, "Serial-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        int[] rawBuffer = new int[BUFFER_SIZE];
        int sampleIndex = 0;
        InputStream in = serialPort.getInputStream();

        try {
            while (running.get()) {
                if (in.available() >= 2) {
                    int high = in.read();
                    if ((high & 0x80) == 0) {
                        continue; // Lost sync
                    }

                    int low = in.read();
                    if (low == -1) break;

                    int raw = ((high & 0x07) << 7) | (low & 0x7F);
                    rawBuffer[sampleIndex++] = raw;

                    if (sampleIndex >= BUFFER_SIZE) {
                        processBuffer(rawBuffer);
                        sampleIndex = 0;
                    }
                } else {
                    Thread.sleep(1);
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                notifyError("Read Error: " + e.getMessage());
            }
        }
    }

    private void processBuffer(int[] rawData) {
        // 1. Convert to Voltage
        double[] voltageData = dspService.convertToVoltage(rawData);

        // 2. Compute FFT (includes Windowing)
        double[] fftData = dspService.computeFFT(voltageData);

        // 3. Create Result
        SignalResult result = new SignalResult(
            voltageData,
            fftData,
            currentSampleRate
        );

        // 4. Notify Listeners
        notifyListeners(result);
    }

    private void notifyListeners(SignalResult result) {
        for (DataListener l : listeners) {
            l.onNewData(result);
        }
    }

    private void notifyError(String msg) {
        for (DataListener l : listeners) {
            l.onError(msg);
        }
    }
}
