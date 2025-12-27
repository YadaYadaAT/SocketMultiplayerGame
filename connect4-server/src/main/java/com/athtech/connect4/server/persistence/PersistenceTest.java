package com.athtech.connect4.server.persistence;

import java.util.List;

public class PersistenceTest {
    public static void main(String[] args) {

        PersistenceManagerImpl pm = new PersistenceManagerImpl();

        System.out.println("=== Testing Persistence Manager ===");

        String testUsername = "test_user";
        String testPassword = "password123";

        // 1. Register a new user
        System.out.println("\n-- Register Player --");
        boolean registered = pm.registerPlayer(testUsername, testPassword);
        System.out.println("Registered: " + registered);

        // 2. Authenticate (login)
        System.out.println("\n-- Authenticate --");
        boolean authenticated = pm.authenticate(testUsername, testPassword);
        System.out.println("Authenticated: " + authenticated);

        // 3. Fetch user by username
        System.out.println("\n-- Fetch by Username --");
        pm.getPlayerByUsername(testUsername).ifPresentOrElse(
                p -> System.out.println("Loaded: " + p.getUsername() + " | Wins: " + p.getWins() +
                        ", Losses: " + p.getLosses() + ", Draws: " + p.getDraws() +
                        ", Games Played: " + p.getGamesPlayed()),
                () -> System.out.println("Player NOT found")
        );

        // 4. Update stats
        System.out.println("\n-- Update Stats --");
        pm.getPlayerByUsername(testUsername).ifPresent(player -> {
            player.incrementWins(); // increments wins and games played
            pm.updatePlayerStats(player);
            System.out.println("Stats updated: Wins=" + player.getWins() + ", GamesPlayed=" + player.getGamesPlayed());
        });

        // 5. Show all players
        System.out.println("\n-- List All Players --");
        List<Player> allPlayers = pm.getAllPlayers();
        if (allPlayers.isEmpty()) {
            System.out.println("No players found.");
        } else {
            allPlayers.forEach(p -> System.out.println(
                    p.getUsername() + " | Wins: " + p.getWins() +
                            ", Losses: " + p.getLosses() +
                            ", Draws: " + p.getDraws() +
                            ", Games Played: " + p.getGamesPlayed()
            ));
        }

        // 6. Delete player
        System.out.println("\n-- Delete Player --");
        pm.getPlayerByUsername(testUsername).ifPresent(player -> {
            boolean deleted = pm.deletePlayer(player.getId());
            System.out.println("Deleted: " + deleted);
        });

        System.out.println("\n=== Test Finished ===");
    }
}
