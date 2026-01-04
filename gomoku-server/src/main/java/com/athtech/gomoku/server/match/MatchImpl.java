package com.athtech.gomoku.server.match;

import com.athtech.gomoku.protocol.payload.BoardState;
import com.athtech.gomoku.protocol.payload.GameStateResponse;
import com.athtech.gomoku.protocol.payload.MoveRequest;
import com.athtech.gomoku.server.game.Game;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represents a single match between two players.
 * Handles game state, turn tracking, AFK timers, disconnections, and rematch voting.
 */
public class MatchImpl implements Match {

    // ─────────────────────────────────────────────
    // Match state
    // ─────────────────────────────────────────────
    private final String matchId;          // Unique identifier for this match
    private final Game game;               // Game logic and board state
    private volatile boolean finalized = false; // once true, no reconnections allowed
    private final Set<String> matchPlayers = Collections.synchronizedSet(new HashSet<>()); // Currently participating players
    private final Map<String, RematchVote> rematchVotes = Collections.synchronizedMap(new HashMap<>()); // Tracks rematch votes
    private final Map<String, RematchVote> midGameAsyncRematchVotes = Collections.synchronizedMap(new HashMap<>());
    private boolean ended = false;         // Flag indicating if the match has ended

    // ─────────────────────────────────────────────
    // Timer management
    // ─────────────────────────────────────────────
    private static final long turnSoftTimeoutMs = 30_000;   // Soft AFK warning timeout; (was set low for reviewer testing)
    private static final long turnHardTimeoutMs = 60_000;   // Hard AFK timeout ;  (was set low for reviewer testing)
    private static final long disconnectTimeoutMs = 70_000; // Disconnect grace period timeout

    private long lastMoveTime;               // Timestamp of last move for the current player(used in combination with turn)
    private boolean softTimeoutWarned = false; // Whether soft timeout warning has been sent
    Consumer<String> onPlayerAdded;
    Consumer<String> onPlayerRemoved;
    private final Map<String, Long> lastConnectionTime = new ConcurrentHashMap<>(); // Last known connection time for each player
    private final Map<String, Boolean> isPlayerConnected = new ConcurrentHashMap<>();     // Connection status of each player



    // ─────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────
    public MatchImpl(String player1, String player2 , Consumer<String> onPlayerAdded ,Consumer<String> onPlayerRemoved) {
        this.matchId = UUID.randomUUID().toString();
        this.game = new Game(player1, player2);

        matchPlayers.add(player1);
        onPlayerAdded.accept(player1);
        matchPlayers.add(player2);
        onPlayerAdded.accept(player2);

        rematchVotes.put(player1, RematchVote.PENDING);
        rematchVotes.put(player2, RematchVote.PENDING);

        midGameAsyncRematchVotes.put(player1, RematchVote.PENDING);
        midGameAsyncRematchVotes.put(player2, RematchVote.PENDING);

        lastMoveTime = System.currentTimeMillis();
        lastConnectionTime.put(player1, System.currentTimeMillis());
        lastConnectionTime.put(player2, System.currentTimeMillis());
        isPlayerConnected.put(player1, true);
        isPlayerConnected.put(player2, true);
        // Call the callback to notify MatchManager
        this.onPlayerAdded = onPlayerAdded;
        this.onPlayerRemoved = onPlayerRemoved;
    }

    // ─────────────────────────────────────────────
    // =================== TIMERS ===================
    // ─────────────────────────────────────────────


    public synchronized void checkSoftTimeout(BiConsumer<String, String> notifier) {
        if (ended) return;

        String current = game.getCurrentPlayer();
        if (!matchPlayers.contains(current) || !isPlayerConnected.getOrDefault(current, false)) return;

        long now = System.currentTimeMillis();
        long secondsLeftTillKick = (turnHardTimeoutMs - turnSoftTimeoutMs) / 1000;
        if (!softTimeoutWarned && now - lastMoveTime >= turnSoftTimeoutMs) {
            notifier.accept(current, "You have been inactive for a while, please make your move!" +
                    "\n (Seconds left before inactivity kickout :" + secondsLeftTillKick);
            softTimeoutWarned = true; // Prevent sending multiple warnings per turn
        }
    }

