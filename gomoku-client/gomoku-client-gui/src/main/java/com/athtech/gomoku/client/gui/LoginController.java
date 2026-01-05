package com.athtech.gomoku.client.gui;

import com.athtech.gomoku.client.gui.util.Logger;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import com.athtech.gomoku.client.net.ClientNetworkAdapter;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.LoginRequest;
import com.athtech.gomoku.protocol.payload.LoginResponse;

import java.util.function.Consumer;

public class LoginController {

    @FXML private TextField txtUser;
    @FXML private PasswordField txtPwd;
    @FXML private Label lblMessage;
    @FXML private Button btnLogin;
    @FXML private Button btnSignup;

    private Stage stage;
    private ClientNetworkAdapter network;
    private Runnable onLoginSuccess;
    private Runnable onSignupSwitch;

    // Reference to unregister later if needed
    private Consumer<NetPacket> packetHandler;

    /**
     * Initialize controller with GomokuFXApp, not directly network.setListener
     */
    public void init(Stage stage, GomokuFXApp app, Runnable onLoginSuccess, Runnable onSignupSwitch) {
        this.stage = stage;
        this.network = app.getNetwork();
        this.onLoginSuccess = onLoginSuccess;
        this.onSignupSwitch = onSignupSwitch;

        // Register this controller with the app's central dispatcher
        packetHandler = this::handleServerPacket;
        app.registerPacketHandler(packetHandler);
    }

    @FXML
    private void handleLogin() {
        String username = txtUser.getText().trim();
        String pwd = txtPwd.getText().trim();

        Logger.info("Sending login request for user: " + username);
        network.sendPacket(new NetPacket(PacketType.LOGIN_REQUEST, username, new LoginRequest(username, pwd)));
        lblMessage.setText("Login request sent...");
    }

    @FXML
    private void switchToSignup() {
        onSignupSwitch.run();
    }

    /**
     * Handles packets from the server that are relevant to this controller
     */
    private void handleServerPacket(NetPacket packet) {
        if (packet.type() == PacketType.LOGIN_RESPONSE) {
            LoginResponse resp = (LoginResponse) packet.payload();

            // Always update UI on JavaFX thread
            Platform.runLater(() -> {
                lblMessage.setText(resp.success() ? "Login successful!" : "Login failed");
                Logger.debug("Login response received: success=" + resp.success());
                if (resp.success()) onLoginSuccess.run();
            });
        }
    }

    /**
     * Optional cleanup if you ever switch scenes and want to unregister
     */
    public void cleanup(GomokuFXApp app) {
        if (packetHandler != null) {
            app.unregisterPacketHandler(packetHandler);
            packetHandler = null;
        }
    }
}
