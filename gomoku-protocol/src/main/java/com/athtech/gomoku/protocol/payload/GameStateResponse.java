package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record GameStateResponse(
        BoardState board,
        String currentPlayer,
        boolean gameOver,
        String player1,
        String player2
) implements Serializable {}
