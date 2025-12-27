package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record GameStateResponse(BoardState board, String currentPlayer, boolean gameOver) implements Serializable {}
