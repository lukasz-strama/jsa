package pl.polsl.rtsa.api.dto;

/**
 * Data Transfer Object containing pre-computed signal statistics.
 * Immutable record for thread-safe access.
 *
 * @param rmsVoltage      Root Mean Square voltage of the signal (V).
 * @param peakToPeak      Peak-to-peak voltage (V).
 * @param minVoltage      Minimum voltage in the sample window (V).
 * @param maxVoltage      Maximum voltage in the sample window (V).
 * @param dcOffset        DC offset (average voltage) (V).
 * @param dominantFreq    Detected dominant frequency (Hz).
 * @param dominantFreqMag Magnitude of the dominant frequency component.
 */
public record SignalStatistics(
        double rmsVoltage,
        double peakToPeak,
        double minVoltage,
        double maxVoltage,
        double dcOffset,
        double dominantFreq,
        double dominantFreqMag) {
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

    /**
     * Mutable builder for constructing {@link SignalStatistics} instances.
     */
    public static class Builder {
        private double rmsVoltage = 0.0;
        private double peakToPeak = 0.0;
        private double minVoltage = 0.0;
        private double maxVoltage = 0.0;
        private double dcOffset = 0.0;
        private double dominantFreq = 0.0;
        private double dominantFreqMag = 0.0;

        /** Sets the RMS voltage value. */
        public Builder rmsVoltage(double rmsVoltage) {
            this.rmsVoltage = rmsVoltage;
            return this;
        }

        /** Sets the peak-to-peak voltage value. */
        public Builder peakToPeak(double peakToPeak) {
            this.peakToPeak = peakToPeak;
            return this;
        }

        /** Sets the minimum voltage value. */
        public Builder minVoltage(double minVoltage) {
            this.minVoltage = minVoltage;
            return this;
        }

        /** Sets the maximum voltage value. */
        public Builder maxVoltage(double maxVoltage) {
            this.maxVoltage = maxVoltage;
            return this;
        }

        /** Sets the DC offset (mean voltage) value. */
        public Builder dcOffset(double dcOffset) {
            this.dcOffset = dcOffset;
            return this;
        }

        /** Sets the dominant frequency value in Hz. */
        public Builder dominantFreq(double dominantFreq) {
            this.dominantFreq = dominantFreq;
            return this;
        }

        /** Sets the magnitude of the dominant frequency component. */
        public Builder dominantFreqMag(double dominantFreqMag) {
            this.dominantFreqMag = dominantFreqMag;
            return this;
        }

        /**
         * Builds an immutable {@link SignalStatistics} from the current builder state.
         *
         * @return a new {@code SignalStatistics} instance
         */
        public SignalStatistics build() {
            return new SignalStatistics(
                    rmsVoltage, peakToPeak, minVoltage, maxVoltage,
                    dcOffset, dominantFreq, dominantFreqMag);
        }
    }
}
