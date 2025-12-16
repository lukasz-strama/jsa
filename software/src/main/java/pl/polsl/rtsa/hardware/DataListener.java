package pl.polsl.rtsa.hardware;

import pl.polsl.rtsa.model.SignalResult;

/**
 * Listener interface for receiving asynchronous data and error events from the {@link DeviceClient}.
 */
public interface DataListener {

    /**
     * Called when a new batch of signal data is available.
     *
     * @param result The {@link SignalResult} containing time and frequency domain data.
     */
    void onNewData(SignalResult result);

    /**
     * Called when an error occurs within the device client.
     *
     * @param message A descriptive error message.
     */
    void onError(String message);
}
