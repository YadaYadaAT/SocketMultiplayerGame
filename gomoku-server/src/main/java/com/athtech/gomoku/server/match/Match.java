package com.athtech.gomoku.server.match;

import com.athtech.gomoku.protocol.payload.GameStateResponse;
import com.athtech.gomoku.protocol.payload.MoveRequest;

import java.util.Map;
import java.util.Set;

public interface Match {
    void requestRematch(String player);
    Map<String, RematchVote> getRematchVotes();
    Map<String, RematchVote> getMidGameAsyncRematchVotes();
    boolean isRematchReady();
    void resetRematchRequests();
    void declineRematch(String player);
    Set<String> getMatchPlayers();
    RematchVote getRematchOutcome();
    String getOpponent(String player);
//    void markUnavailable(String player);
    void playerDisconnected(String player);
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
    String getmidGameAsyncRematchVotesOpponent(String player);
    String getRematchVoteOpponent(String player);
}
