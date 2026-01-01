package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.*;
import com.athtech.connect4.server.match.MatchController;
import com.athtech.connect4.server.persistence.PersistenceManager;

import java.time.Instant;
import java.util.List;
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
            case RESYNC_REQUEST -> handleResyncRequest(client, packet);
            case INVITE_REQUEST -> handleInviteRequest(client, packet);
            case INVITE_DECISION_REQUEST -> handleInviteDecision(client, packet);
            case REMATCH_REQUEST -> handleRematchRequest(client, packet);
            case MOVE_REQUEST -> handleMove(client, packet);
            case GAME_QUIT_REQUEST -> handleGameQuitRequest(client,packet);
            case HANDSHAKE -> handleHandshake(client,packet);
            default -> client.sendPacket(new NetPacket(PacketType.ERROR_MESSAGE_RESPONSE, "server",
                    new ErrorMessageResponse("Unknown packet type: " + packet.type())));
        }
    }

    private void handleHandshake(ClientHandler client, NetPacket packet){
        client.sendPacket(new NetPacket(PacketType.HANDSHAKE, "server",
               "handshake-response"));
    }

    private void handleGameQuitRequest(ClientHandler client, NetPacket packet) {
        if (client.getUsername() == null) {
            client.sendPacket(new NetPacket(
                    PacketType.GAME_QUIT_RESPONSE,
                    "server",
                    new GameQuitResponse(false)
            ));
            return;
        }

        boolean success = matchController.handleGameQuit(client.getUsername());

        client.sendPacket(new NetPacket(
                PacketType.GAME_QUIT_RESPONSE,
                "server",
                new GameQuitResponse(success)
        ));
    }

    private void handleLogin(ClientHandler client, NetPacket packet) {
        var req = (LoginRequest) packet.payload();
        boolean ok = persistence.authenticate(req.username(), req.password());
        if (!ok) {
            client.sendPacket(new NetPacket(PacketType.LOGIN_RESPONSE, "server",
                    new LoginResponse(false, "Invalid credentials", null, null,
                            null, null, null, null)));
            return;
        }

        client.setUsername(req.username());
        String relogCode = UUID.randomUUID().toString();

        persistence.getPlayerByUsername(client.getUsername())
                .ifPresent(player -> {
                    persistence.setRelogCode(player, relogCode);

                    // Fetch full state for login response
                    PlayerStatsResponse stats = persistence.getPlayerStats(player.getUsername());
                    InviteNotificationResponse[] pendingInvites = matchController.getInvitationsFor(player.getUsername());
                    GameStateResponse currentGame = matchController.getCurrentGame(player.getUsername());

                    client.sendPacket(new NetPacket(PacketType.LOGIN_RESPONSE, "server",
                            new LoginResponse(
                                    true,
                                    "Welcome " + player.getUsername(),
                                    relogCode,
                                    stats,
                                    pendingInvites,
                                    currentGame,
                                    player.getUsername(),
                                    player.getNickname()
                            )));
                });

        lobbyController.userLoggedIn(client.getUsername(), client.getClientId());
        lobbyController.broadcastLobby();
    }


    private void handleSignup(ClientHandler client, NetPacket packet) {
        var req = (SignupRequest) packet.payload();

        // Trim input
        String username = req.username().trim();
        String password = req.password().trim();
        String nickname = req.nickname() != null ? req.nickname().trim() : "";

        // Basic validation
        if (username.isEmpty() || password.isEmpty()) {
            client.sendPacket(new NetPacket(PacketType.SIGNUP_RESPONSE, "server",
                    new SignupResponse(false, "Username and password cannot be empty")));
            return;
        }

        // Only allow letters, numbers, and underscores for username and nickname
        if (!username.matches("[A-Za-z0-9_]+") || (!nickname.isEmpty() && !nickname.matches("[A-Za-z0-9_]+"))) {
            client.sendPacket(new NetPacket(PacketType.SIGNUP_RESPONSE, "server",
                    new SignupResponse(false, "Username and nickname can only contain letters, numbers, and underscores")));
            return;
        }

        // Create signup timestamp in UTC
        Instant createdAt = Instant.now();

        boolean ok = persistence.registerPlayer(username, password, nickname, createdAt);
        client.sendPacket(new NetPacket(PacketType.SIGNUP_RESPONSE, "server",
                new SignupResponse(ok, ok ? "Sign up complete, login and start gaming!" : "Username already exists")));
    }

    private void handleLogout(ClientHandler client) {
        if (client.getUsername() != null) {
            lobbyController.userLoggedOut(client.getUsername());
            client.sendPacket(new NetPacket(PacketType.LOGOUT_RESPONSE, "server",
                    new LogoutResponse(true, "Logged out successfully.")));
            client.setUsername(null);
            lobbyController.broadcastLobby();
        }
    }

    private void handleResyncRequest(ClientHandler client, NetPacket packet) {
        var req = (ResyncRequest) packet.payload();

        Optional<String> stored = persistence.getRelogCode(req.username());
        if (stored.isEmpty() || !stored.get().equals(req.relogCode())) {
            client.sendPacket(new NetPacket(
                    PacketType.RESYNC_RESPONSE,
                    "server",
                    new ResyncResponse(
                            false,
                            "Invalid relog code. Please login again.",
                            null, null, null, null, null
                    )
            ));
            return;
        }

        boolean alreadyLoggedIn = lobbyController.isUserLoggedIn(req.username());
        String relogCode;

        if (!alreadyLoggedIn) {
            client.setUsername(req.username());
            relogCode = UUID.randomUUID().toString();
            persistence.getPlayerByUsername(req.username())
                    .ifPresent(player -> persistence.setRelogCode(player, relogCode));

            lobbyController.userLoggedIn(req.username(), client.getClientId());
        } else {
            relogCode = req.relogCode();
        }

        List<String> lobbyPlayers = lobbyController.getLoggedInUsernames();
        PlayerStatsResponse stats = persistence.getPlayerStats(req.username());
        InviteNotificationResponse[] pendingInvites =
                matchController.getInvitationsFor(req.username());
        GameStateResponse currentGame =
                matchController.getCurrentGame(req.username());

        client.sendPacket(new NetPacket(
                PacketType.RESYNC_RESPONSE,
                "server",
                new ResyncResponse(
                        true,
                        "Re synced successfully.",
                        new LobbyPlayersResponse(lobbyPlayers),
                        stats,
                        pendingInvites,
                        currentGame,
                        relogCode
                )
        ));
    }


    private void handleInviteRequest(ClientHandler client, NetPacket packet) {
        InviteRequest req = (InviteRequest) packet.payload();
        matchController.sendInvite(client.getUsername(), req.targetUsername());
    }

    private void handleInviteDecision(ClientHandler client, NetPacket packet) {
        InviteDecisionRequest req = (InviteDecisionRequest) packet.payload();
        matchController.processInviteDecision(client.getUsername(), req.inviterUsername(), req.accepted());
    }

    private void handleRematchRequest(ClientHandler client,NetPacket packet ) {
        RematchRequest rem = (RematchRequest) packet.payload();
        matchController.sendRematchRequest(client.getUsername() , rem.decision());
    }

    private void handleMove(ClientHandler client, NetPacket packet) {
        MoveRequest move = (MoveRequest) packet.payload();
        matchController.processMove(client.getUsername(), move);
    }
}
