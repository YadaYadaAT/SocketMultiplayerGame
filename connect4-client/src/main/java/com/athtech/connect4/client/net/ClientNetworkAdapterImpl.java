package com.athtech.connect4.client.net;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.ReconnectRequest;
import com.athtech.connect4.protocol.payload.ReconnectResponse;

import java.io.*;
import java.net.Socket;

public class ClientNetworkAdapterImpl implements ClientNetworkAdapter {

    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private PacketListener listener;
    private Runnable connectionLostListener;

    public ClientNetworkAdapterImpl(String host, int port, Runnable connectionLostCallback) {
        this.connectionLostListener = connectionLostCallback;
        openSocket(host, port);
    }

    private void openSocket(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            new Thread(this::listenLoop, "ClientNetworkAdapter-ListenThread").start();
        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
            if (connectionLostListener != null) connectionLostListener.run();
        }
    }

    private void listenLoop() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (obj instanceof NetPacket packet && listener != null && packet.type() != null) {
                    listener.onPacketReceived(packet);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Connection closed: " + e.getMessage());
            if (connectionLostListener != null) connectionLostListener.run();
        }
    }

    @Override
    public boolean attemptReconnectWithRelogCode(String username, String relogCode) {
        if (socket == null || socket.isClosed()) return false;

        try {
            var req = new ReconnectRequest(username, relogCode);
            var packet = new NetPacket(PacketType.RECONNECT_REQUEST, username, req);
            out.writeObject(packet);
            out.flush();
            return true; // actual response handled in server listener
        } catch (IOException e) {
            System.err.println("Reconnect attempt failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void setConnectionLostListener(Runnable callback) {
        this.connectionLostListener = callback;
    }

    @Override
    public void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    @Override
    public void sendPacket(NetPacket packet) {
        try {
            if (socket != null && !socket.isClosed()) {
                out.writeObject(packet);
                out.flush();
            } else if (connectionLostListener != null) connectionLostListener.run();
        } catch (IOException e) {
            System.err.println("Send failed: " + e.getMessage());
            if (connectionLostListener != null) connectionLostListener.run();
        }
    }

    @Override
    public void setListener(PacketListener listener) {
        this.listener = listener;
    }
}
