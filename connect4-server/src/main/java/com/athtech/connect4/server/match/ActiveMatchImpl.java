package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.payload.BoardState;
import com.athtech.connect4.protocol.payload.GameState;
import com.athtech.connect4.protocol.payload.Move;
import com.athtech.connect4.server.game.Game;

public class ActiveMatchImpl implements ActiveMatch{
    private final String player1;
    private final String player2;
    private final Game game;

    public ActiveMatchImpl(String p1, String p2) {
        this.player1 = p1;
        this.player2 = p2;
        this.game = new Game();
    }

    public GameState getCurrentState() {
        return new GameState(
                new BoardState(game.getBoardCopy()),
                game.getCurrentPlayer(),
                game.isGameOver()
        );
    }

    @Override
    public String getMatchId() {
        return "";
    }

    public String getPlayer1() {
        return player1;
    }
    public String getPlayer2() {
        return player2;
    }

    @Override
    public String getCurrentTurn() {
        return "";
    }

    public boolean makeMove(String player, Move move) {
        if (!player.equals(game.getCurrentPlayer())) return false;
        return game.makeMove(move.row(),move.col());
    }

    @Override
    public boolean isFinished() {
        return false;
    }
}
