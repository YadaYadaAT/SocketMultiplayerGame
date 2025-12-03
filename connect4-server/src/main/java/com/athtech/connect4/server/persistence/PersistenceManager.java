package com.athtech.connect4.server.persistence;

import java.util.List;
import java.util.Optional;

public interface PersistenceManager {
    boolean registerPlayer(String username, String password);
    boolean authenticate(String username, String password);
    Optional<Player> getPlayerById(String id);
    Optional<Player> getPlayerByUsername(String username);
    void updatePlayerStats(Player player); // save wins/losses/draws/gamesPlayed
    void updatePassword(Player player); // if password changes
    List<Player> getAllPlayers();
}
