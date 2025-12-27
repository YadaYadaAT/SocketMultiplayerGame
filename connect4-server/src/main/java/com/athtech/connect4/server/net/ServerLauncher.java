package com.athtech.connect4.server.net;

import com.athtech.connect4.server.persistence.PersistenceManagerImpl;

public class ServerLauncher {
    public static void main(String[] args) {
        var persistence = new PersistenceManagerImpl();
        ServerNetworkAdapter server = new ServerNetworkAdapter(persistence);

        int portNum = 999;
        server.startServer(portNum);
        System.out.println("Server started on port " + portNum + " ...");
    }
}
