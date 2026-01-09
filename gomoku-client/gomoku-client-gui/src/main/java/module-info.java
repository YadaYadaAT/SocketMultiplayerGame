module gomoku.client.gui {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires javafx.graphics; // safe to add
    requires gomoku.client.net;
    requires gomoku.protocol;

    opens com.athtech.gomoku.client.gui to javafx.fxml;
    opens com.athtech.gomoku.client.gui.controllers to javafx.fxml;

    exports com.athtech.gomoku.client.gui;
    exports com.athtech.gomoku.client.gui.controllers;


}