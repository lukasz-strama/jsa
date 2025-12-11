package pl.polsl.rtsa.controller;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.paint.Color;

public class MainController {

    @FXML
    private Canvas oscilloscopeCanvas;

    @FXML
    private ComboBox<String> portComboBox;

    @FXML
    private Button connectButton;

    @FXML
    public void initialize() {
        GraphicsContext gc = oscilloscopeCanvas.getGraphicsContext2D();
        gc.setStroke(Color.LIGHTGREEN);
        gc.setLineWidth(2);
        gc.strokeLine(0, 200, 600, 200);
        gc.fillText("System Ready", 10, 20);
        
        portComboBox.getItems().addAll("COM1", "COM3", "/dev/ttyUSB0");
    }

    @FXML
    private void handleConnect() {
        System.out.println("Kliknięto Połącz! Wybrany port: " + portComboBox.getValue());
    }
}