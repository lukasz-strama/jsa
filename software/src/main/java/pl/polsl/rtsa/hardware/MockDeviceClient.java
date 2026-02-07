package pl.polsl.rtsa.hardware;

import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;
import pl.polsl.rtsa.service.SignalProcessingService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock implementation of {@link DeviceClient} for off-line testing.
 * <p>
 * Generates a synthetic 50 Hz sine wave (2.5 V DC offset, 2.0 Vpp)
 * with a small amount of additive white noise. Data is produced in
 * 1024-sample batches at the currently configured sample rate.
 * </p>
 */
public class MockDeviceClient implements DeviceClient {

    /** Registered data/error listeners. */
    private final List<DataListener> listeners = new CopyOnWriteArrayList<>();

    /** Flag indicating whether the generator thread is active. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** DSP service used for FFT computation on the generated data. */
    private final SignalProcessingService dspService = new SignalProcessingService();

    /** Background thread that produces synthetic samples. */
    private Thread generatorThread;

    /** Current sample rate in Hz. */
    private volatile double sampleRate = 1000.0;

    /**
     * {@inheritDoc}
     * <p>
     * Always succeeds — prints a diagnostic message to stdout.
     * </p>
     */
    @Override
    public boolean connect(String port) {
        System.out.println("[Mock] Connected to " + port);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void disconnect() {
        running.set(false);
        if (generatorThread != null) {
            try {
                generatorThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("[Mock] Disconnected");
    }

    /** {@inheritDoc} */
    @Override
    public void sendCommand(DeviceCommand cmd) {
        System.out.println("[Mock] Command received: " + cmd);
        switch (cmd) {
            case START_ACQUISITION -> startGenerating();
            case STOP_ACQUISITION -> running.set(false);
            case SET_RATE_1KHZ -> sampleRate = 1000.0;
            case SET_RATE_10KHZ -> sampleRate = 10000.0;
            case SET_RATE_20KHZ -> sampleRate = 20000.0;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void addListener(DataListener listener) {
        listeners.add(listener);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns two hard-coded mock port names.
     * </p>
     */
    @Override
    public List<String> getAvailablePorts() {
        return List.of("MOCK_PORT_1", "MOCK_PORT_2");
    }

    /**
     * Spawns a background thread that produces synthetic 50 Hz sine-wave
     * batches with additive noise and dispatches them to all listeners.
     */
    private void startGenerating() {
        if (running.get())
            return;
        running.set(true);
        generatorThread = new Thread(() -> {
            double t = 0;
            while (running.get()) {
                try {
                    // Capture current sample rate for this iteration
                    double currentRate = sampleRate;

                    // Simulate buffer filling time (approx)
                    long sleepTime = (long) ((1024.0 / currentRate) * 1000);
                    Thread.sleep(Math.max(10, sleepTime));

                    double[] timeData = new double[1024];
                    for (int i = 0; i < 1024; i++) {
                        // Generate 50Hz sine wave + noise
                        timeData[i] = 2.5 + 2.0 * Math.sin(2 * Math.PI * 50 * t) + (Math.random() - 0.5) * 0.2;
                        t += 1.0 / currentRate;
                    }

                    // Compute real FFT from time-domain data
                    double[] freqData = dspService.computeFFT(timeData);

                    SignalResult result = new SignalResult(timeData, freqData, currentRate);
                    for (DataListener l : listeners) {
                        l.onNewData(result);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Mock-Generator");
        generatorThread.start();
    }
}