    public synchronized Optional<String> checkHardTimeout() {
        if (ended) return Optional.empty();

        String current = game.getCurrentPlayer();
        if (!matchPlayers.contains(current) || !isPlayerConnected.getOrDefault(current, false)) return Optional.empty();

        long now = System.currentTimeMillis();
        if (now - lastMoveTime >= turnHardTimeoutMs) {
            // Current player loses due to AFK
            String winner = current.equals(game.getPlayer1()) ? game.getPlayer2() : game.getPlayer1();
            ended = true; // Mark locally to prevent multiple triggers
            return Optional.of(winner);
        }
        return Optional.empty();
    }



    public Optional<String> checkDisconnectTimeout() {
        if (ended) return Optional.empty();

        long now = System.currentTimeMillis();

        for (String player : matchPlayers) {
            if (!isPlayerConnected.getOrDefault(player, true)) {
                long lastSeen = lastConnectionTime.getOrDefault(player, now);
                if (now - lastSeen >= disconnectTimeoutMs) {
                    ended = true;
                    return Optional.of(
                            game.getPlayer1().equals(player)
                                    ? game.getPlayer2()
                                    : game.getPlayer1()
                    );
                }
            }
        }

        return Optional.empty();
    }


    public boolean isDisconnectDraw() {
        if (ended) return false;

        for (String player : matchPlayers) {
            if (isPlayerConnected.getOrDefault(player, true)) {
                return false;
            }
        }

        ended = true;
        return true;
    }


    public synchronized void playerReconnected(String player) {
        if (!matchPlayers.contains(player)) return;
        if (finalized) return;

        lastConnectionTime.put(player, System.currentTimeMillis());
        isPlayerConnected.put(player, true);

        // Reset AFK timers if it's the player's turn
        if (player.equals(game.getCurrentPlayer())) {
            lastMoveTime = System.currentTimeMillis();
            softTimeoutWarned = false;
        }
    }

    public synchronized void markFinalized() {
        finalized = true;
    }

    public boolean isFinalized() {
        return finalized;
    }

    @Override
    public synchronized void playerDisconnected(String player) {
        if (!matchPlayers.contains(player)) return;
        isPlayerConnected.put(player, false);
        lastConnectionTime.put(player, System.currentTimeMillis());
    }

    // ─────────────────────────────────────────────
    // =================== GAME ===================
    // ─────────────────────────────────────────────


    @Override
    public synchronized boolean makeMove(String player, MoveRequest moveRequest) {
        if (!player.equals(game.getCurrentPlayer())) return false;

        boolean ok = game.makeMove(moveRequest.row(), moveRequest.col());
        if (ok) {
            lastMoveTime = System.currentTimeMillis(); // Update last move time
            softTimeoutWarned = false;                // Reset soft timeout warning
        }
        return ok;
    }


    @Override
    public boolean isFinished() {
        return game.isGameOver();
    }


    @Override
    public String getWinner() {
        if (!game.isGameOver()) return null;
        return game.getWinner();
    }


    @Override
    public String getLoser() {
        if (!game.isGameOver()) return null;
        String winner = game.getWinner();
        if (winner == null) return null;
        return winner.equals(game.getPlayer1()) ? game.getPlayer2() : game.getPlayer1();
    }


    @Override
    public boolean isDraw() {
        return game.isGameOver() && game.getWinner() == null;
    }

    @Override
    public String getMatchId() {
        return matchId;
    }

    @Override
    public String getPlayer1() {
        return game.getPlayer1();
    }

    @Override
    public String getPlayer2() {
        return game.getPlayer2();
    }

    @Override
    public String getCurrentPlayer() {
        return game.getCurrentPlayer();
    }


    @Override
    public GameStateResponse getCurrentState() {
        return new GameStateResponse(
                new BoardState(game.getBoardCopy()),
                game.getCurrentPlayer(),
                game.isGameOver(),
                game.getPlayer1(),
                game.getPlayer2(),
                Game.getWinCount()
        );
    }

    @Override
    public synchronized Set<String> getMatchPlayers() {
        return new HashSet<>(matchPlayers);
    }

    @Override
    public synchronized boolean isEnded() {
        return ended;
    }



