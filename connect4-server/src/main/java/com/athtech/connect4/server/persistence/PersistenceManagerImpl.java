package com.athtech.connect4.server.persistence;

import com.athtech.connect4.protocol.payload.PlayerStatsResponse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.*;
import java.util.Base64;

public class PersistenceManagerImpl implements PersistenceManager {

    private final Connection connection;

    public PersistenceManagerImpl() {
        try {
            Class.forName("org.h2.Driver");//In production user and password would have been set through env variables
            connection = DriverManager.getConnection("jdbc:h2:./data/connect4;AUTO_SERVER=TRUE", "sa", "");
            initDatabase();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to connect to database", e);
        }
    }

    private void initDatabase() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    id VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    password_hash VARCHAR(256) NOT NULL,
                    wins INT DEFAULT 0,
                    losses INT DEFAULT 0,
                    draws INT DEFAULT 0,
                    games_played INT DEFAULT 0,
                    relog_code VARCHAR(36)
                )
            """);
        }
    }

    @Override
    public boolean registerPlayer(String username, String password) {
        String sql = "INSERT INTO players (id, username, password_hash) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, username);
            ps.setString(3, hashPassword(password));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            // likely duplicate username
            return false;
        }
    }

    @Override
    public boolean authenticate(String username, String password) {
        String sql = "SELECT password_hash FROM players WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                return checkPassword(password, storedHash);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public Optional<Player> getPlayerById(String id) {
        String sql = "SELECT * FROM players WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(extractPlayer(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public Optional<Player> getPlayerByUsername(String username) {
        String sql = "SELECT * FROM players WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(extractPlayer(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public void updatePlayerStats(Player player) {
        String sql = "UPDATE players SET wins=?, losses=?, draws=?, games_played=? WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, player.getWins());
            ps.setInt(2, player.getLosses());
            ps.setInt(3, player.getDraws());
            ps.setInt(4, player.getGamesPlayed());
            ps.setString(5, player.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updatePassword(Player player) {
        String sql = "UPDATE players SET password_hash=? WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, hashPassword(player.getPassword()));
            ps.setString(2, player.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Player> getAllPlayers() {
        List<Player> players = new ArrayList<>();
        String sql = "SELECT * FROM players";
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) players.add(extractPlayer(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return players;
    }

    @Override
    public PlayerStatsResponse getPlayerStats(String username) {
        String sql = "SELECT wins, losses, draws, games_played FROM players WHERE username=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerStatsResponse(
                        rs.getInt("games_played"),
                        rs.getInt("wins"),
                        rs.getInt("losses"),
                        rs.getInt("draws")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new PlayerStatsResponse(0,0,0,0);
    }

    @Override
    public boolean deletePlayer(String id) {
        String sql = "DELETE FROM players WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void setRelogCode(Player player, String relogCode) {
        String sql = "UPDATE players SET relog_code=? WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, relogCode);
            ps.setString(2, player.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    @Override
    public Optional<String> getRelogCode(String username) {
        String sql = "SELECT relog_code FROM players WHERE username=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.ofNullable(rs.getString("relog_code"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }


    @Override
    public synchronized void recordMatchResult(String player1, String player2, boolean draw, String winner) {
        try {
            connection.setAutoCommit(false);

            if (draw) {
                // both players: draws +1, games_played +1
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE players SET draws = draws + 1, games_played = games_played + 1 WHERE username = ?"
                )) {
                    ps.setString(1, player1);
                    ps.executeUpdate();
                    ps.setString(1, player2);
                    ps.executeUpdate();
                }
            } else {
                String loser = winner.equals(player1) ? player2 : player1;

                // winner: wins +1, games_played +1
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE players SET wins = wins + 1, games_played = games_played + 1 WHERE username = ?"
                )) {
                    ps.setString(1, winner);
                    ps.executeUpdate();
                }

                // loser: losses +1, games_played +1
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE players SET losses = losses + 1, games_played = games_played + 1 WHERE username = ?"
                )) {
                    ps.setString(1, loser);
                    ps.executeUpdate();
                }
            }

            connection.commit();
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("Failed to record match result", e);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // -------------------------
    // HASHING METHODS
    // -------------------------
    private String hashPassword(String plainPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plainPassword.getBytes());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private boolean checkPassword(String plainPassword, String storedHash) {
        return hashPassword(plainPassword).equals(storedHash);
    }

    private Player extractPlayer(ResultSet rs) throws SQLException {
        return new Player(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getInt("wins"),
                rs.getInt("losses"),
                rs.getInt("draws"),
                rs.getInt("games_played")
        );
    }




}
