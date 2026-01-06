package com.athtech.gomoku.client.gui.controllers;

import com.athtech.gomoku.protocol.payload.InfoResponse;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class WrapperController extends BaseController{

    @FXML private Label lblUsername;
    @FXML private Label lblConnection;
    @FXML
    private StackPane contentPane;

    public void setUsername(String name) {
        lblUsername.setText(name);
    }

    public void setConnectionStatus(String status) {
        lblConnection.setText(status);
    }

    public StackPane getContentPane() {
        return contentPane;
    }

    @Override
    public void showInfo(InfoResponse infoResponse) {

    }
}
