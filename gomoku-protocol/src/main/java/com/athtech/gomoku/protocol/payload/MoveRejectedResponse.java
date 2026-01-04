package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record MoveRejectedResponse(
        String reason,
        String currentPlayer
) implements Serializable {}
