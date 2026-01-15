package pl.polsl.rtsa.hardware;

import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;
import pl.polsl.rtsa.service.SignalProcessingService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MockDeviceClient implements DeviceClient {

    private final List<DataListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final SignalProcessingService dspService = new SignalProcessingService();
    private Thread generatorThread;
    private volatile double sampleRate = 1000.0;

    @Override
    public boolean connect(String port) {
        System.out.println("[Mock] Connected to " + port);
        return true;
    }

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

    @Override
    public void addListener(DataListener listener) {
        listeners.add(listener);
    }

    @Override
    public List<String> getAvailablePorts() {
        return List.of("MOCK_PORT_1", "MOCK_PORT_2");
    }

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
