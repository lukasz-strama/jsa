package pl.polsl.rtsa.api.dto;

/**
 * Data Transfer Object containing pre-computed signal statistics.
 * Immutable record for thread-safe access.
 *
 * @param rmsVoltage       Root Mean Square voltage of the signal (V).
 * @param peakToPeak       Peak-to-peak voltage (V).
 * @param minVoltage       Minimum voltage in the sample window (V).
 * @param maxVoltage       Maximum voltage in the sample window (V).
 * @param dcOffset         DC offset (average voltage) (V).
 * @param dominantFreq     Detected dominant frequency (Hz).
 * @param dominantFreqMag  Magnitude of the dominant frequency component.
 */
public record SignalStatistics(
        double rmsVoltage,
        double peakToPeak,
        double minVoltage,
        double maxVoltage,
        double dcOffset,
        double dominantFreq,
        double dominantFreqMag
) {
    /**
     * Creates empty/zero statistics.
     */
    public static SignalStatistics empty() {
        return new SignalStatistics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Builder for constructing SignalStatistics.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double rmsVoltage = 0.0;
        private double peakToPeak = 0.0;
        private double minVoltage = 0.0;
        private double maxVoltage = 0.0;
        private double dcOffset = 0.0;
        private double dominantFreq = 0.0;
        private double dominantFreqMag = 0.0;

        public Builder rmsVoltage(double rmsVoltage) {
            this.rmsVoltage = rmsVoltage;
            return this;
        }

        public Builder peakToPeak(double peakToPeak) {
            this.peakToPeak = peakToPeak;
            return this;
        }

        public Builder minVoltage(double minVoltage) {
            this.minVoltage = minVoltage;
            return this;
        }

        public Builder maxVoltage(double maxVoltage) {
            this.maxVoltage = maxVoltage;
            return this;
        }

        public Builder dcOffset(double dcOffset) {
            this.dcOffset = dcOffset;
            return this;
        }

        public Builder dominantFreq(double dominantFreq) {
            this.dominantFreq = dominantFreq;
            return this;
        }

        public Builder dominantFreqMag(double dominantFreqMag) {
            this.dominantFreqMag = dominantFreqMag;
            return this;
        }

        public SignalStatistics build() {
            return new SignalStatistics(
                    rmsVoltage, peakToPeak, minVoltage, maxVoltage,
                    dcOffset, dominantFreq, dominantFreqMag
            );
        }
    }
}
