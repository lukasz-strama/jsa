package pl.polsl.rtsa.controller;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import pl.polsl.rtsa.hardware.DeviceClient;
import pl.polsl.rtsa.hardware.MockDeviceClient;
import javafx.event.ActionEvent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.AnchorPane;
import pl.polsl.rtsa.hardware.DataListener;
import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;
import javafx.animation.AnimationTimer;


public class MainController{


    private DeviceClient deviceClient = new MockDeviceClient();

    private final FFTDomainRen fftRenderer = new FFTDomainRen();
    private final TimeDomainRen timeRenderer = new TimeDomainRen();
    private final Autoscaler autoscaler = new Autoscaler();

    private double timeMin = 0.0;
    private double timeMax = 5.0;
    private double fftMax = 5.0;

    private volatile boolean newDataAvailable = false;
    private volatile SignalResult lastResult;
    private boolean isRunning = false;
    private boolean fftVisible = false;

    private double cursorX = -1; 
    private double cursorY = -1; 
    private boolean cursorActive = false;



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
    @FXML private SplitPane splitPane;
    @FXML private AnchorPane timePane;
    @FXML private AnchorPane fftPane;



    //Initialize method
    @FXML
    public void initialize() {

        fftCanvas.setVisible(false);
        fftCanvas.setManaged(false);
        splitPane.setDividerPositions(1.0);

        // Resize listeners
        timePane.widthProperty().addListener((obs, oldV, newV) -> { oscilloscopeCanvas.setWidth(newV.doubleValue()); }); 
        timePane.heightProperty().addListener((obs, oldV, newV) -> { oscilloscopeCanvas.setHeight(newV.doubleValue()); });
        fftPane.widthProperty().addListener((obs, oldV, newV) -> { fftCanvas.setWidth(newV.doubleValue()); });
        fftPane.heightProperty().addListener((obs, oldV, newV) -> { fftCanvas.setHeight(newV.doubleValue()); });

        portComboBox.getItems().addAll(deviceClient.getAvailablePorts());

        samplingFreq.getItems().addAll("1 kHz", "10 kHz", "20 kHz");
        samplingFreq.getSelectionModel().selectFirst();

        connectionStatus.setText("Nie połączono");
        connectionStatus.setStyle("-fx-text-fill: red;");

        toggleButtonStart.setText("Start");

        // Mouse events for cursor
        oscilloscopeCanvas.setOnMouseMoved(e -> { 
            cursorX = e.getX(); 
            cursorY = e.getY(); 
            cursorActive = true; 
        }); 
        
        oscilloscopeCanvas.setOnMouseExited(e -> { 
            cursorActive = false; });

        
        startRenderLoop();
    }

    //Connect
    @FXML
    void handleConnect(ActionEvent event) {
        String port = portComboBox.getValue();

        if(port == null){
            connectionStatus.setText("Nie wybrano portu");
            connectionStatus.setStyle("-fx-text-fill: orange;");
        return;
        }

        boolean connected = deviceClient.connect(port);

        if(connected) {
            connectionStatus.setText("Połączono");
            connectionStatus.setStyle("-fx-text-fill: green;");
            deviceClient.addListener(dataListener);
        } else {
            connectionStatus.setText("Błąd połączenia");
            connectionStatus.setStyle("-fx-text-fill: red;");
        }
    }

    //Start/Stop Acquisition
    @FXML
    void handleStart(ActionEvent event) {

        if(toggleButtonStart.isSelected()) {
            isRunning = true;
            deviceClient.sendCommand(DeviceCommand.START_ACQUISITION);
            toggleButtonStart.setText("Stop");

            // Restore FFTCheck state
            fftCanvas.setVisible(fftVisible);
            FFTCheck.setSelected(fftVisible);
            fftCanvas.setManaged(fftVisible);
            oscilloscopeCanvas.setVisible(true);

            // Enable controls
            FFTCheck.setDisable(false);
            autoCheck.setDisable(false);
            FreezeCheck.setDisable(false);
            
        } else {
            isRunning = false;
            deviceClient.sendCommand(DeviceCommand.STOP_ACQUISITION);
            toggleButtonStart.setText("Start");

            // Save and disable FFTCheck 
            fftVisible = FFTCheck.isSelected();
            FFTCheck.setSelected(false);
            FFTCheck.setDisable(true); 
            FreezeCheck.setDisable(true);
            autoCheck.setDisable(true);  

            // Hide canvases
            fftCanvas.setVisible(false);
            fftCanvas.setManaged(false);
            oscilloscopeCanvas.setVisible(false);

            // Clear canvases
            clearCanvas(fftCanvas);
            clearCanvas(oscilloscopeCanvas);
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
            lastResult = result;
            newDataAvailable = true;
        }

        @Override
        public void onError(String message){
                connectionStatus.setText("Error: " + message);
                connectionStatus.setStyle("-fx-text-fill: red;");
        }
    };


     //Render loop
    private long lastFrameTime = 0;
    private static final long FRAME_INTERVAL = 33_333_333; // 30 FPS

