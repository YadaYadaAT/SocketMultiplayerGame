package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record GameStartResponse(BoardState board, String currentPlayer, boolean gameOver) implements Serializable {}