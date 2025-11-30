package com.athtech.connect4.server.net;

public class ServerLauncher {
    public static void main(String[] args) {
        var server = new ServerNetworkAdapterImpl();
        int portNum = 999;
        server.startServer(portNum);
        System.out.println("Server started on port " + portNum + " ...");
    }
}
