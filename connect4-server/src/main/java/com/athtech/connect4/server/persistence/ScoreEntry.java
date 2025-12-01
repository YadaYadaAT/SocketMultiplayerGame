package com.athtech.connect4.server.persistence;

public record ScoreEntry(String gameId, String winnerId, String loserId) {
}

