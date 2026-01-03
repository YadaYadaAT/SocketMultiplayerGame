package com.athtech.gomoku.server;

import com.athtech.gomoku.server.net.ServerNetworkAdapter;
import com.athtech.gomoku.server.persistence.PersistenceManagerImpl;

public class ServerLauncher {
    public static void main(String[] args) {

        var server = new ServerNetworkAdapter(new PersistenceManagerImpl());
        server.startServer(999);
    }
}
