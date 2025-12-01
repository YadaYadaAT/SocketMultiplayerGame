package com.athtech.connect4.client.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;

import java.io.*;
import java.net.Socket;

public class ClientNetworkAdapterImpl implements ClientNetworkAdapter {

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private PacketListener listener;

    public ClientNetworkAdapterImpl(String host, int port) {
        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Wrap the socket's output stream in a PrintWriter for convenient text output.
            // Using println() automatically adds a line break and can auto-flush to ensure the server receives the message immediately.
            writer = new PrintWriter(socket.getOutputStream(), true);
            new Thread(this::listenLoop).start();
        } catch (IOException e) {
            System.out.println("Connection failed: " + e.getMessage());
        }
    }

    private void listenLoop() {
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (listener != null) {
                    listener.onPacketReceived(new NetPacket(PacketType.GAME_STATE, "dummy-sender", line));
                }
            }
        } catch (IOException e) {
            System.out.println("Error in listenLoop: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    @Override
    public void sendPacket(Object packet) {
        writer.println(packet.toString());
    }

    @Override
    public void setListener(PacketListener listener) {
        this.listener = listener;
    }
}
