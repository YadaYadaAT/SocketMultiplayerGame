package com.athtech.connect4.client;

import com.athtech.connect4.client.cli.CLIController;
import com.athtech.connect4.client.cli.CLIView;
import com.athtech.connect4.client.net.ClientNetworkAdapterImpl;

public class Connect4CliGameClient {
    public static void main(String[] args) {
        CLIView view = new CLIView();
        ClientNetworkAdapterImpl adapter = new ClientNetworkAdapterImpl("localhost", 999);
        CLIController controller = new CLIController(view, adapter);
        controller.run();
    }
}
