package pl.polsl.rtsa.controller;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class TimeDomainRen {

    private static final int MAX_POINTS = 666;
    
     public void draw(GraphicsContext gc, double[] samples, double timeMin, double timeMax, double w, double h, boolean cursorActive, double cursorX, double cursorY) {

        if(w <=0 || h <=0) return;
        if(samples == null || samples.length < 2) return;

        gc.setFill(Color.BLACK); 
        gc.fillRect(0, 0, w, h);
        gc.setStroke(Color.LIGHTSEAGREEN);
        gc.setLineWidth(0.5);
        gc.fillText(String.format("%.2f V", timeMax), 5, 12);
        gc.fillText(String.format("%.2f V", timeMin), 5, h - 5);
        gc.fillText("Czas", w - 50, h - 5);

        int total = samples.length;
        int target = Math.min((int) w, MAX_POINTS);
        if(target < 2) return;

        double range = timeMax - timeMin;
        if(range <= 0) range = 1.0;

        int samplesPerPixel = total / target;
        if (samplesPerPixel < 1) samplesPerPixel = 1;

        double xScale = w / (double) (target - 1);

        int idx = 0; 
        double[] xPoints = new double[target];
        double[] yPoints = new double[target];

        for(int i = 0; i < total && idx < target; i += samplesPerPixel) {
            xPoints[idx] = idx * xScale;

            double norm = (samples[i] - timeMin) / range;
            yPoints[idx] = h - (norm * h);
            idx++;
        }

        if(cursorActive) {
            CursorOverlay cursorOverlay = new CursorOverlay();
            cursorOverlay.drawCursor(gc, samples, timeMin, timeMax, w, h, cursorX, cursorY);
        }

        gc.strokePolyline(xPoints, yPoints, idx);

        
    }
}
