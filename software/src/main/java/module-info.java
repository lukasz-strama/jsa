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
