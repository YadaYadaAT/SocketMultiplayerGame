package com.athtech.gomoku.client.gui;


import com.athtech.gomoku.client.net.ClientNetworkAdapter;
import com.athtech.gomoku.client.net.ClientNetworkAdapterImpl;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.LinkedHashMap;
import java.util.Map;

public class FXController {

    private final Stage stage;
    private final ClientNetworkAdapter clientNetwork;

    // UI scenes
    private Scene loginScene;
    private Scene lobbyScene;
    private Scene gameScene;

    // Session state
    private String username;
    private String relogCode;
    private String nickname;
    private volatile boolean inGame = false;
    private volatile boolean loggedIn = false;

    private final Map<String, Boolean> lobbyPlayers = new LinkedHashMap<>();
    private InviteNotificationResponse lastInvite;

    // UI components that need dynamic updates
    private VBox lobbyPlayerListBox;
    private TextArea gameBoardArea;
    private TextArea chatArea;

    public FXController(Stage stage, ClientNetworkAdapter network) {
        this.stage = stage;
        this.clientNetwork = network;
        this.clientNetwork.setListener(this::handleServerPacket);

        setupLoginScene();
        setupLobbyScene();
        setupGameScene();
    }

    /** ----------------------- Scene Getters ----------------------- **/
    public Scene getLoginScene() { return loginScene; }
    public Scene getLobbyScene() { return lobbyScene; }
    public Scene getGameScene() { return gameScene; }

    /** ----------------------- Login Scene ----------------------- **/
    private void setupLoginScene() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        Label lblUser = new Label("Username:");
        TextField txtUser = new TextField();
        Label lblPwd = new Label("Password:");
        PasswordField txtPwd = new PasswordField();
        Button btnLogin = new Button("Login");
        Button btnSignup = new Button("Signup");
        Label lblMessage = new Label();

        btnLogin.setOnAction(e -> {
            username = txtUser.getText().trim();
            String pwd = txtPwd.getText().trim();
            clientNetwork.sendPacket(new NetPacket(PacketType.LOGIN_REQUEST, username, new LoginRequest(username, pwd)));
            lblMessage.setText("Login request sent...");
        });

        btnSignup.setOnAction(e -> {
            username = txtUser.getText().trim();
            String pwd = txtPwd.getText().trim();
            String nick = username; // for simplicity
            clientNetwork.sendPacket(new NetPacket(PacketType.SIGNUP_REQUEST, username, new SignupRequest(username, pwd, nick)));
            lblMessage.setText("Signup request sent...");
        });

