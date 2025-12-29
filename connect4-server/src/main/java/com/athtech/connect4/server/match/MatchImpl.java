package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.payload.BoardState;
import com.athtech.connect4.protocol.payload.GameStateResponse;
import com.athtech.connect4.protocol.payload.MoveRequest;
import com.athtech.connect4.server.game.Game;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MatchImpl implements Match {

    private final String matchId;
    private final Game game;
    private final Set<String> activePlayers =
            Collections.synchronizedSet(new HashSet<>());

    // null = waiting for response
    private final Map<String, RematchDecision> rematchDecisions =
            Collections.synchronizedMap(new HashMap<>());

    private boolean ended = false;
    private long lastActivityTime;

    public MatchImpl(String player1, String player2) {
        this.matchId = UUID.randomUUID().toString();
        this.game = new Game(player1, player2);
        activePlayers.add(player1);
        activePlayers.add(player2);
        rematchDecisions.put(player1, null);
        rematchDecisions.put(player2, null);

        touch();
    }


    @Override
    public synchronized void requestRematch(String player) {
        if (ended || !activePlayers.contains(player)) return;

        RematchDecision current = rematchDecisions.get(player);
        if (current == RematchDecision.DECLINED ||
                current == RematchDecision.UNAVAILABLE) {
            throw new IllegalStateException("Rematch no longer possible");
        }

        rematchDecisions.put(player, RematchDecision.ACCEPTED);
    }

    @Override
    public synchronized void declineRematch(String player) {
        if (ended || !activePlayers.contains(player)) return;

        rematchDecisions.put(player, RematchDecision.DECLINED);
        removePlayer(player);
    }

    @Override
    public synchronized void markUnavailable(String player) {
        if (ended || !activePlayers.contains(player)) return;

        rematchDecisions.put(player, RematchDecision.UNAVAILABLE);
        removePlayer(player);
    }

    @Override
    public synchronized boolean isRematchReady() {
        return !rematchDecisions.isEmpty() &&
                rematchDecisions.values().stream()
                        .allMatch(d -> d == RematchDecision.ACCEPTED);
    }

    @Override
    public synchronized RematchDecision getRematchOutcome() {
        if (rematchDecisions.values().contains(RematchDecision.DECLINED))
            return RematchDecision.DECLINED;

        if (rematchDecisions.values().contains(RematchDecision.UNAVAILABLE))
            return RematchDecision.UNAVAILABLE;

        return null;
    }

    @Override
    public synchronized void resetRematchRequests() {
        rematchDecisions.replaceAll((k, v) -> null);
    }

    // Player / match removal

    private synchronized void removePlayer(String player) {
        activePlayers.remove(player);
        rematchDecisions.remove(player);

        if (activePlayers.isEmpty()) {
            ended = true;
        }
    }

    @Override
    public synchronized Set<String> getActivePlayers() {
        return new HashSet<>(activePlayers);
    }

    @Override
    public synchronized boolean markEnded() {
        if (ended) return false;
        ended = true;
        return true;
    }

    @Override
    public synchronized boolean isEnded() {
        return ended;
    }

    // Match / Game info

    @Override
    public void touch() {
        lastActivityTime = System.currentTimeMillis();
    }

    @Override
    public boolean isInactive(long timeoutMs) {
        return System.currentTimeMillis() - lastActivityTime > timeoutMs;
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
                game.isGameOver()
        );
    }

    @Override
    public boolean makeMove(String player, MoveRequest moveRequest) {
        touch();
        if (!player.equals(game.getCurrentPlayer())) return false;
        return game.makeMove(moveRequest.row(), moveRequest.col());
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
        return winner.equals(getPlayer1()) ? getPlayer2() : getPlayer1();
    }

    @Override
    public boolean isDraw() {
        return game.isGameOver() && game.getWinner() == null;
    }
}
