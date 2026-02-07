package pl.polsl.rtsa.controller;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import pl.polsl.rtsa.api.SignalAnalyzerApi;
import pl.polsl.rtsa.api.dto.ConnectionStatus;
import pl.polsl.rtsa.api.dto.SignalData;
import pl.polsl.rtsa.api.dto.SignalStatistics;
import pl.polsl.rtsa.api.exception.ConnectionException;
import pl.polsl.rtsa.api.exception.DeviceException;
import pl.polsl.rtsa.config.AppConfig;

/**
 * Main JavaFX controller for the JSignalAnalysis UI.
 * <p>
 * Manages all user interactions (connect, start/stop, zoom, trigger, freeze),
 * drives the 30 FPS render loop via {@link AnimationTimer}, and bridges
 * the {@link SignalAnalyzerApi} backend with the canvas-based oscilloscope
 * and FFT views.
 * </p>
 *
 * <h2>Render Pipeline:</h2>
 * <ol>
 * <li>Backend delivers {@link SignalData} via callback (background thread)</li>
 * <li>Data is posted to the FX thread with {@code Platform.runLater()}</li>
 * <li>AnimationTimer picks it up at ~30 FPS and redraws both canvases</li>
 * </ol>
 */
public class MainController {

    // ---- API ----
    private final SignalAnalyzerApi api = AppConfig.getInstance().isUseMock()
            ? SignalAnalyzerApi.createMock()
            : SignalAnalyzerApi.create();

    // ---- Renderers ----
    private final TimeDomainRen timeRenderer = new TimeDomainRen();
    private final FFTDomainRen fftRenderer = new FFTDomainRen();
    private final Autoscaler autoscaler = new Autoscaler();

    // ---- Voltage / magnitude ranges ----
    private double voltageMin = -5.0;
    private double voltageMax = 5.0;
    private double magnitudeMax = 1.0;

    // ---- State ----
    private volatile SignalData lastSignalData;
    private volatile boolean newDataAvailable = false;
    private boolean isRunning = false;
    private boolean needsRedraw = true;

    // ---- Cursor ----
    private double cursorX = -1, cursorY = -1;
    private boolean cursorActive = false;

    // ---- Animation ----
    private AnimationTimer animationTimer;
    private long lastFrameTime = 0;
    private static final long FRAME_INTERVAL = 33_333_333L; // ~30 FPS

    // ================================================================
    // FXML bindings
    // ================================================================

    @FXML
    private Canvas oscilloscopeCanvas;
    @FXML
    private Canvas fftCanvas;
    @FXML
    private Pane timeContainer;
    @FXML
    private Pane fftContainer;
    @FXML
    private VBox timeSection;
    @FXML
    private VBox fftSection;
    @FXML
    private VBox plotArea;

    @FXML
    private Slider timeZoomX;
    @FXML
    private Slider timeZoomY;
    @FXML
    private Slider fftZoomX;
    @FXML
    private Slider fftZoomY;
    @FXML
    private Label timeZoomXLabel;
    @FXML
    private Label timeZoomYLabel;
    @FXML
    private Label fftZoomXLabel;
    @FXML
    private Label fftZoomYLabel;

    @FXML
    private ComboBox<String> portComboBox;
    @FXML
    private ComboBox<String> samplingFreq;
    @FXML
    private Button connectButton;
    @FXML
    private Label connectionStatus;
    @FXML
    private ToggleButton toggleButtonStart;

    @FXML
    private Label Vrms;
    @FXML
    private Label Vmax;
    @FXML
    private Label Vmin;
    @FXML
    private Label dominantFreqLabel;

    @FXML
    private CheckBox FFTCheck;
    @FXML
    private CheckBox FreezeCheck;
    @FXML
    private CheckBox autoCheck;
    @FXML
    private CheckBox gridCheck;

    // ---- Trigger controls ----
    @FXML
    private CheckBox triggerCheck;
    @FXML
    private Slider triggerLevel;
    @FXML
    private Label triggerLevelLabel;
    @FXML
    private Label triggerStatusLabel;

    // ================================================================
    // Initialization
    // ================================================================

