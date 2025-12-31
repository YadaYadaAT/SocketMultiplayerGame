package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.server.match.MatchController;
import com.athtech.connect4.server.persistence.PersistenceManager;

import java.io.*;
import java.net.Socket;
import java.util.UUID;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final ServerNetworkAdapter server;
    private final PacketDispatcher dispatcher;
    private final LobbyController lobbyController;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private final String clientId = UUID.randomUUID().toString();
    private String username = null;

    public ClientHandler(Socket clientSocket,
                         ServerNetworkAdapter server,
                         PersistenceManager persistence,
                         LobbyController lobbyController,
                         MatchController matchController) {
        this.clientSocket = clientSocket;
        this.server = server;
        this.lobbyController = lobbyController;
        this.dispatcher = new PacketDispatcher(persistence, lobbyController, matchController);
    }

    @Override
    public void run() {
        try {
            initStreams();
            System.out.println("Handler started for client: " + clientId);
            sendPacket(new NetPacket(PacketType.INFO_RESPONSE, "server", "Connected to server..."));

            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof NetPacket packet)) continue;
                dispatcher.dispatch(this, packet);
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client disconnected: " + clientId);
        } finally {
            if (username != null) lobbyController.userLoggedOut(username);
            server.unregisterClientConnection(clientId);
            try { clientSocket.close(); } catch (IOException ignored) {}
            System.out.println(username + " has been disconnected");
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
            } catch (IOException e) {
                System.err.println("Send failed to client " + clientId + ": " + e.getMessage());
            }
        }
    }

    private void initStreams() throws IOException {
        out = new ObjectOutputStream(clientSocket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(clientSocket.getInputStream());
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getClientId() { return clientId; }
}
