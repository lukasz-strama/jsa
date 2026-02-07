package pl.polsl.rtsa.controller;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Renders the time-domain oscilloscope view with:
 */
public class TimeDomainRen {

    // ---- Layout margins (px) ----
    static final double ML = 65; // left – Y-axis labels
    static final double MR = 15; // right
    static final double MT = 15; // top
    static final double MB = 35; // bottom – X-axis labels

    private static final int MAX_POINTS = 2048;
    private static final int GRID_DIVISIONS = 8;

    // ---- Palette ----
    private static final Color BG_COLOR = Color.web("#1a1a2e");
    private static final Color GRID_COLOR = Color.web("#2a2a4a");
    private static final Color AXIS_COLOR = Color.gray(0.4);
    private static final Color LABEL_COLOR = Color.gray(0.65);
    private static final Color ZERO_LINE_CLR = Color.web("#3a3a6a");
    private static final Color SIGNAL_COLOR = Color.web("#2dd4bf");
    private static final Color TRIGGER_COLOR = Color.web("#f43f5e");
    private static final Color TITLE_COLOR = Color.gray(0.5);

    private final double[] xBuf = new double[MAX_POINTS];
    private final double[] yBuf = new double[MAX_POINTS];
    private final CursorOverlay cursorOverlay = new CursorOverlay();

    private boolean showGrid = true;

    public void setShowGrid(boolean show) {
        this.showGrid = show;
    }

    // ================================================================
    // Public draw methods
    // ================================================================

    /**
     * Draw the time-domain view with signal data.
     *
     * @param gc         canvas graphics context
     * @param samples    raw voltage samples (V)
     * @param vMin       visible voltage minimum (V)
     * @param vMax       visible voltage maximum (V)
     * @param tStart     visible time-window start (s)
     * @param tEnd       visible time-window end (s)
     * @param sampleRate sampling rate (Hz)
     * @param w          canvas width (px)
     * @param h          canvas height (px)
     * @param cursorOn   is the mouse cursor inside the canvas?
     * @param cx         cursor X in canvas coordinates
     * @param cy         cursor Y in canvas coordinates
     */
    /**
     * Draw without trigger line (backward compatible).
     */
    public void draw(GraphicsContext gc, double[] samples,
            double vMin, double vMax,
            double tStart, double tEnd,
            double sampleRate,
            double w, double h,
            boolean cursorOn, double cx, double cy) {
        draw(gc, samples, vMin, vMax, tStart, tEnd, sampleRate,
                w, h, cursorOn, cx, cy, false, 0.0);
    }

    /**
     * Draw with optional trigger threshold line.
     */
    public void draw(GraphicsContext gc, double[] samples,
            double vMin, double vMax,
            double tStart, double tEnd,
            double sampleRate,
            double w, double h,
            boolean cursorOn, double cx, double cy,
            boolean showTrigger, double triggerThreshold) {

        if (w <= 0 || h <= 0)
            return;
        double pw = w - ML - MR;
        double ph = h - MT - MB;
        if (pw <= 0 || ph <= 0)
            return;

        // Background
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, w, h);

        // Grid + axes
        if (showGrid)
            drawGrid(gc, vMin, vMax, tStart, tEnd, pw, ph);
        drawBorder(gc, pw, ph);

        // Trigger threshold line
        if (showTrigger) {
            double vRange = vMax - vMin;
            if (vRange > 0 && triggerThreshold >= vMin && triggerThreshold <= vMax) {
                double trigY = MT + ph - ((triggerThreshold - vMin) / vRange) * ph;
                gc.setStroke(TRIGGER_COLOR);
                gc.setLineWidth(1.0);
                gc.setLineDashes(6, 4);
                gc.strokeLine(ML, trigY, ML + pw, trigY);
                gc.setLineDashes((double[]) null);
                gc.setFill(TRIGGER_COLOR);
                gc.setFont(Font.font("Monospaced", 10));
                gc.fillText("TRIG", ML + pw - 32, trigY - 4);
            }
        }

        // Signal trace
        if (samples != null && samples.length >= 2 && sampleRate > 0) {
            drawSignal(gc, samples, vMin, vMax, tStart, tEnd, sampleRate, pw, ph);

            if (cursorOn) {
                cursorOverlay.drawTimeDomain(gc, samples,
                        vMin, vMax, tStart, tEnd, sampleRate,
                        pw, ph, ML, MT, cx, cy);
            }
        } else {
            drawPlaceholder(gc, pw, ph, "Oczekiwanie na dane...");
        }

