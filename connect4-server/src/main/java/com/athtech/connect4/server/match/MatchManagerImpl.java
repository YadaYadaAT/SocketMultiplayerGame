package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.messaging.MatchEndReason;
import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.GameEndResponse;
import com.athtech.connect4.protocol.payload.GameStateResponse;
import com.athtech.connect4.protocol.payload.MatchSessionEndedResponse;
import com.athtech.connect4.protocol.payload.PlayerDisconnectedNotificationResponse;
import com.athtech.connect4.server.net.ServerNetworkAdapter;
import com.athtech.connect4.server.persistence.PersistenceManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Manages all active matches on the server.
 * Responsible for ticking timers (AFK/disconnect), handling forced match ends,
 * cleaning up ended matches, and providing match lookup APIs.
 */
public class MatchManagerImpl implements MatchManager {

    private final ConcurrentMap<String, Match> matches = new ConcurrentHashMap<>();
    private final BiConsumer<String, NetPacket> notifier;
    private final PersistenceManager persistence;

    private static final long TICK_INTERVAL_MS = 1_000;
    private static final long CLEANUP_INTERVAL_MS = 30_000;

    public MatchManagerImpl(ServerNetworkAdapter server, PersistenceManager persistence) {
        this.notifier = server::sendToClient;
        this.persistence = persistence;

        startMatchTickThread();
        startCleanupThread();
    }

    /* ===================== THREADS ===================== */

    private void startMatchTickThread() {
        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    tickMatches();
                    Thread.sleep(TICK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "match-tick-thread");

        tickThread.setDaemon(true);
        tickThread.start();
    }

    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    cleanupMatches();
                    Thread.sleep(CLEANUP_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "match-cleanup-thread");

        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    /* ===================== CORE LOGIC ===================== */

    private void tickMatches() {
        matches.values().forEach(match -> {
            if (!(match instanceof MatchImpl impl)) return;

            // Soft AFK warning
            impl.checkSoftTimeout((player, message) ->
                    notifier.accept(player, new NetPacket(
                            PacketType.PLAYER_INACTIVITY_WARNING_RESPONSE,
                            "server",
                            message
                    ))
            );

            // Hard AFK
            impl.checkHardTimeout().ifPresent(winner -> {
                System.out.println("[Match] AFK timeout. Winner: " + winner +
                        " | Match: " + impl.getMatchId());
                handleForcedEnd(impl, winner, MatchEndReason.WIN_TIMEOUT);
            });

            // Disconnect draw
            if (impl.isDisconnectDraw()) {
                System.out.println("[Match] Both players disconnected. Draw enforced." +
                        " | Match: " + impl.getMatchId());
                handleForcedEnd(impl, null, MatchEndReason.DRAW);
                return;
            }

            // Disconnect win
            impl.checkDisconnectTimeout().ifPresent(winner -> {
                System.out.println("[Match] Disconnect timeout. Winner: " + winner +
                        " | Match: " + impl.getMatchId());
                handleForcedEnd(impl, winner, MatchEndReason.WIN_DISCONNECT);
            });
        });
    }

    private void cleanupMatches() {
        matches.values().forEach(match -> {
            if (!(match instanceof MatchImpl impl)) return;

            if (impl.getMatchPlayers().isEmpty()
                    || (System.currentTimeMillis() - impl.getLastMoveTime() >= 2_400_000)) {

                matches.remove(match.getMatchId());
                System.out.println("[Cleanup] Removed match " + match.getMatchId() +
                        " (inactive or no players)");
            }
        });
    }

    /* ===================== API ===================== */

    @Override
    public synchronized Match createMatch(String player1, String player2) {
        if (getMatchByPlayer(player1).isPresent() || getMatchByPlayer(player2).isPresent())
            throw new IllegalStateException("One of the players is already in a match");

        Match match = new MatchImpl(player1, player2);
        matches.put(match.getMatchId(), match);

        System.out.println("[Match] Created new match " + match.getMatchId() +
                " (" + player1 + " vs " + player2 + ")");

        return match;
    }

    @Override
    public Optional<Match> getMatch(String matchId) {
        return Optional.ofNullable(matches.get(matchId));
    }

    @Override
    public Optional<Match> getMatchByPlayer(String username) {
        return matches.values().stream()
                .filter(m -> m.getMatchPlayers().contains(username))
                .findFirst();
    }

    @Override
    public Optional<String> getMatchIdByPlayer(String username) {
        return getMatchByPlayer(username).map(Match::getMatchId);
    }

    @Override
    public Optional<GameStateResponse> getCurrentStateForPlayer(String username) {
        return getMatchByPlayer(username).map(Match::getCurrentState);
    }

