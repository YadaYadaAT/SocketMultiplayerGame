package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerNetworkAdapterImpl implements ServerNetworkAdapter{
    private ServerSocket srvSocket;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    @Override
    public void startServer(int port) {

        try {
            srvSocket = new ServerSocket(port);
            System.out.println("Server listening...");
            new Thread(this::acceptLoop).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void acceptLoop() {
        while (true) {
            try {
                Socket clientSocket = srvSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void sendToClient(String clientId, NetPacket packet) {
        ClientHandler handler = clients.get(clientId);
        if (handler != null) {
            handler.sendPacket(packet);
        }
    }

    public void registerClient(String clientId, ClientHandler handler) {
        clients.put(clientId, handler);
    }

    public void unregisterClient(String clientId) {
        clients.remove(clientId);
    }

}
