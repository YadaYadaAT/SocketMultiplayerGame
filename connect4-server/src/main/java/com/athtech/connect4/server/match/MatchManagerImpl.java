package com.athtech.connect4.server.match;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class MatchManagerImpl implements MatchManager {

    private final ConcurrentMap<String, ActiveMatch> activeMatches = new ConcurrentHashMap<>();

    @Override
    public ActiveMatch createMatch(String player1, String player2) {
        ActiveMatch match = new ActiveMatchImpl(player1, player2);
        activeMatches.put(match.getMatchId(), match);
        return match;
    }

    @Override
    public Optional<ActiveMatch> getMatch(String matchId) {
        return Optional.ofNullable(activeMatches.get(matchId));
    }

    @Override
    public List<ActiveMatch> getActiveMatches() {
        return activeMatches.values().stream().collect(Collectors.toList());
    }

    @Override
    public void endMatch(String matchId) {
        activeMatches.remove(matchId);
    }

    @Override
    public Optional<ActiveMatch> getMatchByPlayer(String username) {
        return activeMatches.values().stream()
                .filter(m -> m.getPlayer1().equals(username) || m.getPlayer2().equals(username))
                .findFirst();
    }

}