        root.getChildren().addAll(lblUser, txtUser, lblPwd, txtPwd, btnLogin, btnSignup, lblMessage);
        loginScene = new Scene(root, 400, 300);
    }

    /** ----------------------- Lobby Scene ----------------------- **/
    private void setupLobbyScene() {
        BorderPane root = new BorderPane();
        lobbyPlayerListBox = new VBox(5);
        lobbyPlayerListBox.setPadding(new Insets(10));

        Button btnRefresh = new Button("Refresh Players");
        btnRefresh.setOnAction(e -> requestLobbyPlayers());

        root.setTop(btnRefresh);
        root.setCenter(lobbyPlayerListBox);

        lobbyScene = new Scene(root, 500, 400);
    }

    /** ----------------------- Game Scene ----------------------- **/
    private void setupGameScene() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        gameBoardArea = new TextArea();
        gameBoardArea.setEditable(false);
        chatArea = new TextArea();
        chatArea.setEditable(false);

        TextField moveInput = new TextField();
        moveInput.setPromptText("row,col (or 'q' to quit)");
        Button btnSendMove = new Button("Send Move");

        btnSendMove.setOnAction(e -> {
            String input = moveInput.getText().trim();
            if (input.equalsIgnoreCase("q")) {
                clientNetwork.sendPacket(new NetPacket(PacketType.GAME_QUIT_REQUEST, username, new GameQuitRequest(false)));
            } else {
                String[] parts = input.split(",");
                if (parts.length == 2) {
                    try {
                        int row = Integer.parseInt(parts[0].trim()) - 1;
                        int col = Integer.parseInt(parts[1].trim()) - 1;
                        clientNetwork.sendPacket(new NetPacket(PacketType.MOVE_REQUEST, username, new MoveRequest(row, col)));
                    } catch (NumberFormatException ex) {
                        appendChat("Invalid move numbers.");
                    }
                } else appendChat("Invalid move format.");
            }
            moveInput.clear();
        });

        root.getChildren().addAll(gameBoardArea, chatArea, moveInput, btnSendMove);
        gameScene = new Scene(root, 600, 500);
    }

    /** ----------------------- Lobby Methods ----------------------- **/
    private void requestLobbyPlayers() {
        clientNetwork.sendPacket(new NetPacket(PacketType.LOBBY_PLAYERS_REQUEST, username, new LobbyPlayersRequest()));
    }

    private void updateLobbyPlayers(Map<String, Boolean> players) {
        Platform.runLater(() -> {
            lobbyPlayers.clear();
            lobbyPlayers.putAll(players);
            lobbyPlayerListBox.getChildren().clear();
            players.forEach((user, inGame) -> {
                Label lbl = new Label(user + (inGame ? " 🎮 [IN GAME]" : " ✅ [AVAILABLE]"));
                lobbyPlayerListBox.getChildren().add(lbl);
            });
        });
    }

    /** ----------------------- Game Methods ----------------------- **/
    private void updateBoard(String boardStr) {
        Platform.runLater(() -> gameBoardArea.setText(boardStr));
    }

    private void appendChat(String msg) {
        Platform.runLater(() -> chatArea.appendText(msg + "\n"));
    }

    /** ----------------------- Packet Handling ----------------------- **/
    private void handleServerPacket(NetPacket packet) {
        switch (packet.type()) {
            case LOGIN_RESPONSE -> handleLoginResponse(packet);
            case SIGNUP_RESPONSE -> handleSignupResponse(packet);
            case LOBBY_PLAYERS_RESPONSE -> handleLobbyPlayersResponse(packet);
            case GAME_STATE_RESPONSE -> handleGameState(packet);
            case GAME_END_RESPONSE -> handleGameEnd(packet);
            case INVITE_NOTIFICATION_RESPONSE -> handleInviteNotification(packet);
            default -> appendChat("[Unhandled packet] " + packet.type());
        }
    }

    private void handleLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();
        loggedIn = resp.success();
        relogCode = resp.relogCode();
        nickname = resp.nickname();

        if (loggedIn && clientNetwork instanceof ClientNetworkAdapterImpl adapter) {
            adapter.updateCredentials(username, relogCode);
        }

        Platform.runLater(() -> {
            if (loggedIn) stage.setScene(lobbyScene);
        });
    }

    private void handleSignupResponse(NetPacket packet) {
        SignupResponse resp = (SignupResponse) packet.payload();
        appendChat(resp.message());
    }

    private void handleLobbyPlayersResponse(NetPacket packet) {
        LobbyPlayersResponse resp = (LobbyPlayersResponse) packet.payload();
        updateLobbyPlayers(resp.players());
    }

    private void handleGameState(NetPacket packet) {
        GameStateResponse gs = (GameStateResponse) packet.payload();
        StringBuilder boardStr = new StringBuilder();

        BoardState board = gs.board();

        for (char[] row : board.cells()) {
            for (char cell : row) {
                boardStr.append(cell == '\0' ? "." : cell).append(" ");
            }
            boardStr.append("\n");
        }

        updateBoard(boardStr.toString());
    }

    private void handleGameEnd(NetPacket packet) {
        GameEndResponse resp = (GameEndResponse) packet.payload();
        appendChat("Game ended: " + resp.reason());
        inGame = false;
        Platform.runLater(() -> stage.setScene(lobbyScene));
    }

    private void handleInviteNotification(NetPacket packet) {
        lastInvite = (InviteNotificationResponse) packet.payload();
        appendChat("Invite from: " + lastInvite.fromUsername());
    }

}
