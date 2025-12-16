package pl.polsl.rtsa.hardware;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class DeviceClientTest {

    @Test
    @DisplayName("Mock Client should generate data after START_ACQUISITION")
    void testMockDataGeneration() throws InterruptedException {
        // 1. Setup
        DeviceClient client = new MockDeviceClient();
        CountDownLatch latch = new CountDownLatch(1);
        final SignalResult[] receivedResult = new SignalResult[1];

        client.addListener(new DataListener() {
            @Override
            public void onNewData(SignalResult result) {
                receivedResult[0] = result;
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                Assertions.fail("Should not receive error: " + message);
            }
        });

        // 2. Execution
        boolean connected = client.connect("TEST_PORT");
        Assertions.assertTrue(connected, "Client should connect successfully");

        client.sendCommand(DeviceCommand.START_ACQUISITION);

        // 3. Verification (Async)
        // Wait up to 2 seconds for the first data packet
        boolean received = latch.await(2, TimeUnit.SECONDS);
        Assertions.assertTrue(received, "Should receive data within timeout");

        // 4. Assertions on Data
        SignalResult result = receivedResult[0];
        Assertions.assertNotNull(result, "SignalResult should not be null");
        Assertions.assertTrue(result.sampleRate() > 0, "Sample rate should be positive");
        Assertions.assertNotNull(result.timeDomainData(), "Time domain data should not be null");
        Assertions.assertEquals(1024, result.timeDomainData().length, "Should receive 1024 samples");

        // Cleanup
        client.disconnect();
    }
}
