package pl.polsl.rtsa.controller;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Renders the FFT magnitude spectrum with:
 * <ul>
 * <li>Grid lines at "nice" frequency and magnitude intervals</li>
 * <li>Frequency (X) and magnitude (Y) axis labels</li>
 * <li>Zoom-aware rendering via explicit visible-range parameters</li>
 * <li>Correct bin→Hz mapping: f = bin × (sampleRate / (2 × N))</li>
 * </ul>
 */
public class FFTDomainRen {

    // ---- Layout margins (same as TimeDomainRen for visual consistency) ----
    private static final double ML = 65, MR = 15, MT = 15, MB = 35;

    private static final int MAX_POINTS = 2048;
    private static final int GRID_DIVISIONS = 8;

    // ---- Palette ----
    private static final Color BG_COLOR = Color.web("#1a1a2e");
    private static final Color GRID_COLOR = Color.web("#2a2a4a");
    private static final Color AXIS_COLOR = Color.gray(0.4);
    private static final Color LABEL_COLOR = Color.gray(0.65);
    private static final Color SIGNAL_COLOR = Color.web("#f59e0b"); // amber
    private static final Color TITLE_COLOR = Color.gray(0.5);

    private final double[] xBuf = new double[MAX_POINTS];
    private final double[] yBuf = new double[MAX_POINTS];

    private boolean showGrid = true;

    public void setShowGrid(boolean show) {
        this.showGrid = show;
    }

    // ================================================================
    // Public draw methods
    // ================================================================

    /**
     * Draw the FFT magnitude spectrum.
     *
     * @param gc         canvas graphics context
     * @param fft        magnitude values (half-spectrum, N/2 bins, positive freqs)
     * @param magMin     visible magnitude minimum (usually 0)
     * @param magMax     visible magnitude maximum
     * @param freqStart  visible frequency start (Hz)
     * @param freqEnd    visible frequency end (Hz)
     * @param sampleRate sampling rate (Hz) — needed for bin→Hz mapping
     * @param w          canvas width (px)
     * @param h          canvas height (px)
     */
    public void draw(GraphicsContext gc, double[] fft,
            double magMin, double magMax,
            double freqStart, double freqEnd,
            double sampleRate,
            double w, double h) {

        if (w <= 0 || h <= 0)
            return;
        double pw = w - ML - MR;
        double ph = h - MT - MB;
        if (pw <= 0 || ph <= 0)
            return;

        // Background
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, w, h);

        // Grid + border
        if (showGrid)
            drawGrid(gc, magMin, magMax, freqStart, freqEnd, pw, ph);
        drawBorder(gc, pw, ph);

        // Spectrum trace
        if (fft != null && fft.length >= 2 && sampleRate > 0 && magMax > magMin) {
            drawSpectrum(gc, fft, magMin, magMax, freqStart, freqEnd, sampleRate, pw, ph);
        } else {
            drawPlaceholder(gc, pw, ph, "Brak danych FFT");
        }

        // Title
        gc.setFill(TITLE_COLOR);
        gc.setFont(Font.font("System", 10));
        gc.fillText("Widmo FFT", ML + 5, MT + 12);
    }

    /**
     * Draw empty canvas with default grid and placeholder.
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
            drawGrid(gc, 0, 1, 0, 5000, pw, ph);
        drawBorder(gc, pw, ph);
        drawPlaceholder(gc, pw, ph, "Brak danych FFT");
    }

    // ================================================================
    // Internal helpers
    // ================================================================

    private void drawGrid(GraphicsContext gc,
            double magMin, double magMax,
            double freqStart, double freqEnd,
            double pw, double ph) {

        double magRange = magMax - magMin;
        double freqRange = freqEnd - freqStart;
        if (magRange <= 0 || freqRange <= 0)
            return;

        gc.setFont(Font.font("Monospaced", 10));

        // — Y-axis grid (magnitude) —
        var yGrid = GridCalculator.calculate(magMin, magMax, GRID_DIVISIONS);
        for (double m = yGrid.start(); m <= magMax + yGrid.step() * 1e-9; m += yGrid.step()) {
            double y = MT + ph - ((m - magMin) / magRange) * ph;
            if (y < MT - 1 || y > MT + ph + 1)
                continue;

            gc.setStroke(GRID_COLOR);
            gc.setLineWidth(0.5);
            gc.setLineDashes((double[]) null);
            gc.strokeLine(ML, y, ML + pw, y);

            gc.setStroke(AXIS_COLOR);
            gc.strokeLine(ML - 4, y, ML, y);

            String label = GridCalculator.formatValue(m, yGrid.step()) + " V";
            gc.setFill(LABEL_COLOR);
            gc.fillText(label, 3, y + 3.5);
        }

        // — X-axis grid (frequency) —
        var xGrid = GridCalculator.calculate(freqStart, freqEnd, GRID_DIVISIONS);
        for (double f = xGrid.start(); f <= freqEnd + xGrid.step() * 1e-9; f += xGrid.step()) {
            double x = ML + ((f - freqStart) / freqRange) * pw;
            if (x < ML - 1 || x > ML + pw + 1)
                continue;

            gc.setStroke(GRID_COLOR);
            gc.setLineWidth(0.5);
            gc.setLineDashes((double[]) null);
            gc.strokeLine(x, MT, x, MT + ph);

            gc.setStroke(AXIS_COLOR);
            gc.strokeLine(x, MT + ph, x, MT + ph + 4);

            String label = GridCalculator.formatFrequency(f, xGrid.step());
            gc.setFill(LABEL_COLOR);
            gc.fillText(label, x - 20, MT + ph + 16);
        }
    }

    private void drawBorder(GraphicsContext gc, double pw, double ph) {
        gc.setStroke(AXIS_COLOR);
        gc.setLineWidth(1.0);
        gc.setLineDashes((double[]) null);
        gc.strokeRect(ML, MT, pw, ph);
    }

    /**
     * Render the FFT magnitude trace.
     * <p>
     * Bin-to-frequency mapping:
     * {@code freq(bin) = bin × freqResolution}
     * where {@code freqResolution = sampleRate / (2 × fft.length)}
     * because {@code fft[]} is the half-spectrum (N/2 bins from an N-point FFT).
     */
    private void drawSpectrum(GraphicsContext gc, double[] fft,
            double magMin, double magMax,
            double freqStart, double freqEnd,
            double sampleRate,
            double pw, double ph) {

        double magRange = magMax - magMin;
        double freqRange = freqEnd - freqStart;
        if (magRange <= 0 || freqRange <= 0)
            return;

        // Frequency resolution per bin
        double freqRes = sampleRate / (2.0 * fft.length);

        // Visible bin range
        int startBin = Math.max(0, (int) Math.floor(freqStart / freqRes));
        int endBin = Math.min(fft.length - 1, (int) Math.ceil(freqEnd / freqRes));
        int binCount = endBin - startBin + 1;
        if (binCount < 2)
            return;

        // Downsample to screen pixels
        int target = Math.min(binCount, Math.min((int) pw + 1, MAX_POINTS));
        if (target < 2)
            target = 2;

        double step = (double) binCount / target;

        for (int i = 0; i < target; i++) {
            int bin = startBin + (int) (i * step);
            if (bin >= fft.length)
                bin = fft.length - 1;

            double freq = bin * freqRes;
            double x = ML + ((freq - freqStart) / freqRange) * pw;
            double norm = (Math.abs(fft[bin]) - magMin) / magRange;
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
