package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.server.match.ActiveMatch;
import com.athtech.connect4.server.match.MatchManager;
import com.athtech.connect4.server.match.MatchManagerImpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerNetworkAdapter {

    private ServerSocket srvSocket;

    // all connected clients: <clientId, handler>
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    // logged-in users: <username, clientId>
    private final Map<String, String> loggedInUsers = new ConcurrentHashMap<>();

    private final MatchManager matchManager = new MatchManagerImpl();

    public void startServer(int port) {
        try {
            srvSocket = new ServerSocket(port);
            System.out.println("Server listening on " + port);
            new Thread(this::acceptLoop).start();
        } catch (IOException e) {
            throw new RuntimeException("Could not start server: " + e.getMessage());
        }
    }

    private void acceptLoop() {
        while (true) {
            try {
                Socket clientSocket = srvSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();
            } catch (IOException e) {
                System.err.println("Accept failed: " + e.getMessage());
            }
        }
    }

    public void registerClient(String clientId, ClientHandler handler) {
        clients.put(clientId, handler);
    }

    public void unregisterClient(String clientId) {
        clients.remove(clientId);
    }

    public void setUserLoggedIn(String username, String clientId) {
        loggedInUsers.put(username, clientId);
        broadcastLoggedInUsers();
    }

    public void setUserLoggedOut(String username) {
        loggedInUsers.remove(username);
        broadcastLoggedInUsers();
    }

    public List<String> getLoggedInUsernames() {
        return new ArrayList<>(loggedInUsers.keySet());
    }

    public void broadcastLoggedInUsers() {
        String[] array = getLoggedInUsernames().toArray(new String[0]);
        NetPacket packet = new NetPacket(PacketType.LOBBY_PLAYERS, "server", array);
        broadcast(packet);
    }

    public void broadcast(NetPacket packet) {
        clients.values().forEach(h -> h.sendPacket(packet));
    }

    public void sendToClient(String username, NetPacket packet) {
        String clientId = loggedInUsers.get(username);
        if (clientId != null) {
            ClientHandler handler = clients.get(clientId);
            if (handler != null) handler.sendPacket(packet);
        }
    }

    // ----- Match handling -----
    public ActiveMatch createMatch(String player1, String player2) {
        return matchManager.createMatch(player1, player2);
    }

    public void endMatch(String matchId) {
        matchManager.endMatch(matchId);
    }

    public List<ActiveMatch> getActiveMatches() {
        return matchManager.getActiveMatches();
    }

    public void broadcastMatchUpdate(ActiveMatch match) {
        NetPacket packet = new NetPacket(
                PacketType.GAME_STATE,
                "server",
                match.getCurrentState()
        );
        sendToClient(match.getPlayer1(), packet);
        sendToClient(match.getPlayer2(), packet);
    }
}
