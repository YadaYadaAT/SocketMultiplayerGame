package com.athtech.gomoku.server.net;

import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.InfoResponse;
import com.athtech.gomoku.server.match.MatchController;
import com.athtech.gomoku.server.persistence.PersistenceManager;
import com.athtech.gomoku.server.persistence.PersistenceManagerImpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerNetworkAdapter {

    private ServerSocket srvSocket;
    // client id - clientHandler
    private final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    //username -clientID
    private final Map<String, String> loggedInClients = new ConcurrentHashMap<>();
    private final long INACTIVITY_LIMIT_MS = 3 * 60 * 1000; // 3 min (was set low for reviewer testing)
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
            System.out.println("[Server] Started and listening on port " + port);
            startInactivityChecker();
            new Thread(this::acceptLoop).start();
        } catch (IOException e) {
            throw new RuntimeException("Server could not start: " + e.getMessage());
        }
    }

    private void acceptLoop() {
        while (true) {
            try {
                Socket clientSocket = srvSocket.accept();
                clientSocket.setKeepAlive(true);
                System.out.println("[Server] New client connection from " + clientSocket.getRemoteSocketAddress());
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
                System.err.println("[Server] Client connection failed: " + e.getMessage());
            }
        }
    }

    public void forceDisconnectUser(String username) {
        String oldClientId = loggedInClients.get(username);
        if (oldClientId == null) return;

        ClientHandler oldHandler = connectedClients.get(oldClientId);
        if (oldHandler != null) {
            System.out.println("[Server] Forcing disconnect of previous session for user: " + username +
                    " (clientId=" + oldClientId + ")");
            oldHandler.close();
        }
    }

    public void registerClientConnection(String clientId, ClientHandler handler) {
        connectedClients.put(clientId, handler);
        System.out.println("[Server] Client registered: " + clientId);
    }

    public void unregisterClientConnection(String clientId) {
        connectedClients.remove(clientId);
        System.out.println("[Server] Client unregistered: " + clientId);
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

    private void startInactivityChecker() {
        new Thread(() -> {
            while (true) {
                try {
                    System.out.println("[Server] start inactivity checker started");
                    Thread.sleep(1 * 30 * 1000); // every half minute ,(was set low for reviewer testing)
                    System.out.println("[Server] start inactivity checker triggerred");
                    long now = System.currentTimeMillis();

                    for (ClientHandler client : connectedClients.values()) {
                        if (now - client.getLastActivity() > INACTIVITY_LIMIT_MS) {
                            System.out.println("[Server] Disconnecting inactive client: " + client.getUsername());

                            // send info packet
                            client.sendPacket(new NetPacket(
                                    PacketType.INFO_RESPONSE,
                                    "server",
                                    new InfoResponse("You have been disconnected due to inactivity.")
                            ));

                            client.close(); // triggers cleanup in finally block of ClientHandler.run()
                        }
                    }
                } catch (InterruptedException ignored) {}
            }
        }, "InactivityChecker").start();
    }

    // Getters
    public LobbyController getLobbyController() { return lobbyController; }
    public MatchController getMatchController() { return matchController; }
}
