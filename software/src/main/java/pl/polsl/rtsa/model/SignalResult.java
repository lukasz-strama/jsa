package pl.polsl.rtsa.model;

/**
 * A record representing a snapshot of signal data.
 * <p>
 * Contains both the raw time-domain samples and the processed frequency-domain
 * magnitude spectrum.
 * </p>
 *
 * @param timeDomainData The array of voltage samples in the time domain.
 * @param freqDomainData The array of magnitude values in the frequency domain.
 * @param sampleRate     The sampling rate (in Hz) used to capture this data.
 */
public record SignalResult(double[] timeDomainData, double[] freqDomainData, double sampleRate) {
}
