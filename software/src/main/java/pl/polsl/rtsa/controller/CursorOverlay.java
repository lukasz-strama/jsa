package pl.polsl.rtsa.controller;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class CursorOverlay {
    public void drawCursor(GraphicsContext gc, double[] samples, double timeMin, double timeMax, double w, double h, double cursorX, double cursorY) {

        if (samples == null || samples.length < 2) return; 
        
        int total = samples.length; 
        int index = (int) ((cursorX / w) * total); 
        index = Math.max(0, Math.min(index, total - 1)); 

        double range = timeMax - timeMin; if (range <= 0) 
            range = 1.0; double norm = (samples[index] - timeMin) / range; 
        
        double sampleY = h - (norm * h); 
        double distance = Math.abs(cursorY - sampleY); 
    
        if (distance > 8) return; 
        
        gc.setFill(Color.RED); 
        gc.fillOval(cursorX - 3, sampleY - 3, 6, 6); 
        
        String text = String.format("%.3f V", samples[index]); 
        gc.setFill(Color.color(0, 0, 0, 0.75)); 
        gc.fillRoundRect(cursorX + 10, sampleY - 15, 60, 20, 5, 5); 
        gc.setFill(Color.WHITE); 
        gc.fillText(text, cursorX + 15, sampleY); 
    
    }
}
