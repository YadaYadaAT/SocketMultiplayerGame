package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.*;
import com.athtech.connect4.server.persistence.PersistenceManager;

import java.io.*;
import java.net.Socket;
import java.util.Optional;
import java.util.UUID;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final ServerNetworkAdapter server;
    private final PersistenceManager persistence;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private final String clientId = UUID.randomUUID().toString();

    private String username = null; // (username = null) => not logged in

    public ClientHandler(Socket clientSocket, ServerNetworkAdapter server, PersistenceManager persistence) {
        this.clientSocket = clientSocket;
        this.server = server;
        this.persistence = persistence;
    }

    @Override
    public void run() {
        try {
            initObjectStreamsUsingClientSocket();
            server.registerClientConnection(clientId, this);
            System.out.println("Handler started for client: " + clientId);
            sendPacket(new NetPacket(PacketType.INFO_RESPONSE, "server", "Connected to server..."));

            while (true) {
                Object obj = in.readObject();//ignores any non Netpacket object
                if (!(obj instanceof NetPacket packet)) continue;
                handlePacket(packet);
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client disconnected: " + clientId);
        } finally {
            if (username != null) server.setUserLoggedOut(username);
            server.unregisterClientConnection(clientId);
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    private void handlePacket(NetPacket packet) {
        switch (packet.type()) {
            case LOGIN_REQUEST -> handleLogin(packet);
            case SIGNUP_REQUEST -> handleSignup(packet);
            case LOGOUT_REQUEST -> handleLogout(packet);
            case RECONNECT_REQUEST -> handleReconnect(packet);
            case INVITE_REQUEST -> handleInviteRequest(packet);
            case INVITE_DECISION_REQUEST -> handleInviteDecision(packet);
            case REMATCH_REQUEST -> handleRematchRequest(packet);
            case REMATCH_DECISION_REQUEST -> handleRematchDecision(packet);
            case MOVE_REQUEST -> handleMove(packet);
            default -> sendPacket(new NetPacket(PacketType.ERROR_MESSAGE_RESPONSE, "server",
                    new ErrorMessageResponse("Unknown packet type: " + packet.type())));
        }
    }

    // -----------------------------
    // LOGIN / SIGNUP / LOGOUT
    // -----------------------------
    private void handleLogin(NetPacket packet) {
        var req = (LoginRequest) packet.payload();
        boolean ok = persistence.authenticate(req.username(), req.password());
        if (!ok) {
            sendPacket(new NetPacket(
                    PacketType.LOGIN_RESPONSE,
                    "server",
                    new LoginResponse(false, "Invalid credentials", null, null)
            ));
            return;
        }
        // success
        username = req.username();
        var relogCode = UUID.randomUUID().toString();

        // persist relogCode in DB
        persistence.getPlayerByUsername(username).ifPresent(player -> {
            persistence.setRelogCode(player, relogCode);
        });

        server.setUserLoggedIn(username, clientId);

        // fetch stats from DB
        PlayerStatsResponse stats = persistence.getPlayerStats(username);

        sendPacket(new NetPacket(
                PacketType.LOGIN_RESPONSE,
                "server",
                new LoginResponse(
                        true,
                        "Welcome " + username,
                        relogCode,
                        stats
                )
        ));
    }

    private void handleSignup(NetPacket packet) {
        var req = (SignupRequest) packet.payload();
        boolean didDbAcceptSignUp = persistence.registerPlayer(req.username(), req.password());

        sendPacket(new NetPacket(PacketType.SIGNUP_RESPONSE, "server",
                new SignupResponse(didDbAcceptSignUp, didDbAcceptSignUp ? "Sign up complete, " +
                        ", login and start gaming!" : "Username already exists")));
    }

    private void handleLogout(NetPacket packet) {
        if (username != null) {
            server.setUserLoggedOut(username);
            sendPacket(new NetPacket(PacketType.LOGOUT_RESPONSE, "server",
                    new LogoutResponse(true, "Logged out successfully.")));
            username = null;
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleReconnect(NetPacket packet) {
        var req = (ReconnectRequest) packet.payload();

        // fetch relogCode from DB
        Optional<String> storedRelog = persistence.getRelogCode(req.username());

        boolean valid = storedRelog.isPresent() && storedRelog.get().equals(req.relogCode());
        if (!valid) {
            sendPacket(new NetPacket(PacketType.RECONNECT_RESPONSE, "server",
                    new ReconnectResponse(false, "Invalid relog code. Please login again.",
                            null, null, null, null, null)));
            return;
        }

        username = req.username();
        var relogCode = UUID.randomUUID().toString();

        // persist new relogCode in DB
        persistence.getPlayerByUsername(username).ifPresent(player -> {
            persistence.setRelogCode(player, relogCode);
        });

        server.setUserLoggedIn(username, clientId);
        String[] lobbyPlayers = server.getLoggedInUsernames().toArray(new String[0]);
        PlayerStatsResponse stats = persistence.getPlayerStats(username);
        InviteNotificationResponse[] pendingGamingInvites = {}; // Optional
        GameStateResponse currentGameState = server.getActiveMatchForPlayer(username);

        sendPacket(new NetPacket(PacketType.RECONNECT_RESPONSE, "server",
                new ReconnectResponse(true, "Reconnected successfully.",
                        lobbyPlayers, stats, pendingGamingInvites, currentGameState, relogCode)));
    }

    private void handleInviteRequest(NetPacket packet) {
        InviteRequest req = (InviteRequest) packet.payload();
        server.sendInvite(username, req.targetUsername());
    }

    private void handleInviteDecision(NetPacket packet) {
        InviteDecisionRequest req = (InviteDecisionRequest) packet.payload();
        server.processInviteDecision(username, req.inviterUsername(), req.accepted());
    }

    private void handleRematchRequest(NetPacket packet) {
        server.sendRematchRequest(username);
    }

    private void handleRematchDecision(NetPacket packet) {
        RematchDecisionRequest req = (RematchDecisionRequest) packet.payload();
        server.processRematchDecision(username, req.accepted());
    }

    private void handleMove(NetPacket packet) {
        System.out.println("Move Request reached the server");
        MoveRequest move = (MoveRequest) packet.payload();
        server.processMove(username, move);
    }

    public void sendPacket(NetPacket packet) {
        try {
            out.writeObject(packet);
            out.flush();
        } catch (IOException e) {
            System.err.println("Send failed to client " + clientId + ": " + e.getMessage());
        }
    }


    public String getUsername() { return username; }
    public String getClientId() { return clientId; }
    public void setUsername(String username) { this.username = username; }

    private void initObjectStreamsUsingClientSocket() throws IOException {
        out = new ObjectOutputStream(clientSocket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(clientSocket.getInputStream());
    }
}
