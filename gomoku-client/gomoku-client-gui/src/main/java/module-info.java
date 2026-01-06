module gomoku.client.gui {
    requires javafx.controls;
    requires javafx.fxml;
    requires gomoku.client.net;
    requires gomoku.protocol;

    opens com.athtech.gomoku.client.gui to javafx.fxml;
    exports com.athtech.gomoku.client.gui;
    exports com.athtech.gomoku.client.gui.controllers;
    opens com.athtech.gomoku.client.gui.controllers to javafx.fxml;
}