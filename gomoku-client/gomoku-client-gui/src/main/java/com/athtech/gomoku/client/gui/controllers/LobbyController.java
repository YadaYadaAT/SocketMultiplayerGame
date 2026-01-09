package com.athtech.gomoku.client.gui.controllers;

import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.*;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.util.Duration;


import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;


import java.util.*;

public class LobbyController extends BaseController {

    /* ---------------- UI ---------------- */
    @FXML private ListView<String> lobbyPlayersList;
    @FXML private ListView<String> inviteListView;
    @FXML private ListView<String> chatListView;
    @FXML private Label outgoingInviteLabel;
    @FXML private Label lobbyStatusLabel;
    @FXML private Label played;
    @FXML private Label wins;
    @FXML private Label loses;
    @FXML private Label draws;
    @FXML private TextField chatInput;
    @FXML
    private Button acceptBtn; // fx:id="acceptBtn"
    @FXML
    private Button declineBtn; // fx:id="declineBtn"

    @FXML private Button muteBtn;

    // Media for background music
    private MediaPlayer backgroundMusic;

    private Timeline clearInviteTimeline;

    /* ---------------- State ---------------- */
    private final Map<String, InviteNotificationResponse> incomingInvites = new HashMap<>();
    private final Map<String, String> displayToUsername = new HashMap<>();

    private final LinkedList<String> chatQueue = new LinkedList<>();
    private static final int CHAT_QUEUE_MAX = 25;

    /* ---------------- UI actions ---------------- */

