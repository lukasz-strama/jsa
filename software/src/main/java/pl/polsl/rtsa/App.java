package pl.polsl.rtsa;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import pl.polsl.rtsa.controller.MainController;

import java.io.IOException;

public class App extends Application {

    private static Scene scene;
    private MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pl/polsl/rtsa/view/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        scene = new Scene(root, 800, 600);

        stage.setTitle("JSignalAnalysis - Real Time FFT Analyzer");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
            controller = null;
        }
    }

    public static void main(String[] args) {
        launch();
    }
}