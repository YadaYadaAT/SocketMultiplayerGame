package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.*;
import com.athtech.connect4.server.match.MatchController;
import com.athtech.connect4.server.persistence.PersistenceManager;

import java.util.Optional;
import java.util.UUID;

public class PacketDispatcher {

    private final PersistenceManager persistence;
    private final LobbyController lobbyController;
    private final MatchController matchController;

    public PacketDispatcher(PersistenceManager persistence,
                            LobbyController lobbyController,
                            MatchController matchController) {
        this.persistence = persistence;
        this.lobbyController = lobbyController;
        this.matchController = matchController;
    }

    public void dispatch(ClientHandler client, NetPacket packet) {
        switch (packet.type()) {
            case LOGIN_REQUEST -> handleLogin(client, packet);
            case SIGNUP_REQUEST -> handleSignup(client, packet);
            case LOGOUT_REQUEST -> handleLogout(client);
            case RECONNECT_REQUEST -> handleReconnect(client, packet);
            case INVITE_REQUEST -> handleInviteRequest(client, packet);
            case INVITE_DECISION_REQUEST -> handleInviteDecision(client, packet);
            case REMATCH_REQUEST -> handleRematchRequest(client);
            case REMATCH_DECISION_REQUEST -> handleRematchDecision(client, packet);
            case MOVE_REQUEST -> handleMove(client, packet);
            default -> client.sendPacket(new NetPacket(PacketType.ERROR_MESSAGE_RESPONSE, "server",
                    new ErrorMessageResponse("Unknown packet type: " + packet.type())));
        }
    }

    private void handleLogin(ClientHandler client, NetPacket packet) {
        var req = (LoginRequest) packet.payload();
        boolean ok = persistence.authenticate(req.username(), req.password());
        if (!ok) {
            client.sendPacket(new NetPacket(PacketType.LOGIN_RESPONSE, "server",
                    new LoginResponse(false, "Invalid credentials", null, null)));
            return;
        }

        client.setUsername(req.username());
        String relogCode = UUID.randomUUID().toString();
        persistence.getPlayerByUsername(client.getUsername())
                .ifPresent(player -> persistence.setRelogCode(player, relogCode));

        lobbyController.userLoggedIn(client.getUsername(), client.getClientId());
        PlayerStatsResponse stats = persistence.getPlayerStats(client.getUsername());

        client.sendPacket(new NetPacket(PacketType.LOGIN_RESPONSE, "server",
                new LoginResponse(true, "Welcome " + client.getUsername(), relogCode, stats)));
    }

    private void handleSignup(ClientHandler client, NetPacket packet) {
        var req = (SignupRequest) packet.payload();
        boolean ok = persistence.registerPlayer(req.username(), req.password());
        client.sendPacket(new NetPacket(PacketType.SIGNUP_RESPONSE, "server",
                new SignupResponse(ok, ok ? "Sign up complete, login and start gaming!" : "Username already exists")));
    }

    private void handleLogout(ClientHandler client) {
        if (client.getUsername() != null) {
            lobbyController.userLoggedOut(client.getUsername());
            client.sendPacket(new NetPacket(PacketType.LOGOUT_RESPONSE, "server",
                    new LogoutResponse(true, "Logged out successfully.")));
            client.setUsername(null);
        }
    }

    private void handleReconnect(ClientHandler client, NetPacket packet) {
        var req = (ReconnectRequest) packet.payload();
        Optional<String> stored = persistence.getRelogCode(req.username());
        if (stored.isEmpty() || !stored.get().equals(req.relogCode()) || lobbyController.isUserLoggedIn(req.username())) {
            client.sendPacket(new NetPacket(PacketType.RECONNECT_RESPONSE, "server",
                    new ReconnectResponse(false, "Invalid relog code. Please login again.",
                            null, null, null, null, null)));
            return;
        }

        client.setUsername(req.username());
        String relogCode = UUID.randomUUID().toString();
        persistence.getPlayerByUsername(client.getUsername())
                .ifPresent(player -> persistence.setRelogCode(player, relogCode));

        lobbyController.userLoggedIn(client.getUsername(), client.getClientId());

        String[] lobbyPlayers = lobbyController.getLoggedInUsernames().toArray(new String[0]);
        PlayerStatsResponse stats = persistence.getPlayerStats(client.getUsername());
        InviteNotificationResponse[] pendingInvites = matchController.getInvitationsFor(client.getUsername());
        GameStateResponse currentGame = matchController.getCurrentGame(client.getUsername());
        client.sendPacket(new NetPacket(PacketType.RECONNECT_RESPONSE, "server",
                new ReconnectResponse(true, "Reconnected successfully.",
                        lobbyPlayers, stats, pendingInvites, currentGame, relogCode)));
    }

    private void handleInviteRequest(ClientHandler client, NetPacket packet) {
        InviteRequest req = (InviteRequest) packet.payload();
        matchController.sendInvite(client.getUsername(), req.targetUsername());
    }

    private void handleInviteDecision(ClientHandler client, NetPacket packet) {
        InviteDecisionRequest req = (InviteDecisionRequest) packet.payload();
        matchController.processInviteDecision(client.getUsername(), req.inviterUsername(), req.accepted());
    }

    private void handleRematchRequest(ClientHandler client) {
        matchController.sendRematchRequest(client.getUsername());
    }

    private void handleRematchDecision(ClientHandler client, NetPacket packet) {
        RematchDecisionRequest req = (RematchDecisionRequest) packet.payload();
        matchController.processRematchDecision(client.getUsername(), req.accepted());
    }

    private void handleMove(ClientHandler client, NetPacket packet) {
        MoveRequest move = (MoveRequest) packet.payload();
        matchController.processMove(client.getUsername(), move);
    }
}
