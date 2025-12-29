package com.athtech.connect4.server.match;

import com.athtech.connect4.server.net.LobbyController;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class MatchManagerImpl implements MatchManager {

    private final LobbyController lobbyController;
    private final ConcurrentMap<String, Match> matches = new ConcurrentHashMap<>();
    private static final long INACTIVITY_TIMEOUT_MS = 120_000; // 2 minutes

    public MatchManagerImpl(LobbyController lobbyController) {
        this.lobbyController = lobbyController;
    }

    @Override
    public synchronized Match createMatch(String player1, String player2) throws IllegalStateException {
        if (getMatchByPlayer(player1).isPresent() || getMatchByPlayer(player2).isPresent()) {
            throw new IllegalStateException("One of the players is already in a match");
        }
        Match match = new MatchImpl(player1, player2);
        matches.put(match.getMatchId(), match);
        return match;
    }

    @Override
    public void endMatch(String matchId) {
        matches.remove(matchId);
    }

    @Override
    public Optional<Match> getMatch(String matchId) {
        return Optional.ofNullable(matches.get(matchId));
    }

    @Override
    public List<Match> getMatches() {
        return matches.values().stream().collect(Collectors.toList());
    }

    @Override
    public Optional<Match> getMatchByPlayer(String username) {
        return matches.values().stream()
                .filter(m -> m.getPlayer1().equals(username) || m.getPlayer2().equals(username))
                .findFirst();
    }

    /** Cleanup inactive or disconnected matches */
    public void cleanupInactiveMatches() {
        matches.values().forEach(match -> {
            boolean p1Online = lobbyController.isUserLoggedIn(match.getPlayer1());
            boolean p2Online = lobbyController.isUserLoggedIn(match.getPlayer2());
            if (!p1Online && !p2Online) {
                matches.remove(match.getMatchId());
            } else if (match instanceof MatchImpl && ((MatchImpl) match).isInactive(INACTIVITY_TIMEOUT_MS)) {
                matches.remove(match.getMatchId());
            }
        });
    }
}
