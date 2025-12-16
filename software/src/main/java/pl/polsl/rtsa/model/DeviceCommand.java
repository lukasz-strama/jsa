package pl.polsl.rtsa.model;

/**
 * Represents the commands that can be sent to the hardware device.
 * <p>
 * These commands control the acquisition state and sampling parameters
 * of the connected signal analyzer.
 * </p>
 */
public enum DeviceCommand {
    /** Starts the data acquisition process. */
    START_ACQUISITION,
    
    /** Stops the data acquisition process. */
    STOP_ACQUISITION,
    
    /** Sets the sampling rate to 1 kHz. */
    SET_RATE_1KHZ,
    
    /** Sets the sampling rate to 10 kHz. */
    SET_RATE_10KHZ,
    
    /** Sets the sampling rate to 20 kHz. */
    SET_RATE_20KHZ
}
