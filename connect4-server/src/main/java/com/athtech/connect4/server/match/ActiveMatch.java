package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.payload.GameState;
import com.athtech.connect4.protocol.payload.Move;

public interface ActiveMatch {
    String getMatchId();
    String getPlayer1();
    String getPlayer2();
    String getCurrentTurn(); // username of player whose turn it is
    GameState getCurrentState();

    boolean makeMove(String player, Move move); // returns true if move accepted
    boolean isFinished();
}
