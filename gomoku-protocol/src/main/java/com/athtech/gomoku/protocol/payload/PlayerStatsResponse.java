package com.athtech.gomoku.protocol.payload;
//STUDENTS-CODE-NUMBER : CSY-22115
import java.io.Serializable;

public record PlayerStatsResponse(
        int gamesPlayed,
        int wins,
        int losses,
        int draws
) implements Serializable {}
