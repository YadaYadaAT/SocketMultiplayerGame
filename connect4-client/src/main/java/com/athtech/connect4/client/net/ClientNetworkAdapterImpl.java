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
                Object obj = in.readObject();//FIN send from server if shutdown throws IO exception
                if (obj instanceof NetPacket packet && listener != null && packet.type()!=null) {
                    listener.onPacketReceived(packet);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Connection closed: " + e.getMessage());
        }
    }

    @Override
    public boolean attemptReconnectWithRelogCode(String username, String relogCode) {
        if (socket == null || socket.isClosed()) {
            System.err.println("Cannot reconnect: no active socket.");
            return false;
        }

        try {
            // Create a reconnect request payload
            var req = new ReconnectRequest(username, relogCode);

            // Wrap it in a NetPacket
            var packet = new NetPacket(PacketType.RECONNECT_REQUEST, username, req);

            // Send packet
            out.writeObject(packet);
            out.flush();

            // Wait for server response (blocking read with timeout)
            socket.setSoTimeout(8000); // 8 seconds timeout for response
            Object obj = in.readObject();
            socket.setSoTimeout(0); // reset timeout

            if (obj instanceof NetPacket responsePacket) {
                if (responsePacket.type() == PacketType.RECONNECT_RESPONSE) {
                    ReconnectResponse resp = (ReconnectResponse) responsePacket.payload();
                    if (resp.success()) {
                        System.out.println("Reconnect successful: session state restored.");
                        return true;
                    } else {
                        System.err.println("Reconnect failed: " + resp.message());
                    }
                } else {
                    System.err.println("Unexpected packet type during reconnect: " + responsePacket.type());
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Reconnect attempt failed: " + e.getMessage());
        }

        return false;
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
