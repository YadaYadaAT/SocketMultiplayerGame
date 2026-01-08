module gomoku.client.gui {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires gomoku.client.net;
    requires gomoku.protocol;
    requires java.desktop;


    opens com.athtech.gomoku.client.gui to javafx.fxml;
    exports com.athtech.gomoku.client.gui;
    exports com.athtech.gomoku.client.gui.controllers;
    opens com.athtech.gomoku.client.gui.controllers to javafx.fxml;
}