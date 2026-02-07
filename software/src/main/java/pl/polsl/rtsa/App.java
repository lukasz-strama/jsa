package pl.polsl.rtsa;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import pl.polsl.rtsa.controller.MainController;

import java.io.IOException;

/**
 * JavaFX application entry point for the JSignalAnalysis real-time FFT
 * analyzer.
 * <p>
 * Loads the FXML-based UI, applies the dark theme stylesheet, and manages
 * the application lifecycle (start → stop → JVM exit).
 * </p>
 */
public class App extends Application {

    /** The primary scene shared across the application. */
    private static Scene scene;

    /** Reference to the main UI controller for lifecycle management. */
    private MainController controller;

    /**
     * Initialises the primary stage with the FXML layout and dark theme.
     *
     * @param stage the primary stage provided by the JavaFX runtime
     * @throws IOException if the FXML resource cannot be loaded
     */
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pl/polsl/rtsa/view/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(
                getClass().getResource("/pl/polsl/rtsa/view/dark-theme.css").toExternalForm());

        stage.setTitle("JSignalAnalysis - Real Time FFT Analyzer");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Gracefully shuts down the controller and forces JVM exit.
     * <p>
     * {@code System.exit(0)} is required because jSerialComm may leave
     * non-daemon native threads alive after the JavaFX stage is closed.
     * </p>
     */
    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
            controller = null;
        }
        System.exit(0);
    }

    /**
     * Application entry point — delegates to {@link Application#launch()}.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        launch();
    }
}