package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record GameStateResponse(
        BoardState board, // 2D Array of current gamestate
        String currentPlayer, // Whose turn it is
        boolean gameOver, // Allows triggering a rematch or exiting the game
        String player1, // Player 1 - Always the person who triggers the invite
        String player2, // Player 2 - Always the person who accepts an invite
        int winCount, // How many pieces need to be connected in order to win the game (e.g. "Connect [4] pieces to win the game")
        long version // The version of the game state response. Used to ensure game state validity for the client in case of synchronization problems. Auto-incremented.
) implements Serializable {}
