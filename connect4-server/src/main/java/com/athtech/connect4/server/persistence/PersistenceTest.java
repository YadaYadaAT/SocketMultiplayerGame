package com.athtech.connect4.server.persistence;

public class PersistenceTest {
    public static void main(String[] args) {

        PersistenceManagerImpl pm = new PersistenceManagerImpl();

        System.out.println("=== Testing Persistence Manager ===");

        // 1. Register a new user
        System.out.println("\n-- Register Player --");
        boolean registered = pm.registerPlayer("test_user", "password123");
        System.out.println("Registered: " + registered);

        // 2. Try login (should succeed if registered)
        System.out.println("\n-- Authenticate --");
        boolean authenticated = pm.authenticate("test_user", "password123");
        System.out.println("Authenticated: " + authenticated);

        // 3. Fetch user by username
        System.out.println("\n-- Fetch by Username --");
        pm.getPlayerByUsername("test_user").ifPresentOrElse(
                p -> System.out.println("Loaded: " + p),
                () -> System.out.println("Player NOT found")
        );

        // 4. Update stats
        System.out.println("\n-- Update Stats --");
        pm.getPlayerByUsername("test_user").ifPresent(player -> {
            player.setWins(player.getWins() + 1);
            player.setGamesPlayed(player.getGamesPlayed() + 1);
            pm.updatePlayerStats(player);
            System.out.println("Stats updated.");
        });

        // 5. Show all players
        System.out.println("\n-- List All Players --");
        pm.getAllPlayers().forEach(System.out::println);

        // 6. Delete player test
        System.out.println("\n-- Delete Player --");
        pm.getPlayerByUsername("test_user").ifPresent(player -> {
            boolean deleted = pm.deletePlayer(player.getId());
            System.out.println("Deleted: " + deleted);
        });

        System.out.println("\n=== Test Finished ===");
    }
}
