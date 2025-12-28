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
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerNetworkAdapter {

    private ServerSocket srvSocket;

    // connected clients: <clientId, ClientHandler>
    private final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    // logged-in clients: <username, clientId>
    private final Map<String, String> loggedInClients = new ConcurrentHashMap<>();
    // match manager
    private final MatchManager matchManager = new MatchManagerImpl();
    // pending invites <targetUsername, list of fromUsername>
    private final Map<String, CopyOnWriteArrayList<String>> pendingGamingInvites = new ConcurrentHashMap<>();
    // database - h2 being used currently, persist files at data folder
    private final PersistenceManager persistence;

    public ServerNetworkAdapter(PersistenceManager persistence) {
        this.persistence = persistence;
    }

    //SERVER BOOT
    public void startServer(int port) {
        try {
            srvSocket = new ServerSocket(port);
            System.out.println("Server started and listens on port " + port);
            new Thread(this::acceptLoop).start();
        } catch (IOException e) {
            throw new RuntimeException("Server could not start: " + e.getMessage());
        }
    }

    //CREATE CLIENT CONNECTION
    private void acceptLoop() {
        while (true) {
            try {//the accept thread occupation time is so small that doesn't create conjestion issues
                Socket clientSocket = srvSocket.accept();//acquire socket from server to be passed at clientHandler
                var handler = new ClientHandler(clientSocket, this, persistence);
                new Thread(handler).start();// start of new thread for the connection of the client.
            } catch (IOException e) {
                System.err.println("Client connection to the server failed: " + e.getMessage());
            }
        }
    }


    //METHODS USED BY ClientHandler TO MANAGE CLIENTS REQUEST
    public void registerClientConnection(String clientId, ClientHandler handler) {
        connectedClients.put(clientId, handler);
    }

    public void unregisterClientConnection(String clientId) {
        connectedClients.remove(clientId);
    }

    public void setUserLoggedIn(String username, String clientId) {
        loggedInClients.put(username, clientId);
        broadcastLoggedInUsersOnlyToLoggedInUsers();
    }

    public void setUserLoggedOut(String username) {
        loggedInClients.remove(username);
        broadcastLoggedInUsersOnlyToLoggedInUsers();
    }

    public List<String> getLoggedInUsernames() {
        return new ArrayList<>(loggedInClients.keySet());
    }

    public void broadcastLoggedInUsersOnlyToLoggedInUsers() {
        String[] array = getLoggedInUsernames().toArray(new String[0]);
        connectedClients.values().stream()
                .filter(cHandler -> cHandler.getUsername() != null)
                .forEach(h -> h.sendPacket(new NetPacket(PacketType.LOBBY_PLAYERS_RESPONSE, "server", array)));
    }

    public void broadcast(NetPacket packet) {
        connectedClients.values().forEach(h -> h.sendPacket(packet));
    }

    public void sendToClient(String username, NetPacket packet) {
        String clientId = loggedInClients.get(username);
        if (clientId != null) {
            ClientHandler handler = connectedClients.get(clientId);
            if (handler != null) handler.sendPacket(packet);
        }
    }

    // -------------------
    // Match handling
    // -------------------
    public ActiveMatch createMatch(String player1, String player2) {
        ActiveMatch match = matchManager.createMatch(player1, player2);
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


    public List<ActiveMatch> getActiveMatches() {
        return matchManager.getActiveMatches();
    }

    public void broadcastMatchCreate(ActiveMatch match){
        NetPacket packet = new NetPacket(PacketType.GAME_START_RESPONSE, "server", match.getCurrentState());
        sendToClient(match.getPlayer1(), packet);
        sendToClient(match.getPlayer2(), packet);
    }

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
        if (!loggedInClients.containsKey(targetUsername)) {
            sendToClient(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                    new InviteResponse(false, "Player not online")));
            return;
        }

        // get or create list of pending invites for target
        pendingGamingInvites.computeIfAbsent(targetUsername, k -> new CopyOnWriteArrayList<>())
                .add(fromUsername);

        sendToClient(targetUsername, new NetPacket(PacketType.INVITE_NOTIFICATION_RESPONSE, "server",
                new InviteNotificationResponse(fromUsername)));//send invitation to target of invite
        sendToClient(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                new InviteResponse(true, null)));// send confirmation to inviter
    }

    public void processInviteDecision(String targetUsername, String inviterUsername, boolean accepted) {
        CopyOnWriteArrayList<String> invites = pendingGamingInvites.get(targetUsername);
        if (invites == null || !invites.contains(inviterUsername)) return;

        // remove the specific invite from the list
        invites.remove(inviterUsername);

        var resp = new InviteDecisionResponse(inviterUsername, targetUsername, accepted);
        sendToClient(inviterUsername, new NetPacket(PacketType.INVITE_DECISION_RESPONSE, "server", resp));
        sendToClient(targetUsername, new NetPacket(PacketType.INVITE_DECISION_RESPONSE, "server", resp));

        // remove the entry if no more invites left
        if (invites.isEmpty()) pendingGamingInvites.remove(targetUsername);

        if (accepted) createMatch(inviterUsername, targetUsername);
    }

    public InviteNotificationResponse[] getInvitationsFor(String targetUsername) {
        CopyOnWriteArrayList<String> invites = pendingGamingInvites.get(targetUsername);
        if (invites == null || invites.isEmpty()) return new InviteNotificationResponse[0];

        return invites.stream()
                .map(InviteNotificationResponse::new)
                .toArray(InviteNotificationResponse[]::new);
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

    // Game moves
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

    // Reconnect
    public boolean attemptReconnect(String username, String relogCode, ClientHandler handler) {
        Optional<Player> playerOpt = persistence.getPlayerByUsername(username);
        if (playerOpt.isEmpty() || !relogCode.equals(playerOpt.get().getRelogCode()) || loggedInClients.containsKey(username))
            return false;
        setUserLoggedIn(username, handler.getClientId());
        handler.setUsername(username);
        return true;
    }


    private void broadcastMatchEnd(ActiveMatch match) {
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

}
