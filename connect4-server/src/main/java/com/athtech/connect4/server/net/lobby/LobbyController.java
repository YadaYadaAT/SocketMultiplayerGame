package com.athtech.connect4.server.net.lobby;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.server.net.ClientHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LobbyController {

    // logged-in users: <username, clientId>
    private final Map<String, String> loggedInClients = new ConcurrentHashMap<>();

    private final Collection<ClientHandler> connectedClients;
    private final Consumer<NetPacket> broadcastToLoggedIn;

    public LobbyController(Collection<ClientHandler> connectedClients, Consumer<NetPacket> broadcastToLoggedIn) {
        this.connectedClients = connectedClients;
        this.broadcastToLoggedIn = broadcastToLoggedIn;
    }

    public void userLoggedIn(String username, String clientId) {
        loggedInClients.put(username, clientId);
        broadcastLobby();
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
        broadcastLobby();
    }

    private void broadcastLobby() {
        String[] users = getLoggedInUsernames().toArray(new String[0]);
        broadcastToLoggedIn.accept(
                new NetPacket(PacketType.LOBBY_PLAYERS_RESPONSE, "server", users)
        );
    }
}
