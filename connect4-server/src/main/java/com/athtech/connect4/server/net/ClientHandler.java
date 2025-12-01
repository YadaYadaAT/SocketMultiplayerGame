package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.server.persistence.PersistenceManager;
import com.athtech.connect4.server.persistence.Player;

import java.io.*;
import java.net.*;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerNetworkAdapter server;
    private final PersistenceManager persistenceManager;
    private BufferedReader reader;
    private PrintWriter writer;
    private final String clientId = UUID.randomUUID().toString(); // temporary ID

    public ClientHandler(Socket socket, ServerNetworkAdapter server, PersistenceManager pm) {
        this.socket = socket;
        this.server = server;
        this.persistenceManager = pm;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            server.registerClient(clientId, this);
            sendPacket("WELCOME " + clientId); // send welcome to client
            String line;
            // Reads a line of text from the client's socket input stream.
            // Internally:
            //   socket.getInputStream() provides raw bytes from the network,
            //   InputStreamReader converts bytes to characters,
            //   BufferedReader buffers characters and detects line breaks for readLine().
            // This call blocks until a full line is available or the stream is closed.
            while ((line = reader.readLine()) != null) {
                NetPacket packet = parse(line); // maybe JSON decode or split fields
                switch(packet.type()) {
                    case SIGNUP_REQUEST -> handleSignUp(packet);
                    case LOGIN_REQUEST -> handleLogin(packet);
                    // other game messages later
                }
            }

        } catch (IOException e) {
            // Could also notify client or log more gracefully
            System.err.println("Connection error for client " + clientId + ": " + e.getMessage());
        } finally {
            server.unregisterClient(clientId);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public void sendPacket(Object packet) {
        if (writer != null) {
            writer.println(packet.toString());
            writer.flush();
        }
    }

    private void handleSignUp(NetPacket packet) {
        // extract username/password from packet.data()
        boolean success = persistenceManager.registerPlayer(username, password);
        sendPacket(new NetPacket(SIGNUP_RESPONSE, "", success ? "OK" : "FAIL"));
    }

    private void handleLogin(NetPacket packet) {
        Optional<Player> player = persistenceManager.authenticate(username, password);
        sendPacket(new NetPacket(LOGIN_RESPONSE, "", player.isPresent() ? "OK" : "FAIL"));
        if(player.isPresent()) {
            // store Player object for this session
        }
    }


}
