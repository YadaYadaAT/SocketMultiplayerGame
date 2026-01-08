package com.athtech.gomoku.server.net;

import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.InfoResponse;
import com.athtech.gomoku.protocol.payload.LobbyChatMessageResponse;
import com.athtech.gomoku.server.match.MatchController;
import com.athtech.gomoku.server.persistence.PersistenceManager;

import java.io.*;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final ServerNetworkAdapter server;
    private final PacketDispatcher dispatcher;
    private final LobbyController lobbyController;
    private final MatchController matchController;
    private volatile long lastActivity = System.currentTimeMillis();
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private final String clientId = UUID.randomUUID().toString();
    private String username = null;

    private static final int MAX_MESSAGES = 5;       // max messages allowed
    private static final long WINDOW_MS = 10000;    // in 10 seconds
    private final Deque<Long> recentMessageTimestamps = new ArrayDeque<>();

    public ClientHandler(Socket clientSocket,
                         ServerNetworkAdapter server,
                         PersistenceManager persistence,
                         LobbyController lobbyController,
                         MatchController matchController) {
        this.matchController =matchController;
        this.clientSocket = clientSocket;
        this.server = server;
        this.lobbyController = lobbyController;
        this.dispatcher = new PacketDispatcher(persistence, lobbyController, matchController);
    }

    @Override
    public void run() {
        try {
            initStreams();
            System.out.println("\uD83D\uDEF0\uFE0F [ClientHandler] Handler started (clientId=" + clientId + ")");

            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof NetPacket packet)) continue;
                System.out.println(" [ClientHandler] Received packet from client " + clientId +
                        " | Type: " + packet.type() +
                        " | Payload: " + packet.payload());

                updateActivity();
                dispatcher.dispatch(this, packet);
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("\uD83D\uDEF0\uFE0F [ClientHandler] Client disconnected unexpectedly (clientId=" + clientId + ")");
        } finally {
            if (username != null) {
                matchController.disconnectPlayer(username);
                lobbyController.userLoggedOut(username);
                lobbyController.broadcastLobby(matchController);
                lobbyController.broadcastMessageLobbyChat("[server] : ",username + " has left the lobby.");
                System.out.println("\uD83D\uDEF0\uFE0F [ClientHandler] User session ended: " + username);
            }
            server.unregisterClientConnection(clientId);
            try { clientSocket.close(); } catch (IOException ignored) {}
            if (username!=null){
                System.out.println("\uD83D\uDEF0\uFE0F [ClientHandler]" +username + " has been disconnected");
            }else{
                System.out.println("\uD83D\uDEF0\uFE0F [ClientHandler] Unregistered user has been disconnected");
            }

        }
    }

    public void sendPacket(NetPacket packet) {
        synchronized (this) { //!!! CULPRIT....during broadcast of login and logout
            // this little monster here was breaking since it the socket was being used
            //by multiple threads. At the start we thought that it was safe to by unsync
            //since it belongs to the thread of a cliend only but obviously it is being used by everyone who wants
            //to send to him.
            try {
                out.writeObject(packet);
                out.flush();
                // Debug output
                System.out.println(" [ClientHandler] Sent packet to client " + clientId +
                        " | Type: " + packet.type() +
                        " | Payload: " + packet.payload());
            } catch (IOException e) {
                System.err.println("Send failed to client " + clientId + ": " + e.getMessage());
            }
        }
    }

    public void disconnectExistingSession(String username) {
        server.forceDisconnectUser(username);
    }

    public void close() {
        try {
            clientSocket.close(); // this will break readObject()
        } catch (IOException ignored) {}
    }

    private void initStreams() throws IOException {
        out = new ObjectOutputStream(clientSocket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(clientSocket.getInputStream());
    }

    public String getUsername() { return username; }
    public void setUsername(String username) {
        if (username == null){
            matchController.disconnectPlayer(this.username);
        }
        this.username = username;
    }
    public String getClientId() { return clientId; }
    public void updateActivity() {
        lastActivity = System.currentTimeMillis();
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public synchronized boolean canSendLobbyMessage() {
        long now = System.currentTimeMillis();

        // remove old timestamps outside the window
        while (!recentMessageTimestamps.isEmpty() && now - recentMessageTimestamps.peekFirst() > WINDOW_MS) {
            recentMessageTimestamps.pollFirst();
        }

        if (recentMessageTimestamps.size() >= MAX_MESSAGES) {
            return false;
        }

        recentMessageTimestamps.addLast(now);
        return true;
    }


}
