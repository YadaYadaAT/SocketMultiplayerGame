package com.athtech.connect4.client.cli;

import com.athtech.connect4.protocol.messaging.ErrorPacket;
import com.athtech.connect4.protocol.messaging.GameStatePacket;

public interface CLIView {
    void displayBoard(GameStatePacket state);
    void displayMessage(String message);
    void displayError(ErrorPacket error);
}