    /**
     * FXML initialisation hook — called automatically after all @FXML fields
     * have been injected.
     * <p>
     * Wires canvas resize listeners, zoom/trigger slider listeners, populates
     * the port and sample-rate combo boxes, registers API callbacks, and
     * starts the render loop.
     * </p>
     */
    @FXML
    public void initialize() {

        // --- Canvas tracks its Pane container via listeners (NOT bindings) ---
        hookCanvasToPane(oscilloscopeCanvas, timeContainer);
        hookCanvasToPane(fftCanvas, fftContainer);

        // --- FFT section hidden by default ---
        fftSection.setVisible(false);
        fftSection.setManaged(false);

        // --- Zoom slider → label + dirty flag ---
        timeZoomX.valueProperty().addListener((o, a, nv) -> {
            timeZoomXLabel.setText(String.format("%.1fx", nv.doubleValue()));
            needsRedraw = true;
        });
        timeZoomY.valueProperty().addListener((o, a, nv) -> {
            timeZoomYLabel.setText(String.format("%.1fx", nv.doubleValue()));
            needsRedraw = true;
        });
        fftZoomX.valueProperty().addListener((o, a, nv) -> {
            fftZoomXLabel.setText(String.format("%.1fx", nv.doubleValue()));
            needsRedraw = true;
        });
        fftZoomY.valueProperty().addListener((o, a, nv) -> {
            fftZoomYLabel.setText(String.format("%.1fx", nv.doubleValue()));
            needsRedraw = true;
        });

        // --- Trigger slider ---
        triggerLevel.valueProperty().addListener((o, a, nv) -> {
            triggerLevelLabel.setText(String.format("%.2f V", nv.doubleValue()));
            needsRedraw = true;
        });
        triggerCheck.selectedProperty().addListener((o, a, b) -> needsRedraw = true);

        // --- Freeze / grid checkboxes ---
        FreezeCheck.selectedProperty().addListener((o, a, b) -> needsRedraw = true);
        gridCheck.selectedProperty().addListener((o, a, nv) -> {
            timeRenderer.setShowGrid(nv);
            fftRenderer.setShowGrid(nv);
            needsRedraw = true;
        });

        // --- Populate controls ---
        portComboBox.getItems().addAll(api.getAvailablePorts().ports());
        samplingFreq.getItems().addAll("1 kHz", "10 kHz", "20 kHz");
        samplingFreq.getSelectionModel().selectFirst();

        connectionStatus.setText("Nie polaczono");
        connectionStatus.setStyle("-fx-text-fill: #ef4444;");

        // --- API callbacks ---
        api.setDataCallback(this::onSignalData);
        api.setErrorCallback(this::onError);
        api.setConnectionCallback(this::onConnectionChange);

        // --- Mouse cursor (time-domain only) ---
        oscilloscopeCanvas.setOnMouseMoved(e -> {
            cursorX = e.getX();
            cursorY = e.getY();
            cursorActive = true;
            needsRedraw = true;
        });
        oscilloscopeCanvas.setOnMouseExited(e -> {
            cursorActive = false;
            needsRedraw = true;
        });

        // --- Start render loop ---
        startRenderLoop();
    }

    /**
     * Manually track the container Pane size and resize the Canvas.
     * This avoids the bidirectional layout-feedback loop that binding caused.
     */
    /**
     * Binds a {@link Canvas} size to its parent {@link Pane} so the canvas
     * resizes automatically when the window layout changes.
     *
     * @param canvas    the canvas to resize
     * @param container the parent pane whose dimensions drive the canvas size
     */
    private void hookCanvasToPane(Canvas canvas, Pane container) {
        container.widthProperty().addListener((obs, o, nv) -> {
            double w = nv.doubleValue();
            if (w > 0) {
                canvas.setWidth(w);
                needsRedraw = true;
            }
        });
        container.heightProperty().addListener((obs, o, nv) -> {
            double h = nv.doubleValue();
            if (h > 0) {
                canvas.setHeight(h);
                needsRedraw = true;
            }
        });
    }

    // ================================================================
    // Actions
    // ================================================================

