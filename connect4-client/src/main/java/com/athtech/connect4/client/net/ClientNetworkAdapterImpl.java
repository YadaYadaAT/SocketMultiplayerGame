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
        connect(host, port);
    }

    private void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
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
                final String packetLine = line;
                if (listener != null) {
                    listener.onPacketReceived(new NetPacket() {
                        @Override
                        public PacketType getType() { return PacketType.ERROR; }

                        @Override
                        public String getSender() { return "dummy-sender"; }

                        @Override
                        public String getData() { return packetLine; }
                    });
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
