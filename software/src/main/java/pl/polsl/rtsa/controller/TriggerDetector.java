package pl.polsl.rtsa.controller;

/**
 * Rising-edge trigger for the oscilloscope view.
 */
public final class TriggerDetector {

    private TriggerDetector() {
    }

    /**
     * Find the first rising-edge crossing of {@code threshold} in {@code samples}.
     *
     * @param samples   voltage sample array
     * @param threshold trigger threshold voltage (V)
     * @return index of the first sample at or just after the crossing,
     *         or {@code -1} if no rising edge was found
     */
    public static int findRisingEdge(double[] samples, double threshold) {
        return findRisingEdge(samples, threshold, 1, samples == null ? 0 : samples.length);
    }

    /**
     * Find the first rising-edge crossing within a bounded search range.
     * <p>
     * Use this overload to guarantee the crossing leaves enough room
     * for an unclamped window (so the trigger display is perfectly stable).
     *
     * @param samples   voltage sample array
     * @param threshold trigger threshold voltage (V)
     * @param minIdx    earliest index to consider (inclusive, ≥ 1)
     * @param maxIdx    latest index to consider (exclusive)
     * @return index of the first qualifying crossing, or {@code -1}
     */
    public static int findRisingEdge(double[] samples, double threshold,
            int minIdx, int maxIdx) {
        if (samples == null || samples.length < 2)
            return -1;

        int lo = Math.max(1, minIdx);
        int hi = Math.min(samples.length, maxIdx);

        for (int i = lo; i < hi; i++) {
            if (samples[i - 1] < threshold && samples[i] >= threshold) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Extract the visible window around a trigger point.
     * <p>
     * Returns the start and end <b>indices</b> (inclusive) of a sub-array
     * centred on the trigger crossing. The window size is determined by
     * the total number of samples divided by the zoom factor.
     *
     * @param triggerIdx index returned by {@link #findRisingEdge}
     * @param totalLen   total length of the sample array
     * @param windowLen  desired number of visible samples
     * @return int[2] = { startIdx, endIdx } clamped to [0, totalLen-1]
     */
    public static int[] windowAroundTrigger(int triggerIdx, int totalLen, int windowLen) {
        if (triggerIdx < 0 || totalLen < 2) {
            return new int[] { 0, totalLen - 1 };
        }

        // Place trigger point at ~25 % from left so you see the rise
        int preTrigger = windowLen / 4;
        int start = triggerIdx - preTrigger;
        int end = start + windowLen - 1;

        // Clamp
        if (start < 0) {
            end -= start;
            start = 0;
        }
        if (end >= totalLen) {
            start -= (end - totalLen + 1);
            end = totalLen - 1;
        }
        start = Math.max(0, start);
        end = Math.min(totalLen - 1, end);

        return new int[] { start, end };
    }
}
