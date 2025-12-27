package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record GameEndResponse(
        BoardState finalBoard,  // final board state
        String winner,          // null if draw
        String loser,           // null if draw
        String reason,          // "Win/Loss", "Draw", "Timeout", etc.
        String opponent         // opponent's username
) implements Serializable { }
