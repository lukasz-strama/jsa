package pl.polsl.rtsa.controller;

public class Autoscaler {
    public double[] scaleTime(double[] samples) {
    if (samples == null || samples.length < 2) 
        return new double[]{0.0, 5.0}; 

    double min = Double.POSITIVE_INFINITY; 
    double max = Double.NEGATIVE_INFINITY; 
    
    for (double v : samples) { 
        if (v < min) min = v; 
        if (v > max) max = v; } 
        if (min == max) { 
            min -= 1.0; 
            max += 1.0; 
        } return new double[]{min, max}; 
    } 
    
    public double scaleFFT(double[] fft) { 
        if (fft == null || fft.length < 2) 
            return 1.0; double max = 0.0; 
        for (double v : fft) { 
            if (v > max) max = v; } 
            if (max == 0.0) 
                max = 1.0; 
            return max;

}
}
