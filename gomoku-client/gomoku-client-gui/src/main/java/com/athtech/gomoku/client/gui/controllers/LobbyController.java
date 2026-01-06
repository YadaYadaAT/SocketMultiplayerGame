package com.athtech.gomoku.client.gui.controllers;

import com.athtech.gomoku.client.gui.enums.View;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.*;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.HashMap;
import java.util.Map;

public class LobbyController extends BaseController {

    @FXML private ListView<String> lobbyPlayersList;
    @FXML private Label lobbyStatusLabel;
    @FXML private Label inviteLabel;

    @FXML private Label played;
    @FXML private Label wins;
    @FXML private Label loses;
    @FXML private Label draws;

    private Map<String, String> displayToUsername = new HashMap<>();
    private InviteNotificationResponse lastInvite;

    /* ---------------- UI actions ---------------- */

    @FXML
    private void handleInvite() {
        String selectedLabel  = lobbyPlayersList.getSelectionModel().getSelectedItem();
        if (selectedLabel  == null) {
            lobbyStatusLabel.setText("Select a player first.");
            return;
        }
        String usernameToInvite = displayToUsername.get(selectedLabel );
        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.INVITE_REQUEST,
                        data.getUsername(),
                        new InviteRequest(usernameToInvite)
                )
        );
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
    private void handleAcceptInvite() {
        if (lastInvite == null) return;

        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.INVITE_DECISION_REQUEST,
                        data.getUsername(),
                        new InviteDecisionRequest(lastInvite.fromUsername(), true)
                )
        );

        inviteLabel.setText("");
        lastInvite = null;
    }

    @FXML
    private void handleDeclineInvite() {
        if (lastInvite == null) return;

        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.INVITE_DECISION_REQUEST,
                        data.getUsername(),
                        new InviteDecisionRequest(lastInvite.fromUsername(), false)
                )
        );

        inviteLabel.setText("");
        lastInvite = null;
    }

    /* ---------------- Network → UI ---------------- */

    public void onLobbyPlayersResponse(NetPacket packet) {
        LobbyPlayersResponse resp = (LobbyPlayersResponse) packet.payload();

        Platform.runLater(() -> {
            lobbyPlayersList.getItems().clear();
            displayToUsername.clear();

            for (Map.Entry<String, Boolean> entry : resp.players().entrySet()) {
                String username = entry.getKey();
                boolean inGame = entry.getValue();

                if (username.equals(data.getUsername())) continue;

                String label = username + (inGame ? " 🎮 [IN GAME]" : " ✅ [AVAILABLE]");
                lobbyPlayersList.getItems().add(label);
                displayToUsername.put(label, username);
            }
        });
    }

    public void onPlayerStatsResponse(NetPacket packet) {
        PlayerStatsResponse stats = (PlayerStatsResponse) packet.payload();
        Platform.runLater(() ->{
            played.setText(Integer.toString(stats.gamesPlayed()));
            wins.setText(Integer.toString(stats.wins()));
            loses.setText(Integer.toString(stats.losses()));
            draws.setText(Integer.toString(stats.draws()));
        });
    }


    public void onInviteResponse(NetPacket packet) {
        var resp = (InviteResponse) packet.payload();
        String msg = resp.delivered() ? "Invite delivered." : "Invite failed: " + resp.reason();
        Platform.runLater(() ->  inviteLabel.setText(msg) );
    }

    public void onInviteNotificationResponse(NetPacket packet) {
        lastInvite = (InviteNotificationResponse) packet.payload();

        Platform.runLater(() ->
                inviteLabel.setText("Invite from " + lastInvite.fromUsername())
        );
    }

    public void onInviteDecisionResponse(NetPacket packet) {
        InviteDecisionResponse resp = (InviteDecisionResponse) packet.payload();

        Platform.runLater(() -> {

            if (resp.accepted()) {
                //navigations triggered by callbacks are allowed only on wrapperController to avoid duplicates...
                // Here is the spot where we clear the invites !!!
            }else{
                if (resp.targetUsername().equals(data.getUsername())){
                    lobbyStatusLabel.setText("You declined invitation to " + resp.inviterUsername());
                }else{
                    lobbyStatusLabel.setText(resp.targetUsername() + " declined your invitation");
                }
            }
        });
    }


    public void onLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();

        if (((LoginResponse) packet.payload()).success()) {
            Platform.runLater(() ->{
                PlayerStatsResponse stats = resp.myStats();
                played.setText(Integer.toString(stats.gamesPlayed()));
                wins.setText(Integer.toString(stats.wins()));
                loses.setText(Integer.toString(stats.losses()));
                draws.setText(Integer.toString(stats.draws()));
             });
        }

    }



    public void onLogoutResponse(NetPacket packet) {
        LogoutResponse resp = (LogoutResponse) packet.payload();
        if (resp.success()){
            Platform.runLater(() -> {
                lobbyStatusLabel.setText(resp.message());
            });
        }
    }

    public void showInfo(InfoResponse info) {
        Platform.runLater(() -> lobbyStatusLabel.setText(info.msg()));
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
}
