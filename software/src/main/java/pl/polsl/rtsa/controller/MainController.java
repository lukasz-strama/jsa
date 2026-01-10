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
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.AnchorPane;
import pl.polsl.rtsa.hardware.DataListener;
import pl.polsl.rtsa.model.DeviceCommand;
import pl.polsl.rtsa.model.SignalResult;
import java.util.Arrays;
import javafx.scene.control.SplitPane;

import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.animation.AnimationTimer;


public class MainController{


    private DeviceClient deviceClient = new MockDeviceClient();

    private double[] lastTimeData = new double[0];
    private double[] lastFFTData = new double[0];
    private static final int MAX_POINTS = 50000; 
    private final double[] xPoints = new double[MAX_POINTS]; 
    private final double[] yPoints = new double[MAX_POINTS];

    private GraphicsContext gc;
    private boolean isRunning = false;
    private boolean fftVisible = false;

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

        // Bind canvas sizes to their parent panes - to make them responsive
        oscilloscopeCanvas.widthProperty().bind(timePane.widthProperty());
        oscilloscopeCanvas.heightProperty().bind(timePane.heightProperty());
        fftCanvas.widthProperty().bind(fftPane.widthProperty());
        fftCanvas.heightProperty().bind(fftPane.heightProperty());

        portComboBox.getItems().addAll(deviceClient.getAvailablePorts());

        samplingFreq.getItems().addAll("1 kHz", "10 kHz", "20 kHz");
        samplingFreq.getSelectionModel().selectFirst();

        connectionStatus.setText("Nie połączono");
        connectionStatus.setStyle("-fx-text-fill: red;");

        toggleButtonStart.setText("Start");

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

            lastTimeData = result.timeDomainData();
            //lastFFTData = result.freqDomainData();
            lastFFTData = computeFFT(lastTimeData);

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

    private long lastFrameTime = 0;
    private static final long FRAME_INTERVAL = 16_666_666; // ~60 FPS

    private void startRenderLoop() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if(now - lastFrameTime < FRAME_INTERVAL) return; 
                lastFrameTime = now;

                if(FreezeCheck.isSelected()) return;

                drawTimeDomain(lastTimeData);

                if(FFTCheck.isSelected() && fftCanvas.isVisible()) {
                    drawFFTDomain(lastFFTData);
                }

                if(!isRunning) return;
            
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
        gc.fillRect(0, 0, w, h);

        int n = samples.length;
        if(n<2) return;
        if(n > MAX_POINTS) n = MAX_POINTS;

        double xScale = w / (double) (n - 1);

        double min, max;

        if(autoCheck.isSelected()) {
            min = Double.POSITIVE_INFINITY; 
            max = Double.NEGATIVE_INFINITY; 
            
            for(int i = 0; i<n; i++){
                double v = samples[i];
                if(v < min) min = v;
                if(v > max) max = v;
            }
            
        } else {
            min = 0.0;
            max = 5.0;
        }

        double range = max - min;
        if(range == 0) range = 1;


        for(int i = 0; i < n; i++) {
            xPoints[i] = i * xScale;
            double norm = (samples[i] - min) / range;
            yPoints[i] = h - (norm * h);
        }

        gc.strokePolyline(xPoints, yPoints, n);
    }

    //FFT Visualization
    @FXML
    void FFTVisualization(ActionEvent event) {
        if(FFTCheck.isSelected()) {
            fftCanvas.setVisible(true);
            fftCanvas.setManaged(true);
            splitPane.setDividerPositions(0.5);
            splitPane.requestLayout();
            drawFFTDomain(lastFFTData);
        
        } else {
            fftCanvas.setVisible(false);
            fftCanvas.setManaged(false);
            splitPane.setDividerPositions(1.0);
        }
    }

    //Draw FFT Domain
    private void drawFFTDomain(double[] fft) {
    if(fftCanvas == null || fft.length < 2) return;
    if (!fftCanvas.isVisible()) return;

    GraphicsContext gc = fftCanvas.getGraphicsContext2D();
    double w = fftCanvas.getWidth();
    double h = fftCanvas.getHeight();
    if(w <=0 || h <=0) return;

    gc.clearRect(0, 0, w, h);
    gc.setFill(Color.BLACK); 
    gc.fillRect(0, 0, w, h);
    gc.setStroke(Color.ORANGE);
    gc.setLineWidth(1.5);
    

    int n = fft.length;
    double xScale = w / (double) (n - 1);
    double max = Arrays.stream(fft).map(Math::abs).max().orElse(1);
    if(max == 0) max = 1;

    double[] x = new double[n];
    double[] y = new double[n];

    for(int i = 0; i < n; i++) {
        x[i] = i * xScale;
        double norm = Math.abs(fft[i]) / max;
        y[i] = h - (norm * h);
    
    }

   // System.out.println("FFT length: " + fft.length);
   // System.out.println("FFT min: " + Arrays.stream(fft).min().orElse(0)); 
   // System.out.println("FFT max: " + Arrays.stream(fft).max().orElse(0));
    gc.strokePolyline(x, y, n);
}

    @FXML
    void autoscaling(ActionEvent event) {
        if(autoCheck.isSelected()) {
            // Implement autoscaling logic if needed
        } else {
            // Disable autoscaling logic if needed
        }
    }

    @FXML
    void freeze(ActionEvent event) {
        
    }

    //Clear Canvas
    private void clearCanvas(Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    //FTT for debugging
    private double[] computeFFT(double[] samples) { 
        int n = samples.length; 
        int m = 1; while (m < n) m <<= 1; 
        double[] real = new double[m]; 
        double[] imag = new double[m]; 
        System.arraycopy(samples, 0, real, 0, n);
        for (int len = 2; len <= m; len <<= 1) { 
            double ang = -2 * Math.PI / len; 
            double wlenR = Math.cos(ang); 
            double wlenI = Math.sin(ang); 
            for (int i = 0; i < m; i += len) { 
                double wr = 1; double wi = 0; 
                for (int j = 0; j < len / 2; j++) { 
                    int u = i + j; int v = i + j + len / 2; 
                    double r = real[v] * wr - imag[v] * wi; 
                    double im = real[v] * wi + imag[v] * wr; 
                    real[v] = real[u] - r; imag[v] = imag[u] - im; 
                    real[u] += r; imag[u] += im; 
                    double nextWr = wr * wlenR - wi * wlenI; 
                    wi = wr * wlenI + wi * wlenR; wr = nextWr; 
                } 
            } 
        } 
        double[] mag = new double[m / 2]; 
        for (int i = 0; i < mag.length; i++) { 
            mag[i] = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]); }
            
            return mag; }

}


   

    

    

