package pl.polsl.rtsa.api;

import org.junit.jupiter.api.*;
import pl.polsl.rtsa.api.dto.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the SignalAnalyzerApi.
 * Uses mock device client for deterministic testing.
 */
class SignalAnalyzerApiTest {

    private SignalAnalyzerApi api;

    @BeforeEach
    void setUp() {
        api = SignalAnalyzerApi.createMock();
    }

    @AfterEach
    void tearDown() {
        if (api != null && !api.isShutdown()) {
            api.shutdown();
        }
    }

    // ==================== Connection Tests ====================

    @Test
    @DisplayName("Should list available ports")
    void testGetAvailablePorts() {
        // Force refresh to ensure we get the mock ports
        AvailablePorts ports = api.refreshPorts();
        
        assertNotNull(ports);
        assertTrue(ports.hasAvailablePorts(), "Mock should provide ports");
        assertTrue(ports.count() > 0);
    }

    @Test
    @DisplayName("Should connect to mock port successfully")
    void testConnect() {
        api.connect("MOCK_PORT_1");
        
        assertTrue(api.isConnected());
        ConnectionStatus status = api.getConnectionStatus();
        assertTrue(status.connected());
        assertEquals("MOCK_PORT_1", status.portName());
    }

    @Test
    @DisplayName("Should disconnect cleanly")
    void testDisconnect() {
        api.connect("MOCK_PORT_1");
        assertTrue(api.isConnected());
        
        api.disconnect();
        
        assertFalse(api.isConnected());
        ConnectionStatus status = api.getConnectionStatus();
        assertFalse(status.connected());
        assertNull(status.portName());
    }

    @Test
    @DisplayName("Should handle disconnect when not connected")
    void testDisconnectWhenNotConnected() {
        assertFalse(api.isConnected());
        assertDoesNotThrow(() -> api.disconnect());
    }

    // ==================== Acquisition Tests ====================

    @Test
    @DisplayName("Should start and stop acquisition")
    void testStartStopAcquisition() {
        api.connect("MOCK_PORT_1");
        
        api.startAcquisition();
        assertTrue(api.isAcquiring());
        
        api.stopAcquisition();
        assertFalse(api.isAcquiring());
    }

    @Test
    @DisplayName("Should throw when starting acquisition without connection")
    void testStartAcquisitionNotConnected() {
        assertFalse(api.isConnected());
        assertThrows(Exception.class, () -> api.startAcquisition());
    }

    @Test
    @DisplayName("Should receive data through callback")
    void testDataCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SignalData> receivedData = new AtomicReference<>();

        api.setDataCallback(data -> {
            receivedData.set(data);
            latch.countDown();
        });

        api.connect("MOCK_PORT_1");
        api.startAcquisition();

        boolean received = latch.await(3, TimeUnit.SECONDS);
        
