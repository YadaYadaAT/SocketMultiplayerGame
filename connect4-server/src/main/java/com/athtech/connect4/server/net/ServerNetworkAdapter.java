package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.*;
import com.athtech.connect4.server.match.ActiveMatch;
import com.athtech.connect4.server.match.MatchManager;
import com.athtech.connect4.server.match.MatchManagerImpl;
import com.athtech.connect4.server.persistence.PersistenceManager;
import com.athtech.connect4.server.persistence.Player;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerNetworkAdapter {

    private ServerSocket srvSocket;

    // connected clients: <clientId, handler>
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    // logged-in users: <username, clientId>
    private final Map<String, String> loggedInUsers = new ConcurrentHashMap<>();
    private final MatchManager matchManager = new MatchManagerImpl();
    private final PersistenceManager persistence;
    private final Map<String, String> pendingInvites = new ConcurrentHashMap<>();

    public ServerNetworkAdapter(PersistenceManager persistence) {
        this.persistence = persistence;
    }

    public void startServer(int port) {
        try {
            srvSocket = new ServerSocket(port);
            System.out.println("[SERVER] Listening on port " + port);
            new Thread(this::acceptLoop).start();
        } catch (IOException e) {
            throw new RuntimeException("[SERVER] Could not start server: " + e.getMessage());
        }
    }

    private void acceptLoop() {
        while (true) {
            try {
                Socket clientSocket = srvSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, this, persistence);
                new Thread(handler).start();
            } catch (IOException e) {
                System.err.println("[SERVER] Accept failed: " + e.getMessage());
            }
        }
    }

    // -------------------
    // Client registration
    // -------------------
    public void registerClient(String clientId, ClientHandler handler) { clients.put(clientId, handler); }
    public void unregisterClient(String clientId) { clients.remove(clientId); }
    public void setUserLoggedIn(String username, String clientId) { loggedInUsers.put(username, clientId); broadcastLoggedInUsers(); }
    public void setUserLoggedOut(String username) { loggedInUsers.remove(username); broadcastLoggedInUsers(); }
    public List<String> getLoggedInUsernames() { return new ArrayList<>(loggedInUsers.keySet()); }
    public void broadcastLoggedInUsers() {
        String[] array = getLoggedInUsernames().toArray(new String[0]);
        broadcast(new NetPacket(PacketType.LOBBY_PLAYERS_RESPONSE, "server", array));
    }

    public void broadcast(NetPacket packet) { clients.values().forEach(h -> h.sendPacket(packet)); }
    public void sendToClient(String username, NetPacket packet) {
        String clientId = loggedInUsers.get(username);
        if (clientId != null) {
            ClientHandler handler = clients.get(clientId);
            if (handler != null) handler.sendPacket(packet);
        }
    }

    // -------------------
    // Match handling
    // -------------------
    public ActiveMatch createMatch(String player1, String player2) {
        ActiveMatch match = matchManager.createMatch(player1, player2);
        broadcastMatchUpdate(match);
        return match;
    }

    public void endMatch(String matchId) {
        matchManager.getMatch(matchId).ifPresent(match -> {
            updateStats(match);
            broadcastMatchUpdate(match);
            sendGameEnd(match);
            matchManager.endMatch(matchId);
        });
    }

    private void updateStats(ActiveMatch match) {
        if (!match.isFinished()) return;
        String winner = match.getWinner(), loser = match.getLoser();
        if (winner != null) persistence.getPlayerByUsername(winner).ifPresent(p -> { p.incrementWins(); persistence.updatePlayerStats(p); });
        if (loser != null) persistence.getPlayerByUsername(loser).ifPresent(p -> { p.incrementLosses(); persistence.updatePlayerStats(p); });
        if (match.isDraw()) {
            persistence.getPlayerByUsername(match.getPlayer1()).ifPresent(p -> { p.incrementDraws(); persistence.updatePlayerStats(p); });
            persistence.getPlayerByUsername(match.getPlayer2()).ifPresent(p -> { p.incrementDraws(); persistence.updatePlayerStats(p); });
        }
    }

    private void sendGameEnd(ActiveMatch match) {
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

    public List<ActiveMatch> getActiveMatches() { return matchManager.getActiveMatches(); }
    public void broadcastMatchUpdate(ActiveMatch match) {
        NetPacket packet = new NetPacket(PacketType.GAME_STATE_RESPONSE, "server", match.getCurrentState());
        sendToClient(match.getPlayer1(), packet);
        sendToClient(match.getPlayer2(), packet);
    }
    public GameStateResponse getActiveMatchForPlayer(String username) {
        return matchManager.getMatchByPlayer(username).map(ActiveMatch::getCurrentState).orElse(null);
    }

    // -------------------
    // Invite handling
    // -------------------
    public void sendInvite(String fromUsername, String targetUsername) {
        if (!loggedInUsers.containsKey(targetUsername)) {
            sendToClient(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                    new InviteResponse(false, "Player not online")));
            return;
        }
        pendingInvites.put(targetUsername, fromUsername);
        sendToClient(targetUsername, new NetPacket(PacketType.INVITE_NOTIFICATION_RESPONSE, "server",
                new InviteNotificationResponse(fromUsername)));
        sendToClient(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                new InviteResponse(true, null)));
    }

    public void processInviteDecision(String targetUsername, boolean accepted) {
        String inviter = pendingInvites.get(targetUsername);
        if (inviter == null) return;
        InviteDecisionResponse resp = new InviteDecisionResponse(inviter, targetUsername, accepted);
        sendToClient(inviter, new NetPacket(PacketType.INVITE_DECISION_RESPONSE, "server", resp));
        sendToClient(targetUsername, new NetPacket(PacketType.INVITE_DECISION_RESPONSE, "server", resp));
        pendingInvites.remove(targetUsername);
        if (accepted) createMatch(inviter, targetUsername);
    }

    // -------------------
    // Rematch handling
    // -------------------
    public void sendRematchRequest(String username) {
        matchManager.getMatchByPlayer(username).ifPresent(match -> {

            // handle timeout first
            if (match.isRematchTimedOut()) {
                match.cancelRematch();

                sendToClient(match.getPlayer1(),
                        new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                                new RematchResponse(false, "Rematch timed out")));
                sendToClient(match.getPlayer2(),
                        new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                                new RematchResponse(false, "Rematch timed out")));
                return;
            }

            match.requestRematch(username);

            String other = match.getPlayer1().equals(username)
                    ? match.getPlayer2()
                    : match.getPlayer1();

            sendToClient(other, new NetPacket(
                    PacketType.REMATCH_NOTIFICATION_RESPONSE,
                    "server",
                    new RematchNotificationResponse(username)
            ));

            if (match.canStartRematch()) {
                match.resetRematchState();
                endMatch(match.getMatchId());
                createMatch(match.getPlayer1(), match.getPlayer2());
            }
        });
    }


    public void processRematchDecision(String username, boolean accepted) {
        matchManager.getMatchByPlayer(username).ifPresent(match -> {

            // NO = immediate cancel
            if (!accepted) {
                match.cancelRematch();

                sendToClient(match.getPlayer1(),
                        new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                                new RematchResponse(false, "Rematch declined")));
                sendToClient(match.getPlayer2(),
                        new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                                new RematchResponse(false, "Rematch declined")));
            }

            // YES is handled ONLY via sendRematchRequest
        });
    }

    // -------------------
    // Game moves
    // -------------------
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

    // -------------------
    // Reconnect
    // -------------------
    public boolean attemptReconnect(String username, String relogCode, ClientHandler handler) {
        Optional<Player> playerOpt = persistence.getPlayerByUsername(username);
        if (playerOpt.isEmpty() || !relogCode.equals(playerOpt.get().getRelogCode()) || loggedInUsers.containsKey(username))
            return false;
        setUserLoggedIn(username, handler.getClientId());
        handler.setUsername(username);
        return true;
    }
}
