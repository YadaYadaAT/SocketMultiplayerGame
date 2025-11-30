// ClientHandler.java
package com.athtech.connect4.server.net;

import java.io.*;
import java.net.*;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerNetworkAdapterImpl server;

    private BufferedReader reader;
    private PrintWriter writer;
    private String clientId = UUID.randomUUID().toString(); // temporary ID

    public ClientHandler(Socket socket, ServerNetworkAdapterImpl server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            server.registerClient(clientId, this);
            writer.println("WELCOME " + clientId);

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Received from client " + clientId + ": " + line);
                writer.println("ECHO: " + line); // dummy response
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            server.unregisterClient(clientId);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public void sendPacket(Object packet) {
        writer.println(packet.toString()); // dummy serialization
    }
}