    private void startRenderLoop() { 
        new AnimationTimer() { 
            @Override 
            public void handle(long now) { 
                if (!isRunning) return; 
                if (now - lastFrameTime < FRAME_INTERVAL) return; 
                lastFrameTime = now; 
                renderFrame(); 
            } 
        }.start(); 
    }
        private void renderFrame() { 
            if (FreezeCheck.isSelected()) { 
                if (lastResult != null) 
                    drawAll(lastResult); 
                return; } 
                if (!newDataAvailable) return; 
                newDataAvailable = false; 
                if (lastResult != null) { 
                    updateLabels(lastResult); 
                    drawAll(lastResult); } } 
                    
        private void drawAll(SignalResult result) { 
        
            timeRenderer.draw( oscilloscopeCanvas.getGraphicsContext2D(), 
            result.timeDomainData(), timeMin, timeMax, 
            oscilloscopeCanvas.getWidth(), 
            oscilloscopeCanvas.getHeight(), 
            FreezeCheck.isSelected() && cursorActive, cursorX, cursorY ); 
            
            if (FFTCheck.isSelected() && fftCanvas.isVisible()) { 
                fftRenderer.draw( fftCanvas.getGraphicsContext2D(), 
                result.freqDomainData(), 
                fftMax, 
                getSamplingRate(), 
                fftCanvas.getWidth(), 
                fftCanvas.getHeight() ); 
            } 
        } 
        
        @FXML void autoscaling() { 
            if (!isRunning) { 
                autoCheck.setSelected(false); 
                return; } 
                if (autoCheck.isSelected() && lastResult != null) { 
                    double[] scaled = autoscaler.scaleTime(lastResult.timeDomainData()); 
                    timeMin = scaled[0]; timeMax = scaled[1]; 
                    fftMax = autoscaler.scaleFFT(lastResult.freqDomainData()); 
                } else { 
                    timeMin = 0.0; 
                    timeMax = 5.0; 
                    fftMax = 5.0; 
                }
             }

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

    private double getSamplingRate() { 
        return switch (samplingFreq.getValue()) { 
            case "1 kHz" -> 1000.0; 
            case "10 kHz" -> 10000.0; 
            case "20 kHz" -> 20000.0; 
            default -> 1000.0; }; }
   

//     //Draw Time Domain
//    if (FreezeCheck.isSelected() && cursorActive && lastResult != null) { 
//             int index = (int) ((cursorX / w) * total); 
//             index = Math.max(0, Math.min(index, total - 1)); 
            
//             double norm = (samples[index] - timeMin) / range; 
//             double sampleY = h - (norm * h); 
//             double distance = Math.abs(cursorY - sampleY); 
            
//             if (distance < 8) { 
//                 gc.setFill(Color.RED); 
//                 gc.fillOval(cursorX - 3, sampleY - 3, 6, 6);
//                 String text = String.format("%.3f V", samples[index]); 
//                 gc.setFill(Color.color(0, 0, 0, 0.75)); 
//                 gc.fillRoundRect(cursorX + 10, sampleY - 15, 60, 20, 5, 5); 
//                 gc.setFill(Color.WHITE); 
//                 gc.fillText(text, cursorX + 15, sampleY); 
//             } 
//         } 

    //FFT Visualization
    @FXML
    void FFTVisualization(ActionEvent event) {
        if(FFTCheck.isSelected()) {
            fftCanvas.setVisible(true);
            fftCanvas.setManaged(true);
            splitPane.setDividerPositions(0.5);
            splitPane.requestLayout();

            if(lastResult != null)
            {
              drawAll(lastResult);
            }
        } else {
            fftCanvas.setVisible(false);
            fftCanvas.setManaged(false);
            splitPane.setDividerPositions(1.0);
        }
    }

//     //Draw FFT Domain
   

    
    @FXML
    void freeze(ActionEvent event) {
        
    }

    //Clear Canvas
    private void clearCanvas(Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

//     //Autoscale Time Domain
//     private void autoscaleTimeDomain() {
//        if(lastResult == null) return;

//         double[] lastTimeData = lastResult.timeDomainData();
//         if(lastTimeData.length == 0 || lastTimeData.length < 2) return;

//        double min = Double.POSITIVE_INFINITY;
//        double max = Double.NEGATIVE_INFINITY;   

//        for(double v : lastTimeData) {
//            if(v < min) min = v;
//            if(v > max) max = v;
//        }

//        if(min == max) {
//            min -= 1.0;
//            max += 1.0;
//        }

//        timeMin = min;
//        timeMax = max;
        
//     }

//     //Autoscale FFT
//     private void autoscaleFFT() {
        
//         if(lastResult == null) return;
//         double[] lastFFTData = lastResult.freqDomainData();
//         if(lastFFTData.length == 0 || lastFFTData.length < 2) return;

//         double max = 0;

//         for(double v : lastFFTData) {
//             if(v > max) max = v;
//         }

//         if(max == 0) {
//             max = 1.0;
//         }

//         fftMax = max;
//     }

}


   

    

    

