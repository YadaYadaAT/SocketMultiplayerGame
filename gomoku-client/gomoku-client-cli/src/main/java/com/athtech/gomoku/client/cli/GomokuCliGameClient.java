package com.athtech.gomoku.client.cli;

import com.athtech.gomoku.client.net.ClientNetworkAdapterImpl;

public class GomokuCliGameClient {
    public static void main(String[] args) {
        CLIView view = new CLIView();
        ClientNetworkAdapterImpl adapter = new ClientNetworkAdapterImpl("localhost", 999);
        CLIController controller = new CLIController(view, adapter);
        controller.run();
    }
}
