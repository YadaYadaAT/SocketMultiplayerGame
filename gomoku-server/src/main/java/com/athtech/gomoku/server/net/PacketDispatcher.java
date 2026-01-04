package com.athtech.gomoku.server.net;

import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.server.match.MatchController;
import com.athtech.gomoku.server.persistence.PersistenceManager;
import com.athtech.gomoku.protocol.payload.*;

import java.time.Instant;
import java.util.Map;
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
            case LOBBY_PLAYERS_REQUEST -> handleLobbyPlayerRequest(client, packet);
            case REMATCH_REQUEST -> handleRematchRequest(client, packet);
            case MOVE_REQUEST -> handleMove(client, packet);
            case GAME_QUIT_REQUEST -> handleGameQuitRequest(client,packet);
            case HANDSHAKE_REQUEST -> handleHandshakeRequest(client,packet);
            default -> client.sendPacket(new NetPacket(PacketType.ERROR_MESSAGE_RESPONSE, "server",
                    new ErrorMessageResponse("Unknown packet type: " + packet.type())));
        }
    }

    private void handleLobbyPlayerRequest(ClientHandler client, NetPacket packet){
        if(client.getUsername() == null){
            return;
        }
        Map<String, Boolean> lobbyPlayers = lobbyController.getLobbySnapshot(matchController);
        client.sendPacket(new NetPacket(PacketType.LOBBY_PLAYERS_RESPONSE,"server",new LobbyPlayersResponse(lobbyPlayers)));
    }

    private void handleHandshakeRequest(ClientHandler client, NetPacket packet){
        if (client.getUsername() == null){
            client.sendPacket(new NetPacket(PacketType.HANDSHAKE_RESPONSE, "server",
                    new HandshakeResponse("Connection to server tested") ));
        }else{
            client.sendPacket(new NetPacket(PacketType.HANDSHAKE_RESPONSE, "server",
                  new HandshakeResponse("Connection to server tested. User:  " + client.getUsername() + " .")  ));
        }


    }

    private void handleGameQuitRequest(ClientHandler client, NetPacket packet) {
        if (client.getUsername() == null || !(packet.payload() instanceof GameQuitRequest)) {
            return;
        }
        GameQuitRequest payload = (GameQuitRequest) packet.payload();

        if (payload.isUnstuckProcess()){
         GameStateResponse game = matchController.getCurrentGame(client.getUsername());
             if (game == null){
                 client.sendPacket(new NetPacket(PacketType.INFO_RESPONSE,"server",
                         new InfoResponse("You are not part of any active game")));
                 return;
             }
        }
        boolean success = matchController.handleGameQuit(client.getUsername());
        client.sendPacket(new NetPacket(PacketType.GAME_QUIT_RESPONSE,"server",new GameQuitResponse(success)));
    }

    private void handleLogin(ClientHandler client, NetPacket packet) {
        if (client.getUsername() != null) {
            client.sendPacket(new NetPacket(
                    PacketType.LOGIN_RESPONSE,
                    "server",
                    new LoginResponse(false,
                            "You are already logged in on this client session",
                            null, null, null, null, null, null)
            ));
            return;
        }

        var req = (LoginRequest) packet.payload();
        String username = req.username().trim().toLowerCase();
        String password = req.password();

        boolean ok = persistence.authenticate(username, password);
        if (!ok) {
            client.sendPacket(new NetPacket(
                    PacketType.LOGIN_RESPONSE,
                    "server",
                    new LoginResponse(false, "Invalid credentials",
                            null, null, null, null, null, null)
            ));
            return;
        }

        client.disconnectExistingSession(username);
        client.setUsername(username);
        // reconnect player in match if any...
        matchController.reconnectPlayer(username);
        String relogCode = UUID.randomUUID().toString();

        persistence.getPlayerByUsername(username).ifPresent(player -> {
            persistence.setRelogCode(player, relogCode);

            PlayerStatsResponse stats = persistence.getPlayerStats(username);
            InviteNotificationResponse[] pendingInvites =
                    matchController.getInvitationsFor(username);
            GameStateResponse currentGame =
                    matchController.getCurrentGame(username);

            client.sendPacket(new NetPacket(
                    PacketType.LOGIN_RESPONSE,
                    "server",
                    new LoginResponse(
                            true,
                            "Welcome " + player.getUsername(),
                            relogCode,
                            stats,
                            pendingInvites,
                            currentGame,
                            player.getUsername(),
                            player.getNickname()
                    )
            ));
        });

        lobbyController.userLoggedIn(username, client.getClientId());
        lobbyController.broadcastLobby(matchController);
    }



    private void handleSignup(ClientHandler client, NetPacket packet) {
        var req = (SignupRequest) packet.payload();

        // Normalize input
        String username = req.username().trim().toLowerCase();
        String password = req.password().trim();
        String nickname = req.nickname() != null ? req.nickname().trim() : "";

        if (username.isEmpty() || password.isEmpty()) {
            client.sendPacket(new NetPacket(
                    PacketType.SIGNUP_RESPONSE,
                    "server",
                    new SignupResponse(false, "Username and password cannot be empty")
            ));
            return;
        }

        if (!username.matches("[a-z0-9_]+") ||
                (!nickname.isEmpty() && !nickname.matches("[A-Za-z0-9_]+"))) {

            client.sendPacket(new NetPacket(
                    PacketType.SIGNUP_RESPONSE,
                    "server",
                    new SignupResponse(false,
                            "Username may contain lowercase letters, numbers, and underscores only")
            ));
            return;
        }

        boolean ok = persistence.registerPlayer(
                username,
                password,
                nickname,
                Instant.now()
        );

        if (!ok) {
            client.sendPacket(new NetPacket(
                    PacketType.SIGNUP_RESPONSE,
                    "server",
                    new SignupResponse(false, "Username already exists")
            ));
            return;
        }

        client.sendPacket(new NetPacket(
                PacketType.SIGNUP_RESPONSE,
                "server",
                new SignupResponse(true, "Sign up complete, login and start gaming!")
        ));
    }


    private void handleLogout(ClientHandler client) {
        if (client.getUsername() != null) {
            lobbyController.userLoggedOut(client.getUsername());
            client.sendPacket(new NetPacket(PacketType.LOGOUT_RESPONSE, "server",
                    new LogoutResponse(true, "Logged out successfully.")));
            client.setUsername(null);
            lobbyController.broadcastLobby(matchController);
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

        Map<String, Boolean> lobbyPlayers = lobbyController.getLobbySnapshot(matchController);
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
                        "Re synced : Welcome back " + req.username(),
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
