package com.athtech.connect4.client;

import com.athtech.connect4.client.cli.CLIController;
import com.athtech.connect4.client.cli.CLIControllerImpl;
import com.athtech.connect4.client.cli.CLIView;
import com.athtech.connect4.client.cli.CLIViewImpl;
import com.athtech.connect4.client.net.ClientNetworkAdapter;
import com.athtech.connect4.client.net.ClientNetworkAdapterImpl;

public class Connect4GameClient {
    public static void main(String[] args) {
        CLIView view = new CLIViewImpl();
        ClientNetworkAdapter network = new ClientNetworkAdapterImpl("localhost", 999);
        CLIController controller = new CLIControllerImpl(view, network);
        controller.start();
    }
}
