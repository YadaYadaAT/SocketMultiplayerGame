package com.athtech.connect4.server.match;

import java.util.List;
import java.util.Optional;
//TODO : the match list (game sessions ) should be in here:Map<String, ActiveMatch> activeMatches = new ConcurrentHashMap<>()
public class MatchManagerImpl implements MatchManager{
    @Override
    public ActiveMatch createMatch(String player1, String player2) {
        return null;
    }

    @Override
    public Optional<ActiveMatch> getMatch(String matchId) {
        return Optional.empty();
    }

    @Override
    public List<ActiveMatch> getActiveMatches() {
        return List.of();
    }

    @Override
    public void endMatch(String matchId) {

    }
}
