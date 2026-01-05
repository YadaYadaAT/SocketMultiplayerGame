package com.athtech.gomoku.client.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.athtech.gomoku.client.net.ClientNetworkAdapter;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.*;

import java.util.*;
import java.util.function.Consumer;

public class LobbyController {

    @FXML private VBox lobbyPlayerListBox;
    @FXML private Button btnRefresh;
    @FXML private Button btnLogout;
    @FXML private Button btnSendInvite;
    @FXML private Button btnViewInvites;
    @FXML private Button btnPing;

    private Stage stage;
    private ClientNetworkAdapter network;
    private Runnable onEnterGame;
    private Runnable onLogout;

    private final Map<String, Boolean> lobbyPlayers = new LinkedHashMap<>();
    private final List<InviteNotificationResponse> pendingInvites = new ArrayList<>();
    private Consumer<NetPacket> packetHandler;

    public void init(Stage stage, GomokuFXApp app, Runnable onEnterGame, Runnable onLogout) {
        this.stage = stage;
        this.network = app.getNetwork();
        this.onEnterGame = onEnterGame;
        this.onLogout = onLogout;

        packetHandler = this::handleServerPacket;
        app.registerPacketHandler(packetHandler);

        requestLobbyPlayers();
    }

    @FXML
    private void requestLobbyPlayers() {
        network.sendPacket(new NetPacket(PacketType.LOBBY_PLAYERS_REQUEST, "", new LobbyPlayersRequest()));
    }

    @FXML
    private void handleLogout() {
        onLogout.run();
    }

    @FXML
    private void handleSendInvite() {
        List<String> availablePlayers = new ArrayList<>();
        lobbyPlayers.forEach((user, inGame) -> { if (!inGame) availablePlayers.add(user); });

        if (availablePlayers.isEmpty()) return;

        ChoiceDialog<String> dialog = new ChoiceDialog<>(availablePlayers.get(0), availablePlayers);
        dialog.setTitle("Send Invite");
        dialog.setHeaderText("Select a player to invite");
        dialog.setContentText("Player:");

        dialog.showAndWait().ifPresent(target -> {
            network.sendPacket(new NetPacket(PacketType.INVITE_REQUEST, "", new InviteRequest(target)));
        });
    }

    @FXML
    private void handleViewInvites() {
        if (pendingInvites.isEmpty()) return;

        // Build a list of display strings
        List<String> choices = new ArrayList<>();
        Map<String, InviteNotificationResponse> mapping = new HashMap<>();
        for (InviteNotificationResponse invite : pendingInvites) {
            String display = invite.fromUsername();
            choices.add(display);
            mapping.put(display, invite);
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle("Pending Invites");
        dialog.setHeaderText("Select an invite to respond");
        dialog.setContentText("Invite from:");

        dialog.showAndWait().ifPresent(selected -> {
            InviteNotificationResponse invite = mapping.get(selected);
            if (invite == null) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Respond to Invite");
            confirm.setHeaderText("Invite from " + invite.fromUsername());
            confirm.setContentText("Accept invite?");
            boolean accept = confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;

            network.sendPacket(new NetPacket(PacketType.INVITE_DECISION_REQUEST, "",
                    new InviteDecisionRequest(invite.fromUsername(), accept)));

            pendingInvites.remove(invite);
        });
    }

    @FXML
    private void handlePing() {
        network.sendPacket(new NetPacket(PacketType.HANDSHAKE_REQUEST, "", new HandshakeRequest()));
    }

    private void handleServerPacket(NetPacket packet) {
        switch (packet.type()) {
            case LOBBY_PLAYERS_RESPONSE -> {
                LobbyPlayersResponse resp = (LobbyPlayersResponse) packet.payload();
                Platform.runLater(() -> updateLobbyPlayers(resp.players()));
            }
            case INVITE_NOTIFICATION_RESPONSE -> {
                InviteNotificationResponse invite = (InviteNotificationResponse) packet.payload();
                pendingInvites.add(invite);
            }
            case INVITE_RESPONSE, INVITE_DECISION_RESPONSE, HANDSHAKE_RESPONSE -> {
                // optionally handle responses in a small status area or console log
            }
        }
    }

    private void updateLobbyPlayers(Map<String, Boolean> players) {
        lobbyPlayers.clear();
        lobbyPlayers.putAll(players);

        lobbyPlayerListBox.getChildren().clear();
        players.forEach((user, inGame) -> {
            Label lbl = new Label(user + (inGame ? " 🎮 [IN GAME]" : " ✅ [AVAILABLE]"));
            lobbyPlayerListBox.getChildren().add(lbl);
        });
    }

    public void cleanup(GomokuFXApp app) {
        if (packetHandler != null) {
            app.unregisterPacketHandler(packetHandler);
            packetHandler = null;
        }
    }
}
