package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.server.match.MatchController;
import com.athtech.connect4.server.persistence.PersistenceManager;
import com.athtech.connect4.server.persistence.PersistenceManagerImpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerNetworkAdapter {

    private ServerSocket srvSocket;
    private final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    //username -clientID
    private final Map<String, String> loggedInClients = new ConcurrentHashMap<>();
    private final PersistenceManager persistenceManager;
    private final LobbyController lobbyController;
    private final MatchController matchController;

    public ServerNetworkAdapter(PersistenceManagerImpl persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.lobbyController = new LobbyController(
                loggedInClients,
                connectedClients.values(),
                this::broadcastToLobby
        );
        this.matchController = new MatchController(this, lobbyController, persistenceManager);

    }

    public void startServer(int port) {
        try {
            srvSocket = new ServerSocket(port);
            System.out.println("Server started and listening on port " + port);
            new Thread(this::acceptLoop).start();
        } catch (IOException e) {
            throw new RuntimeException("Server could not start: " + e.getMessage());
        }
    }

    private void acceptLoop() {
        while (true) {
            try {
                Socket clientSocket = srvSocket.accept();
                ClientHandler handler = new ClientHandler(
                        clientSocket,
                        this,
                        persistenceManager,
                        lobbyController,
                        matchController
                );
                new Thread(handler).start();
                registerClientConnection(handler.getClientId(), handler);
            } catch (IOException e) {
                System.err.println("Client connection failed: " + e.getMessage());
            }
        }
    }

    public void registerClientConnection(String clientId, ClientHandler handler) {
        connectedClients.put(clientId, handler);
    }

    public void unregisterClientConnection(String clientId) {
        connectedClients.remove(clientId);
    }

    public void sendToClient(String username, NetPacket packet) {

        String clientId = loggedInClients.get(username);
        if (clientId != null) {
            ClientHandler handler = connectedClients.get(clientId);
            if (handler != null) handler.sendPacket(packet);
        }
    }

    public void broadcastToLobby(NetPacket packet) {
        connectedClients.values().stream().filter(c -> c.getUsername()!=null)
                .forEach(h -> h.sendPacket(packet));
    }

    public void broadcast(NetPacket packet) {
        connectedClients.values().forEach(h -> h.sendPacket(packet));
    }

    // Getters
    public LobbyController getLobbyController() { return lobbyController; }
    public MatchController getMatchController() { return matchController; }
}
