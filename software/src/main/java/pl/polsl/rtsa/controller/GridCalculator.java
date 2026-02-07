package pl.polsl.rtsa.controller;

/**
 * Utility for calculating grid line positions for plot axes.
 * Uses the 1–2–5 sequence so tick labels are always human-readable
 */
public final class GridCalculator {

    private GridCalculator() {
    }

    /**
     * Computed grid information.
     *
     * @param start position of the first grid line (≥ visible min)
     * @param step  distance between consecutive grid lines
     * @param count total number of grid lines in the visible range
     */
    public record GridInfo(double start, double step, int count) {
    }

    /**
     * Calculate grid lines for a given visible range.
     *
     * @param min             visible minimum value
     * @param max             visible maximum value
     * @param targetDivisions desired number of divisions
     * @return grid info with start position, step, and count of grid lines
     */
    public static GridInfo calculate(double min, double max, int targetDivisions) {
        if (max <= min || targetDivisions < 1) {
            return new GridInfo(min, Math.max(max - min, 1.0), 1);
        }

        double range = max - min;
        double roughStep = range / targetDivisions;
        double step = niceNumber(roughStep);

        // First grid line at or just above min, snapped to step
        double start = Math.ceil(min / step) * step;

        // Guard against floating-point drift
        if (start < min - step * 1e-9) {
            start += step;
        }

        int count = 0;
        for (double v = start; v <= max + step * 1e-9; v += step) {
            count++;
            if (count > 200)
                break; // safety
        }
        if (count == 0)
            count = 1;

        return new GridInfo(start, step, count);
    }

    /**
     * Round to the nearest number in the 1–2–5 decade sequence.
     */
    public static double niceNumber(double value) {
        if (value <= 0)
            return 1.0;

        double exp = Math.floor(Math.log10(value));
        double base = Math.pow(10, exp);
        double frac = value / base;

        double nice;
        if (frac <= 1.0)
            nice = 1.0;
        else if (frac <= 2.0)
            nice = 2.0;
        else if (frac <= 5.0)
            nice = 5.0;
        else
            nice = 10.0;

        return nice * base;
    }

    /**
     * Format a numeric value with precision appropriate for the grid step.
     * Avoids displaying "-0.0" for values very close to zero.
     */
    public static String formatValue(double value, double step) {
        if (Math.abs(value) < step * 0.01)
            value = 0.0;
        if (step >= 1.0)
            return String.format("%.0f", value);
        if (step >= 0.1)
            return String.format("%.1f", value);
        if (step >= 0.01)
            return String.format("%.2f", value);
        return String.format("%.3f", value);
    }

    /**
     * Format a time value with the most readable unit (s, ms, µs).
     */
    public static String formatTime(double seconds, double step) {
        if (Math.abs(seconds) < step * 0.01)
            seconds = 0.0;
        if (step >= 0.5)
            return String.format("%.1f s", seconds);
        if (step >= 0.001)
            return String.format("%.1f ms", seconds * 1_000);
        if (step >= 1e-6)
            return String.format("%.1f µs", seconds * 1_000_000);
        return String.format("%.2f ns", seconds * 1_000_000_000);
    }

    /**
     * Format a frequency value with the most readable unit (Hz, kHz).
     */
    public static String formatFrequency(double hz, double step) {
        if (Math.abs(hz) < step * 0.01)
            hz = 0.0;
        if (hz >= 1000 || step >= 500) {
            return String.format("%.1f kHz", hz / 1000.0);
        }
        return String.format("%.0f Hz", hz);
    }
}
