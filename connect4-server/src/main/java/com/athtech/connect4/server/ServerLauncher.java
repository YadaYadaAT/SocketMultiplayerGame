package com.athtech.connect4.server;

import com.athtech.connect4.server.net.ServerNetworkAdapter;
import com.athtech.connect4.server.persistence.PersistenceManagerImpl;

public class ServerLauncher {
    public static void main(String[] args) {

        var server = new ServerNetworkAdapter(new PersistenceManagerImpl());
        server.startServer(999);
    }
}
