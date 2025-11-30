package com.athtech.connect4.client.cli;

import com.athtech.connect4.client.net.ClientNetworkAdapter;

import java.util.Scanner;

public class CLIControllerImpl implements CLIController {

    private final CLIView view;
    private final ClientNetworkAdapter clientNetwork;

    public CLIControllerImpl(CLIView view, ClientNetworkAdapter clientNetwork) {
        this.view = view;
        this.clientNetwork = clientNetwork;
    }

    @Override
    public void handleInput(String input) {
        clientNetwork.sendPacket(input);
    }

    @Override
    public void start() {
        Scanner scanner = new Scanner(System.in);
        view.displayMessage("Type messages to send to server. Type 'quit' to exit.");
        while (true) {
            String line = scanner.nextLine();
            if ("quit".equalsIgnoreCase(line)) break;
            handleInput(line);
        }
        clientNetwork.disconnect();
    }

    @Override
    public void stop() {
        clientNetwork.disconnect();
    }
}
