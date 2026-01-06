package com.athtech.gomoku.client.gui.controllers;

import com.athtech.gomoku.client.gui.GomokuFXApp;
import com.athtech.gomoku.client.gui.GomokuFXCommonToAllControllersData;
import com.athtech.gomoku.client.gui.GomokuFXNetworkHandler;
import com.athtech.gomoku.client.gui.enums.View;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import com.athtech.gomoku.protocol.payload.*;

public class LoginController extends BaseController{

    @FXML private TextField txtLoginUser;
    @FXML private PasswordField txtLoginPwd;
    @FXML private Label serverLoginResponseMessage;

    @FXML
    private void handleLogin() {
        String username = txtLoginUser.getText();
        String password = txtLoginPwd.getText();
        clientNetwork.sendPacket(new NetPacket(PacketType.LOGIN_REQUEST, username, new LoginRequest(username, password)));
    }

    @FXML
    private void handleSwitchSignup(){
        navigator.goTo(View.SIGNUP);
    }

    @Override
    public void onEnter() {
        Platform.runLater(() -> serverLoginResponseMessage.setText(""));
    }


    public void onLoginResponse(NetPacket packet) {
       LoginResponse resp = (LoginResponse) packet.payload();
        data.setLoggedIn(resp.success());
        Platform.runLater(() -> serverLoginResponseMessage.setText(resp.message()));
        if (data.isLoggedIn()) {
            data.setRelogCode(resp.relogCode());
            data.setNickname(resp.nickname());
            data.setUsername(resp.username());
            data.setInvites(resp.pendingInvites());
            clientNetwork.updateCredentials(data.getUsername(), data.getRelogCode());
        }

    }

    public void showInfo(InfoResponse info) {
        Platform.runLater(() -> {serverLoginResponseMessage.setText(info.msg());});
    }

}
