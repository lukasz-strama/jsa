package pl.polsl.rtsa.api.exception;

/**
 * Base exception for all device-related errors.
 * <p>
 * This exception hierarchy allows the frontend to handle device errors
 * in a structured and consistent manner.
 * </p>
 */
public class DeviceException extends RuntimeException {

    private final ErrorCode errorCode;

    public DeviceException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DeviceException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Error codes for categorizing device exceptions.
     */
    public enum ErrorCode {
        /** Device connection failed */
        CONNECTION_FAILED,
        /** Device not connected when operation requires connection */
        NOT_CONNECTED,
        /** Handshake with device failed */
        HANDSHAKE_FAILED,
        /** Serial communication error */
        COMMUNICATION_ERROR,
        /** Invalid port specified */
        INVALID_PORT,
        /** Port is already in use */
        PORT_BUSY,
        /** Device timeout */
        TIMEOUT,
        /** Configuration error */
        CONFIGURATION_ERROR,
        /** Unknown/unexpected error */
        UNKNOWN
    }
}
