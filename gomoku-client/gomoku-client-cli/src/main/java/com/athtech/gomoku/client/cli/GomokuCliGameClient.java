package com.athtech.gomoku.client.cli;
//STUDENTS-CODE-NUMBER : CSY-22117
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

        // Create a new CLIView; centralized systemOut class to avoid polluting the controller
        CLIView view = new CLIView();
        // Create a new ClientNetworkAdapter; this manages socket functionality
        ClientNetworkAdapterImpl adapter = new ClientNetworkAdapterImpl(host, port);
        // Create the controller; this handles communication with the Server
        CLIController controller = new CLIController(view, adapter);
        // Separate method to initialize the main controller loop
        controller.run();
    }
}
