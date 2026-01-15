package pl.polsl.rtsa.api.dto;

/**
 * Data Transfer Object representing the connection state.
 * Immutable record for thread-safe access.
 *
 * @param connected   Whether a device is currently connected.
 * @param portName    The name of the connected port, or null if not connected.
 * @param deviceInfo  Device identification string from handshake, or null.
 */
public record ConnectionStatus(
        boolean connected,
        String portName,
        String deviceInfo
) {
    /**
     * Creates a disconnected status.
     */
    public static ConnectionStatus disconnected() {
        return new ConnectionStatus(false, null, null);
    }

    /**
     * Creates a connected status.
     *
     * @param portName   The connected port name.
     * @param deviceInfo Device identification info.
     */
    public static ConnectionStatus connected(String portName, String deviceInfo) {
        return new ConnectionStatus(true, portName, deviceInfo);
    }
}
