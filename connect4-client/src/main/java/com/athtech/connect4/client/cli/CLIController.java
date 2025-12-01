package com.athtech.connect4.client.cli;

import com.athtech.connect4.client.net.ClientNetworkAdapter;

import java.util.Scanner;

public class CLIController {

    private final CLIView view;
    private final ClientNetworkAdapter clientNetwork;

    public CLIController(CLIView view, ClientNetworkAdapter clientNetwork) {
        this.view = view;
        this.clientNetwork = clientNetwork;

        // Register listener here
        clientNetwork.setListener(packet -> {
            // This is called from the network thread whenever a packet arrives
            view.displayMessage(packet.getData());
        });
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        view.displayMessage("Type messages to send to server. Type 'quit' to exit.");
        while (true) {
            String line = scanner.nextLine();
            if ("quit".equalsIgnoreCase(line)) break;
            clientNetwork.sendPacket(line);
        }
        clientNetwork.disconnect();
    }
}
