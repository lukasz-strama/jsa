package pl.polsl.rtsa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.polsl.rtsa.hardware.DataListener;
import pl.polsl.rtsa.hardware.DeviceClient;
import pl.polsl.rtsa.hardware.RealDeviceClient;
import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;

import java.util.List;

/**
 * A console diagnostic tool for the Signal Analysis hardware.
 * <p>
 * This tool verifies the connection, performs a handshake, and captures a brief
 * sample of data to ensure the backend logic is functioning correctly.
 * </p>
 */
public class ConsoleTester {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleTester.class);

    /**
     * Main entry point for the diagnostic tool.
     *
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        logger.info("=== JSignalAnalysis Diagnostic Tool ===");

        DeviceClient client = new RealDeviceClient();

        client.addListener(new DataListener() {
            @Override
            public void onNewData(SignalResult result) {
                double rms = calculateRMS(result.timeDomainData());
                
                // Calculate Dominant Frequency
                double[] freqData = result.freqDomainData();
                int maxIndex = 0;
                double maxVal = -1.0;
                // Skip DC component at index 0
                for (int i = 1; i < freqData.length; i++) {
                    if (freqData[i] > maxVal) {
                        maxVal = freqData[i];
                        maxIndex = i;
                    }
                }
                
                // Calculate Frequency: index * (SampleRate / 2) / arrayLength
                double dominantFreq = maxIndex * (result.sampleRate() / 2.0) / freqData.length;

                logger.info(String.format("RECEIVED | RMS: %.4f V | Main Freq: %.1f Hz | Rate: %.0f Hz",
                        rms,
                        dominantFreq,
                        result.sampleRate()));
            }

            @Override
            public void onError(String message) {
                logger.error("DEVICE ERROR: {}", message);
            }
        });

        try {
            List<String> ports = client.getAvailablePorts();
            logger.info("Available Ports: {}", ports);

            if (ports.isEmpty()) {
                logger.warn("No serial ports found. Please check your connection.");
                return;
            }

            String targetPort = ports.get(0);
            logger.info("Attempting connection to: {}", targetPort);

            if (client.connect(targetPort)) {
                logger.info("Connected successfully.");

                logger.info("Sending START_ACQUISITION...");
                client.sendCommand(DeviceCommand.START_ACQUISITION);

                logger.info("Collecting data for 3 seconds...");
                Thread.sleep(3000);

                logger.info("Sending STOP_ACQUISITION...");
                client.sendCommand(DeviceCommand.STOP_ACQUISITION);
                
                Thread.sleep(500); // Allow command to process

                logger.info("Disconnecting...");
                client.disconnect();
                logger.info("Disconnected cleanly.");
            } else {
                logger.error("Failed to connect to {}", targetPort);
                logger.warn("Troubleshooting Tips:");
                logger.warn("1. Check if the device is plugged in.");
                logger.warn("2. Verify you have read/write permissions (e.g., sudo chmod 666 /dev/ttyACM0).");
                logger.warn("3. Ensure no other application is using the port.");
            }

        } catch (InterruptedException e) {
            logger.error("Test interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }

        logger.info("=== Test Finished ===");
    }

    /**
     * Calculates the Root Mean Square (RMS) of the signal data.
     *
     * @param data The time-domain signal data.
     * @return The calculated RMS value.
     */
    private static double calculateRMS(double[] data) {
        if (data == null || data.length == 0) return 0.0;
        double sum = 0;
        for (double v : data) {
            sum += v * v;
        }
        return Math.sqrt(sum / data.length);
    }
}
