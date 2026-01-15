package pl.polsl.rtsa.controller;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class FFTDomainRen {

    private static final int MAX_POINTS = 666;
    
     public void draw(GraphicsContext gc, double[] fft, double fftMax, double samplingRate, double w, double h) {
       

    if(fft == null || fft.length < 2) return;
    if(w <=0 || h <=0) return;

    gc.setFill(Color.BLACK); 
    gc.fillRect(0, 0, w, h);

    gc.setStroke(Color.ORANGE);
    gc.setLineWidth(0.5);
    gc.setFill(Color.ORANGE);
    gc.fillText(String.format("%.2f V", fftMax), 5, 12);
    gc.fillText("0 V", 5, h - 5);
    gc.fillText("Freq [Hz]", w - 50, h - 5);
   

    int target = Math.min(fft.length, MAX_POINTS);
    int step = fft.length / target;
    if(step < 1) step = 1;

    double xScale = w / (double) (target - 1);
    double[] xPoints = new double[target];
    double[] yPoints = new double[target];
    
    int idx = 0; 

    for(int i = 0; i < fft.length && idx < target; i += step) {
        xPoints[idx] = idx * xScale;
        double norm = Math.abs(fft[i]) / fftMax;
        yPoints[idx] = h - (norm * h);
        idx++;
    }
    gc.strokePolyline(xPoints, yPoints, idx);
}
}