    @Override
    public Optional<Match> getEndedMatchByPlayer(String username) {
        return matches.values().stream()
                .filter(Match::isEnded)
                .filter(m -> m.getPlayer1().equals(username) || m.getPlayer2().equals(username))
                .findFirst();
    }

    @Override
    public List<Match> getMatches() {
        return matches.values().stream().collect(Collectors.toList());
    }

    @Override
    public void endMatch(String matchId) {
        matches.remove(matchId);
        System.out.println("[Match] Match manually ended: " + matchId);
    }

    /* ===================== CONNECTION API ===================== */

    public boolean playerDisconnected(String player) {
        Optional<Match> opt = getMatchByPlayer(player);

        if (opt.isEmpty()) {
            return false;
        }

        Match match = opt.get();
        if (match instanceof MatchImpl impl) {
            System.out.println("[Connection] Player disconnected: " + player +
                    " from Match: " + impl.getMatchId());

            impl.playerDisconnected(player);

            // Determine opponent
            String opponent = impl.getOpponent(player);
            boolean isOpponentsTurn = opponent.equals(impl.getCurrentPlayer());

            StringBuilder msg = new StringBuilder();
            msg.append("Your opponent has disconnected.\n");
            if (isOpponentsTurn) {
                msg.append("It's your turn! You may play your move, as the inactive timer still applies.\n");
            }
            msg.append("The game will terminate in 30 seconds(~was set low for reviewer testing~) if your opponent does not reconnect, resulting in a tie.\n");
            msg.append("Note: Its better to wait for the termination than quiting to not count as a loss");

            // Send notification
            notifier.accept(opponent, new NetPacket(
                    PacketType.PLAYER_DISCONNECTED_NOTIFICATION_RESPONSE,
                    "server",
                    new PlayerDisconnectedNotificationResponse(msg.toString())
            ));
        }

        return true;
    }

    public void playerReconnected(String player) {
        getMatchByPlayer(player).ifPresent(match -> {
            if (match instanceof MatchImpl impl) {
                System.out.println("[Connection] Player reconnected: " + player +
                        " | Match: " + impl.getMatchId());
                impl.playerReconnected(player);
            }
        });
    }

    /* ===================== FORCED END HANDLING ===================== */

    public void handleForcedEnd(MatchImpl match, String winner, MatchEndReason endReason) {
        if (!matches.containsKey(match.getMatchId())) return;

        boolean draw = winner == null;
        String loser = draw ? null
                : (winner.equals(match.getPlayer1()) ? match.getPlayer2() : match.getPlayer1());

        System.out.println("[Match End] Match " + match.getMatchId() +
                " ended. Reason=" + endReason +
                (draw ? " (DRAW)" : " Winner=" + winner + " Loser=" + loser));

        // Persist match result
        persistence.recordMatchResult(match.getPlayer1(), match.getPlayer2(), draw, winner);

        // Send updated stats
        notifier.accept(match.getPlayer1(), new NetPacket(
                PacketType.PLAYER_STATS_RESPONSE, "server",
                persistence.getPlayerStats(match.getPlayer1())
        ));
        notifier.accept(match.getPlayer2(), new NetPacket(
                PacketType.PLAYER_STATS_RESPONSE, "server",
                persistence.getPlayerStats(match.getPlayer2())
        ));

        // Determine per-player end reason
        MatchEndReason p1Reason;
        MatchEndReason p2Reason;

        if (draw) {
            p1Reason = p2Reason = MatchEndReason.DRAW;
        } else if (winner.equals(match.getPlayer1())) {
            p1Reason = endReason.isWinType() ? endReason : MatchEndReason.WIN_NORMAL;
            p2Reason = endReason.correspondingLoss();
        } else {
            p1Reason = endReason.correspondingLoss();
            p2Reason = endReason.isWinType() ? endReason : MatchEndReason.WIN_NORMAL;
        }

        // Send match end responses
        notifier.accept(match.getPlayer1(), new NetPacket(
                PacketType.GAME_END_RESPONSE, "server",
                new GameEndResponse(match.getCurrentState().board(), winner, loser, p1Reason, match.getPlayer2())
        ));
        notifier.accept(match.getPlayer2(), new NetPacket(
                PacketType.GAME_END_RESPONSE, "server",
                new GameEndResponse(match.getCurrentState().board(), winner, loser, p2Reason, match.getPlayer1())
        ));

        // Notify session ended (no rematch)
        notifier.accept(match.getPlayer1(), new NetPacket(
                PacketType.MATCH_SESSION_ENDED_RESPONSE, "server",
                new MatchSessionEndedResponse(false)
        ));
        notifier.accept(match.getPlayer2(), new NetPacket(
                PacketType.MATCH_SESSION_ENDED_RESPONSE, "server",
                new MatchSessionEndedResponse(false)
        ));

        matches.remove(match.getMatchId());
    }
}
