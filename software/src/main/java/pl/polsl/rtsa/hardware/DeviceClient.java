package pl.polsl.rtsa.hardware;

import pl.polsl.rtsa.model.DeviceCommand;
import java.util.List;

/**
 * Interface defining the contract for communicating with the signal analysis hardware.
 * <p>
 * Implementations of this interface handle the low-level details of connection management,
 * command transmission, and data retrieval.
 * </p>
 */
public interface DeviceClient {

    /**
     * Establishes a connection to the specified port.
     *
     * @param port The system port name (e.g., "COM3", "/dev/ttyACM0").
     * @return {@code true} if the connection was successfully established, {@code false} otherwise.
     */
    boolean connect(String port);

    /**
     * Closes the active connection and releases resources.
     */
    void disconnect();

    /**
     * Sends a command to the connected device.
     *
     * @param cmd The {@link DeviceCommand} to send.
     */
    void sendCommand(DeviceCommand cmd);

    /**
     * Registers a listener to receive data and error events.
     *
     * @param listener The {@link DataListener} to add.
     */
    void addListener(DataListener listener);

    /**
     * Retrieves a list of currently available serial ports on the system.
     *
     * @return A list of port names.
     */
    List<String> getAvailablePorts();
}
