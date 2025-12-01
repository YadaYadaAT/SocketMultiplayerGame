package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerNetworkAdapter{
    private ServerSocket srvSocket;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

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
                //well in real apps we would have the NAT port here instead of OS chosen one or the one we set at client
                Socket clientSocket = srvSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendToClient(String clientId, NetPacket packet) {
        ClientHandler handler = clients.get(clientId);
        if (handler != null) {
            handler.sendPacket(packet);
        }
    }

    // These methods below may be called concurrently from multiple ClientHandler threads.
    // The 'clients' map is a ConcurrentHashMap, so individual put/get/remove operations are thread-safe.
    public void registerClient(String clientId, ClientHandler handler) {
        clients.put(clientId, handler);
    }

    public void unregisterClient(String clientId) {
        clients.remove(clientId);
    }

}
