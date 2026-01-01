package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.payload.GameStateResponse;
import com.athtech.connect4.protocol.payload.MoveRequest;

import java.util.Set;

public interface Match {
    void requestRematch(String player);
    boolean isRematchReady();
    void resetRematchRequests();
    void declineRematch(String player);
    Set<String> getMatchPlayers();
    RematchVote getRematchOutcome();
    String getOpponent(String player);
//    void markUnavailable(String player);
    String getMatchId();
    String getPlayer1();
    String getPlayer2();
    String getCurrentPlayer(); // username of player whose turn it is
    GameStateResponse getCurrentState();
    String getWinner();
    String getLoser();
    boolean isDraw();
    boolean makeMove(String player, MoveRequest moveRequest); // returns true if move accepted
    boolean isFinished();
    boolean markEnded();
//    void touch();
//    boolean isInactive(long timeoutMs);
    boolean isEnded();
}
