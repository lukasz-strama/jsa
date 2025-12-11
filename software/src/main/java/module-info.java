module pl.polsl.rtsa {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fazecast.jSerialComm;
    requires org.apache.commons.lang3;
    requires JTransforms; 

    exports pl.polsl.rtsa;
    opens pl.polsl.rtsa.controller to javafx.fxml;
}