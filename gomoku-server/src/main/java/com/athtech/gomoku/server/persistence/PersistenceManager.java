package com.athtech.gomoku.server.persistence;

import com.athtech.gomoku.protocol.payload.PlayerStatsResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PersistenceManager {
    boolean registerPlayer(String username, String password , String nickname ,  Instant createdAt);
    boolean authenticate(String username, String password);
    Optional<Player> getPlayerById(String id);
    Optional<Player> getPlayerByUsername(String username);
    void updatePlayerStats(Player player); // save wins/losses/draws/gamesPlayed
    void updatePassword(Player player); // if password changes
    List<Player> getAllPlayers();
    PlayerStatsResponse getPlayerStats(String username);
    boolean deletePlayer(String id);
    void setRelogCode(Player player, String relogCode);
    void setRelogCode(String username, String relogCode);
    Optional<String> getRelogCode(String username);
    void recordMatchResult(String player1, String player2, boolean draw, String winner);
}
