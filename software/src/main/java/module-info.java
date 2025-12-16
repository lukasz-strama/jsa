module pl.polsl.rtsa {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fazecast.jSerialComm;
    requires org.apache.commons.lang3;
    requires JTransforms;

    exports pl.polsl.rtsa;
    exports pl.polsl.rtsa.model;
    exports pl.polsl.rtsa.hardware;

    opens pl.polsl.rtsa to javafx.fxml;
    opens pl.polsl.rtsa.controller to javafx.fxml;
}
