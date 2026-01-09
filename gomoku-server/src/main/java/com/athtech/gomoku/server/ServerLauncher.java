package com.athtech.gomoku.server;

import com.athtech.gomoku.server.net.ServerNetworkAdapter;
import com.athtech.gomoku.server.persistence.PersistenceManagerImpl;

public class ServerLauncher {

    private static final int DEFAULT_PORT = 999;

    public static void main(String[] args) {

        int port = DEFAULT_PORT;

        String envPort = System.getenv("PORT");
        if (envPort != null) {
            try {
                port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {
                // fallback to default
            }
        }

        System.out.println("Starting Gomoku server on port " + port);

        var server = new ServerNetworkAdapter(new PersistenceManagerImpl());
        server.startServer(port);
    }
}
