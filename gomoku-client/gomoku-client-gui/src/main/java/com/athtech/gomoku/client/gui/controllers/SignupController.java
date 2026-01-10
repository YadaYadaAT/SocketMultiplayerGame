package com.athtech.gomoku.client.gui.controllers;

import com.athtech.gomoku.client.gui.GomokuFXCommonToAllControllersData;
import com.athtech.gomoku.client.gui.GomokuFXNetworkHandler;
import com.athtech.gomoku.client.gui.GomokuFXViewNavigator;
import com.athtech.gomoku.client.gui.enums.View;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.*;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

// Endpoint for Signup Screen
// All FXML components are annotated with @FXML
// Handles signup process
public class SignupController extends BaseController {

    @FXML private TextField txtSignupUser;
    @FXML private PasswordField txtSignupPwd;
    @FXML private TextField txtSignupNickname;
    @FXML private Label serverSignupResponseMessage;
    @FXML private Button btnSignup;
    @FXML private Button btnSwitchLogin;

    @FXML
    private void initialize() {
        // Press Enter in username → focus password
        txtSignupUser.setOnAction(e -> txtSignupPwd.requestFocus());

        // Press Enter in password → focus nickname
        txtSignupPwd.setOnAction(e -> txtSignupNickname.requestFocus());

        // Press Enter in nickname → attempt signup if all fields filled
        txtSignupNickname.setOnAction(e -> trySignupOnEnter());
    }

    // Attempt to complete signup if all fields are populated (Validity check lives here)
    private void trySignupOnEnter() {
        String username = txtSignupUser.getText().trim();
        String password = txtSignupPwd.getText().trim();
        String nickname = txtSignupNickname.getText().trim();

        if (!username.isEmpty() && !password.isEmpty() && !nickname.isEmpty()) {
            handleSignup(); // call existing signup method
        }
    }

    // Simple flag to track success state
    private boolean signupSuccessful = false;

    // Handles sending the valid signup request to the server
    @FXML
    private void handleSignup() {
        String username = txtSignupUser.getText();
        String password = txtSignupPwd.getText();
        String nickname = txtSignupNickname.getText();

        clientNetwork.sendPacket(
                new NetPacket(PacketType.SIGNUP_REQUEST, username, new SignupRequest(username, password, nickname))
        );
    }

    // Switch back to login view
    @FXML
    private void handleSwitchLogin() {
        navigator.goTo(View.LOGIN);
    }

    @Override
    public void onEnter() {
        // Reset UI every time view becomes visible
        resetForm();
    }

    @Override
    public void onLeave() {
        // Clear UI to avoid stale data
        resetForm();
    }

    // Resets the signup form
    private void resetForm() {
        signupSuccessful = false;
        Platform.runLater(()->{
            txtSignupUser.setText("");
            txtSignupPwd.setText("");
            txtSignupNickname.setText("");
        });
        // Show all input fields and signup button
        txtSignupUser.setVisible(true);
        txtSignupPwd.setVisible(true);
        txtSignupNickname.setVisible(true);
        btnSignup.setVisible(true);
        Platform.runLater(() -> serverSignupResponseMessage.setText(""));
    }

    // Accept signup response packet from server
    public void onSignupResponse(NetPacket packet) {
        SignupResponse resp = (SignupResponse) packet.payload();

        Platform.runLater(() -> {
            serverSignupResponseMessage.setText(resp.message());

            if (resp.success()) {
                // Hide input fields and signup button
                txtSignupUser.setVisible(false);
                txtSignupPwd.setVisible(false);
                txtSignupNickname.setVisible(false);
                btnSignup.setVisible(false);

                signupSuccessful = true;
                // Only "Back to Login" remains visible
                btnSwitchLogin.setVisible(true);
            }
        });

    }

    @Override
    public void showInfo(InfoResponse info) {
        Platform.runLater(() -> serverSignupResponseMessage.setText(info.msg()));
    }
}

