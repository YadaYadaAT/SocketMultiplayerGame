package com.athtech.gomoku.server.net;

import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.LobbyPlayersResponse;
import com.athtech.gomoku.server.match.MatchController;

import java.util.*;
import java.util.function.Consumer;

public class LobbyController {

    // logged-in users: <username, clientId>
    private final Map<String, String> loggedInClients ;
    private final Consumer<NetPacket> broadcastToLoggedIn;

    public LobbyController(Map<String, String> loggedInClients, Collection<ClientHandler> connectedClients,
                           Consumer<NetPacket> broadcastToLoggedIn) {
        this.loggedInClients = loggedInClients;
        this.broadcastToLoggedIn = broadcastToLoggedIn;
    }

    public void userLoggedIn(String username, String clientId) {
        loggedInClients.put(username, clientId);
        System.out.println("[Lobby] User logged in: " + username + " (clientId=" + clientId + ")");
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
        System.out.println("[Lobby] User logged out: " + username);
    }

    public void broadcastLobby(MatchController matchController) {
        Map<String, Boolean> lobby = getLobbySnapshot(matchController);

        System.out.println("[Lobby] Broadcasting lobby (" + lobby.size() + " users)");

        broadcastToLoggedIn.accept(
                new NetPacket(
                        PacketType.LOBBY_PLAYERS_RESPONSE,
                        "server",
                        new LobbyPlayersResponse(lobby)
                )
        );
    }
}
