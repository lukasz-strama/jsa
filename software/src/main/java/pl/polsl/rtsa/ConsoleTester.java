package pl.polsl.rtsa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.polsl.rtsa.hardware.DataListener;
import pl.polsl.rtsa.hardware.DeviceClient;
import pl.polsl.rtsa.hardware.RealDeviceClient;
import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;
import pl.polsl.rtsa.service.SignalProcessingService;

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
    private static final SignalProcessingService dspService = new SignalProcessingService();

    /**
     * Main entry point for the diagnostic tool.
     *
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        logger.info("=== JSignalAnalysis Diagnostic Tool ===");

        String targetPort = null;
        int targetFreq = 1000;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (("-p".equals(arg) || "--port".equals(arg)) && i + 1 < args.length) {
                targetPort = args[++i];
            } else if (("-f".equals(arg) || "--freq".equals(arg)) && i + 1 < args.length) {
                try {
                    targetFreq = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    logger.error("Invalid frequency format: {}", args[i]);
                    return;
                }
            }
        }

        DeviceClient client = new RealDeviceClient();

        client.addListener(new DataListener() {
            @Override
            public void onNewData(SignalResult result) {
                double rms = dspService.calculateRMS(result.timeDomainData());
                
                // Calculate Dominant Frequency
                double[] freqData = result.freqDomainData();
                int maxIndex = 0;
                double maxVal = -1.0;
                
                // Ignore frequencies below 2.0 Hz to avoid DC leakage and 1/f noise
                // Bin Width = SampleRate / (2 * freqData.length)
                // Index = Freq / BinWidth
                int startIndex = (int) (2.0 * freqData.length / (result.sampleRate() / 2.0));
                if (startIndex < 1) startIndex = 1; // Always skip DC (0)

                for (int i = startIndex; i < freqData.length; i++) {
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
            if (targetPort == null) {
                List<String> ports = client.getAvailablePorts();
                logger.info("Available Ports: {}", ports);

                if (ports.isEmpty()) {
                    logger.warn("No serial ports found. Please check your connection.");
                    return;
                }
                targetPort = ports.get(0);
            }

            logger.info("Attempting connection to: {}", targetPort);

            if (client.connect(targetPort)) {
                logger.info("Connected successfully.");

                // Set Frequency
                DeviceCommand rateCmd;
                switch (targetFreq) {
                    case 10000 -> rateCmd = DeviceCommand.SET_RATE_10KHZ;
                    case 20000 -> rateCmd = DeviceCommand.SET_RATE_20KHZ;
                    default -> {
                        if (targetFreq != 1000) {
                            logger.warn("Unsupported frequency: {}Hz. Defaulting to 1000Hz.", targetFreq);
                        }
                        rateCmd = DeviceCommand.SET_RATE_1KHZ;
                    }
                }
                
                logger.info("Setting sample rate to {}Hz...", targetFreq);
                client.sendCommand(rateCmd);
                Thread.sleep(200); // Allow rate change to propagate

                logger.info("Sending START_ACQUISITION...");
                client.sendCommand(DeviceCommand.START_ACQUISITION);

                logger.info("Collecting data for 5 seconds...");
                Thread.sleep(5000);

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
        System.exit(0);
    }
}
