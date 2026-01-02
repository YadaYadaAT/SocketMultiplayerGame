package com.athtech.connect4.protocol.payload;

import com.athtech.connect4.protocol.messaging.MatchEndReason;

import java.io.Serializable;

public record GameEndResponse(
        BoardState finalBoard,  // final board state
        String winner,          // null if draw
        String loser,           // null if draw
        MatchEndReason reason,   // instead of String
        String opponent,         // opponent's username
        String player1,
        String player2
) implements Serializable { }
