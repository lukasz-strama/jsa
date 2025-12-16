package pl.polsl.rtsa;

import pl.polsl.rtsa.hardware.DataListener;
import pl.polsl.rtsa.hardware.DeviceClient;
import pl.polsl.rtsa.hardware.RealDeviceClient;
import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A console diagnostic tool for the Signal Analysis hardware.
 * <p>
 * This tool verifies the connection, performs a handshake, and captures a brief
 * sample of data to ensure the backend logic is functioning correctly.
 * </p>
 */
public class ConsoleTester {

    /**
     * Main entry point for the diagnostic tool.
     *
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        ConsoleLogger.info("=== JSignalAnalysis Diagnostic Tool ===");

        DeviceClient client = new RealDeviceClient();
        //DeviceClient client = new MockDeviceClient(); // For testing without hardware

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

                ConsoleLogger.success(String.format("RECEIVED | RMS: %.4f V | Main Freq: %.1f Hz | Rate: %.0f Hz",
                        rms,
                        dominantFreq,
                        result.sampleRate()));
            }

            @Override
            public void onError(String message) {
                ConsoleLogger.error("DEVICE ERROR: " + message);
            }
        });

        try {
            List<String> ports = client.getAvailablePorts();
            ConsoleLogger.info("Available Ports: " + ports);

            if (ports.isEmpty()) {
                ConsoleLogger.warn("No serial ports found. Please check your connection.");
                return;
            }

            String targetPort = ports.get(0);
            ConsoleLogger.info("Attempting connection to: " + targetPort);

            if (client.connect(targetPort)) {
                ConsoleLogger.success("Connected successfully.");

                ConsoleLogger.info("Sending START_ACQUISITION...");
                client.sendCommand(DeviceCommand.START_ACQUISITION);

                ConsoleLogger.info("Collecting data for 3 seconds...");
                Thread.sleep(3000);

                ConsoleLogger.info("Sending STOP_ACQUISITION...");
                client.sendCommand(DeviceCommand.STOP_ACQUISITION);
                
                Thread.sleep(500); // Allow command to process

                ConsoleLogger.info("Disconnecting...");
                client.disconnect();
                ConsoleLogger.success("Disconnected cleanly.");
            } else {
                ConsoleLogger.error("Failed to connect to " + targetPort);
                ConsoleLogger.warn("Troubleshooting Tips:");
                ConsoleLogger.warn("1. Check if the device is plugged in.");
                ConsoleLogger.warn("2. Verify you have read/write permissions (e.g., sudo chmod 666 /dev/ttyACM0).");
                ConsoleLogger.warn("3. Ensure no other application is using the port.");
            }

        } catch (InterruptedException e) {
            ConsoleLogger.error("Test interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            ConsoleLogger.error("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }

        ConsoleLogger.info("=== Test Finished ===");
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

    /**
     * Helper class for formatted console logging with ANSI colors.
     */
    private static class ConsoleLogger {
        private static final String RESET = "\u001B[0m";
        private static final String RED = "\u001B[31m";
        private static final String GREEN = "\u001B[32m";
        private static final String YELLOW = "\u001B[33m";
        private static final String CYAN = "\u001B[36m";

        private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

        private static void log(String level, String color, String message) {
            String time = LocalTime.now().format(TIME_FORMAT);
            System.out.printf("%s[%s] [%s] %s%s%n", color, time, level, message, RESET);
        }

        public static void info(String message) {
            log("INFO", CYAN, message);
        }

        public static void success(String message) {
            log("SUCCESS", GREEN, message);
        }

        public static void warn(String message) {
            log("WARN", YELLOW, message);
        }

        public static void error(String message) {
            log("ERROR", RED, message);
        }
    }
}