        // Tiny title
        gc.setFill(TITLE_COLOR);
        gc.setFont(Font.font("System", 10));
        gc.fillText("Domena czasowa", ML + 5, MT + 12);
    }

    /**
     * Draw an empty canvas with grid and placeholder text.
     * Used when no data has been received yet.
     */
    public void drawEmpty(GraphicsContext gc, double w, double h) {
        if (w <= 0 || h <= 0)
            return;
        double pw = w - ML - MR;
        double ph = h - MT - MB;
        if (pw <= 0 || ph <= 0)
            return;

        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, w, h);
        if (showGrid)
            drawGrid(gc, -5, 5, 0, 1, pw, ph);
        drawBorder(gc, pw, ph);
        drawPlaceholder(gc, pw, ph, "Oczekiwanie na dane...");
    }

    // ================================================================
    // Internal helpers
    // ================================================================

    private void drawGrid(GraphicsContext gc,
            double vMin, double vMax,
            double tStart, double tEnd,
            double pw, double ph) {
        double vRange = vMax - vMin;
        double tRange = tEnd - tStart;
        if (vRange <= 0 || tRange <= 0)
            return;

        gc.setFont(Font.font("Monospaced", 10));

        // — Y-axis grid (voltage) —
        var yGrid = GridCalculator.calculate(vMin, vMax, GRID_DIVISIONS);
        for (double v = yGrid.start(); v <= vMax + yGrid.step() * 1e-9; v += yGrid.step()) {
            double y = MT + ph - ((v - vMin) / vRange) * ph;
            if (y < MT - 1 || y > MT + ph + 1)
                continue;

            gc.setStroke(GRID_COLOR);
            gc.setLineWidth(0.5);
            gc.setLineDashes((double[]) null);
            gc.strokeLine(ML, y, ML + pw, y);

            // Tick + label
            gc.setStroke(AXIS_COLOR);
            gc.strokeLine(ML - 4, y, ML, y);

            String label = GridCalculator.formatValue(v, yGrid.step()) + " V";
            gc.setFill(LABEL_COLOR);
            gc.fillText(label, 3, y + 3.5);
        }

        // — X-axis grid (time) —
        var xGrid = GridCalculator.calculate(tStart, tEnd, GRID_DIVISIONS);
        for (double t = xGrid.start(); t <= tEnd + xGrid.step() * 1e-9; t += xGrid.step()) {
            double x = ML + ((t - tStart) / tRange) * pw;
            if (x < ML - 1 || x > ML + pw + 1)
                continue;

            gc.setStroke(GRID_COLOR);
            gc.setLineWidth(0.5);
            gc.setLineDashes((double[]) null);
            gc.strokeLine(x, MT, x, MT + ph);

            // Tick + label
            gc.setStroke(AXIS_COLOR);
            gc.strokeLine(x, MT + ph, x, MT + ph + 4);

            String label = GridCalculator.formatTime(t, xGrid.step());
            gc.setFill(LABEL_COLOR);
            gc.fillText(label, x - 18, MT + ph + 16);
        }

        // — Zero-voltage reference (dashed) —
        if (vMin < 0 && vMax > 0) {
            double zeroY = MT + ph - ((0.0 - vMin) / vRange) * ph;
            gc.setStroke(ZERO_LINE_CLR);
            gc.setLineWidth(1.0);
            gc.setLineDashes(4, 4);
            gc.strokeLine(ML, zeroY, ML + pw, zeroY);
            gc.setLineDashes((double[]) null);
        }
    }

    private void drawBorder(GraphicsContext gc, double pw, double ph) {
        gc.setStroke(AXIS_COLOR);
        gc.setLineWidth(1.0);
        gc.setLineDashes((double[]) null);
        gc.strokeRect(ML, MT, pw, ph);
    }

    private void drawSignal(GraphicsContext gc, double[] samples,
            double vMin, double vMax,
            double tStart, double tEnd,
            double sampleRate,
            double pw, double ph) {

        int totalSamples = samples.length;
        double vRange = vMax - vMin;
        double tRange = tEnd - tStart;
        if (vRange <= 0 || tRange <= 0 || totalSamples < 2 || sampleRate <= 0)
            return;

        double totalDuration = totalSamples / sampleRate;

        // Map tStart/tEnd → sample indices.
        // tStart may be negative (trigger mode with relative time) or
        // positive (free-run with absolute time). We map time linearly:
        // sample_time(i) = tStart + i * (totalDuration / totalSamples)
        // → i = (t - tStart) * sampleRate IF tStart corresponds to index 0
        // For the general case where tStart maps to the left edge:
        // index(t) = (t - tStart) / totalDuration * totalSamples
        // but this only works when tStart=0. Use direct mapping instead:
        // time_of_index(i) = tStart + (i / (totalSamples-1)) * totalDuration
        // → render from index 0 to totalSamples-1 covering [tStart, tEnd]
        //
        // Simplest correct approach: the caller already sliced the array
        // when using trigger mode, so we always render the full array
        // mapped to [tStart, tEnd].

        int target = Math.min(totalSamples, Math.min((int) pw + 1, MAX_POINTS));
        if (target < 2)
            target = 2;

        double step = (double) totalSamples / target;

        for (int i = 0; i < target; i++) {
            int idx = (int) (i * step);
            if (idx >= totalSamples)
                idx = totalSamples - 1;

            // X: map sample index proportionally to [tStart, tEnd]
            // idx=0 → tStart, idx=N-1 → tEnd
            double frac = (double) idx / (totalSamples - 1);
            double x = ML + frac * pw;

            // Y: voltage → pixels
            double norm = (samples[idx] - vMin) / vRange;
            double y = MT + ph - (norm * ph);

            xBuf[i] = x;
            yBuf[i] = clamp(y, MT, MT + ph);
        }

        gc.setStroke(SIGNAL_COLOR);
        gc.setLineWidth(1.5);
        gc.setLineDashes((double[]) null);
        gc.strokePolyline(xBuf, yBuf, target);
    }

    private void drawPlaceholder(GraphicsContext gc, double pw, double ph, String text) {
        gc.setFill(Color.gray(0.4));
        gc.setFont(Font.font("System", 14));
        double approxW = text.length() * 7.0;
        gc.fillText(text, ML + pw / 2 - approxW / 2, MT + ph / 2);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
