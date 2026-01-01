package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;

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

    public void userLoggedOut(String username) {
        loggedInClients.remove(username);
        System.out.println("[Lobby] User logged out: " + username);
    }

    public void broadcastLobby() {
        String[] users = getLoggedInUsernames().toArray(new String[0]);

        System.out.println("[Lobby] Broadcasting lobby list (" + users.length + " users)");

        broadcastToLoggedIn.accept(
                new NetPacket(PacketType.LOBBY_PLAYERS_RESPONSE, "server", users)
        );
    }
}
