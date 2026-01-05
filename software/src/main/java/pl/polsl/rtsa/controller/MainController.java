package pl.polsl.rtsa.controller;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.paint.Color;
import pl.polsl.rtsa.hardware.DeviceClient;
import pl.polsl.rtsa.hardware.MockDeviceClient;
import javafx.event.ActionEvent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import pl.polsl.rtsa.hardware.DataListener;
import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;

import java.util.Arrays;

import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.animation.AnimationTimer;

public class MainController{


    private DeviceClient deviceClient = new MockDeviceClient();

    private double[] lastTimeData = new double[0];
    private double[] lastFFTData = new double[0];

    private GraphicsContext gc;

    @FXML private CheckBox FFTCheck;
    @FXML private CheckBox FreezeCheck;
    @FXML private Label Vmax;
    @FXML private Label Vmin;
    @FXML private Label Vrms;
    @FXML private CheckBox autoCheck;
    @FXML private Button connectButton;
    @FXML private Label connectionStatus;
    @FXML private Canvas oscilloscopeCanvas;
    @FXML private Canvas fftCanvas;
    @FXML private ComboBox<String> portComboBox;
    @FXML private ComboBox<String> samplingFreq;
    @FXML private ToggleButton toggleButtonStart;


    //Initialize method
    @FXML
    public void initialize() {
        gc = oscilloscopeCanvas.getGraphicsContext2D();
        // gc.setStroke(Color.LIGHTGREEN);
        // gc.setLineWidth(2);
        // gc.strokeLine(0, 200, 600, 200);
        // gc.fillText("System Ready", 10, 20);
        
       // portComboBox.getItems().addAll("COM1", "COM3", "/dev/ttyUSB0");

        portComboBox.getItems().addAll(deviceClient.getAvailablePorts());

        samplingFreq.getItems().addAll("1 kHz", "10 kHz", "20 kHz");
        samplingFreq.getSelectionModel().selectFirst();

        connectionStatus.setText("Disconnected");
        connectionStatus.setStyle("-fx-text-fill: red;");

        toggleButtonStart.setText("Start");

        startRenderLoop();
    }

    //Connect
    @FXML
    void handleConnect(ActionEvent event) {
        String port = portComboBox.getValue();
        if(port == null){
            connectionStatus.setText("No port selected");
            connectionStatus.setStyle("-fx-text-fill: orange;");
        return;
        }

        boolean connected = deviceClient.connect(port);

        if(connected) {
            connectionStatus.setText("Connected");
            connectionStatus.setStyle("-fx-text-fill: green;");
            deviceClient.addListener(dataListener);
        } else {
            connectionStatus.setText("Connection failed");
            connectionStatus.setStyle("-fx-text-fill: red;");
        }
    }

    //Start/Stop Acquisition
    @FXML
    void handleStart(ActionEvent event) {
        if(toggleButtonStart.isSelected()) {
            deviceClient.sendCommand(DeviceCommand.START_ACQUISITION);
            toggleButtonStart.setText("Stop");
        } else {
            deviceClient.sendCommand(DeviceCommand.STOP_ACQUISITION);
            toggleButtonStart.setText("Start");
        }
    }

    // Change Sampling Frequency
    @FXML
    void changeSamplingFreq(ActionEvent event) {
        String freq = samplingFreq.getValue();
        if(freq == null) return;

       switch(freq) {
           case "1 kHz":
               deviceClient.sendCommand(DeviceCommand.SET_RATE_1KHZ);
               break;
           case "10 kHz":
               deviceClient.sendCommand(DeviceCommand.SET_RATE_10KHZ);
               break;
           case "20 kHz":
               deviceClient.sendCommand(DeviceCommand.SET_RATE_20KHZ);
               break;
       }
    }

    //Listener
    private final DataListener dataListener = new DataListener() {
        @Override
        public void onNewData(SignalResult result) {
            if(FreezeCheck.isSelected()) return; 

            lastTimeData = result.timeDomainData();
            

            Platform.runLater(() -> updateLabels(result));
            
        }

        @Override
        public void onError(String message){
            Platform.runLater(() -> {
                connectionStatus.setText("Error: " + message);
                connectionStatus.setStyle("-fx-text-fill: red;");
            });
        }
    };

    //Labels
    private void updateLabels(SignalResult result) {
        double[] samples = result.timeDomainData(); 
        double max = Double.NEGATIVE_INFINITY; 
        double min = Double.POSITIVE_INFINITY; 
        double sumSq = 0; 
        
        for (double v : samples) { 
            if (v > max) max = v; 
            if (v < min) min = v; 
            sumSq += v * v; } 
            
        double rms = Math.sqrt(sumSq / samples.length); 

        Vmax.setText(String.format("Vmax: %.2f V", max)); 
        Vmin.setText(String.format("Vmin: %.2f V", min)); 
        Vrms.setText(String.format("Vrms: %.2f V", rms));
    }

    //Render loop
    private void startRenderLoop() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if(FreezeCheck.isSelected()) return;
                drawTimeDomain(lastTimeData);
            }
        };
        timer.start();
    }

    //Draw Time Domain
    private void drawTimeDomain(double[] samples) {
        GraphicsContext gc = oscilloscopeCanvas.getGraphicsContext2D();
        double w = oscilloscopeCanvas.getWidth();
        double h = oscilloscopeCanvas.getHeight();
        gc.clearRect(0, 0, w, h);
        gc.setStroke(Color.LIGHTSEAGREEN);
        gc.setLineWidth(1.0);
        gc.setFill(Color.BLACK);

        int n = samples.length;
        if(n<2) return;

        double xScale = w / (double) (n - 1);

        double min = Arrays.stream(samples).min().orElse(0);
        double max = Arrays.stream(samples).max().orElse(1);
        double range = max - min;
        if(range == 0) range = 1;

        double[] x = new double[n];
        double[] y = new double[n];

        for(int i = 0; i < n; i++) {
            x[i] = i * xScale;
            double norm = (samples[i] - min) / range;
            y[i] = h - (norm * h);
        }

        gc.strokePolyline(x, y, n);
    }

    @FXML
    void FFTVisualization(ActionEvent event) {
    }


    @FXML
    void autoscaling(ActionEvent event) {

    }

    @FXML
    void freeze(ActionEvent event) {
        
    }


}


   

    

    

