package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.payload.GameStateResponse;
import com.athtech.connect4.protocol.payload.MoveRequest;

public interface match {
    String getMatchId();
    String getPlayer1();
    String getPlayer2();
    String getCurrentTurn(); // username of player whose turn it is
    GameStateResponse getCurrentState();
    String getWinner();
    String getLoser();
    boolean isDraw();
    boolean makeMove(String player, MoveRequest moveRequest); // returns true if move accepted
    boolean isFinished();
    void requestRematch(String player);
    void cancelRematch();
    boolean canStartRematch();
    boolean isRematchTimedOut();
    void resetRematchState();
}
