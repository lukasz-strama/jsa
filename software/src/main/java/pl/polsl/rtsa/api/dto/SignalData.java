package pl.polsl.rtsa.api.dto;

import java.time.Instant;

/**
 * Data Transfer Object representing a complete signal analysis result.
 * Immutable record for thread-safe access from UI thread.
 *
 * @param timestamp        When this result was created.
 * @param timeDomainData   Array of voltage samples in time domain (V).
 * @param freqDomainData   Array of FFT magnitude values.
 * @param sampleRate       Sampling rate used to capture this data (Hz).
 * @param statistics       Pre-computed signal statistics.
 */
public record SignalData(
        Instant timestamp,
        double[] timeDomainData,
        double[] freqDomainData,
        double sampleRate,
        SignalStatistics statistics
) {
    /**
     * Returns the duration of the time-domain data in seconds.
     */
    public double getDurationSeconds() {
        if (timeDomainData == null || timeDomainData.length == 0 || sampleRate <= 0) {
            return 0.0;
        }
        return timeDomainData.length / sampleRate;
    }

    /**
     * Returns the frequency resolution of the FFT (Hz per bin).
     */
    public double getFrequencyResolution() {
        if (freqDomainData == null || freqDomainData.length == 0 || sampleRate <= 0) {
            return 0.0;
        }
        // FFT size = 2 * spectrum length
        int fftSize = freqDomainData.length * 2;
        return sampleRate / fftSize;
    }

    /**
     * Returns the maximum representable frequency (Nyquist frequency).
     */
    public double getNyquistFrequency() {
        return sampleRate / 2.0;
    }

    /**
     * Returns the number of samples in the time domain.
     */
    public int getSampleCount() {
        return timeDomainData != null ? timeDomainData.length : 0;
    }

    /**
     * Returns the number of frequency bins.
     */
    public int getFrequencyBinCount() {
        return freqDomainData != null ? freqDomainData.length : 0;
    }
}
