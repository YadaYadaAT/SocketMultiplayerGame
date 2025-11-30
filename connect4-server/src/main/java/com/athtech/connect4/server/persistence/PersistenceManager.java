package com.athtech.connect4.server.persistence;

import java.util.List;

public interface PersistenceManager {
    void savePlayer(PlayerRecord player);
    PlayerRecord loadPlayer(String username);
    void saveScore(ScoreEntry score);
    List<ScoreEntry> getScores(String playerId);
}
