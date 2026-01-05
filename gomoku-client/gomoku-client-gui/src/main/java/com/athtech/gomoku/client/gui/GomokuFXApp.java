package com.athtech.gomoku.client.gui;

import com.athtech.gomoku.client.net.ClientNetworkAdapter;
import com.athtech.gomoku.client.net.ClientNetworkAdapterImpl;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class GomokuFXApp extends Application {

    private Stage stage;
    private ClientNetworkAdapter network;

    private Scene loginScene;
    private Scene signupScene;
    private Scene lobbyScene;
    private Scene gameScene;

    // Central list of packet handlers
    private final List<Consumer<NetPacket>> packetHandlers = new ArrayList<>();

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        this.network = new ClientNetworkAdapterImpl("localhost", 999);

        // Single listener for ClientNetworkAdapter
        network.setListener(this::centralPacketHandler);

        // Load all scenes at startup
        loadLoginScene();
        loadSignupScene();
        loadLobbyScene();
        loadGameScene();

        // Start with login
        stage.setTitle("Gomoku-YadaYada");
        stage.setScene(loginScene);
        stage.setResizable(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

    /** ----------------------
     * Central listener dispatcher
     * ---------------------- */
    private void centralPacketHandler(NetPacket packet) {
        for (Consumer<NetPacket> handler : packetHandlers) {
            Platform.runLater(() -> handler.accept(packet)); // safe for JavaFX UI
        }
    }

    /** Controllers call this to receive packets */
    public void registerPacketHandler(Consumer<NetPacket> handler) {
        packetHandlers.add(handler);
    }

    /** Controllers can unregister when no longer interested */
    public void unregisterPacketHandler(Consumer<NetPacket> handler) {
        packetHandlers.remove(handler);
    }

    /** ----------------------
     * Scene loading
     * ---------------------- */
    private void loadLoginScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(GomokuFXApp.class.getResource("/Login.fxml"));
        loginScene = new Scene(loader.load());
        LoginController ctrl = loader.getController();
        ctrl.init(stage, this,
                () -> stage.setScene(lobbyScene), // on successful login -> Lobby
                () -> stage.setScene(signupScene) // switch to signup
        );
    }

    private void loadSignupScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(GomokuFXApp.class.getResource("/Signup.fxml"));
        signupScene = new Scene(loader.load());
        SignupController ctrl = loader.getController();
        ctrl.init(stage, this,
                () -> stage.setScene(loginScene) // after signup -> login
        );
    }

    private void loadLobbyScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(GomokuFXApp.class.getResource("/Lobby.fxml"));
        lobbyScene = new Scene(loader.load());
        LobbyController ctrl = loader.getController();
        ctrl.init(stage, this,
                () -> stage.setScene(gameScene), // start game
                () -> stage.setScene(loginScene) // logout -> login
        );
    }

    private void loadGameScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(GomokuFXApp.class.getResource("/Game.fxml"));
        gameScene = new Scene(loader.load());
        GameController ctrl = loader.getController();
        ctrl.init(stage, this,
                () -> stage.setScene(lobbyScene) // back to lobby when game ends
        );
    }

    /** ----------------------
     * Helper for controllers
     * ---------------------- */
    public ClientNetworkAdapter getNetwork() {
        return network;
    }
}
