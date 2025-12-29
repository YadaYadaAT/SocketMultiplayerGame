package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.payload.BoardState;
import com.athtech.connect4.protocol.payload.GameStateResponse;
import com.athtech.connect4.protocol.payload.MoveRequest;
import com.athtech.connect4.server.game.Game;

import java.util.UUID;

public class MatchImpl implements Match {

    private final String matchId;
    private final String player1;
    private final String player2;
    private final Game game;
    private boolean ended = false;
    private long lastActivityTime;

    public MatchImpl(String p1, String p2) {
        this.matchId = UUID.randomUUID().toString();
        this.player1 = p1;
        this.player2 = p2;
        this.game = new Game(p1, p2);
        touch();
    }

    @Override
    public void touch() {
        lastActivityTime = System.currentTimeMillis();
    }

    @Override
    public boolean isInactive(long timeoutMs) {
        return System.currentTimeMillis() - lastActivityTime > timeoutMs;
    }

    @Override
    public String getMatchId() { return matchId; }
    @Override
    public String getPlayer1() { return player1; }
    @Override
    public String getPlayer2() { return player2; }
    @Override
    public String getCurrentPlayer() { return game.getCurrentPlayer(); }

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

    @Override
    public synchronized boolean markEnded() {
        if (ended) return false;
        ended = true;
        return true;
    }
}
