package com.athtech.gomoku.server.persistence;

public record ScoreEntry(String gameId, String winnerId, String loserId) {
}