    @Override
    public synchronized boolean markEnded() {
        ended = true;
        return true;
    }


    public static long turnSoftTimeoutMs() {
        return turnSoftTimeoutMs;
    }

    public static long turnHardTimeoutMs() {
        return turnHardTimeoutMs;
    }

    public static long disconnectTimeoutMs() {
        return disconnectTimeoutMs;
    }

    // ─────────────────────────────────────────────
    // =================== REMATCH ===================
    // ─────────────────────────────────────────────


    @Override
    public synchronized void requestRematch(String player) {

        if (!matchPlayers.contains(player)){
            throw new IllegalStateException("Player not in this match");
        }
        if (!isPlayerConnected.get(player)){
            matchPlayers.remove(player);
            onPlayerRemoved.accept(player);
            throw new IllegalStateException("You are not connected,yet you request a rematch," +
                    "we have been hacked! or we are simple really bad devs...and lazy..specially lazy since" +
                    "we do not even have different type of exceptions...");
        }

        if (game.isGameOver()){
                String opponent = rematchVotes.keySet().stream()
                        .filter(p -> !p.equals(player))
                        .findFirst()
                        .orElse(null);
                if (!isPlayerConnected.get(opponent)){
                    matchPlayers.remove(player);
                    onPlayerRemoved.accept(player);
                    throw new IllegalStateException("Cannot rematch with disconnected opponent");
                }

                if (opponent == null || rematchVotes.get(opponent) == RematchVote.DECLINED
                        || rematchVotes.get(opponent) == RematchVote.UNAVAILABLE) {
                    matchPlayers.remove(player);
                    onPlayerRemoved.accept(player);
                    throw new IllegalStateException("Rematch unavailable or declined");
                }

                rematchVotes.put(player, RematchVote.ACCEPTED);

        }else{
            var opponent = midGameAsyncRematchVotes.keySet().stream()
                    .filter(p -> !p.equals(player))
                    .findFirst()
                    .orElse(null);
            if (!isPlayerConnected.get(opponent)){
                System.out.println("");
                matchPlayers.remove(player);
                onPlayerRemoved.accept(player);
                throw new IllegalStateException("Cannot rematch with disconnected opponent");
            }

            if (opponent == null || midGameAsyncRematchVotes.get(opponent) == RematchVote.DECLINED
                    || midGameAsyncRematchVotes.get(opponent) == RematchVote.UNAVAILABLE) {
                System.out.println("");
                matchPlayers.remove(player);
                onPlayerRemoved.accept(player);
                throw new IllegalStateException("Rematch unavailable or declined");
            }

            midGameAsyncRematchVotes.put(player, RematchVote.ACCEPTED);
        }

    }



    @Override
    public synchronized void declineRematch(String player) {
        if (isEnded()){
            if (!matchPlayers.contains(player)) return;
            rematchVotes.put(player, RematchVote.DECLINED);
            matchPlayers.remove(player);
            onPlayerRemoved.accept(player);
        }else{


        }


    }


    @Override
    public synchronized boolean isRematchReady() {
        if (isEnded()){
            return rematchVotes.size() == 2 &&
                    rematchVotes.values().stream()
                            .allMatch(v -> v == RematchVote.ACCEPTED);
        }else{
            return midGameAsyncRematchVotes.size() == 2 &&
                    midGameAsyncRematchVotes.values().stream()
                            .allMatch(v -> v == RematchVote.ACCEPTED);
        }

    }

    @Override
    public synchronized String getOpponent(String player) {
        if (!matchPlayers.contains(player)) return null;
        for (String p : matchPlayers) {
            if (!p.equals(player)) return p;
        }
        return null; // just in case
    }

    @Override
    public synchronized RematchVote getRematchOutcome() {
        if (rematchVotes.values().contains(RematchVote.DECLINED))
            return RematchVote.DECLINED;
        if (rematchVotes.values().contains(RematchVote.UNAVAILABLE))
            return RematchVote.UNAVAILABLE;
        return null;
    }


    @Override
    public synchronized void resetRematchRequests() {
        rematchVotes.replaceAll((k, v) -> RematchVote.PENDING);
    }

    public long getLastMoveTime() {
        return lastMoveTime;
    }
}
