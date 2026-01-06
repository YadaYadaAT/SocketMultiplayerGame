package com.athtech.gomoku.client.gui.controllers;

import com.athtech.gomoku.client.gui.enums.View;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.payload.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class WrapperController extends BaseController{

    @FXML private Label lblUsername;
    @FXML private Label lblError;
    @FXML private Label lblConnection;
    @FXML
    private StackPane contentPane;

    public void setUsername(String name) {
        Platform.runLater(() -> {
         lblUsername.setText(name);
        });
    }

    public void setConnectionStatus(String status) {
        Platform.runLater(() -> {
         lblConnection.setText(status);
        });
    }

    public StackPane getContentPane() {
        return contentPane;
    }

    public void onErrorMessageResponse(NetPacket packet) {
        ErrorMessageResponse err = (ErrorMessageResponse) packet.payload();
        Platform.runLater(() -> {
        lblConnection.setText("ERROR: " + err.message());
        });
    }

    public void onInviteDecisionResponse(NetPacket packet) {
        InviteDecisionResponse resp = (InviteDecisionResponse) packet.payload();

        Platform.runLater(() -> {
            if (resp.accepted()) {
                navigator.goTo(View.GAME);
            }
        });
    }

    public void onLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();
        if (resp.success()) {
            Platform.runLater(() -> {
                setUsername(resp.message());
                navigator.goTo(View.LOBBY);
            });
        }
    }

    public void onLogoutResponse(NetPacket packet) {
        LogoutResponse resp = (LogoutResponse) packet.payload();
        if (resp.success()){
            setUsername("");
            data.reset();
            navigator.goTo(View.LOGIN);
        }
    }

    @Override
    public void showInfo(InfoResponse infoResponse) {

    }
}