    @FXML
    private void initialize() {
        setupListGradientAnimation();

        chatInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                sendChatMessage();
            }
        });

        // Accept/Decline buttons only enabled when an invite is selected
        inviteListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            boolean hasSelection = newSel != null;
            acceptBtn.setVisible(hasSelection);
            declineBtn.setVisible(hasSelection);
        });

        // Initially hide Accept/Decline buttons
        acceptBtn.setVisible(false);
        declineBtn.setVisible(false);


    }

    @Override
    public void onEnter() {
        Platform.runLater(()->{
            setupListGradientAnimation();
            setupMusic();
            boolean muted = backgroundMusic.isMute();
            muteBtn.setText(muted ? "Unmute 🔇"  : "Mute 🔊");
        });
    }

    @Override
    public void onLeave() {
        Platform.runLater(()->{
        backgroundMusic.setMute(true);
        });
    }


    @FXML
    private void handleInvite() {
        String selectedLabel = lobbyPlayersList.getSelectionModel().getSelectedItem();
        if (selectedLabel == null) {
            lobbyStatusLabel.setText("Select a player first.");
            return;
        }

        String usernameToInvite = displayToUsername.get(selectedLabel);
        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.INVITE_REQUEST,
                        data.getUsername(),
                        new InviteRequest(usernameToInvite)
                )
        );
    }

    @FXML
    private void handleRandomInvite() {
        List<String> availablePlayers = new ArrayList<>();
        for (String label : lobbyPlayersList.getItems()) {
            String username = displayToUsername.get(label);
            // Make sure the username is not yourself
            if (!username.equals(data.getUsername())) {
                availablePlayers.add(username);
            }
        }

        if (availablePlayers.isEmpty()) {
            // Show simple info popup
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("No Players Available");
                alert.setHeaderText(null);
                alert.setContentText("There are no available players in the lobby to invite.");
                alert.showAndWait();
            });
            return;
        }

        // Pick random player
        String randomUsername = availablePlayers.get(new Random().nextInt(availablePlayers.size()));

        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.INVITE_REQUEST,
                        data.getUsername(),
                        new InviteRequest(randomUsername)
                )
        );

        Platform.runLater(() -> outgoingInviteLabel.setText("Invite sent to: " + randomUsername));
    }

    @FXML
    private void handleLogout() {
        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.LOGOUT_REQUEST,
                        data.getUsername(),
                        new LogoutRequest()
                )
        );
    }

    @FXML
    private void handleAcceptSelectedInvite() {
        String selected = inviteListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        InviteNotificationResponse invite = incomingInvites.remove(selected);
        inviteListView.getItems().remove(selected);

        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.INVITE_DECISION_REQUEST,
                        data.getUsername(),
                        new InviteDecisionRequest(invite.fromUsername(), true)
                )
        );
    }

    @FXML
    private void handleDeclineSelectedInvite() {
        String selected = inviteListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        InviteNotificationResponse invite = incomingInvites.remove(selected);
        inviteListView.getItems().remove(selected);

        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.INVITE_DECISION_REQUEST,
                        data.getUsername(),
                        new InviteDecisionRequest(invite.fromUsername(), false)
                )
        );
    }

    @FXML
    private void sendChatMessage() {
        String msg = chatInput.getText().trim();
        if (msg.isEmpty() || msg.length() > 250) return;

        chatInput.clear();
        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.LOBBY_CHAT_MESSAGE_REQUEST,
                        data.getUsername(),
                        new LobbyChatMessageRequest(msg)
                )
        );
    }

    /* ---------------- Network → UI ---------------- */

    public void onLobbyChatMessageResponse(NetPacket packet) {
        LobbyChatMessageResponse resp = (LobbyChatMessageResponse) packet.payload();
        String formatted =
                String.format("[%tT] %s: %s",
                        resp.timestamp(),
                        resp.username(),
                        resp.message());

        Platform.runLater(() -> {
            if (chatQueue.size() >= CHAT_QUEUE_MAX) {
                chatQueue.removeFirst();
            }
            chatQueue.add(formatted);
            chatListView.getItems().setAll(chatQueue);
            chatListView.scrollTo(chatQueue.size() - 1);
        });
    }

    public void onLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();

        if (resp.success()) {
            Platform.runLater(() -> {

                PlayerStatsResponse stats = resp.myStats();

                updateStats(stats);
                for(InviteNotificationResponse i : resp.pendingInvites()){
                    onInviteNotificationResponseFromPayload(i);
                }

            });
        } else {
            Platform.runLater(() ->
                    lobbyStatusLabel.setText("Login failed: " + resp.message())
            );
        }
    }

    public void updateStats(PlayerStatsResponse stats){
        Platform.runLater(() -> {
            played.setText(Integer.toString(stats.gamesPlayed()));
            wins.setText(Integer.toString(stats.wins()));
            loses.setText(Integer.toString(stats.losses()));
            draws.setText(Integer.toString(stats.draws()));
        });

    }

    public void onResyncResponse(NetPacket packet){
        ResyncResponse resp = (ResyncResponse)  packet.payload();
        updateStats(resp.myStats());
        for(InviteNotificationResponse i : resp.pendingInvites()){
            onInviteNotificationResponseFromPayload(i);
        }
        onLobbyPlayersResponseFromPayload(resp.lobbyPlayers());

    }


    public void onLobbyPlayersResponse(NetPacket packet) {
        LobbyPlayersResponse resp = (LobbyPlayersResponse) packet.payload();
        onLobbyPlayersResponseFromPayload(resp);

    }

    public void onLobbyPlayersResponseFromPayload(LobbyPlayersResponse resp) {
        Platform.runLater(() -> {
            lobbyPlayersList.getItems().clear();
            displayToUsername.clear();

            for (Map.Entry<String, Boolean> entry : resp.players().entrySet()) {
                String username = entry.getKey();
                boolean inGame = entry.getValue();

                if (username.equals(data.getUsername())) continue;

                String label =
                        username + (inGame ? " 🎮 [IN GAME]" : " ✅ [AVAILABLE]");
                lobbyPlayersList.getItems().add(label);
                displayToUsername.put(label, username);
            }
        });
    }


    public void onPlayerStatsResponse(NetPacket packet) {
        PlayerStatsResponse stats = (PlayerStatsResponse) packet.payload();

        Platform.runLater(() -> {
            played.setText(String.valueOf(stats.gamesPlayed()));
            wins.setText(String.valueOf(stats.wins()));
            loses.setText(String.valueOf(stats.losses()));
            draws.setText(String.valueOf(stats.draws()));
        });
    }

    public void onInviteResponse(NetPacket packet) {
        InviteResponse resp = (InviteResponse) packet.payload();
        String msg =
                resp.delivered()
                        ? "Invite delivered."
                        : "Invite failed: " + resp.reason();

        Platform.runLater(() -> {
            outgoingInviteLabel.setText(msg);

            if (clearInviteTimeline != null) {
                clearInviteTimeline.stop();
            }

            clearInviteTimeline =
                    new Timeline(new KeyFrame(
                            Duration.seconds(3),
                            e -> outgoingInviteLabel.setText("")
                    ));
            clearInviteTimeline.play();
        });
    }

    public void onInviteNotificationResponse(NetPacket packet) {
        InviteNotificationResponse invite =
                (InviteNotificationResponse) packet.payload();
        onInviteNotificationResponseFromPayload(invite);
    }

    public void onInviteNotificationResponseFromPayload(InviteNotificationResponse invite){
        String from = invite.fromUsername();

        Platform.runLater(() -> {
            if (!incomingInvites.containsKey(from)) {
                incomingInvites.put(from, invite);
                inviteListView.getItems().add(from);
            }
        });
    }

    public void onInviteDecisionResponse(NetPacket packet) {
        InviteDecisionResponse resp =
                (InviteDecisionResponse) packet.payload();

        Platform.runLater(() -> {
            if (resp.accepted()) {
                // navigation handled elsewhere
            } else {
                if (resp.targetUsername().equals(data.getUsername())) {
                    lobbyStatusLabel.setText(
                            "You declined invitation to " + resp.inviterUsername()
                    );
                } else {
                    lobbyStatusLabel.setText(
                            resp.targetUsername() + " declined your invitation"
                    );
                }
            }
        });
    }

    public void onLogoutResponse(NetPacket packet) {
        LogoutResponse resp = (LogoutResponse) packet.payload();

        Platform.runLater(() -> {
            if (resp.success()) {
                lobbyStatusLabel.setText(resp.message());
            } else {
                lobbyStatusLabel.setText("Logout failed: " + resp.message());
            }
        });
    }

    public void showInfo(InfoResponse info) {
        Platform.runLater(() ->
                lobbyStatusLabel.setText(info.msg())
        );
    }

    /* ---------------- Helpers ---------------- */

    private void requestLobbyPlayers() {
        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.LOBBY_PLAYERS_REQUEST,
                        data.getUsername(),
                        new LobbyPlayersRequest()
                )
        );
    }

    private void setupListGradientAnimation() {
        // List of colors to cycle
        Color[] colors = new Color[] {
                Color.web("#00fffc"), Color.web("#ff00e1"), Color.web("#00ff00"), Color.web("#ffdd00")
        };

        // For lobbyPlayersList
        Timeline lobbyTimeline = new Timeline(new KeyFrame(Duration.millis(1000), event -> {
            // shift colors
            Color first = colors[0];
            System.arraycopy(colors, 1, colors, 0, colors.length - 1);
            colors[colors.length - 1] = first;

            // build gradient
            String gradient = String.format(
                    "-fx-border-color: linear-gradient(to right, %s, %s, %s, %s); " +
                            "-fx-border-width: 3; -fx-border-radius: 8;",
                    toHex(colors[0]), toHex(colors[1]), toHex(colors[2]), toHex(colors[3])
            );
            lobbyPlayersList.setStyle(gradient);
        }));
        lobbyTimeline.setCycleCount(Animation.INDEFINITE);
        lobbyTimeline.play();

        // Duplicate for inviteListView (can even use same Timeline if you want synced)
        Timeline inviteTimeline = new Timeline(new KeyFrame(Duration.millis(2000), event -> {
            Color first = colors[0];
            System.arraycopy(colors, 1, colors, 0, colors.length - 1);
            colors[colors.length - 1] = first;
            String gradient = String.format(
                    "-fx-border-color: linear-gradient(to right, %s, %s, %s, %s); " +
                            "-fx-border-width: 3; -fx-border-radius: 8;",
                    toHex(colors[0]), toHex(colors[1]), toHex(colors[2]), toHex(colors[3])
            );
            inviteListView.setStyle(gradient);
        }));
        inviteTimeline.setCycleCount(Animation.INDEFINITE);
        inviteTimeline.play();
    }

    // helper to convert Color to #RRGGBB
    private String toHex(Color color) {
        int r = (int) (color.getRed() * 255);
        int g = (int) (color.getGreen() * 255);
        int b = (int) (color.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }




    private void setupMusic() {
        try {

            Media music1 = new Media(getClass().getResource("/com/athtech/gomoku/client/gui/music/johny.mp3").toExternalForm());

            backgroundMusic = new MediaPlayer(music1);

            backgroundMusic.setCycleCount(MediaPlayer.INDEFINITE);
            backgroundMusic.setVolume(0.5); // adjust starting volume
            backgroundMusic.play();

        } catch (Exception e) {
            System.err.println("Failed to load background music: " + e.getMessage());
        }
    }

    @FXML
    private void handleMute() {
        if (backgroundMusic == null) return;

        boolean muted = backgroundMusic.isMute();
        backgroundMusic.setMute(!muted);
        muteBtn.setText(muted ? "Mute 🔊" : "Unmute 🔇");
    }




}
