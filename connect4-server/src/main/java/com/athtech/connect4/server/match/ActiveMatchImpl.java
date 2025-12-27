package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.payload.BoardState;
import com.athtech.connect4.protocol.payload.GameStateResponse;
import com.athtech.connect4.protocol.payload.MoveRequest;
import com.athtech.connect4.server.game.Game;

import java.util.UUID;

public class ActiveMatchImpl implements ActiveMatch {

    private final String matchId;
    private final String player1;
    private final String player2;
    private final Game game;

    // Rematch state
    private boolean rematchRequestedByP1 = false;
    private boolean rematchRequestedByP2 = false;
    private boolean rematchLocked = false; // no new requests after timeout or decision
    private long rematchStartTime = 0; // timestamp when first rematch request comes in
    private static final long REMATCH_TIMEOUT_MS = 30_000; // 30 sec

    public ActiveMatchImpl(String p1, String p2) {
        this.matchId = UUID.randomUUID().toString();
        this.player1 = p1;
        this.player2 = p2;
        this.game = new Game(p1, p2);
    }

    @Override
    public String getMatchId() { return matchId; }

    @Override
    public String getPlayer1() { return player1; }

    @Override
    public String getPlayer2() { return player2; }

    @Override
    public String getCurrentTurn() { return game.getCurrentPlayer(); }

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
        if (!player.equals(game.getCurrentPlayer())) return false;
        return game.makeMove(moveRequest.row(), moveRequest.col());
    }

    @Override
    public boolean isFinished() { return game.isGameOver(); }

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
        return winner.equals(player1) ? player2 : player1;
    }

    @Override
    public boolean isDraw() {
        return game.isGameOver() && game.getWinner() == null;
    }

    // --------------------------
    // REMATCH METHODS
    // --------------------------

    @Override
    public synchronized void requestRematch(String player) {
        if (rematchLocked) return;
        if (rematchStartTime == 0) rematchStartTime = System.currentTimeMillis();

        if (player.equals(player1)) rematchRequestedByP1 = true;
        else if (player.equals(player2)) rematchRequestedByP2 = true;
    }

    @Override
    public synchronized void cancelRematch(String player) {
        if (rematchLocked) return;
        rematchLocked = true;
        rematchRequestedByP1 = false;
        rematchRequestedByP2 = false;
    }

    @Override
    public synchronized boolean canStartRematch() {
        if (rematchLocked) return false;
        if (rematchRequestedByP1 && rematchRequestedByP2) {
            rematchLocked = true;
            return true;
        }
        if (rematchStartTime > 0 && System.currentTimeMillis() - rematchStartTime > REMATCH_TIMEOUT_MS) {
            // Timeout reached, cancel rematch
            rematchLocked = true;
            rematchRequestedByP1 = false;
            rematchRequestedByP2 = false;
            return false;
        }
        return false;
    }




}