    /**
     * Handles the “Connect” button press — attempts to connect to the
     * serial port selected in {@link #portComboBox}.
     *
     * @param event the originating action event
     */
    @FXML
    void handleConnect(ActionEvent event) {
        String port = portComboBox.getValue();
        if (port == null) {
            connectionStatus.setText("Nie wybrano portu");
            connectionStatus.setStyle("-fx-text-fill: #f59e0b;");
            return;
        }
        try {
            api.connect(port);
        } catch (ConnectionException e) {
            connectionStatus.setText("Blad: " + e.getMessage());
            connectionStatus.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    /**
     * Handles the Start/Stop toggle button — starts or stops data acquisition
     * and updates the button label accordingly.
     *
     * @param event the originating action event
     */
    @FXML
    void handleStart(ActionEvent event) {
        try {
            if (toggleButtonStart.isSelected()) {
                if (!api.isConnected()) {
                    toggleButtonStart.setSelected(false);
                    connectionStatus.setText("Urzadzenie nie jest podlaczone");
                    connectionStatus.setStyle("-fx-text-fill: #ef4444;");
                    return;
                }
                isRunning = true;
                api.startAcquisition();
                toggleButtonStart.setText("Stop");
            } else {
                isRunning = false;
                api.stopAcquisition();
                toggleButtonStart.setText("Start");
                // Keep last frame on screen — no canvas clearing
                needsRedraw = true;
            }
        } catch (DeviceException e) {
            toggleButtonStart.setSelected(false);
            isRunning = false;
            ErrorHandler.handle(e, connectionStatus);
        }
    }

    /**
     * Handles sample-rate combo box changes — sends the appropriate
     * rate command to the hardware.
     *
     * @param event the originating action event
     */
    @FXML
    void changeSamplingFreq(ActionEvent event) {
        String freq = samplingFreq.getValue();
        if (freq == null)
            return;
        try {
            switch (freq) {
                case "1 kHz" -> api.setSampleRate1kHz();
                case "10 kHz" -> api.setSampleRate10kHz();
                case "20 kHz" -> api.setSampleRate20kHz();
            }
        } catch (Exception e) {
            ErrorHandler.handle(e, connectionStatus);
        }
    }

    /**
     * Toggles the FFT spectrum panel visibility based on the FFT checkbox state.
     *
     * @param event the originating action event
     */
    @FXML
    void FFTVisualization(ActionEvent event) {
        boolean show = FFTCheck.isSelected();
        fftSection.setVisible(show);
        fftSection.setManaged(show);
        needsRedraw = true;
    }

    /**
     * Recomputes voltage and magnitude ranges from the current signal data
     * when the autoscale checkbox is selected.
     */
    @FXML
    void autoscaling() {
        if (autoCheck.isSelected() && lastSignalData != null) {
            double[] scaled = autoscaler.scaleTime(lastSignalData.timeDomainData());
            voltageMin = scaled[0];
            voltageMax = scaled[1];
            magnitudeMax = autoscaler.scaleFFT(lastSignalData.freqDomainData());
            needsRedraw = true;
        }
    }

    // ================================================================
    // API callbacks (may arrive on background thread)
    // ================================================================

    /**
     * API data callback — stores the latest signal data for the next render
     * frame unless the display is frozen.
     *
     * @param data the new signal data received from the backend
     */
    private void onSignalData(SignalData data) {
        Platform.runLater(() -> {
            if (FreezeCheck.isSelected())
                return;
            lastSignalData = data;
            newDataAvailable = true;
        });
    }

    /**
     * API error callback — displays the error message in the status label.
     *
     * @param message the error description
     */
    private void onError(String message) {
        Platform.runLater(() -> {
            connectionStatus.setText("Blad: " + message);
            connectionStatus.setStyle("-fx-text-fill: #ef4444;");
        });
    }

    /**
     * API connection-state callback — updates the status label colour and text.
     *
     * @param status the new connection status
     */
    private void onConnectionChange(ConnectionStatus status) {
        Platform.runLater(() -> {
            if (status.connected()) {
                connectionStatus.setText("Polaczono");
                connectionStatus.setStyle("-fx-text-fill: #22c55e;");
            } else {
                connectionStatus.setText("Nie polaczono");
                connectionStatus.setStyle("-fx-text-fill: #ef4444;");
            }
        });
    }

    // ================================================================
    // Render loop
    // ================================================================

    /**
     * Creates and starts the {@link AnimationTimer} that drives the render loop
     * at approximately 30 FPS.
     */
    private void startRenderLoop() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastFrameTime < FRAME_INTERVAL)
                    return;
                lastFrameTime = now;
                renderFrame();
            }
        };
        animationTimer.start();
    }

    /**
     * Renders a single frame — invoked from the {@link AnimationTimer}.
     * <p>
     * Handles empty-canvas drawing, frozen/stopped states, autoscaling,
     * label updates, and delegates to {@link #drawAll(SignalData)}.
     * </p>
     */
    private void renderFrame() {

        // No data yet → placeholder
        if (lastSignalData == null) {
            if (needsRedraw) {
                timeRenderer.drawEmpty(oscilloscopeCanvas.getGraphicsContext2D(),
                        oscilloscopeCanvas.getWidth(), oscilloscopeCanvas.getHeight());
                if (fftSection.isVisible()) {
                    fftRenderer.drawEmpty(fftCanvas.getGraphicsContext2D(),
                            fftCanvas.getWidth(), fftCanvas.getHeight());
                }
                needsRedraw = false;
            }
            return;
        }

        // Frozen or stopped → redraw only when dirty
        if (FreezeCheck.isSelected() || !isRunning) {
            if (needsRedraw) {
                drawAll(lastSignalData);
                needsRedraw = false;
            }
            return;
        }

        // Running → consume new data
        if (!newDataAvailable && !needsRedraw)
            return;
        newDataAvailable = false;
        needsRedraw = false;

        // Continuous autoscaling
        if (autoCheck.isSelected()) {
            double[] scaled = autoscaler.scaleTime(lastSignalData.timeDomainData());
            voltageMin = scaled[0];
            voltageMax = scaled[1];
            magnitudeMax = autoscaler.scaleFFT(lastSignalData.freqDomainData());

            // Keep trigger slider range in sync with actual signal
            updateTriggerSliderRange(voltageMin, voltageMax);
        }

        updateLabels(lastSignalData);
        drawAll(lastSignalData);
    }

    // ================================================================
    // Drawing
    // ================================================================

    /**
     * Draws the time-domain and (optionally) FFT canvases for the given data.
     * <p>
     * Applies zoom, trigger detection, window slicing, and delegates to
     * {@link TimeDomainRen} and {@link FFTDomainRen}.
     * </p>
     *
     * @param data the signal data to render
     */
    private void drawAll(SignalData data) {

        double w = oscilloscopeCanvas.getWidth();
        double h = oscilloscopeCanvas.getHeight();
        if (w <= 0 || h <= 0)
            return;

        double sampleRate = data.sampleRate();
        double[] samples = data.timeDomainData();
        if (samples == null || samples.length < 2 || sampleRate <= 0)
            return;

        double totalDuration = data.getDurationSeconds();
        if (totalDuration <= 0)
            totalDuration = 1.0;

        double zoomX = timeZoomX.getValue();
        double zoomY = timeZoomY.getValue();

        // ======== Time window (trigger-aware) ========

        double tStart, tEnd;

        if (triggerCheck.isSelected()) {
            // Rising-edge trigger
            double threshold = triggerLevel.getValue();

            int windowSamples = (int) (samples.length / zoomX);
            if (windowSamples < 2)
                windowSamples = 2;

            // Trigger sits at 25 % from left edge
            int preTrigger = windowSamples / 4;

            // Prefer a crossing far enough from the buffer start so we
            // have real data (no zero-padding) for the pre-trigger portion.
            int trigIdx = TriggerDetector.findRisingEdge(
                    samples, threshold, preTrigger, samples.length);
            // Fallback: accept any crossing (left side may be shorter)
            if (trigIdx < 0) {
                trigIdx = TriggerDetector.findRisingEdge(samples, threshold);
            }

            if (trigIdx >= 0) {
                // Extract sub-array — never zero-pad; clamp to buffer bounds
                int start = Math.max(0, trigIdx - preTrigger);
                int end = Math.min(samples.length, start + windowSamples);
                int winLen = end - start;
                if (winLen < 2)
                    winLen = 2;

                double[] triggered = new double[winLen];
                System.arraycopy(samples, start, triggered, 0, winLen);
                samples = triggered;

                // Time axis: trigger at t = 0
                int trigPosInWindow = trigIdx - start;
                tStart = -(double) trigPosInWindow / sampleRate;
                tEnd = (double) (winLen - 1 - trigPosInWindow) / sampleRate;

                triggerStatusLabel.setText("TRIGGERED");
                triggerStatusLabel.setStyle("-fx-text-fill: #22c55e;");
            } else {
                // No edge found — fall back to free-run
                double visibleDuration = totalDuration / zoomX;
                double tCenter = totalDuration / 2.0;
                tStart = tCenter - visibleDuration / 2.0;
                tEnd = tCenter + visibleDuration / 2.0;
                tStart = Math.max(0, tStart);
                tEnd = Math.min(totalDuration, tEnd);
                triggerStatusLabel.setText("BRAK ZBOCZA");
                triggerStatusLabel.setStyle("-fx-text-fill: #f59e0b;");
            }
        } else {
            triggerStatusLabel.setText("");
            // Free-run, zoom centred
            double visibleDuration = totalDuration / zoomX;
            double tCenter = totalDuration / 2.0;
            tStart = tCenter - visibleDuration / 2.0;
            tEnd = tCenter + visibleDuration / 2.0;

            if (tStart < 0) {
                tEnd -= tStart;
                tStart = 0;
            }
            if (tEnd > totalDuration) {
                tStart -= (tEnd - totalDuration);
                tEnd = totalDuration;
            }
            tStart = Math.max(0, tStart);
            tEnd = Math.min(totalDuration, tEnd);

            // Slice the samples array to match the visible window so the
            // renderer (which maps index 0→tStart, N-1→tEnd) shows the
            // correct portion — otherwise zoom X has no visible effect.
            if (zoomX > 1.001) {
                int idxStart = (int) Math.floor(tStart / totalDuration * samples.length);
                int idxEnd = (int) Math.ceil(tEnd / totalDuration * samples.length);
                idxStart = Math.max(0, idxStart);
                idxEnd = Math.min(samples.length, idxEnd);
                int len = idxEnd - idxStart;
                if (len >= 2) {
                    double[] sliced = new double[len];
                    System.arraycopy(samples, idxStart, sliced, 0, len);
                    samples = sliced;
                }
            }
        }

        // Voltage zoom
        double vRange = (voltageMax - voltageMin) / zoomY;
        double vCenter = (voltageMax + voltageMin) / 2.0;
        double vMin = vCenter - vRange / 2.0;
        double vMax = vCenter + vRange / 2.0;

        // Draw trigger threshold line (passed via an extra field)
        boolean showTrigLine = triggerCheck.isSelected();
        double trigThreshold = triggerLevel.getValue();

        timeRenderer.draw(oscilloscopeCanvas.getGraphicsContext2D(),
                samples, vMin, vMax, tStart, tEnd, sampleRate,
                w, h,
                cursorActive, cursorX, cursorY,
                showTrigLine, trigThreshold);

        // ======== FFT domain ========

        if (FFTCheck.isSelected() && fftSection.isVisible()) {
            double fw = fftCanvas.getWidth();
            double fh = fftCanvas.getHeight();
            if (fw <= 0 || fh <= 0)
                return;

            double nyquist = data.getNyquistFrequency();
            if (nyquist <= 0)
                nyquist = sampleRate / 2.0;

            double fZoomX = fftZoomX.getValue();
            double fZoomY = fftZoomY.getValue();

            double visibleFreq = nyquist / fZoomX;
            double mMax = magnitudeMax / fZoomY;

            fftRenderer.draw(fftCanvas.getGraphicsContext2D(),
                    data.freqDomainData(),
                    0.0, mMax, 0.0, visibleFreq, sampleRate,
                    fw, fh);
        }
    }

    /**
     * Keep trigger slider min/max in sync with actual signal voltage range.
     * If the current trigger level is outside the new range, snap it to midpoint.
     */
    /**
     * Whether the trigger slider range has been initialised from autoscale data.
     */
    private boolean triggerRangeInitialized = false;

    /**
     * Updates the trigger-level slider's min/max to match the current
     * autoscaled voltage range.
     *
     * @param vMin the current minimum voltage
     * @param vMax the current maximum voltage
     */
    private void updateTriggerSliderRange(double vMin, double vMax) {
        double margin = (vMax - vMin) * 0.05;
        double newMin = vMin - margin;
        double newMax = vMax + margin;

        // Avoid constant jitter — only update if range changed significantly
        if (Math.abs(triggerLevel.getMin() - newMin) > 0.01
                || Math.abs(triggerLevel.getMax() - newMax) > 0.01) {
            triggerLevel.setMin(newMin);
            triggerLevel.setMax(newMax);
            triggerLevel.setMajorTickUnit((newMax - newMin) / 5.0);
        }

        // First time: auto-center the trigger at signal midpoint
        if (!triggerRangeInitialized) {
            double mid = (vMin + vMax) / 2.0;
            triggerLevel.setValue(mid);
            triggerRangeInitialized = true;
        }
    }

    /**
     * Refreshes the on-screen statistics labels (Vmax, Vmin, RMS, f0) from
     * the given signal data.
     *
     * @param data the signal data whose statistics to display
     */
    private void updateLabels(SignalData data) {
        SignalStatistics stats = data.statistics();
        Vmax.setText(String.format("Max: %.3f V", stats.maxVoltage()));
        Vmin.setText(String.format("Min: %.3f V", stats.minVoltage()));
        Vrms.setText(String.format("RMS: %.3f V", stats.rmsVoltage()));
        dominantFreqLabel.setText(String.format("f0: %.1f Hz", stats.dominantFreq()));
    }

    // ================================================================
    // Shutdown
    // ================================================================

    /**
     * Shuts down the render loop and the backend API.
     * Called from {@link pl.polsl.rtsa.App#stop()} during application exit.
     */
    public void shutdown() {
        isRunning = false;
        if (animationTimer != null)
            animationTimer.stop();
        api.shutdown();
    }
}
