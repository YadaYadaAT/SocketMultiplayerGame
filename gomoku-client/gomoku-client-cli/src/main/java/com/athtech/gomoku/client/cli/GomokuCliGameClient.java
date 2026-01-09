package com.athtech.gomoku.client.cli;

import com.athtech.gomoku.client.net.ClientNetworkAdapterImpl;

public class GomokuCliGameClient {
    public static void main(String[] args) {

        // Check environment variables first
        String host = System.getenv().getOrDefault("GOMOKU_HOST", "localhost");
        int port;
        try {
            port = Integer.parseInt(System.getenv().getOrDefault("GOMOKU_PORT", "999"));
        } catch (NumberFormatException e) {
            port = 999; // fallback default
        }

        CLIView view = new CLIView();
        ClientNetworkAdapterImpl adapter = new ClientNetworkAdapterImpl(host, port);
        CLIController controller = new CLIController(view, adapter);
        controller.run();
    }
}
