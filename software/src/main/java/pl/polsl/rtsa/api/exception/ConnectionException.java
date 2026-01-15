package pl.polsl.rtsa.api.exception;

/**
 * Exception thrown when connection-related errors occur.
 */
public class ConnectionException extends DeviceException {

    public ConnectionException(String message) {
        super(ErrorCode.CONNECTION_FAILED, message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(ErrorCode.CONNECTION_FAILED, message, cause);
    }

    public ConnectionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ConnectionException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
