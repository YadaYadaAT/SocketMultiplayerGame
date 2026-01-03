package com.athtech.gomoku.server.match;

import com.athtech.gomoku.protocol.messaging.MatchEndReason;
import com.athtech.gomoku.protocol.payload.GameStateResponse;

import java.util.List;
import java.util.Optional;

public interface MatchManager {
    Optional<GameStateResponse> getCurrentStateForPlayer(String username);
    Optional<String> getMatchIdByPlayer(String username);
    Match createMatch(String player1, String player2) throws IllegalStateException;
    List<Match> getMatches();
    void endMatch(String matchId);
    Optional<Match> getMatchByPlayer(String username);
    Optional<Match> getEndedMatchByPlayer(String username);
    Optional<Match> getMatch(String matchId);
    void handleForcedEnd(MatchImpl match, String winner, MatchEndReason endReason);
}
