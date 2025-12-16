package pl.polsl.rtsa.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignalProcessingServiceTest {

    private final SignalProcessingService service = new SignalProcessingService();

    @Test
    @DisplayName("Should convert raw ADC values to correct voltages")
    void testConvertToVoltage() {
        // Given
        int[] rawData = {0, 512, 1024}; // 1024 is technically out of 10-bit range (0-1023) but useful for math check: 5.0V

        // When
        double[] voltages = service.convertToVoltage(rawData);

        // Then
        Assertions.assertEquals(3, voltages.length);
        Assertions.assertEquals(0.0, voltages[0], 0.01);
        Assertions.assertEquals(2.5, voltages[1], 0.01); // 512 is half of 1024
        Assertions.assertEquals(5.0, voltages[2], 0.01);
    }

    @Test
    @DisplayName("Should calculate correct RMS for DC and Sine signals")
    void testCalculateRMS() {
        // Scenario 1: DC Signal 5.0V
        double[] dcSignal = new double[100];
        for (int i = 0; i < dcSignal.length; i++) {
            dcSignal[i] = 5.0;
        }
        double rmsDC = service.calculateRMS(dcSignal);
        Assertions.assertEquals(5.0, rmsDC, 0.001, "RMS of 5V DC should be 5V");

        // Scenario 2: Sine Wave Amplitude 1.0V
        double[] sineSignal = new double[1000];
        for (int i = 0; i < sineSignal.length; i++) {
            sineSignal[i] = Math.sin(2 * Math.PI * i / 100.0); // Frequency doesn't matter much for RMS
        }
        double rmsSine = service.calculateRMS(sineSignal);
        Assertions.assertEquals(0.707, rmsSine, 0.01, "RMS of 1V Sine should be ~0.707V");
    }

    @Test
    @DisplayName("FFT should detect correct peak frequency for 50Hz sine wave")
    void testFFT_PeakDetection() {
        // Given
        int sampleRate = 1000;
        int bufferSize = 1024;
        double targetFreq = 50.0;
        double[] signal = new double[bufferSize];

        // Generate 50Hz Sine Wave
        for (int i = 0; i < bufferSize; i++) {
            double t = (double) i / sampleRate;
            signal[i] = Math.sin(2 * Math.PI * targetFreq * t);
        }

        // When
        double[] magnitudeSpectrum = service.computeFFT(signal);

        // Then
        // Find peak bin
        int peakBin = 0;
        double maxVal = -1.0;
        for (int i = 1; i < magnitudeSpectrum.length; i++) { // Skip DC at index 0
            if (magnitudeSpectrum[i] > maxVal) {
                maxVal = magnitudeSpectrum[i];
                peakBin = i;
            }
        }

        // Calculate Frequency from Bin
        // Freq = binIndex * SampleRate / N
        double detectedFreq = (double) peakBin * sampleRate / bufferSize;

        // Assert
        // Bin resolution is SampleRate / N = 1000 / 1024 ~= 0.97 Hz
        // So we expect the result to be within ~1 Hz of 50 Hz
        Assertions.assertEquals(targetFreq, detectedFreq, 1.5, "Peak frequency should be approx 50Hz");
    }
}
