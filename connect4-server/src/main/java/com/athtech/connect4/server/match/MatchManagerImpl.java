package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.GameStateResponse;
import com.athtech.connect4.server.net.ServerNetworkAdapter;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class MatchManagerImpl implements MatchManager {

    private final ConcurrentMap<String, Match> matches = new ConcurrentHashMap<>();
    private final BiConsumer<String, NetPacket> notifier;
    private static final long CLEANUP_INTERVAL_MS = 30_000; // 30 seconds

    public MatchManagerImpl(ServerNetworkAdapter server) {
        this.notifier = server::sendToClient;

        // Automatic cleanup thread
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    cleanupInactiveMatches(notifier);
                    Thread.sleep(CLEANUP_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
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
    public Optional<Match> getEndedMatchByPlayer(String username) {
        return matches.values().stream()
                .filter(Match::isEnded)
                .filter(m -> m.getPlayer1().equals(username) || m.getPlayer2().equals(username))
                .findFirst();
    }

    @Override
    public Optional<GameStateResponse> getCurrentStateForPlayer(String username) {
        return getMatchByPlayer(username).map(Match::getCurrentState);
    }

    @Override
    public Optional<String> getMatchIdByPlayer(String username) {
        return getMatchByPlayer(username).map(Match::getMatchId);
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
                .filter(m -> m.getActivePlayers().contains(username))
                .findFirst();
    }


    public synchronized void cleanupInactiveMatches(BiConsumer<String, NetPacket> notifier) {
        matches.values().forEach(match -> {
            if (match instanceof MatchImpl impl) {
                boolean removed = false;

                // Remove empty matches
                if (impl.getActivePlayers().isEmpty()) {
                    matches.remove(match.getMatchId());
                    System.out.println("[Cleanup] Removed empty match " + match.getMatchId());
                    removed = true;
                }

                // Soft timeout: notify players
                if (!removed && impl.hasSoftTimeoutPassed()) {
                    impl.markSoftTimeoutTriggered();
                    for (String player : impl.getActivePlayers()) {
                        notifier.accept(player, new NetPacket(
                                PacketType.PLAYER_INACTIVITY_WARNING_RESPONSE,
                                "server",
                                "You have been inactive for a while, please make a move!"
                        ));
                    }
                }

                // Hard timeout: remove match
                if (!removed && impl.isInactive(0)) {
                    matches.remove(match.getMatchId());
                    System.out.println("[Cleanup] Removed inactive match " + match.getMatchId());
                }
            }
        });
    }

}
