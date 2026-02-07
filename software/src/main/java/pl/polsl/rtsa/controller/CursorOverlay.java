package pl.polsl.rtsa.controller;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Draws interactive cursor overlays on the time-domain plot.
 * <p>
 * Features:
 * <ul>
 * <li>Dashed crosshair lines at cursor position</li>
 * <li>Snap-to-signal: red dot when the cursor is close to the waveform</li>
 * <li>Tooltip with precise time and voltage values</li>
 * <li>Coordinate math matches {@link TimeDomainRen} exactly</li>
 * </ul>
 */
public class CursorOverlay {

    private static final Color CROSSHAIR_COLOR = Color.gray(0.5, 0.6);
    private static final Color DOT_COLOR = Color.web("#ef4444");
    private static final Color TOOLTIP_BG = Color.color(0, 0, 0, 0.85);
    private static final Color TOOLTIP_TEXT = Color.WHITE;
    private static final double SNAP_DISTANCE = 14.0;

    /**
     * Draw cursor overlay on the time-domain canvas.
     *
     * @param gc          graphics context (same as the canvas being drawn on)
     * @param samples     voltage samples
     * @param vMin/vMax   visible voltage range (V)
     * @param tStart/tEnd visible time range (s)
     * @param sampleRate  sampling rate (Hz)
     * @param pw          plot-area width (px, excluding margins)
     * @param ph          plot-area height (px, excluding margins)
     * @param ml          left margin (px)
     * @param mt          top margin (px)
     * @param cx          cursor X in canvas coordinates
     * @param cy          cursor Y in canvas coordinates
     */
    public void drawTimeDomain(GraphicsContext gc, double[] samples,
            double vMin, double vMax,
            double tStart, double tEnd,
            double sampleRate,
            double pw, double ph,
            double ml, double mt,
            double cx, double cy) {

        if (samples == null || samples.length < 2 || sampleRate <= 0)
            return;

        // Cursor must be inside the plot area
        if (cx < ml || cx > ml + pw || cy < mt || cy > mt + ph)
            return;

        double vRange = vMax - vMin;
        double tRange = tEnd - tStart;
        if (vRange <= 0 || tRange <= 0)
            return;

        // Convert cursor pixel X → time → sample index
        // The samples array covers [tStart, tEnd], so map proportionally:
        // samples[0] → tStart, samples[N-1] → tEnd
        double t = tStart + ((cx - ml) / pw) * tRange;
        double totalDuration = samples.length / sampleRate;
        double frac = (t - tStart) / (tEnd - tStart);
        int idx = (int) Math.round(frac * (samples.length - 1));
        idx = Math.max(0, Math.min(samples.length - 1, idx));

        double voltage = samples[idx];
        // Recompute time from the actual index for tooltip precision
        double actualTime = tStart + ((double) idx / (samples.length - 1)) * tRange;

        // Signal Y position on canvas (same formula as TimeDomainRen)
        double norm = (voltage - vMin) / vRange;
        double signalY = mt + ph - (norm * ph);

        double distance = Math.abs(cy - signalY);

        if (distance > SNAP_DISTANCE) {
            // Far from signal: show crosshair + value at cursor position only
            drawCrosshair(gc, cx, cy, ml, mt, pw, ph);
            drawTooltip(gc, cx, cy, ml, mt, pw, ph,
                    "t = " + GridCalculator.formatTime(actualTime, tRange / 100),
                    String.format("V = %.4f V", voltage));
        } else {
            // Close to signal: snap crosshair to waveform + show red dot
            drawCrosshair(gc, cx, signalY, ml, mt, pw, ph);

            gc.setFill(DOT_COLOR);
            gc.fillOval(cx - 4, signalY - 4, 8, 8);

            drawTooltip(gc, cx, signalY, ml, mt, pw, ph,
                    "t = " + GridCalculator.formatTime(actualTime, tRange / 100),
                    String.format("V = %.4f V", voltage));
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private void drawCrosshair(GraphicsContext gc,
            double x, double y,
            double ml, double mt,
            double pw, double ph) {
        gc.setStroke(CROSSHAIR_COLOR);
        gc.setLineWidth(0.5);
        gc.setLineDashes(3, 3);
        gc.strokeLine(ml, y, ml + pw, y); // horizontal
        gc.strokeLine(x, mt, x, mt + ph); // vertical
        gc.setLineDashes((double[]) null);
    }

    private void drawTooltip(GraphicsContext gc,
            double x, double y,
            double ml, double mt,
            double pw, double ph,
            String line1, String line2) {

        gc.setFont(Font.font("Monospaced", 11));

        double tw = 155;
        double th = 36;
        double tx = x + 14;
        double ty = y - th - 6;

        // Keep inside plot area
        if (tx + tw > ml + pw)
            tx = x - tw - 14;
        if (ty < mt)
            ty = y + 10;
        if (ty + th > mt + ph)
            ty = mt + ph - th - 2;

        gc.setFill(TOOLTIP_BG);
        gc.fillRoundRect(tx, ty, tw, th, 6, 6);

        gc.setFill(TOOLTIP_TEXT);
        gc.fillText(line1, tx + 8, ty + 14);
        gc.fillText(line2, tx + 8, ty + 28);
    }
}
