/**
 * JSignalAnalysis — a real-time signal analysis and FFT visualisation tool.
 * <p>
 * This module provides:
 * <ul>
 * <li>{@code pl.polsl.rtsa} — JavaFX application entry point</li>
 * <li>{@code pl.polsl.rtsa.api} — public API and DTOs for signal analyser
 * operations</li>
 * <li>{@code pl.polsl.rtsa.hardware} — serial device communication layer</li>
 * <li>{@code pl.polsl.rtsa.service} — DSP services (FFT, statistics,
 * windowing)</li>
 * <li>{@code pl.polsl.rtsa.controller} — JavaFX UI controllers and canvas
 * renderers</li>
 * <li>{@code pl.polsl.rtsa.config} — application configuration management</li>
 * </ul>
 */
module pl.polsl.rtsa {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fazecast.jSerialComm;
    requires JTransforms;
    requires org.slf4j;

    // Public API exports
    exports pl.polsl.rtsa;
    exports pl.polsl.rtsa.api;
    exports pl.polsl.rtsa.api.dto;
    exports pl.polsl.rtsa.api.exception;
    exports pl.polsl.rtsa.model;
    exports pl.polsl.rtsa.hardware;
    exports pl.polsl.rtsa.config;
    exports pl.polsl.rtsa.service;

    // FXML reflection access
    opens pl.polsl.rtsa to javafx.fxml;
    opens pl.polsl.rtsa.controller to javafx.fxml;
}
