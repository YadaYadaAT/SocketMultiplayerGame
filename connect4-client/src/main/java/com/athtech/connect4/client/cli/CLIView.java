package com.athtech.connect4.client.cli;

import com.athtech.connect4.protocol.messaging.GameStatePacket;

public class CLIView {

    public void displayBoard(GameStatePacket state) {
        System.out.println("Board: " + state);
    }

    public void displayMessage(String message) {
        System.out.println(message);
    }

}
