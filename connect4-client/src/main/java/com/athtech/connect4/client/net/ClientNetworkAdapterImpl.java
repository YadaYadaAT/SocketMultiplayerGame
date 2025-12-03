package com.athtech.connect4.client.net;

import com.athtech.connect4.protocol.messaging.NetPacket;

import java.io.*;
import java.net.Socket;

public class ClientNetworkAdapterImpl implements ClientNetworkAdapter {

    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private PacketListener listener;

    public ClientNetworkAdapterImpl(String host, int port) {
        try {
            socket = new Socket(host, port);
            //OUTPUT first then INPUT to avoid stream deadlocks
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            new Thread(this::listenLoop).start();

        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    private void listenLoop() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (obj instanceof NetPacket packet && listener != null) {
                    listener.onPacketReceived(packet);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Connection closed: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    @Override
    public void sendPacket(NetPacket packet) {
        try {
            out.writeObject(packet);
            out.flush();
        } catch (IOException e) {
            System.err.println("Send failed: " + e.getMessage());
        }
    }

    @Override
    public void setListener(PacketListener listener) {
        this.listener = listener;
    }
}
