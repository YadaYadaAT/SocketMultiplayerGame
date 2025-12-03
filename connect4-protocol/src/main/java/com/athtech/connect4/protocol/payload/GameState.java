package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record GameState(BoardState board, String currentPlayer) implements Serializable {}
