package com.athtech.gomoku.client.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import com.athtech.gomoku.client.net.ClientNetworkAdapter;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.SignupRequest;
import com.athtech.gomoku.protocol.payload.SignupResponse;

import java.util.function.Consumer;

public class SignupController {

    @FXML private TextField txtUser;
    @FXML private PasswordField txtPwd;
    @FXML private TextField txtNick;
    @FXML private Label lblMessage;
    @FXML private Button btnSignup;
    @FXML private Button btnBack;

    private Stage stage;
    private ClientNetworkAdapter network;
    private Runnable onBack;
    private Consumer<NetPacket> packetHandler;

    public void init(Stage stage, GomokuFXApp app, Runnable onBack) {
        this.stage = stage;
        this.network = app.getNetwork();
        this.onBack = onBack;

        packetHandler = this::handleServerPacket;
        app.registerPacketHandler(packetHandler);
    }

    @FXML
    private void handleSignup() {
        String username = txtUser.getText().trim();
        String pwd = txtPwd.getText().trim();
        String nick = txtNick.getText().trim();

        network.sendPacket(new NetPacket(PacketType.SIGNUP_REQUEST, username, new SignupRequest(username, pwd, nick)));
        lblMessage.setText("Signup request sent...");
    }

    @FXML
    private void goBack() {
        onBack.run();
    }

    private void handleServerPacket(NetPacket packet) {
        if (packet.type() == PacketType.SIGNUP_RESPONSE) {
            SignupResponse resp = (SignupResponse) packet.payload();
            Platform.runLater(() -> lblMessage.setText(resp.message()));
        }
    }

    public void cleanup(GomokuFXApp app) {
        if (packetHandler != null) {
            app.unregisterPacketHandler(packetHandler);
            packetHandler = null;
        }
    }
}
