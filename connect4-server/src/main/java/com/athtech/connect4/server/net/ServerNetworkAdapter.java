package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.*;
import com.athtech.connect4.server.match.match;
import com.athtech.connect4.server.match.MatchManager;
import com.athtech.connect4.server.match.MatchManagerImpl;
import com.athtech.connect4.server.net.invite.InviteController;
import com.athtech.connect4.server.net.lobby.LobbyController;
import com.athtech.connect4.server.net.rematch.RematchController;
import com.athtech.connect4.server.persistence.PersistenceManager;
import com.athtech.connect4.server.persistence.Player;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerNetworkAdapter {

    private ServerSocket srvSocket;
    // connected clients: <clientId, ClientHandler>
    private final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    // match manager
    private final MatchManager matchManager = new MatchManagerImpl();
    private final LobbyController lobbyController;
    private final InviteController inviteController;
    private final RematchController rematchController;
    private final PersistenceManager persistence;

    public ServerNetworkAdapter(PersistenceManager persistence) {
        this.persistence = persistence;
        this.lobbyController = new LobbyController(connectedClients.values(),
                packet -> connectedClients.values().stream()
                        .filter(c -> c.getUsername() != null)
                        .forEach(c -> c.sendPacket(packet))
        );
        this.inviteController = new InviteController(lobbyController::isUserLoggedIn,this::sendToClient,this::createMatch);
        this.rematchController = new RematchController(matchManager, this::sendToClient);
    }

    // --- Server boot ---
    public void startServer(int port) {
        try {
            srvSocket = new ServerSocket(port);
            System.out.println("Server started and listens on port " + port);
            new Thread(this::acceptLoop).start();
        } catch (IOException e) {
            throw new RuntimeException("Server could not start: " + e.getMessage());
        }
    }

    private void acceptLoop() {
        while (true) {
            try {
                Socket clientSocket = srvSocket.accept();
                var handler = new ClientHandler(clientSocket, this, persistence);
                new Thread(handler).start();
            } catch (IOException e) {
                System.err.println("Client connection to the server failed: " + e.getMessage());
            }
        }
    }

    public void registerClientConnection(String clientId, ClientHandler handler) {
        connectedClients.put(clientId, handler);
    }

    public void unregisterClientConnection(String clientId) {
        connectedClients.remove(clientId);
    }

    public void broadcast(NetPacket packet) {
        connectedClients.values().forEach(h -> h.sendPacket(packet));
    }

    public void sendToClient(String username, NetPacket packet) {
        String clientId = lobbyController.getClientId(username).orElse(null);
        if (clientId != null) {
            ClientHandler handler = connectedClients.get(clientId);
            if (handler != null) handler.sendPacket(packet);
        }
    }

    // --- Match handling ---
    public match createMatch(String player1, String player2) {
        match match = matchManager.createMatch(player1, player2);
        broadcastMatchCreate(match);
        return match;
    }

    public void endMatch(String matchId) {
        matchManager.getMatch(matchId).ifPresent(match -> {
            updateStats(match);
            broadcastMatchUpdate(match);
            broadcastMatchEnd(match);
            matchManager.endMatch(matchId);
        });
    }

    public List<match> getActiveMatches() {
        return matchManager.getMatches();
    }

    public void broadcastMatchCreate(match match) {
        NetPacket packet = new NetPacket(PacketType.GAME_START_RESPONSE, "server", match.getCurrentState());
        sendToClient(match.getPlayer1(), packet);
        sendToClient(match.getPlayer2(), packet);
    }

    public void broadcastMatchUpdate(match match) {
        NetPacket packet = new NetPacket(PacketType.GAME_STATE_RESPONSE, "server", match.getCurrentState());
        sendToClient(match.getPlayer1(), packet);
        sendToClient(match.getPlayer2(), packet);
    }

    public GameStateResponse getActiveMatchForPlayer(String username) {
        return matchManager.getMatchByPlayer(username).map(match::getCurrentState).orElse(null);
    }

    // --- Invite handling ---
    public void sendInvite(String fromUsername, String targetUsername) {
        inviteController.sendInvite(fromUsername, targetUsername);
    }

    public void processInviteDecision(String targetUsername, String inviterUsername, boolean accepted) {
        inviteController.processInviteDecision(targetUsername, inviterUsername, accepted);
    }

    public InviteNotificationResponse[] getInvitationsFor(String targetUsername) {
        return inviteController.getInvitationsFor(targetUsername);
    }

    // --- Rematch handling ---
    public void sendRematchRequest(String username) {
        rematchController.sendRematchRequest(username);
    }

    public void processRematchDecision(String username, boolean accepted) {
        rematchController.processRematchDecision(username, accepted);
    }

    // --- Game moves ---
    public void processMove(String username, MoveRequest move) {
        matchManager.getMatchByPlayer(username).ifPresentOrElse(match -> {
            boolean ok = match.makeMove(username, move);
            if (!ok) sendToClient(username, new NetPacket(PacketType.MOVE_REJECTED_RESPONSE, "server",
                    new MoveRejectedResponse("Invalid move or not your turn")));
            else {
                broadcastMatchUpdate(match);
                if (match.isFinished()) endMatch(match.getMatchId());
            }
        }, () -> sendToClient(username, new NetPacket(PacketType.MOVE_REJECTED_RESPONSE, "server",
                new MoveRejectedResponse("No active match found. Are you in a game?"))));
    }

    // --- Reconnect ---
    public boolean attemptReconnect(String username, String relogCode, ClientHandler handler) {
        Optional<Player> playerOpt = persistence.getPlayerByUsername(username);
        if (playerOpt.isEmpty() || !relogCode.equals(playerOpt.get().getRelogCode()) || lobbyController.isUserLoggedIn(username))
            return false;
        lobbyController.userLoggedIn(username, handler.getClientId());
        handler.setUsername(username);
        return true;
    }

    // --- Match end & stats ---
    private void broadcastMatchEnd(match match) {
        GameEndResponse resp1 = new GameEndResponse(match.getCurrentState().board(),
                match.isDraw() ? null : match.getWinner(),
                match.isDraw() ? null : match.getLoser(),
                match.isDraw() ? "Draw" : "Win/Loss",
                match.getPlayer2());
        GameEndResponse resp2 = new GameEndResponse(match.getCurrentState().board(),
                match.isDraw() ? null : match.getWinner(),
                match.isDraw() ? null : match.getLoser(),
                match.isDraw() ? "Draw" : "Win/Loss",
                match.getPlayer1());
        sendToClient(match.getPlayer1(), new NetPacket(PacketType.GAME_END_RESPONSE, "server", resp1));
        sendToClient(match.getPlayer2(), new NetPacket(PacketType.GAME_END_RESPONSE, "server", resp2));
    }

    private void updateStats(match match) {
        if (!match.isFinished()) return;
        String winner = match.getWinner(), loser = match.getLoser();
        if (winner != null) persistence.getPlayerByUsername(winner)
                .ifPresent(p -> { p.incrementWins(); persistence.updatePlayerStats(p); });
        if (loser != null) persistence.getPlayerByUsername(loser)
                .ifPresent(p -> { p.incrementLosses(); persistence.updatePlayerStats(p); });
        if (match.isDraw()) {
            persistence.getPlayerByUsername(match.getPlayer1())
                    .ifPresent(p -> { p.incrementDraws(); persistence.updatePlayerStats(p); });
            persistence.getPlayerByUsername(match.getPlayer2())
                    .ifPresent(p -> { p.incrementDraws(); persistence.updatePlayerStats(p); });
        }
    }

    public LobbyController getLobbyController(){
        return this.lobbyController;
    }

}
