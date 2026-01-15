package pl.polsl.rtsa.api.dto;

/**
 * Data Transfer Object representing signal acquisition parameters.
 * Immutable record for thread-safe access.
 *
 * @param sampleRate         Current sampling rate in Hz.
 * @param bufferSize         Number of samples per buffer.
 * @param acquisitionActive  Whether data acquisition is currently running.
 */
public record AcquisitionConfig(
        double sampleRate,
        int bufferSize,
        boolean acquisitionActive
) {
    /**
     * Predefined sample rate: 1 kHz
     */
    public static final double RATE_1KHZ = 1000.0;

    /**
     * Predefined sample rate: 10 kHz
     */
    public static final double RATE_10KHZ = 10000.0;

    /**
     * Predefined sample rate: 20 kHz (Turbo Mode)
     */
    public static final double RATE_20KHZ = 20000.0;

    /**
     * Creates default acquisition configuration.
     */
    public static AcquisitionConfig defaultConfig() {
        return new AcquisitionConfig(RATE_1KHZ, 1024, false);
    }

    /**
     * Creates configuration with specified sample rate.
     */
    public AcquisitionConfig withSampleRate(double newSampleRate) {
        return new AcquisitionConfig(newSampleRate, this.bufferSize, this.acquisitionActive);
    }

    /**
     * Creates configuration with acquisition state changed.
     */
    public AcquisitionConfig withAcquisitionActive(boolean active) {
        return new AcquisitionConfig(this.sampleRate, this.bufferSize, active);
    }
}
