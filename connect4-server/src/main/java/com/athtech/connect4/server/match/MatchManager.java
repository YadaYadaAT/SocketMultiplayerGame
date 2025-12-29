package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.payload.GameStateResponse;

import java.util.List;
import java.util.Optional;

public interface MatchManager {
    Optional<GameStateResponse> getCurrentStateForPlayer(String username);
    Optional<String> getMatchIdByPlayer(String username);
    Optional<Match> getEndedMatchByPlayer(String username);
    Match createMatch(String player1, String player2) throws IllegalStateException;
    Optional<Match> getMatch(String matchId);
    List<Match> getMatches();
    void endMatch(String matchId);
    Optional<Match> getMatchByPlayer(String username);
}