        assertTrue(received, "Should receive data within timeout");
        assertNotNull(receivedData.get());
        assertNotNull(receivedData.get().timeDomainData());
        assertTrue(receivedData.get().getSampleCount() > 0);
        assertNotNull(receivedData.get().statistics());
    }

    // ==================== Sample Rate Tests ====================

    @Test
    @DisplayName("Should change sample rate to 1kHz")
    void testSetSampleRate1kHz() {
        api.setSampleRate1kHz();
        assertEquals(1000.0, api.getCurrentSampleRate());
    }

    @Test
    @DisplayName("Should change sample rate to 10kHz")
    void testSetSampleRate10kHz() {
        api.setSampleRate10kHz();
        assertEquals(10000.0, api.getCurrentSampleRate());
    }

    @Test
    @DisplayName("Should change sample rate to 20kHz")
    void testSetSampleRate20kHz() {
        api.setSampleRate20kHz();
        assertEquals(20000.0, api.getCurrentSampleRate());
    }

    @Test
    @DisplayName("Should get acquisition config with correct sample rate")
    void testAcquisitionConfig() {
        api.setSampleRate10kHz();
        api.connect("MOCK_PORT_1");
        api.startAcquisition();

        AcquisitionConfig config = api.getAcquisitionConfig();
        
        assertEquals(10000.0, config.sampleRate());
        assertTrue(config.acquisitionActive());
        assertTrue(config.bufferSize() > 0);
    }

    // ==================== Signal Processing Tests ====================

    @Test
    @DisplayName("Should convert raw ADC to voltage correctly")
    void testConvertToVoltage() {
        int[] rawData = {0, 512, 1023};
        double[] voltage = api.convertToVoltage(rawData);

        assertEquals(3, voltage.length);
        assertEquals(0.0, voltage[0], 0.01);
        assertEquals(2.5, voltage[1], 0.05);
        assertTrue(voltage[2] > 4.9, "Max voltage should be near 5V");
    }

    @Test
    @DisplayName("Should compute FFT for sine wave")
    void testComputeFFT() {
        int sampleRate = 1000;
        double targetFreq = 50.0;
        double[] signal = new double[1024];

        for (int i = 0; i < signal.length; i++) {
            double t = (double) i / sampleRate;
            signal[i] = Math.sin(2 * Math.PI * targetFreq * t);
        }

        double[] fft = api.computeFFT(signal, sampleRate);

        assertNotNull(fft);
        assertTrue(fft.length > 0);
    }

    @Test
    @DisplayName("Should compute statistics correctly")
    void testComputeStatistics() {
        double[] signal = {0.0, 1.0, 2.0, 3.0, 4.0, 5.0};
        double[] fft = new double[0]; // No FFT data

        SignalStatistics stats = api.computeStatistics(signal, fft, 1000);

        assertNotNull(stats);
        assertEquals(0.0, stats.minVoltage(), 0.001);
        assertEquals(5.0, stats.maxVoltage(), 0.001);
        assertEquals(5.0, stats.peakToPeak(), 0.001);
        assertEquals(2.5, stats.dcOffset(), 0.001);
    }

    // ==================== Lifecycle Tests ====================

    @Test
    @DisplayName("Should shutdown cleanly")
    void testShutdown() {
        api.connect("MOCK_PORT_1");
        api.startAcquisition();

        api.shutdown();

        assertTrue(api.isShutdown());
        assertFalse(api.isConnected());
        assertFalse(api.isAcquiring());
    }

    @Test
    @DisplayName("Should throw when used after shutdown")
    void testOperationsAfterShutdown() {
        api.shutdown();

        assertThrows(IllegalStateException.class, () -> api.connect("MOCK_PORT_1"));
        assertThrows(IllegalStateException.class, () -> api.refreshPorts());
    }

    @Test
    @DisplayName("Should handle multiple shutdown calls gracefully")
    void testMultipleShutdown() {
        api.shutdown();
        assertDoesNotThrow(() -> api.shutdown());
        assertTrue(api.isShutdown());
    }

    // ==================== Callback Tests ====================

    @Test
    @DisplayName("Should notify connection callback on connect/disconnect")
    void testConnectionCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<ConnectionStatus> lastStatus = new AtomicReference<>();

        api.setConnectionCallback(status -> {
            lastStatus.set(status);
            latch.countDown();
        });

        api.connect("MOCK_PORT_1");
        api.disconnect();

        boolean notified = latch.await(2, TimeUnit.SECONDS);
        assertTrue(notified, "Should receive connection callbacks");
        assertNotNull(lastStatus.get());
        assertFalse(lastStatus.get().connected());
    }

    // ==================== DTO Tests ====================

    @Test
    @DisplayName("SignalData should calculate derived values correctly")
    void testSignalDataDerivedValues() {
        double sampleRate = 1000.0;
        double[] timeData = new double[1000];
        double[] freqData = new double[32768];

        SignalData data = new SignalData(
                java.time.Instant.now(),
                timeData,
                freqData,
                sampleRate,
                SignalStatistics.empty()
        );

        assertEquals(1.0, data.getDurationSeconds(), 0.001);
        assertEquals(500.0, data.getNyquistFrequency(), 0.001);
        assertEquals(1000, data.getSampleCount());
        assertEquals(32768, data.getFrequencyBinCount());
        assertTrue(data.getFrequencyResolution() > 0);
    }

    @Test
    @DisplayName("AcquisitionConfig should create correct instances")
    void testAcquisitionConfigFactory() {
        AcquisitionConfig config = AcquisitionConfig.defaultConfig();
        
        assertEquals(1000.0, config.sampleRate());
        assertFalse(config.acquisitionActive());

        AcquisitionConfig updated = config.withSampleRate(10000.0).withAcquisitionActive(true);
        
        assertEquals(10000.0, updated.sampleRate());
        assertTrue(updated.acquisitionActive());
    }
}
