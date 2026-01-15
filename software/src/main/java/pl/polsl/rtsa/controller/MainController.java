package pl.polsl.rtsa.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.event.ActionEvent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.AnchorPane;
import javafx.animation.AnimationTimer;
import pl.polsl.rtsa.api.SignalAnalyzerApi;
import pl.polsl.rtsa.api.dto.ConnectionStatus;
import pl.polsl.rtsa.api.dto.SignalData;
import pl.polsl.rtsa.api.dto.SignalStatistics;
import pl.polsl.rtsa.api.exception.ConnectionException;


public class MainController {

    private final SignalAnalyzerApi api = SignalAnalyzerApi.create();

    private final FFTDomainRen fftRenderer = new FFTDomainRen();
    private final TimeDomainRen timeRenderer = new TimeDomainRen();
    private final Autoscaler autoscaler = new Autoscaler();

    private double timeMin = 0.0;
    private double timeMax = 5.0;
    private double fftMax = 5.0;

    private volatile boolean newDataAvailable = false;
    private volatile SignalData lastSignalData;
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

        // Populate ports from API
        portComboBox.getItems().addAll(api.getAvailablePorts().ports());

        samplingFreq.getItems().addAll("1 kHz", "10 kHz", "20 kHz");
        samplingFreq.getSelectionModel().selectFirst();

        connectionStatus.setText("Nie połączono");
        connectionStatus.setStyle("-fx-text-fill: red;");

        toggleButtonStart.setText("Start");

        // Setup API callbacks
        api.setDataCallback(this::onSignalData);
        api.setErrorCallback(this::onError);
        api.setConnectionCallback(this::onConnectionChange);

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

        try {
            api.connect(port);
            // Connection callback will update the UI
        } catch (ConnectionException e) {
            connectionStatus.setText("Błąd: " + e.getMessage());
            connectionStatus.setStyle("-fx-text-fill: red;");
        }
    }

    //Start/Stop Acquisition
    @FXML
    void handleStart(ActionEvent event) {

        if(toggleButtonStart.isSelected()) {
            isRunning = true;
            api.startAcquisition();
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
            api.stopAcquisition();
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
            case "1 kHz" -> api.setSampleRate1kHz();
            case "10 kHz" -> api.setSampleRate10kHz();
            case "20 kHz" -> api.setSampleRate20kHz();
        }
    }

    // API Callbacks
    private void onSignalData(SignalData data) {
        Platform.runLater(() -> {
            if (FreezeCheck.isSelected()) return;
            lastSignalData = data;
            newDataAvailable = true;
        });
    }

    private void onError(String message) {
        Platform.runLater(() -> {
            connectionStatus.setText("Error: " + message);
            connectionStatus.setStyle("-fx-text-fill: red;");
        });
    }

    private void onConnectionChange(ConnectionStatus status) {
        Platform.runLater(() -> {
            if (status.connected()) {
                connectionStatus.setText("Połączono");
                connectionStatus.setStyle("-fx-text-fill: green;");
            } else {
                connectionStatus.setText("Nie połączono");
                connectionStatus.setStyle("-fx-text-fill: red;");
            }
        });
    }

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
            if (lastSignalData != null) 
                drawAll(lastSignalData); 
            return; 
        } 
        if (!newDataAvailable) return; 
        newDataAvailable = false; 
        if (lastSignalData != null) { 
            updateLabels(lastSignalData); 
            drawAll(lastSignalData); 
        } 
    } 
                    
    private void drawAll(SignalData data) { 
        timeRenderer.draw(
            oscilloscopeCanvas.getGraphicsContext2D(), 
            data.timeDomainData(), 
            timeMin, timeMax, 
            oscilloscopeCanvas.getWidth(), 
            oscilloscopeCanvas.getHeight(), 
            FreezeCheck.isSelected() && cursorActive, cursorX, cursorY
        ); 
        
        if (FFTCheck.isSelected() && fftCanvas.isVisible()) { 
            fftRenderer.draw(
                fftCanvas.getGraphicsContext2D(), 
                data.freqDomainData(), 
                fftMax, 
                api.getCurrentSampleRate(), 
                fftCanvas.getWidth(), 
                fftCanvas.getHeight()
            ); 
        } 
    } 
    
    //Autoscaling
    @FXML 
    void autoscaling() { 
        if (!isRunning) { 
            autoCheck.setSelected(false); 
            return; 
        } 
        if (autoCheck.isSelected() && lastSignalData != null) { 
            double[] scaled = autoscaler.scaleTime(lastSignalData.timeDomainData()); 
            timeMin = scaled[0]; 
            timeMax = scaled[1]; 
            fftMax = autoscaler.scaleFFT(lastSignalData.freqDomainData()); 
        } else { 
            timeMin = 0.0; 
            timeMax = 5.0; 
            fftMax = 5.0; 
        }
    }

    //Labels - now uses pre-computed statistics from API
    private void updateLabels(SignalData data) {
        SignalStatistics stats = data.statistics();
        Vmax.setText(String.format("Vmax: %.2f V", stats.maxVoltage())); 
        Vmin.setText(String.format("Vmin: %.2f V", stats.minVoltage())); 
        Vrms.setText(String.format("Vrms: %.2f V", stats.rmsVoltage()));
    }
   
    //FFT Visualization
    @FXML
    void FFTVisualization(ActionEvent event) {
        if(FFTCheck.isSelected()) {
            fftCanvas.setVisible(true);
            fftCanvas.setManaged(true);
            splitPane.setDividerPositions(0.5);
            splitPane.requestLayout();

            if(lastSignalData != null) {
                drawAll(lastSignalData);
            }
        } else {
            fftCanvas.setVisible(false);
            fftCanvas.setManaged(false);
            splitPane.setDividerPositions(1.0);
        }
    }

    //Clear Canvas
    private void clearCanvas(Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    /**
     * Shuts down the API gracefully.
     * Should be called when the application is closing.
     */
    public void shutdown() {
        api.shutdown();
    }
}


   

    

    

