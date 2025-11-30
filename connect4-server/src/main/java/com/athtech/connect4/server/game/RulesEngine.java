package com.athtech.connect4.server.game;

public interface RulesEngine {
    RulesResult validateMove(GameState state, int column, String playerId);
    boolean isGameOver(GameState state);
    String getWinner(GameState state);
}
