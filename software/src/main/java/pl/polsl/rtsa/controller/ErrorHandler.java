package pl.polsl.rtsa.controller;

import javafx.application.Platform;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.polsl.rtsa.api.exception.ConnectionException;
import pl.polsl.rtsa.api.exception.DeviceException;

/**
 * Unified error handler for the UI layer.
 * <p>
 * Translates backend exceptions to user-friendly Polish messages
 * and updates the UI status label appropriately.
 * </p>
 */
public class ErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);

    /**
     * Handles any exception, updating the status label appropriately.
     * Safe to call from any thread - automatically dispatches to JavaFX thread.
     *
     * @param e           The exception to handle.
     * @param statusLabel The label to update with error message.
     */
    public static void handle(Exception e, Label statusLabel) {
        String message = getUserMessage(e);
        logger.error("Error handled: {}", message, e);
        
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: red;");
        });
    }

    /**
     * Converts exception to user-friendly Polish message.
     *
     * @param e The exception to convert.
     * @return User-friendly error message in Polish.
     */
    public static String getUserMessage(Exception e) {
        if (e instanceof ConnectionException ce) {
            return switch (ce.getErrorCode()) {
                case INVALID_PORT -> "Nieprawidłowy port";
                case CONNECTION_FAILED -> "Błąd połączenia: " + ce.getMessage();
                case PORT_BUSY -> "Port jest zajęty";
                case HANDSHAKE_FAILED -> "Błąd komunikacji z urządzeniem";
                default -> "Błąd połączenia";
            };
        } else if (e instanceof DeviceException de) {
            return switch (de.getErrorCode()) {
                case NOT_CONNECTED -> "Urządzenie nie jest podłączone";
                case TIMEOUT -> "Przekroczono limit czasu";
                case COMMUNICATION_ERROR -> "Błąd komunikacji";
                case CONFIGURATION_ERROR -> "Błąd konfiguracji";
                default -> "Błąd urządzenia: " + de.getMessage();
            };
        } else if (e instanceof IllegalStateException) {
            return "API zostało zamknięte";
        }
        return "Nieoczekiwany błąd: " + e.getMessage();
    }
}
