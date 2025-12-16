package pl.polsl.rtsa.service;

import org.jtransforms.fft.DoubleFFT_1D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.polsl.rtsa.config.AppConfig;

import java.util.Arrays;

/**
 * Service responsible for Digital Signal Processing (DSP) operations.
 * <p>
 * Handles voltage conversion, windowing, FFT calculation, and statistical analysis
 * of the signal data.
 * </p>
 */
public class SignalProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(SignalProcessingService.class);
    private final AppConfig config = AppConfig.getInstance();
    
    // Cache for FFT instance to avoid re-initialization overhead
    private DoubleFFT_1D cachedFFT;
    private int cachedFFTSize = -1;

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
        double vRef = config.getVRef();
        int adcRes = config.getAdcResolution();
        
        double[] voltageData = new double[rawData.length];
        for (int i = 0; i < rawData.length; i++) {
            voltageData[i] = (rawData[i] * vRef) / adcRes;
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
        // Zero-pad to at least 65536 to improve peak detection resolution
        int paddedSize = Math.max(n, 65536);
        
        // 1. Apply Hamming Window (on a copy to preserve original data)
        double[] windowedData = Arrays.copyOf(voltageData, n);
        applyHammingWindow(windowedData);

        // 2. Prepare Padded Buffer
        double[] processedData = new double[paddedSize];
        System.arraycopy(windowedData, 0, processedData, 0, n);
        // Remaining elements are 0.0 by default

        // 3. Perform FFT
        // JTransforms DoubleFFT_1D.realForward computes FFT in-place.
        if (cachedFFT == null || cachedFFTSize != paddedSize) {
            cachedFFT = new DoubleFFT_1D(paddedSize);
            cachedFFTSize = paddedSize;
            logger.debug("Re-initializing FFT with size: {}", paddedSize);
        }
        cachedFFT.realForward(processedData);

        // 4. Calculate Magnitude
        // Return N/2 bins
        double[] magnitude = new double[paddedSize / 2];

        // DC Component (Index 0)
        magnitude[0] = Math.abs(processedData[0]);

        // AC Components
        for (int k = 1; k < paddedSize / 2; k++) {
            double re = processedData[2 * k];
            double im = processedData[2 * k + 1];
            magnitude[k] = Math.sqrt(re * re + im * im);
        }

        logger.debug("FFT computed. Input: {}, Padded: {}, Spectrum: {}", n, paddedSize, magnitude.length);
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
