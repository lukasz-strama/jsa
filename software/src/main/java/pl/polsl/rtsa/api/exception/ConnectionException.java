package pl.polsl.rtsa.api.exception;

/**
 * Exception thrown when connection-related errors occur.
 */
public class ConnectionException extends DeviceException {

    /**
     * Creates a connection exception with {@link ErrorCode#CONNECTION_FAILED}.
     *
     * @param message descriptive error message
     */
    public ConnectionException(String message) {
        super(ErrorCode.CONNECTION_FAILED, message);
    }

    /**
     * Creates a connection exception with {@link ErrorCode#CONNECTION_FAILED} and a
     * cause.
     *
     * @param message descriptive error message
     * @param cause   the underlying cause
     */
    public ConnectionException(String message, Throwable cause) {
        super(ErrorCode.CONNECTION_FAILED, message, cause);
    }

    /**
     * Creates a connection exception with a specific error code.
     *
     * @param errorCode the categorising {@link ErrorCode}
     * @param message   descriptive error message
     */
    public ConnectionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * Creates a connection exception with a specific error code and cause.
     *
     * @param errorCode the categorising {@link ErrorCode}
     * @param message   descriptive error message
     * @param cause     the underlying cause
     */
    public ConnectionException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
