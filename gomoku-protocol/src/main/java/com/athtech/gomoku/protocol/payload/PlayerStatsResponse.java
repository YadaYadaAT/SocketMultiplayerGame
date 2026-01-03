package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record PlayerStatsResponse(
        int gamesPlayed,
        int wins,
        int losses,
        int draws
) implements Serializable {}
