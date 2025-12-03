package com.athtech.connect4.server.persistence;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PersistenceManagerImpl implements PersistenceManager {

    private final Connection connection;

    public PersistenceManagerImpl() {
        try {
            // Connect to embedded H2 database stored at ./data/connect4
            // AUTO_SERVER=TRUE allows multiple connections (useful for testing)
            connection = DriverManager.getConnection("jdbc:h2:./data/connect4;AUTO_SERVER=TRUE", "sa", "");
            initDatabase(); // Create tables if not exist
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to database", e);
        }
    }

    private void initDatabase() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // TODO: Create "players" table with all necessary fields
            // id (PK), username (unique), password_hash, wins, losses, draws, games_played
            // stmt.execute("CREATE TABLE IF NOT EXISTS players (...)");
        }
    }

    @Override
    public boolean registerPlayer(String username, String password) {
        // TODO: Insert new player into database
        // - Generate a UUID for the player ID
        // - Use the method hashPassword(...) below to hash the password before storing
        // - Return true if success, false if username exists
        return false;
    }

    @Override
    public boolean authenticate(String username, String password) {
        // TODO: Check username and password hash
        // - Fetch player row by username
        // - Use checkPassword(...) method below to compare input password to stored hash
        // - Return Player object if match, else Optional.empty()
        return true;
    }

    @Override
    public Optional<Player> getPlayerById(String id) {
        // TODO: Fetch player by their UUID
        // - Return Optional<Player>
        return Optional.empty();
    }

    @Override
    public Optional<Player> getPlayerByUsername(String username) {
        // TODO: Fetch player by username
        // - Return Optional<Player>
        return Optional.empty();
    }

    @Override
    public void updatePlayerStats(Player player) {
        // TODO: Update stats (wins, losses, draws, gamesPlayed) in database
        // - Use player's ID to update the row
        // - Ensure atomic update to prevent race conditions
    }

    @Override
    public void updatePassword(Player player) {
        // TODO: Update the password in the database
        // - Use hashPassword(...) method below to hash the new password before storing
        // - Use player's ID to identify row
    }

    @Override
    public List<Player> getAllPlayers() {
        // TODO: Fetch all players for lobby display
        // - Return as List<Player>
        return List.of();
    }

    // Optional additional methods:

    public boolean deletePlayer(String id) {
        // TODO: Remove player from database
        // - Ensure no ongoing session is holding this player
        return false;
    }



    // -------------------------
    // HASHING METHODS
    // -------------------------
    private String hashPassword(String plainPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plainPassword.getBytes());
            return Base64.getEncoder().encodeToString(hashBytes); // store Base64 string
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private boolean checkPassword(String plainPassword, String storedHash) {
        String hashed = hashPassword(plainPassword); // hash input password
        return hashed.equals(storedHash);           // compare to stored hash
    }

}
