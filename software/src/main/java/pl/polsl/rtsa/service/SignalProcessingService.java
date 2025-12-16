package pl.polsl.rtsa.service;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.Arrays;

/**
 * Service responsible for Digital Signal Processing (DSP) operations.
 * <p>
 * Handles voltage conversion, windowing, FFT calculation, and statistical analysis
 * of the signal data.
 * </p>
 */
public class SignalProcessingService {

    private static final double V_REF = 5.0;
    private static final int ADC_RES = 1024;

    /**
     * Converts raw ADC values (0-1023) to voltage levels (0.0-5.0V).
     *
     * @param rawData The array of raw integer ADC samples.
     * @return An array of voltage values.
     */
    public double[] convertToVoltage(int[] rawData) {
        if (rawData == null) {
            return new double[0];
        }
        double[] voltageData = new double[rawData.length];
        for (int i = 0; i < rawData.length; i++) {
            voltageData[i] = (rawData[i] * V_REF) / ADC_RES;
        }
        return voltageData;
    }

    /**
     * Calculates the Root Mean Square (RMS) of the signal.
     * <p>
     * RMS = sqrt( (1/N) * sum(x[i]^2) )
     * </p>
     *
     * @param voltageData The time-domain voltage samples.
     * @return The calculated RMS value.
     */
    public double calculateRMS(double[] voltageData) {
        if (voltageData == null || voltageData.length == 0) {
            return 0.0;
        }
        double sum = 0;
        for (double v : voltageData) {
            sum += v * v;
        }
        return Math.sqrt(sum / voltageData.length);
    }

    /**
     * Computes the Magnitude Spectrum of the signal using FFT.
     * <p>
     * This method automatically applies a Hamming window to the input data
     * to reduce spectral leakage before performing the transform.
     * </p>
     *
     * @param voltageData The time-domain voltage samples.
     * @return An array representing the magnitude of the frequency bins (size N/2).
     */
    public double[] computeFFT(double[] voltageData) {
        if (voltageData == null || voltageData.length == 0) {
            return new double[0];
        }

        int n = voltageData.length;
        
        // 1. Apply Hamming Window (on a copy to preserve original data)
        double[] processedData = Arrays.copyOf(voltageData, n);
        applyHammingWindow(processedData);

        // 2. Perform FFT
        // JTransforms DoubleFFT_1D.realForward computes FFT in-place.
        // Layout for even N: [Re(0), Re(N/2), Re(1), Im(1), Re(2), Im(2), ...]
        DoubleFFT_1D fft = new DoubleFFT_1D(n);
        fft.realForward(processedData);

        // 3. Calculate Magnitude
        // Return N/2 bins (DC up to Nyquist-1)
        double[] magnitude = new double[n / 2];

        // DC Component (Index 0)
        magnitude[0] = Math.abs(processedData[0]);

        // AC Components (Indices 1 to N/2 - 1)
        // In JTransforms output array 'processedData':
        // Re(k) is at index 2*k
        // Im(k) is at index 2*k + 1
        for (int k = 1; k < n / 2; k++) {
            double re = processedData[2 * k];
            double im = processedData[2 * k + 1];
            magnitude[k] = Math.sqrt(re * re + im * im);
        }

        // Note: Nyquist component is at processedData[1] (Re) and Im is 0.
        // Currently returning N/2 bins, so Nyquist is excluded from this array.
        // If needed, it could be appended, but usually N/2 bins are sufficient for calculation.

        return magnitude;
    }

    /**
     * Applies a Hamming Window to the data array in-place.
     * <p>
     * Formula: w(n) = 0.54 - 0.46 * cos(2 * PI * n / (N - 1))
     * </p>
     *
     * @param data The data array to modify.
     */
    private void applyHammingWindow(double[] data) {
        int n = data.length;
        if (n <= 1) return;

        for (int i = 0; i < n; i++) {
            double window = 0.54 - 0.46 * Math.cos((2 * Math.PI * i) / (n - 1));
            data[i] *= window;
        }
    }
}
