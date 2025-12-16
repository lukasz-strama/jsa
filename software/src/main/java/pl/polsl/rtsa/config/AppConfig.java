package pl.polsl.rtsa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Singleton configuration manager for the application.
 * Loads settings from 'config.properties' on the classpath.
 */
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final Properties properties = new Properties();
    private static final AppConfig INSTANCE = new AppConfig();

    private AppConfig() {
        loadProperties();
    }

    public static AppConfig getInstance() {
        return INSTANCE;
    }

    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                logger.warn("Sorry, unable to find config.properties. Using default values.");
                return;
            }
            properties.load(input);
            logger.info("Configuration loaded successfully.");
        } catch (IOException ex) {
            logger.error("Error loading configuration file", ex);
        }
    }

    public int getBaudRate() {
        return getIntProperty("serial.baudrate", 2_000_000);
    }

    public int getReadTimeout() {
        return getIntProperty("serial.timeout.read", 2000);
    }

    public int getAutoResetDelay() {
        return getIntProperty("serial.auto_reset_delay", 2000);
    }

    public int getSampleRate() {
        return getIntProperty("dsp.sample_rate", 1000);
    }

    public int getBufferSize() {
        return getIntProperty("dsp.buffer_size", 1024);
    }

    private int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid format for key '{}': {}. Using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }
}
