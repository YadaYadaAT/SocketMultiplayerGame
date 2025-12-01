package com.athtech.connect4.server.net;

import java.io.*;
import java.net.*;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerNetworkAdapter server;

    private BufferedReader reader;
    private PrintWriter writer;
    private final String clientId = UUID.randomUUID().toString(); // temporary ID

    public ClientHandler(Socket socket, ServerNetworkAdapter server) {
        this.socket = socket;
        this.server = server;
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
                // Instead of printing, send back to client
                String response = "ECHO from server: " + line;
                sendPacket(response);

                //handle game messages here later
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
}
