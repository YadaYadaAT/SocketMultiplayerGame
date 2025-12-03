package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.*;
import com.athtech.connect4.server.persistence.PersistenceManager;
import com.athtech.connect4.server.persistence.PersistenceManagerImpl;

import java.io.*;
import java.net.Socket;
import java.util.UUID;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ServerNetworkAdapter server;

    private ObjectInputStream in;
    private ObjectOutputStream out;

    private final String clientId = UUID.randomUUID().toString();

    // null means not logged in
    private String username = null;

    private static final PersistenceManager persistence = new PersistenceManagerImpl();

    public ClientHandler(Socket socket, ServerNetworkAdapter server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            server.registerClient(clientId, this);
            System.out.println("Handler started for client: " + clientId);

            sendPacket(new NetPacket(PacketType.INFO, "server", "Connected to server..."));

            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof NetPacket packet)) continue;

                handlePacket(packet);
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client disconnected: " + clientId);
        } finally {
            if (username != null) {
                server.setUserLoggedOut(username);
            }
            server.unregisterClient(clientId);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handlePacket(NetPacket packet) {
        switch (packet.type()) {
            case LOGIN_REQUEST -> handleLogin(packet);
            case SIGNUP_REQUEST -> handleSignup(packet);

            default -> sendPacket(
                    new NetPacket(PacketType.ERROR_MESSAGE, "server",
                            new ErrorMessage("Unknown packet type: " + packet.type()))
            );
        }
    }

    private void handleLogin(NetPacket packet) {
        LoginRequest req = (LoginRequest) packet.payload();

        boolean ok = persistence.authenticate(req.username(), req.password());

        LoginResponse resp = new LoginResponse(ok, ok ? "Welcome " + req.username() : "Invalid credentials");
        sendPacket(new NetPacket(PacketType.LOGIN_RESPONSE, "server", resp));

        if (ok) {
            username = req.username();
            server.setUserLoggedIn(username, clientId);
        }
    }

    private void handleSignup(NetPacket packet) {
        SignupRequest req = (SignupRequest) packet.payload();

        boolean ok = persistence.registerPlayer(req.username(), req.password());
        SignupResponse resp = new SignupResponse(ok, ok ? "Signup OK" : "Username taken");

        sendPacket(new NetPacket(PacketType.SIGNUP_RESPONSE, "server", resp));
    }

    public void sendPacket(NetPacket packet) {
        try {
            out.writeObject(packet);
            out.flush();
        } catch (IOException e) {
            System.err.println("Send failed to client " + clientId + ": " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }
}
