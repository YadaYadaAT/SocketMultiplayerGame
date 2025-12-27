package com.athtech.connect4.server.match;

import java.util.List;
import java.util.Optional;

public interface MatchManager {
    ActiveMatch createMatch(String player1, String player2);
    Optional<ActiveMatch> getMatch(String matchId);
    List<ActiveMatch> getActiveMatches();
    void endMatch(String matchId);
    Optional<ActiveMatch> getMatchByPlayer(String username);
}
