package pl.polsl.rtsa.service;

import org.jtransforms.fft.DoubleFFT_1D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.polsl.rtsa.api.dto.SignalStatistics;
import pl.polsl.rtsa.config.AppConfig;

import java.util.Arrays;

/**
 * Service responsible for Digital Signal Processing (DSP) operations.
 * <p>
 * Handles voltage conversion, windowing, FFT calculation, and statistical
 * analysis
 * of the signal data. This service is thread-safe and can be shared across
 * multiple threads.
 * </p>
 * <p>
 * <b>Production Notes:</b>
 * <ul>
 * <li>FFT instances are cached per-size to avoid reallocation overhead</li>
 * <li>Zero-padding to 65536 samples provides ~0.015 Hz resolution at 1kHz
 * sample rate</li>
 * <li>Hamming window is applied by default to reduce spectral leakage</li>
 * </ul>
 * </p>
 */
public class SignalProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(SignalProcessingService.class);

    /** Minimum FFT size for adequate frequency resolution */
    private static final int MIN_FFT_SIZE = 65536;

    /** Minimum frequency threshold to skip DC leakage (Hz) */
    private static final double MIN_FREQ_THRESHOLD = 2.0;

    private final AppConfig config;

    // Cache for FFT instance to avoid re-initialization overhead
    private DoubleFFT_1D cachedFFT;
    private int cachedFFTSize = -1;

    // Pre-computed window coefficients cache
    private double[] cachedWindow;
    private int cachedWindowSize = -1;

    /**
     * Creates a new SignalProcessingService with default configuration.
     */
    public SignalProcessingService() {
        this.config = AppConfig.getInstance();
    }

    /**
     * Creates a new SignalProcessingService with custom configuration.
     *
     * @param config The application configuration to use.
     */
    public SignalProcessingService(AppConfig config) {
        this.config = config;
    }

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
        // Zero-pad to at least MIN_FFT_SIZE to improve peak detection resolution
        int paddedSize = Math.max(n, MIN_FFT_SIZE);

        // 1. Remove DC offset so the huge 0-Hz component does not leak
        // into neighbouring bins and confuse the peak-picker (f0).
        double[] windowedData = Arrays.copyOf(voltageData, n);
        double mean = 0.0;
        for (double v : windowedData)
            mean += v;
        mean /= n;
        for (int i = 0; i < n; i++)
            windowedData[i] -= mean;

        // 2. Apply Hamming Window
        double[] window = getWindowCoefficients(n);
        double windowSum = 0.0;
        for (int i = 0; i < n; i++) {
            windowedData[i] *= window[i];
            windowSum += window[i];
        }

        // 3. Prepare Padded Buffer
        double[] processedData = new double[paddedSize];
        System.arraycopy(windowedData, 0, processedData, 0, n);
        // Remaining elements are 0.0 by default

        // 4. Perform FFT
        // JTransforms DoubleFFT_1D.realForward computes FFT in-place.
        synchronized (this) {
            if (cachedFFT == null || cachedFFTSize != paddedSize) {
                cachedFFT = new DoubleFFT_1D(paddedSize);
                cachedFFTSize = paddedSize;
                logger.debug("Re-initializing FFT with size: {}", paddedSize);
            }
            cachedFFT.realForward(processedData);
        }

        // 5. Calculate Magnitude — normalised to voltage amplitude.
        // Single-sided spectrum: multiply by 2 / windowSum so the
        // peak value equals the real signal amplitude (in V).
        double[] magnitude = new double[paddedSize / 2];
        double normFactor = 2.0 / windowSum;

        // DC Component (Index 0) — no doubling
        magnitude[0] = Math.abs(processedData[0]) / windowSum;

        // AC Components
        for (int k = 1; k < paddedSize / 2; k++) {
            double re = processedData[2 * k];
            double im = processedData[2 * k + 1];
            magnitude[k] = Math.sqrt(re * re + im * im) * normFactor;
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
        if (n <= 1)
            return;

        // Use cached window if available
        double[] window = getWindowCoefficients(n);
        for (int i = 0; i < n; i++) {
            data[i] *= window[i];
        }
    }

    /**
     * Gets or computes Hamming window coefficients.
     * Cached to avoid repeated computation.
     *
     * @param size The window size.
     * @return Array of window coefficients.
     */
    private synchronized double[] getWindowCoefficients(int size) {
        if (cachedWindow == null || cachedWindowSize != size) {
            cachedWindow = new double[size];
            for (int i = 0; i < size; i++) {
                cachedWindow[i] = 0.54 - 0.46 * Math.cos((2 * Math.PI * i) / (size - 1));
            }
            cachedWindowSize = size;
            logger.debug("Window coefficients computed for size: {}", size);
        }
        return cachedWindow;
    }

    /**
     * Computes comprehensive signal statistics.
     *
     * @param voltageData Time-domain voltage samples.
     * @param freqData    Frequency-domain magnitude data.
     * @param sampleRate  Sampling rate in Hz.
     * @return SignalStatistics containing all computed metrics.
     */
    public SignalStatistics computeStatistics(double[] voltageData, double[] freqData, double sampleRate) {
        if (voltageData == null || voltageData.length == 0) {
            return SignalStatistics.empty();
        }

        // Compute time-domain statistics
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0.0;
        double sumSquares = 0.0;

        for (double v : voltageData) {
            if (v < min)
                min = v;
            if (v > max)
                max = v;
            sum += v;
            sumSquares += v * v;
        }

        double dcOffset = sum / voltageData.length;
        double rms = Math.sqrt(sumSquares / voltageData.length);
        double peakToPeak = max - min;

        // Compute frequency-domain statistics
        double dominantFreq = 0.0;
        double dominantMag = 0.0;

        if (freqData != null && freqData.length > 0 && sampleRate > 0) {
            int fftSize = freqData.length * 2;
            double binWidth = sampleRate / fftSize;

            // Skip low-frequency bins to avoid DC and 1/f noise
            int startBin = Math.max(1, (int) (MIN_FREQ_THRESHOLD / binWidth));

            for (int i = startBin; i < freqData.length; i++) {
                if (freqData[i] > dominantMag) {
                    dominantMag = freqData[i];
                    dominantFreq = i * binWidth;
                }
            }
        }

        return SignalStatistics.builder()
                .rmsVoltage(rms)
                .peakToPeak(peakToPeak)
                .minVoltage(min)
                .maxVoltage(max)
                .dcOffset(dcOffset)
                .dominantFreq(dominantFreq)
                .dominantFreqMag(dominantMag)
                .build();
    }

    /**
     * Calculates the frequency axis values for FFT output.
     *
     * @param fftBinCount Number of FFT bins (spectrum length).
     * @param sampleRate  Sampling rate in Hz.
     * @return Array of frequency values in Hz for each bin.
     */
    public double[] computeFrequencyAxis(int fftBinCount, double sampleRate) {
        double[] freqAxis = new double[fftBinCount];
        int fftSize = fftBinCount * 2;
        double binWidth = sampleRate / fftSize;

        for (int i = 0; i < fftBinCount; i++) {
            freqAxis[i] = i * binWidth;
        }
        return freqAxis;
    }

    /**
     * Converts magnitude to decibels (dB) with floor limiting.
     *
     * @param magnitude Linear magnitude values.
     * @param floorDb   Minimum dB value (noise floor).
     * @return Array of magnitude values in dB.
     */
    public double[] convertToDecibels(double[] magnitude, double floorDb) {
        if (magnitude == null || magnitude.length == 0) {
            return new double[0];
        }

        double[] dbValues = new double[magnitude.length];
        for (int i = 0; i < magnitude.length; i++) {
            if (magnitude[i] > 0) {
                dbValues[i] = 20.0 * Math.log10(magnitude[i]);
                if (dbValues[i] < floorDb) {
                    dbValues[i] = floorDb;
                }
            } else {
                dbValues[i] = floorDb;
            }
        }
        return dbValues;
    }

    /**
     * Normalizes FFT magnitude data for display.
     *
     * @param magnitude Raw magnitude values.
     * @return Normalized magnitude values (0.0 to 1.0).
     */
    public double[] normalizeMagnitude(double[] magnitude) {
        if (magnitude == null || magnitude.length == 0) {
            return new double[0];
        }

        double max = 0.0;
        for (double m : magnitude) {
            if (m > max)
                max = m;
        }

        if (max == 0.0) {
            return new double[magnitude.length]; // All zeros
        }

        double[] normalized = new double[magnitude.length];
        for (int i = 0; i < magnitude.length; i++) {
            normalized[i] = magnitude[i] / max;
        }
        return normalized;
    }

    /**
     * Applies a simple moving average filter for smoothing.
     *
     * @param data       Input data.
     * @param windowSize Number of samples to average.
     * @return Smoothed data.
     */
    public double[] applyMovingAverage(double[] data, int windowSize) {
        if (data == null || data.length == 0 || windowSize <= 1) {
            return data;
        }

        int halfWindow = windowSize / 2;
        double[] smoothed = new double[data.length];

        for (int i = 0; i < data.length; i++) {
            int start = Math.max(0, i - halfWindow);
            int end = Math.min(data.length, i + halfWindow + 1);
            double sum = 0.0;
            for (int j = start; j < end; j++) {
                sum += data[j];
            }
            smoothed[i] = sum / (end - start);
        }
        return smoothed;
    }

    /**
     * Removes DC offset from the signal.
     *
     * @param data Input voltage data.
     * @return DC-removed signal (zero-mean).
     */
    public double[] removeDCOffset(double[] data) {
        if (data == null || data.length == 0) {
            return data;
        }

        double mean = 0.0;
        for (double v : data) {
            mean += v;
        }
        mean /= data.length;

        double[] result = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i] - mean;
        }
        return result;
    }

    /**
     * Downsamples data for display purposes.
     *
     * @param data         Input data array.
     * @param targetPoints Maximum number of output points.
     * @return Downsampled data preserving min/max envelope.
     */
    public double[] downsampleForDisplay(double[] data, int targetPoints) {
        if (data == null || data.length <= targetPoints) {
            return data;
        }

        // Use min-max envelope to preserve peaks
        int samplesPerBucket = data.length / targetPoints;
        double[] result = new double[targetPoints * 2];

        for (int i = 0; i < targetPoints; i++) {
            int start = i * samplesPerBucket;
            int end = Math.min(start + samplesPerBucket, data.length);

            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;

            for (int j = start; j < end; j++) {
                if (data[j] < min)
                    min = data[j];
                if (data[j] > max)
                    max = data[j];
            }

            result[i * 2] = min;
            result[i * 2 + 1] = max;
        }
        return result;
    }
}
