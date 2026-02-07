package pl.polsl.rtsa.controller;

/**
 * Automatic Y-axis scaler for the oscilloscope and FFT views.
 * <p>
 * Analyses the current signal data and returns visible min/max values
 * with a configurable padding margin so the waveform never touches the
 * canvas edges.
 * </p>
 */
public class Autoscaler {

    /** Padding percentage to add above/below the signal (0.1 = 10%). */
    private static final double PADDING_FACTOR = 0.1;

    /**
     * Computes a padded voltage range for the time-domain display.
     *
     * @param samples voltage samples (V)
     * @return double[2] = { vMin, vMax } with {@value PADDING_FACTOR}
     *         padding applied; defaults to [0.0, 5.0] when data is insufficient
     */
    public double[] scaleTime(double[] samples) {
        if (samples == null || samples.length < 2) {
            return new double[] { 0.0, 5.0 };
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (double v : samples) {
            if (v < min)
                min = v;
            if (v > max)
                max = v;
        }

        if (min == max) {
            min -= 1.0;
            max += 1.0;
        }

        // Add padding so signal doesn't touch canvas edges
        double range = max - min;
        double padding = range * PADDING_FACTOR;
        min -= padding;
        max += padding;

        return new double[] { min, max };
    }

    /**
     * Computes a padded magnitude ceiling for the FFT display.
     *
     * @param fft FFT magnitude bins
     * @return maximum magnitude multiplied by (1 + {@value PADDING_FACTOR});
     *         defaults to 1.0 when data is insufficient
     */
    public double scaleFFT(double[] fft) {
        if (fft == null || fft.length < 2) {
            return 1.0;
        }

        double max = 0.0;
        for (double v : fft) {
            if (v > max)
                max = v;
        }

        if (max == 0.0) {
            max = 1.0;
        }

        // Add padding so FFT peaks don't touch top edge
        return max * (1.0 + PADDING_FACTOR);
    }
}
