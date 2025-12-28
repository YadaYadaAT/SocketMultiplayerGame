package com.athtech.connect4.server.match;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class MatchManagerImpl implements MatchManager {

    private final ConcurrentMap<String, match> matches = new ConcurrentHashMap<>();

    @Override
    public match createMatch(String player1, String player2) {
        match match = new matchImpl(player1, player2);
        matches.put(match.getMatchId(), match);
        return match;
    }

    @Override
    public void endMatch(String matchId) {
        matches.remove(matchId);
    }

    @Override
    public Optional<match> getMatch(String matchId) {
        return Optional.ofNullable(matches.get(matchId));
    }

    @Override
    public List<match> getMatches() {
        return matches.values().stream().collect(Collectors.toList());
    }

    @Override
    public Optional<match> getMatchByPlayer(String username) {
        return matches.values().stream()
                .filter(m -> m.getPlayer1().equals(username) || m.getPlayer2().equals(username))
                .findFirst();
    }

}
