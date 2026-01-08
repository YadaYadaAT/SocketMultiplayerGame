package com.athtech.gomoku.server.net;

import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.LobbyChatMessageResponse;
import com.athtech.gomoku.protocol.payload.LobbyPlayersResponse;
import com.athtech.gomoku.server.match.MatchController;

import java.util.*;
import java.util.function.Consumer;

public class LobbyController {

    // logged-in users: <username, clientId>
    private final Map<String, String> loggedInClients ;
    private final Consumer<NetPacket> broadcastToLoggedIn;
    private MatchController matchController;
    Map<String, Boolean> lastSnapshot;
    private static final long TICK_MS = 1000;

    public LobbyController(Map<String, String> loggedInClients, Collection<ClientHandler> connectedClients,
                           Consumer<NetPacket> broadcastToLoggedIn) {
        this.loggedInClients = loggedInClients;
        this.broadcastToLoggedIn = broadcastToLoggedIn;
        startLobbyThread();
    }

    private void startLobbyThread() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    tick();
                    Thread.sleep(TICK_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "lobby-thread");

        t.setDaemon(true);
        t.start();
    }

    private void tick() {
        Map<String, Boolean> snapshot = new LinkedHashMap<>();

        synchronized (loggedInClients) {
            for (String user : loggedInClients.keySet()) {
                snapshot.put(user, matchController.isPlayerInGame(user));
            }
        }

        if (!snapshot.equals(lastSnapshot)) {
            lastSnapshot = snapshot;
            broadcastLobby(matchController);
        }
    }

    public void userLoggedIn(String username, String clientId) {
        loggedInClients.put(username, clientId);
        System.out.println("\uD83E\uDDD1\u200D\uD83E\uDD1D\u200D\uD83E\uDDD1 [Lobby] User logged in: " + username + " (clientId=" + clientId + ")");
    }

    public boolean isUserLoggedIn(String username) {
        return loggedInClients.containsKey(username);
    }

    public Optional<String> getClientId(String username) {
        return Optional.ofNullable(loggedInClients.get(username));
    }

    public List<String> getLoggedInUsernames() {
        return new ArrayList<>(loggedInClients.keySet());
    }

    public Map<String, Boolean> getLobbySnapshot(MatchController matchController) {
        Map<String, Boolean> snapshot = new LinkedHashMap<>();

        for (String username : loggedInClients.keySet()) {
            boolean inGame = matchController.isPlayerInGame(username);
            snapshot.put(username, inGame);
        }

        return snapshot;
    }

    public void userLoggedOut(String username) {
        loggedInClients.remove(username);
        System.out.println("\uD83E\uDDD1\u200D\uD83E\uDD1D\u200D\uD83E\uDDD1 [Lobby] User logged out: " + username);
    }

    public void broadcastLobby(MatchController matchController) {
        Map<String, Boolean> lobby = getLobbySnapshot(matchController);

        System.out.println("\uD83E\uDDD1\u200D\uD83E\uDD1D\u200D\uD83E\uDDD1 [Lobby] Broadcasting lobby (" + lobby.size() + " users)");

        broadcastToLoggedIn.accept(
                new NetPacket(
                        PacketType.LOBBY_PLAYERS_RESPONSE,
                        "server",
                        new LobbyPlayersResponse(lobby)
                )
        );
    }

    public synchronized void broadcastMessageLobbyChat(String from,String msg){
        LobbyChatMessageResponse lcmr = new LobbyChatMessageResponse(System.currentTimeMillis(),from,msg);
        System.out.println("\uD83E\uDDD1\u200D\uD83E\uDD1D\u200D\uD83E\uDDD1 [Lobby] Broadcasting message to lobby chat :"
                + lcmr.timestamp() +" "+lcmr.username() +" : " +lcmr.message()
        );
        broadcastToLoggedIn.accept(
                new NetPacket(
                        PacketType.LOBBY_CHAT_MESSAGE_RESPONSE,
                        "server",
                        lcmr
                ));
    }

    public void setMatchController (MatchController controller){
        this.matchController = controller;
    }
}
