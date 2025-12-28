package com.athtech.connect4.server.match;

import java.util.List;
import java.util.Optional;

public interface MatchManager {
    match createMatch(String player1, String player2);
    Optional<match> getMatch(String matchId);
    List<match> getMatches();
    void endMatch(String matchId);
    Optional<match> getMatchByPlayer(String username);
}
