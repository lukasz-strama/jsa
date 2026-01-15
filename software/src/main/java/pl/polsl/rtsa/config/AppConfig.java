package pl.polsl.rtsa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Singleton configuration manager for the application.
 * <p>
 * Loads settings from 'config.properties' on the classpath.
 * All values have sensible defaults if the file is missing.
 * </p>
 * 
 * <h2>Configuration Categories:</h2>
 * <ul>
 *   <li><b>serial.*</b> - UART communication settings</li>
 *   <li><b>dsp.*</b> - Digital signal processing settings</li>
 *   <li><b>app.*</b> - Application-level settings</li>
 * </ul>
 */
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "config.properties";
    private static final Properties properties = new Properties();
    private static final AppConfig INSTANCE = new AppConfig();

    private AppConfig() {
        loadProperties();
    }

    /**
     * Gets the singleton configuration instance.
     *
     * @return The AppConfig instance.
     */
    public static AppConfig getInstance() {
        return INSTANCE;
    }

    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                logger.warn("Configuration file '{}' not found. Using default values.", CONFIG_FILE);
                return;
            }
            properties.load(input);
            logger.info("Configuration loaded successfully from {}", CONFIG_FILE);
        } catch (IOException ex) {
            logger.error("Error loading configuration file", ex);
        }
    }

    // ==================== Serial Configuration ====================

    /**
     * Gets the UART baud rate.
     * Default: 2,000,000 (2 Mbps)
     */
    public int getBaudRate() {
        return getIntProperty("serial.baudrate", 2_000_000);
    }

    /**
     * Gets the serial read timeout in milliseconds.
     * Default: 2000ms
     */
    public int getReadTimeout() {
        return getIntProperty("serial.timeout.read", 2000);
    }

    /**
     * Gets the delay after connection for Arduino auto-reset.
     * Default: 2000ms
     */
    public int getAutoResetDelay() {
        return getIntProperty("serial.auto_reset_delay", 2000);
    }

    // ==================== DSP Configuration ====================

    /**
     * Gets the default sample rate in Hz.
     * Default: 1000 Hz
     */
    public int getSampleRate() {
        return getIntProperty("dsp.sample_rate", 1000);
    }

    /**
     * Gets the ring buffer size in samples.
     * Default: 32768
     */
    public int getBufferSize() {
        return getIntProperty("dsp.buffer_size", 32768);
    }

    /**
     * Gets the ADC reference voltage.
     * Default: 5.0V
     */
    public double getVRef() {
        return getDoubleProperty("dsp.v_ref", 5.0);
    }

    /**
     * Gets the ADC resolution (max value + 1).
     * Default: 1024 (10-bit ADC)
     */
    public int getAdcResolution() {
        return getIntProperty("dsp.adc_resolution", 1024);
    }

    /**
     * Gets the minimum FFT size.
     * Default: 65536
     */
    public int getMinFftSize() {
        return getIntProperty("dsp.fft_min_size", 65536);
    }

    /**
     * Gets the target UI refresh rate.
     * Default: 30 FPS
     */
    public int getTargetFps() {
        return getIntProperty("dsp.target_fps", 30);
    }

    // ==================== Application Configuration ====================

    /**
     * Gets whether to use mock device for testing.
     * Default: false
     */
    public boolean isUseMock() {
        return getBooleanProperty("app.use_mock", false);
    }

    /**
     * Gets the configured log level.
     * Default: INFO
     */
    public String getLogLevel() {
        return getStringProperty("app.log_level", "INFO");
    }

    // ==================== Property Accessors ====================

    /**
     * Gets an integer property with a default value.
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid format for key '{}': '{}'. Using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * Gets a double property with a default value.
     */
    public double getDoubleProperty(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid format for key '{}': '{}'. Using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * Gets a boolean property with a default value.
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value.trim());
        }
        return defaultValue;
    }

    /**
     * Gets a string property with a default value.
     */
    public String getStringProperty(String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? value.trim() : defaultValue;
    }

    /**
     * Gets all loaded properties for debugging.
     */
    public Properties getAllProperties() {
        return new Properties(properties);
    }

    @Override
    public String toString() {
        return String.format("AppConfig{baudRate=%d, sampleRate=%d, bufferSize=%d, vRef=%.1f}",
                getBaudRate(), getSampleRate(), getBufferSize(), getVRef());
    }
}
